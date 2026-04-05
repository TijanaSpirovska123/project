package com.example.marketing.insights.service;

import com.example.marketing.insights.dto.CompareRequestDto;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.dto.InsightSyncRequestDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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

    @Transactional
    public List<InsightSnapshotDto> sync(Long userId, InsightSyncRequestDto req) {
        validateSyncRequest(req);

        UserEntity user = getUserOrThrow(userId);
        InsightsFetchStrategy strategy = strategyRegistry.of(req.getProvider());
        FetchMode mode = Optional.ofNullable(req.getFetchMode()).orElse(FetchMode.PER_OBJECT);

        List<String> fields = resolveFields(req, strategy, mode);
        int timeInc = Optional.ofNullable(req.getTimeIncrement()).orElse(1);

        // Let strategy build query params
        Map<String, String> queryParams = strategy.buildQueryParams(req, fields, timeInc);

        return switch (mode) {
            case BATCH_IDS -> syncBatch(user, req, queryParams);
            case ACCOUNT -> syncAccount(user, req, queryParams, strategy);
            default -> syncPerObject(user, req, queryParams, strategy);
        };
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

}