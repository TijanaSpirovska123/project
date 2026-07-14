package com.example.marketing.insights.service;

import com.example.marketing.insights.dto.CompareRequestDto;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.mapper.InsightsSnapshotMapper;
import com.example.marketing.insights.repository.InsightMetricRepository;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.strategy.InsightsFetchStrategy;
import com.example.marketing.insights.strategy.InsightsFetchStrategyRegistry;
import com.example.marketing.insights.util.FetchMode;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final InsightSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final InsightsFetchStrategyRegistry strategyRegistry;
    private final InsightsSnapshotMapper mapper;
    private final ObjectMapper objectMapper;
    private final InsightMetricRepository metricRepository;

    // -----------------------------------------------------------------------
    // Sync - Fetch from platform API and save
    // -----------------------------------------------------------------------

    /**
     * Minimal fields for breakdown-dimension calls.
     * Must NOT include action-array fields (actions, action_values, etc.) because those
     * add an implicit "action_type" dimension, which Meta rejects when combined with
     * demographic or placement breakdowns.
     */
    private static final List<String> BREAKDOWN_FETCH_FIELDS = List.of(
            "impressions", "reach", "clicks", "spend", "date_start", "date_stop"
    );

    /**
     * Valid Meta API breakdown-dimension groups — one API call per entry.
     * Meta does not allow mixing demographics, geography, and placement in one call.
     */
    private static final Map<String, List<String>> BREAKDOWN_GROUPS;
    static {
        BREAKDOWN_GROUPS = new LinkedHashMap<>();
        BREAKDOWN_GROUPS.put("age_gender", List.of("age", "gender"));
        BREAKDOWN_GROUPS.put("country",    List.of("country"));
        BREAKDOWN_GROUPS.put("placement",  List.of("impression_device", "publisher_platform"));
    }

    /** Runs the (up to 3) breakdown-group Meta API calls concurrently instead of sequentially. */
    private static final ExecutorService BREAKDOWN_EXECUTOR =
            Executors.newFixedThreadPool(BREAKDOWN_GROUPS.size());

    /** Maps each breakdown dimension to the group key used in breakdownsJson. */
    private static final Map<String, String> DIMENSION_TO_GROUP = Map.ofEntries(
            Map.entry("age",                "age_gender"),
            Map.entry("gender",             "age_gender"),
            Map.entry("country",            "country"),
            Map.entry("impression_device",  "placement"),
            Map.entry("publisher_platform", "placement"),
            // "placement" is a UI-level dimension key — Meta itself never returns a field
            // literally called "placement", only impression_device + publisher_platform.
            Map.entry("placement",          "placement"),
            // "device" and "os" both derive from the same impression_device field the
            // placement group already fetches — no separate Meta call needed.
            Map.entry("device",             "placement"),
            Map.entry("os",                 "placement")
    );

    @Transactional
    public List<InsightSnapshotDto> sync(Long userId, InsightSyncRequestDto req) {
        validateSyncRequest(req);

        UserEntity user = getUserOrThrow(userId);
        InsightsFetchStrategy strategy = strategyRegistry.of(req.getProvider());
        FetchMode mode = Optional.ofNullable(req.getFetchMode()).orElse(FetchMode.PER_OBJECT);

        List<String> fields = resolveFields(req, strategy, mode);
        int timeInc = Optional.ofNullable(req.getTimeIncrement()).orElse(1);

        // Main sync — no breakdowns param (breakdowns fetched separately below
        // to avoid Meta's invalid-combination error)
        req.setBreakdowns(null);
        Map<String, String> queryParams = strategy.buildQueryParams(req, fields, timeInc);

        List<InsightSnapshotDto> results = switch (mode) {
            case BATCH_IDS -> syncBatch(user, req, queryParams);
            case ACCOUNT   -> syncAccount(user, req, queryParams, strategy);
            default        -> syncPerObject(user, req, queryParams, strategy);
        };

        // Post-sync: fetch demographic/placement breakdown data with separate calls
        // per valid dimension group and store in each snapshot's breakdownsJson.
        if (mode != FetchMode.ACCOUNT) {
            try {
                fetchAndStoreBreakdowns(user, req, strategy);
            } catch (Exception e) {
                logBreakdownFailure("post-sync", e);
            }
        }

        return results;
    }

    // -----------------------------------------------------------------------
    // Breakdown post-sync helpers
    // -----------------------------------------------------------------------

    private void fetchAndStoreBreakdowns(UserEntity user, InsightSyncRequestDto req,
            InsightsFetchStrategy strategy) {

        List<String> objectIds = req.getObjectExternalIds();
        if (objectIds == null || objectIds.isEmpty()) return;

        List<String> validIds = objectIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        if (validIds.isEmpty()) return;

        int storedTimeInc = Optional.ofNullable(req.getTimeIncrement()).orElse(1);

        // Fire all breakdown-group calls concurrently — they're independent Meta API
        // requests (age/gender, country, placement can't be combined in one call), so
        // running them in parallel cuts wait time from ~3x a single call down to ~1x.
        List<CompletableFuture<AbstractMap.SimpleEntry<String, Map<String, Map<String, Object>>>>> futures =
                BREAKDOWN_GROUPS.entrySet().stream()
                        .map(groupEntry -> CompletableFuture.supplyAsync(
                                () -> fetchOneBreakdownGroup(user, req, strategy, validIds, groupEntry),
                                BREAKDOWN_EXECUTOR))
                        .collect(Collectors.toList());

        // Combine each object's rows across all 3 groups first, so every snapshot gets
        // exactly ONE read-modify-write instead of one per group (also avoids a lost-update
        // race that per-group writes would have if this loop were ever parallelized too).
        Map<String, Map<String, List<Object>>> rowsByObjectThenGroup = new LinkedHashMap<>();
        for (var future : futures) {
            AbstractMap.SimpleEntry<String, Map<String, Map<String, Object>>> entry = future.join();
            String groupKey = entry.getKey();
            entry.getValue().forEach((objectId, body) -> {
                @SuppressWarnings("unchecked")
                List<Object> rows = (List<Object>) body.getOrDefault("data", List.of());
                rowsByObjectThenGroup
                        .computeIfAbsent(objectId, k -> new LinkedHashMap<>())
                        .put(groupKey, rows);
            });
        }

        rowsByObjectThenGroup.forEach((objectId, groupRows) ->
                mergeBreakdownGroupsIntoSnapshot(user, req, objectId, storedTimeInc, groupRows));
    }

    private AbstractMap.SimpleEntry<String, Map<String, Map<String, Object>>> fetchOneBreakdownGroup(
            UserEntity user, InsightSyncRequestDto req, InsightsFetchStrategy strategy,
            List<String> validIds, Map.Entry<String, List<String>> groupEntry) {

        String groupKey      = groupEntry.getKey();
        String breakdownsVal = String.join(",", groupEntry.getValue());

        InsightSyncRequestDto bReq   = buildBreakdownRequest(req, breakdownsVal);
        Map<String, String>   bParams = strategy.buildQueryParams(bReq, BREAKDOWN_FETCH_FIELDS, 0);

        try {
            // Reuse fetchForObjects — it batches up to 50 IDs per call automatically
            Map<String, Map<String, Object>> batchResult = strategy.fetchForObjects(user, validIds, bParams);
            return new AbstractMap.SimpleEntry<>(groupKey, batchResult);
        } catch (Exception e) {
            logBreakdownFailure("group '" + groupKey + "'", e);
            return new AbstractMap.SimpleEntry<>(groupKey, Map.of());
        }
    }

    /**
     * Breakdown fetch failures are swallowed (non-fatal — the main sync already
     * succeeded), so this is the only place the real cause is visible. RestTemplate
     * throws HttpStatusCodeException before InsightsFetchStrategy ever sees the
     * response, so the Meta error body (with its rate-limit code) is only reachable
     * here via getResponseBodyAsString() — e.getMessage() alone omits it.
     */
    private void logBreakdownFailure(String what, Exception e) {
        if (e instanceof HttpStatusCodeException httpEx) {
            String responseBody = httpEx.getResponseBodyAsString();
            boolean rateLimited = responseBody != null && (
                    responseBody.contains("\"code\":80004")
                            || responseBody.contains("\"code\":17")
                            || responseBody.contains("\"code\":613")
                            || responseBody.toLowerCase().contains("too many calls"));
            if (rateLimited) {
                log.warn("Breakdown {} failed due to Meta rate limiting [{}]: {}",
                        what, httpEx.getStatusCode(), responseBody);
            } else {
                log.warn("Breakdown {} failed [{}]: {}", what, httpEx.getStatusCode(), responseBody);
            }
        } else {
            log.warn("Breakdown {} failed ({}): {}", what, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private InsightSyncRequestDto buildBreakdownRequest(InsightSyncRequestDto original,
            String breakdownsValue) {
        InsightSyncRequestDto bReq = new InsightSyncRequestDto();
        bReq.setProvider(original.getProvider());
        bReq.setAdAccountId(original.getAdAccountId());
        bReq.setObjectType(original.getObjectType());
        bReq.setDateStart(original.getDateStart());
        bReq.setDateStop(original.getDateStop());
        bReq.setDatePreset(original.getDatePreset());
        // No time_increment — breakdown calls return dimension totals for the whole range
        bReq.setBreakdowns(new LinkedHashMap<>(Map.of("breakdowns", breakdownsValue)));
        bReq.setLimit(500); // enough to cover all dimension values in one page
        return bReq;
    }

    @SuppressWarnings("unchecked")
    private void mergeBreakdownGroupsIntoSnapshot(UserEntity user, InsightSyncRequestDto req,
            String objectId, int storedTimeInc, Map<String, List<Object>> newGroupRows) {

        snapshotRepository
                .findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateStartAndDateStopAndTimeIncrement(
                        user, req.getProvider(), req.getAdAccountId(), req.getObjectType(),
                        objectId, req.getDateStart(), req.getDateStop(), storedTimeInc)
                .ifPresent(snapshot -> {
                    Map<String, Object> breakdownMap = new LinkedHashMap<>();

                    // Preserve already-stored breakdown groups (if in the new keyed format)
                    if (snapshot.getBreakdownsJson() != null) {
                        try {
                            Map<String, Object> existing =
                                    objectMapper.readValue(snapshot.getBreakdownsJson(), Map.class);
                            if (existing.containsKey("age_gender")
                                    || existing.containsKey("country")
                                    || existing.containsKey("placement")) {
                                breakdownMap.putAll(existing);
                            }
                        } catch (Exception ignored) {}
                    }

                    breakdownMap.putAll(newGroupRows);
                    snapshot.setBreakdownsJson(serializeToJson(breakdownMap));
                    snapshotRepository.save(snapshot);
                });
    }

    /**
     * Resolves breakdown rows for a given dimension from a snapshot.
     * Prefers the structured breakdownsJson (new format); falls back to rawJson data
     * array (legacy — only works if the snapshot was synced with that breakdown dimension).
     */
    @SuppressWarnings("unchecked")
    private List<Object> resolveBreakdownRows(InsightSnapshotEntity snap, String dimension) {
        // 1. Try new structured breakdownsJson
        if (snap.getBreakdownsJson() != null) {
            try {
                Map<String, Object> bj = objectMapper.readValue(snap.getBreakdownsJson(), Map.class);
                String groupKey = DIMENSION_TO_GROUP.get(dimension);
                if (groupKey != null && bj.containsKey(groupKey)) {
                    Object rows = bj.get(groupKey);
                    if (rows instanceof List) return (List<Object>) rows;
                }
            } catch (Exception ignored) {}
        }

        // 2. Fallback: rawJson data array (legacy)
        if (snap.getRawJson() != null) {
            try {
                Map<String, Object> raw = objectMapper.readValue(snap.getRawJson(), Map.class);
                List<Object> dataList = (List<Object>) raw.get("data");
                return dataList != null ? dataList : List.of();
            } catch (Exception ignored) {}
        }

        return List.of();
    }

    /**
     * Reads the dimension value off a breakdown row. Several UI-level dimension keys
     * have no field of that literal name in Meta's response and must be derived from
     * whatever field the row actually carries.
     */
    private Object extractDimensionValue(Map<String, Object> row, String dimension) {
        return switch (dimension) {
            // placement/device share the same impression_device+publisher_platform row shape
            case "placement" -> row.get("publisher_platform");
            case "device"    -> row.get("impression_device");
            case "os"        -> classifyOs((String) row.get("impression_device"));
            // "day of week" isn't a Meta breakdown at all — derived from the daily
            // date_start already present on main (non-breakdown-group) time-series rows.
            case "dow"       -> dayOfWeekFromDate((String) row.get("date_start"));
            default          -> row.get(dimension);
        };
    }

    /**
     * Meta has no dedicated OS breakdown — this is a best-effort classification of the
     * impression_device value (e.g. "iphone", "android_smartphone") into iOS/Android/
     * Desktop/Other. Approximate by nature; labeled as such in the UI.
     */
    private static String classifyOs(String impressionDevice) {
        if (impressionDevice == null || impressionDevice.isBlank()) return null;
        String d = impressionDevice.toLowerCase(Locale.ROOT);
        if (d.contains("iphone") || d.contains("ipad") || d.contains("ipod") || d.contains("ios")) return "iOS";
        if (d.contains("android")) return "Android";
        if (d.contains("desktop")) return "Desktop";
        return "Other";
    }

    private static String dayOfWeekFromDate(String dateStart) {
        if (dateStart == null || dateStart.isBlank()) return null;
        try {
            return LocalDate.parse(dateStart).getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        } catch (Exception e) {
            return null;
        }
    }

    private List<InsightSnapshotDto> syncBatch(UserEntity user, InsightSyncRequestDto req,
            Map<String, String> queryParams) {
        InsightsFetchStrategy strategy = strategyRegistry.of(req.getProvider());
        Map<String, Map<String, Object>> results = strategy.fetchForObjects(
                user, req.getObjectExternalIds(), queryParams);

        return results.entrySet().stream()
                .map(entry -> persistSnapshot(user, req, entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<InsightSnapshotDto> syncAccount(UserEntity user, InsightSyncRequestDto req,
            Map<String, String> queryParams,
            InsightsFetchStrategy strategy) {
        Map<String, Object> body = strategy.fetchForAccount(user, req.getAdAccountId(), queryParams);
        return List.of(persistSnapshot(user, req, req.getAdAccountId(), body));
    }

    private List<InsightSnapshotDto> syncPerObject(UserEntity user, InsightSyncRequestDto req,
            Map<String, String> queryParams,
            InsightsFetchStrategy strategy) {
        return req.getObjectExternalIds().stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .map(objectId -> {
                    Map<String, Object> body = strategy.fetchForObject(user, objectId, queryParams);
                    return persistSnapshot(user, req, objectId, body);
                })
                .toList();
    }

    private InsightSnapshotDto persistSnapshot(UserEntity user, InsightSyncRequestDto req,
            String objectId, Map<String, Object> body) {
        String rawJson = serializeToJson(body);

        InsightSnapshotEntity snapshot = upsertSnapshot(
                user, req, objectId, rawJson, req.getBreakdowns());

        return mapper.convertToBaseDto(snapshot);
    }

    private InsightSnapshotEntity upsertSnapshot(UserEntity user, InsightSyncRequestDto req,
            String objectExternalId, String rawJson,
            Map<String, Object> breakdowns) {

        LocalDate dateStart = Objects.requireNonNull(req.getDateStart(), "dateStart must not be null for storage");
        LocalDate dateStop = Objects.requireNonNull(req.getDateStop(), "dateStop must not be null for storage");
        int timeInc = Optional.ofNullable(req.getTimeIncrement()).orElse(1);

        Optional<InsightSnapshotEntity> existing = snapshotRepository
                .findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateStartAndDateStopAndTimeIncrement(
                        user, req.getProvider(), req.getAdAccountId(), req.getObjectType(),
                        objectExternalId, dateStart, dateStop, timeInc);

        LocalDateTime now = LocalDateTime.now();
        InsightSnapshotEntity snapshot;

        if (existing.isPresent()) {
            snapshot = existing.get();
            snapshot.setUpdatedAt(now);
        } else {
            snapshot = InsightSnapshotEntity.builder()
                    .user(user)
                    .provider(req.getProvider())
                    .adAccountId(req.getAdAccountId())
                    .objectType(req.getObjectType())
                    .objectExternalId(objectExternalId)
                    .dateStart(req.getDateStart())
                    .dateStop(req.getDateStop())
                    .timeIncrement(Optional.ofNullable(req.getTimeIncrement()).orElse(1))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }

        snapshot.setRawJson(rawJson);

        if (breakdowns != null && !breakdowns.isEmpty()) {
            snapshot.setBreakdownsJson(serializeToJson(breakdowns));
        }

        return snapshotRepository.save(snapshot);
    }

    // -----------------------------------------------------------------------
    // Query - Read from database
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InsightSnapshotDto> query(Long userId, Provider provider, String adAccountId,
            InsightObjectType objectType, String objectExternalId,
            LocalDate dateStart, LocalDate dateStop) {
        UserEntity user = getUserOrThrow(userId);

        List<InsightSnapshotEntity> snapshots = snapshotRepository
                .findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateRange(
                        user, provider, adAccountId, objectType, objectExternalId, dateStart, dateStop);

        return mapper.convertToBaseDto(snapshots);
    }

    @Transactional(readOnly = true)
    public List<InsightSnapshotDto> queryByType(Long userId, Provider provider, String adAccountId,
            InsightObjectType objectType, String objectExternalId,
            LocalDate dateStart, LocalDate dateStop) {
        UserEntity user = getUserOrThrow(userId);

        List<InsightSnapshotEntity> snapshots = (objectExternalId != null && !objectExternalId.isBlank())
                ? snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateRange(
                        user, provider, adAccountId, objectType, objectExternalId, dateStart, dateStop)
                : snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                        user, provider, adAccountId, objectType, dateStart, dateStop);

        return mapper.convertToBaseDto(snapshots);
    }

    @Transactional(readOnly = true)
    public InsightSnapshotDto getSnapshot(Long userId, Long snapshotId) {
        UserEntity user = getUserOrThrow(userId);
        InsightSnapshotEntity snapshot = snapshotRepository.findByIdAndUser(snapshotId, user)
                .orElseThrow(() -> BusinessException.notFound("Insight snapshot not found with id: " + snapshotId));
        return mapper.convertToBaseDto(snapshot);
    }

    @Transactional
    public void deleteSnapshot(Long userId, Long snapshotId) {
        UserEntity user = getUserOrThrow(userId);
        InsightSnapshotEntity snapshot = snapshotRepository.findByIdAndUser(snapshotId, user)
                .orElseThrow(() -> BusinessException.notFound("Insight snapshot not found with id: " + snapshotId));
        snapshotRepository.delete(snapshot);
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private void validateSyncRequest(InsightSyncRequestDto req) {
        if (req.getProvider() == null) {
            throw BusinessException.badRequest("provider is required");
        }
        if (req.getAdAccountId() == null || req.getAdAccountId().isBlank()) {
            throw BusinessException.badRequest("adAccountId is required");
        }
        boolean hasDateRange = req.getDateStart() != null && req.getDateStop() != null;
        boolean hasDatePreset = req.getDatePreset() != null && !req.getDatePreset().isBlank();
        if (!hasDateRange && !hasDatePreset) {
            throw BusinessException.badRequest("Either datePreset or both dateStart and dateStop are required");
        }
        // Auto-derive dateStart/dateStop from preset so the DB upsert key is always populated
        if (hasDatePreset && !hasDateRange) {
            LocalDate today = LocalDate.now();
            LocalDate[] range = resolveDatesForPreset(req.getDatePreset(), today);
            req.setDateStart(range[0]);
            req.setDateStop(range[1]);
        }
    }

    private LocalDate[] resolveDatesForPreset(String preset, LocalDate today) {
        return switch (preset) {
            case "today"                    -> new LocalDate[]{today, today};
            case "yesterday"                -> new LocalDate[]{today.minusDays(1), today.minusDays(1)};
            case "last_3d"                  -> new LocalDate[]{today.minusDays(3), today};
            case "last_7d",
                 "last_week_mon_sun"        -> new LocalDate[]{today.minusDays(7), today};
            case "last_14d"                 -> new LocalDate[]{today.minusDays(14), today};
            case "last_28d"                 -> new LocalDate[]{today.minusDays(28), today};
            case "last_30d", "last_month"   -> new LocalDate[]{today.minusDays(30), today};
            case "last_90d"                 -> new LocalDate[]{today.minusDays(90), today};
            case "last_quarter"             -> new LocalDate[]{today.minusDays(90), today};
            case "this_month"               -> new LocalDate[]{today.withDayOfMonth(1), today};
            case "this_year"                -> new LocalDate[]{today.withDayOfYear(1), today};
            case "maximum"                  -> new LocalDate[]{LocalDate.of(2019, 1, 1), today};
            default                         -> new LocalDate[]{today.minusDays(30), today};
        };
    }

    private List<String> resolveFields(InsightSyncRequestDto req, InsightsFetchStrategy strategy, FetchMode mode) {
        if (req.getFields() != null && !req.getFields().isEmpty()) {
            return req.getFields();
        }
        return strategy.defaultFieldsFor(req.getObjectType(), mode == FetchMode.BATCH_IDS);
    }

    private String serializeToJson(Object object) {
        if (object == null)
            return null;
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return null;
        }
    }

    private UserEntity getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));
    }

    @Transactional(readOnly = true)
    public List<String> listMetricNames() {
        return metricRepository.findDistinctMetricNames();
    }

    // -----------------------------------------------------------------------
    // Cross-Platform Compare
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> compare(Long userId, CompareRequestDto req) {
        if (req.getPlatforms() == null || req.getPlatforms().size() < 1) {
            throw BusinessException.badRequest("At least one platform is required for comparison");
        }
        if (req.getDateFrom() == null || req.getDateTo() == null) {
            throw BusinessException.badRequest("dateFrom and dateTo are required");
        }

        UserEntity user = getUserOrThrow(userId);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        for (Provider platform : req.getPlatforms()) {
            List<InsightSnapshotEntity> snapshots = snapshotRepository
                    .findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                            user, platform,
                            req.getAdAccountId() != null ? req.getAdAccountId() : "",
                            req.getEntityType() != null ? req.getEntityType() : InsightObjectType.CAMPAIGN,
                            req.getDateFrom(), req.getDateTo());

            Map<String, Object> metrics = aggregateNormalizedMetrics(snapshots);
            result.put(platform.name(), metrics);
        }

        return result;
    }

    private Map<String, Object> aggregateNormalizedMetrics(List<InsightSnapshotEntity> snapshots) {
        double spend = 0, impressions = 0, clicks = 0;

        for (InsightSnapshotEntity snap : snapshots) {
            if (snap.getRawJson() == null) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = objectMapper.readValue(snap.getRawJson(), Map.class);
                spend += toDouble(raw.get("spend"));
                impressions += toDouble(raw.get("impressions"));
                clicks += toDouble(raw.get("clicks"));
            } catch (Exception ignored) {
            }
        }

        double ctr = impressions > 0 ? (clicks / impressions) * 100 : 0;
        double cpc = clicks > 0 ? spend / clicks : 0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spend", round2(spend));
        m.put("impressions", (long) impressions);
        m.put("clicks", (long) clicks);
        m.put("ctr", round2(ctr));
        m.put("cpc", round2(cpc));
        return m;
    }

    private static double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    // -----------------------------------------------------------------------
    // Breakdown by dimension
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InsightsBreakdownRowDto> breakdown(Long userId, Provider provider, String adAccountId,
            String dimension, LocalDate dateStart, LocalDate dateStop, List<String> campaignIds) {
        UserEntity user = getUserOrThrow(userId);

        List<InsightSnapshotEntity> snapshots = new ArrayList<>();
        if (campaignIds != null && !campaignIds.isEmpty()) {
            // Scope to selected campaigns only
            for (String campaignId : campaignIds) {
                snapshots.addAll(snapshotRepository
                        .findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateRange(
                                user, provider, adAccountId, InsightObjectType.CAMPAIGN,
                                campaignId, dateStart, dateStop));
            }
        } else {
            // Account-wide: aggregate across all object types
            for (InsightObjectType type : List.of(InsightObjectType.CAMPAIGN, InsightObjectType.ADSET, InsightObjectType.AD)) {
                snapshots.addAll(snapshotRepository
                        .findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                                user, provider, adAccountId, type, dateStart, dateStop));
            }
        }

        // dimensionValue -> [spend, impressions, clicks, reach, revenueForRoas]
        Map<String, double[]> aggMap = new LinkedHashMap<>();

        for (InsightSnapshotEntity snap : snapshots) {
            List<Object> rows = resolveBreakdownRows(snap, dimension);
            for (Object item : rows) {
                if (!(item instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) item;

                Object dimValue = extractDimensionValue(row, dimension);
                if (dimValue == null) continue;

                String dimStr = String.valueOf(dimValue);
                double[] agg = aggMap.computeIfAbsent(dimStr, k -> new double[5]);
                agg[0] += toDouble(row.get("spend"));
                agg[1] += toDouble(row.get("impressions"));
                agg[2] += toDouble(row.get("clicks"));
                agg[3] += toDouble(row.get("reach"));
                agg[4] += extractRoasRevenue(row);
            }
        }

        if (aggMap.isEmpty()) return List.of();

        double totalSpend = aggMap.values().stream().mapToDouble(a -> a[0]).sum();

        return aggMap.entrySet().stream()
                .map(e -> {
                    double[] a = e.getValue();
                    double ctr = a[1] > 0 ? (a[2] / a[1]) * 100 : 0;
                    double roas = a[0] > 0 ? a[4] / a[0] : 0;
                    double share = totalSpend > 0 ? (a[0] / totalSpend) * 100 : 0;
                    return InsightsBreakdownRowDto.builder()
                            .dimension(dimension)
                            .dimensionValue(e.getKey())
                            .spend(round2(a[0]))
                            .impressions((long) a[1])
                            .clicks((long) a[2])
                            .reach((long) a[3])
                            .ctr(round2(ctr))
                            .roas(round2(roas))
                            .share(round2(share))
                            .build();
                })
                .sorted(Comparator.comparingDouble(InsightsBreakdownRowDto::getShare).reversed())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private double extractRoasRevenue(Map<String, Object> row) {
        Object purchaseRoas = row.get("purchase_roas");
        if (purchaseRoas instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?,?> m) return toDouble(m.get("value"));
        }
        Object actionValues = row.get("action_values");
        if (actionValues instanceof List<?> avList) {
            for (Object av : avList) {
                if (av instanceof Map<?,?> m
                        && "offsite_conversion.fb_pixel_purchase".equals(m.get("action_type"))) {
                    return toDouble(m.get("value"));
                }
            }
        }
        return 0;
    }

}