package com.example.marketing.insights.service;

import com.example.marketing.insights.dto.CompareRequestDto;
import com.example.marketing.insights.dto.InsightBatchSyncResultDto;
import com.example.marketing.insights.dto.InsightEntitySyncResultDto;
import com.example.marketing.insights.dto.InsightMetricDto;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.dto.InsightWarningDto;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.mapper.InsightsSnapshotMapper;
import com.example.marketing.insights.repository.InsightMetricRepository;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.strategy.InsightsFetchStrategy;
import com.example.marketing.insights.strategy.InsightsFetchStrategyRegistry;
import com.example.marketing.insights.util.FetchMode;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.example.marketing.insights.util.InsightWarningCode;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final com.example.marketing.insights.analytics.service.BreakdownAnalyticsService breakdownAnalyticsService;

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

    // DIMENSION_TO_GROUP (read-path dimension -> breakdownsJson group key) moved to
    // BreakdownAnalyticsService along with the rest of the breakdown() read logic.

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
            // .snapshot() is null for FAILED entities — dropped here to keep this method's
            // return type unchanged for existing callers; use syncBatchWithReport for the
            // full per-entity success/empty/failure breakdown.
            case BATCH_IDS -> syncBatch(user, req, queryParams).stream()
                    .map(EntitySyncOutcome::snapshot)
                    .filter(Objects::nonNull)
                    .toList();
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

    /**
     * Batch sync with a full per-entity success/no-activity/failure report — used by the
     * dedicated /sync/{campaigns,adsets,ads}/batch endpoints. One entity failing to sync (bad
     * ID, provider error, persistence error) no longer aborts the whole batch; it's reported
     * individually instead.
     */
    @Transactional
    public InsightBatchSyncResultDto syncBatchWithReport(Long userId, InsightSyncRequestDto req) {
        req.setFetchMode(FetchMode.BATCH_IDS);
        validateSyncRequest(req);

        UserEntity user = getUserOrThrow(userId);
        InsightsFetchStrategy strategy = strategyRegistry.of(req.getProvider());
        List<String> fields = resolveFields(req, strategy, FetchMode.BATCH_IDS);
        int timeInc = Optional.ofNullable(req.getTimeIncrement()).orElse(1);
        req.setBreakdowns(null);
        Map<String, String> queryParams = strategy.buildQueryParams(req, fields, timeInc);

        List<EntitySyncOutcome> outcomes = syncBatch(user, req, queryParams);

        try {
            fetchAndStoreBreakdowns(user, req, strategy);
        } catch (Exception e) {
            logBreakdownFailure("post-sync", e);
        }

        return buildBatchResult(req.getObjectExternalIds(), outcomes);
    }

    /** Per-entity outcome of a batch sync attempt — SYNCED/NO_ACTIVITY/FAILED, matching InsightEntitySyncResultDto.status. */
    private record EntitySyncOutcome(String objectExternalId, String status, InsightSnapshotDto snapshot, List<InsightWarningDto> warnings) {}

    private InsightBatchSyncResultDto buildBatchResult(List<String> requestedIds, List<EntitySyncOutcome> outcomes) {
        int requested = requestedIds == null ? 0 : requestedIds.size();
        int successful = (int) outcomes.stream().filter(o -> "SYNCED".equals(o.status())).count();
        int empty = (int) outcomes.stream().filter(o -> "NO_ACTIVITY".equals(o.status())).count();
        int failed = (int) outcomes.stream().filter(o -> "FAILED".equals(o.status())).count();

        InsightSyncStatus status;
        if (failed == 0) status = InsightSyncStatus.COMPLETE;
        else if (successful + empty > 0) status = InsightSyncStatus.PARTIALLY_COMPLETE;
        else status = InsightSyncStatus.FAILED;

        InsightBatchSyncResultDto result = new InsightBatchSyncResultDto();
        result.setRequestedCount(requested);
        result.setProcessedCount(outcomes.size());
        result.setSuccessfulCount(successful);
        result.setEmptyResultCount(empty);
        result.setFailedCount(failed);
        result.setSyncStatus(status);
        result.setSnapshots(outcomes.stream().map(EntitySyncOutcome::snapshot).filter(Objects::nonNull).toList());
        result.setResults(outcomes.stream().map(o -> {
            InsightEntitySyncResultDto r = new InsightEntitySyncResultDto();
            r.setObjectExternalId(o.objectExternalId());
            r.setStatus(o.status());
            r.setWarnings(o.warnings() != null ? o.warnings() : List.of());
            return r;
        }).toList());
        return result;
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
     * NOTE (known scope limitation): pagination is fully followed for each ID within a chunk
     * only in the sense that Meta's ids= batch response for each ID is taken as-is — per-ID
     * continuation beyond one page within a single batch response is not implemented (unlike
     * fetchForObject/fetchForAccount, which do follow pagination). paginationComplete is
     * therefore always recorded as true for batch-synced snapshots; this is a documented gap,
     * not a silent one.
     */
    private List<EntitySyncOutcome> syncBatch(UserEntity user, InsightSyncRequestDto req,
            Map<String, String> queryParams) {
        InsightsFetchStrategy strategy = strategyRegistry.of(req.getProvider());
        List<String> ids = Optional.ofNullable(req.getObjectExternalIds()).orElse(List.of()).stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();

        Map<String, Map<String, Object>> results;
        try {
            results = strategy.fetchForObjects(user, ids, queryParams);
        } catch (Exception e) {
            log.error("Batch insights fetch failed entirely: {}", e.getMessage());
            InsightWarningDto warning = InsightWarningDto.of(InsightWarningCode.INSIGHT_SYNC_FAILED,
                    "Provider request failed: " + e.getMessage());
            return ids.stream().map(id -> new EntitySyncOutcome(id, "FAILED", null, List.of(warning))).toList();
        }

        List<EntitySyncOutcome> outcomes = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> body = results.get(id);
            if (body == null) {
                outcomes.add(new EntitySyncOutcome(id, "FAILED", null, List.of(
                        InsightWarningDto.of(InsightWarningCode.INSIGHT_SYNC_FAILED,
                                "The provider did not return a result for this ID (invalid ID, no access, or ownership issue)."))));
                continue;
            }
            try {
                InsightSnapshotDto dto = persistSnapshot(user, req, id, body, true);
                boolean hasActivity = dto.getDaysWithActivity() != null && dto.getDaysWithActivity() > 0;
                outcomes.add(new EntitySyncOutcome(id, hasActivity ? "SYNCED" : "NO_ACTIVITY", dto, dto.getWarnings()));
            } catch (Exception e) {
                log.error("Failed to persist insights for {}: {}", id, e.getMessage());
                outcomes.add(new EntitySyncOutcome(id, "FAILED", null, List.of(
                        InsightWarningDto.of(InsightWarningCode.INSIGHT_PERSISTENCE_PARTIAL, e.getMessage()))));
            }
        }
        return outcomes;
    }

    private List<InsightSnapshotDto> syncAccount(UserEntity user, InsightSyncRequestDto req,
            Map<String, String> queryParams,
            InsightsFetchStrategy strategy) {
        InsightsFetchStrategy.ProviderFetchResult result = strategy.fetchForAccount(user, req.getAdAccountId(), queryParams);
        return List.of(persistSnapshot(user, req, req.getAdAccountId(), result.body(), result.paginationComplete()));
    }

    private List<InsightSnapshotDto> syncPerObject(UserEntity user, InsightSyncRequestDto req,
            Map<String, String> queryParams,
            InsightsFetchStrategy strategy) {
        return req.getObjectExternalIds().stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .map(objectId -> {
                    InsightsFetchStrategy.ProviderFetchResult result = strategy.fetchForObject(user, objectId, queryParams);
                    return persistSnapshot(user, req, objectId, result.body(), result.paginationComplete());
                })
                .toList();
    }

    private InsightSnapshotDto persistSnapshot(UserEntity user, InsightSyncRequestDto req,
            String objectId, Map<String, Object> body, boolean paginationComplete) {
        String rawJson = serializeToJson(body);

        InsightSnapshotEntity snapshot = upsertSnapshot(
                user, req, objectId, rawJson, req.getBreakdowns(), paginationComplete);

        return mapper.convertToBaseDto(snapshot);
    }

    private InsightSnapshotEntity upsertSnapshot(UserEntity user, InsightSyncRequestDto req,
            String objectExternalId, String rawJson,
            Map<String, Object> breakdowns, boolean paginationComplete) {

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
        // Always refresh to the latest sync attempt's result, not just set once at creation —
        // a snapshot that previously had incomplete pagination should reflect a later,
        // successful re-sync (and vice versa).
        snapshot.setPaginationComplete(paginationComplete);

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

    /**
     * Only used to seed the DB dedupe/lookup key (dateStart/dateStop) before the Meta call —
     * the actual data range returned is separately derived from the response itself at read
     * time (see InsightMetricsExtractor.computeDataPeriod / InsightSnapshotDto.dataPeriod),
     * which is authoritative. Still worth getting these approximations right, since a large
     * mismatch would make later exact-range lookups miss this snapshot.
     * <p>
     * dateStop is always inclusive, matching Meta's own time_range.until semantics.
     */
    // Package-private (not private) so InsightsServiceDatePresetTest can verify the date math directly.
    LocalDate[] resolveDatesForPreset(String preset, LocalDate today) {
        return switch (preset) {
            case "today"                    -> new LocalDate[]{today, today};
            case "yesterday"                -> new LocalDate[]{today.minusDays(1), today.minusDays(1)};
            case "last_3d"                  -> new LocalDate[]{today.minusDays(3), today};
            case "last_7d",
                 "last_week_mon_sun"        -> new LocalDate[]{today.minusDays(7), today};
            case "last_14d"                 -> new LocalDate[]{today.minusDays(14), today};
            case "last_28d"                 -> new LocalDate[]{today.minusDays(28), today};
            case "last_30d"                 -> new LocalDate[]{today.minusDays(30), today};
            case "last_90d"                 -> new LocalDate[]{today.minusDays(90), today};
            case "last_quarter"             -> new LocalDate[]{today.minusDays(90), today};
            case "this_month"               -> new LocalDate[]{today.withDayOfMonth(1), today};
            // The previous full calendar month — NOT "the last 30 days" (that's last_30d).
            // e.g. if today is 2026-04-15, last_month is 2026-03-01 to 2026-03-31.
            case "last_month" -> {
                LocalDate firstOfThisMonth = today.withDayOfMonth(1);
                yield new LocalDate[]{firstOfThisMonth.minusMonths(1), firstOfThisMonth.minusDays(1)};
            }
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

    /**
     * Sums normalized metrics across snapshots for /compare. Reuses the mapper's already-fixed
     * row extraction (rather than re-parsing rawJson's top level directly, which — like the
     * empty-metrics bug this mirrors — would always read 0/absent fields, since spend/
     * impressions/clicks live inside rawJson's "data" rows, not at its top level).
     * <p>
     * null (not 0) means "no data to compute this from" — e.g. no snapshots existed at all for
     * this platform, or a ratio's denominator was zero/absent — as opposed to 0, which means the
     * provider returned real data and the value is genuinely zero. See InsightWarningCode /
     * InsightSnapshotDto.warnings for the equivalent distinction already applied to per-snapshot
     * metrics.
     */
    Map<String, Object> aggregateNormalizedMetrics(List<InsightSnapshotEntity> snapshots) {
        List<InsightSnapshotDto> dtos = mapper.convertToBaseDto(snapshots);

        BigDecimal spend = null, impressions = null, clicks = null;

        for (InsightSnapshotDto dto : dtos) {
            // Only normalizedMetrics — never the raw providerMetrics/combined list — so this
            // aggregate can never double-count a normalized metric alongside its raw source(s).
            for (InsightMetricDto metric : dto.getNormalizedMetrics()) {
                BigDecimal value = metric.getValueNumber();
                if (value == null) continue;
                switch (metric.getName()) {
                    case "spend" -> spend = addNullable(spend, value);
                    case "impressions" -> impressions = addNullable(impressions, value);
                    case "clicks" -> clicks = addNullable(clicks, value);
                    default -> { }
                }
            }
        }

        BigDecimal ctr = safeRatio(clicks, impressions, BigDecimal.valueOf(100));
        BigDecimal cpc = safeRatio(spend, clicks, null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spend", round2(spend));
        m.put("impressions", impressions == null ? null : impressions.longValue());
        m.put("clicks", clicks == null ? null : clicks.longValue());
        m.put("ctr", round2(ctr));
        m.put("cpc", round2(cpc));
        return m;
    }

    private static BigDecimal addNullable(BigDecimal current, BigDecimal delta) {
        return (current == null ? BigDecimal.ZERO : current).add(delta);
    }

    /** numerator/denominator (× multiplier if given); null if the denominator is zero/absent — division by zero is undefined, never a fabricated 0. */
    static BigDecimal safeRatio(BigDecimal numerator, BigDecimal denominator, BigDecimal multiplier) {
        if (numerator == null || denominator == null || denominator.signum() == 0) return null;
        BigDecimal result = numerator.divide(denominator, 10, RoundingMode.HALF_UP);
        return multiplier != null ? result.multiply(multiplier) : result;
    }

    private static BigDecimal round2(BigDecimal val) {
        return val == null ? null : val.setScale(2, RoundingMode.HALF_UP);
    }

    // -----------------------------------------------------------------------
    // Breakdown by dimension — moved to BreakdownAnalyticsService (Phase 2, Step 13); this
    // method now only enforces user ownership and delegates.
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InsightsBreakdownRowDto> breakdown(Long userId, Provider provider, String adAccountId,
            String dimension, LocalDate dateStart, LocalDate dateStop, List<String> campaignIds) {
        UserEntity user = getUserOrThrow(userId);
        return breakdownAnalyticsService.breakdown(user, provider, adAccountId, dimension, dateStart, dateStop, campaignIds);
    }

}