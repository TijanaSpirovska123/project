package com.example.marketing.insights.analytics.service;

import com.example.marketing.ad.repository.AdRepository;
import com.example.marketing.adset.repository.AdSetRepository;
import com.example.marketing.campaign.repository.CampaignRepository;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.entity.BasePlatformEntity;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.dto.InsightWarningDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.mapper.InsightsSnapshotMapper;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.example.marketing.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single, request-scoped place that fetches snapshots, parses/normalizes each one exactly
 * once, resolves currency, batch-loads object names (no N+1), and validates ownership/date-range/
 * selection-size limits — every Phase-2 analytics service consumes its output rather than
 * re-querying or re-parsing rawJson independently (see CanonicalDatasetLoader's javadoc for the
 * fact-table swap-in boundary this preserves).
 * <p>
 * Ownership is enforced the same way every existing Phase-1 query enforces it: every repository
 * call is scoped by the authenticated {@code UserEntity}, so a request naming another user's
 * campaign/ad-set/ad ID simply matches nothing — never a cross-user leak, without needing a
 * separate ownership-check pass.
 */
@Service
@RequiredArgsConstructor
public class SnapshotCanonicalDatasetLoader implements CanonicalDatasetLoader {

    private final InsightSnapshotRepository snapshotRepository;
    private final InsightsSnapshotMapper snapshotMapper;
    private final CanonicalRecordMapper canonicalRecordMapper;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;
    private final InsightsAnalyticsLimitsProperties limits;
    private final CampaignRepository campaignRepository;
    private final AdSetRepository adSetRepository;
    private final AdRepository adRepository;

    @Override
    @Transactional(readOnly = true)
    public CanonicalDataset load(UserEntity user, AnalyticsFilterRequest request) {
        validateBasics(request);

        Selection selection = resolveSelection(request);
        validateSelectionSize(selection);

        List<InsightSnapshotEntity> snapshotEntities = fetchSnapshots(user, request, selection);
        // Entity-level DTOs carry syncStatus/warnings/pagination state (fetch-attempt attributes,
        // not per-day ones) and are also what currency extraction reads from.
        List<InsightSnapshotDto> snapshotDtos = snapshotMapper.convertToBaseDto(snapshotEntities);

        ProviderAnalyticsStrategy strategy = strategyRegistry.getStrategy(request.getProvider());

        Set<String> currencies = new LinkedHashSet<>();
        List<CanonicalInsightRecord> records = new ArrayList<>();
        List<InsightWarningDto> warnings = new ArrayList<>();
        for (int i = 0; i < snapshotEntities.size(); i++) {
            InsightSnapshotEntity entity = snapshotEntities.get(i);
            InsightSnapshotDto entityDto = snapshotDtos.get(i);

            String currency = strategy.extractCurrency(entityDto).orElse(null);
            if (currency != null) currencies.add(currency);
            if (entityDto.getWarnings() != null) warnings.addAll(entityDto.getWarnings());

            // Day-level granularity: a snapshot's own combined normalizedMetrics is the SUM
            // across every row it stored, which would silently make every analytics service
            // (time-series especially) lose daily resolution — explode into one record per row
            // instead, reusing the exact same extraction/normalization Phase 1 already trusts.
            for (InsightSnapshotDto dayDto : snapshotMapper.explodeDailyDtos(entity)) {
                CanonicalInsightRecord record = canonicalRecordMapper.toRecord(dayDto).toBuilder()
                        .currency(currency)
                        .syncStatus(entityDto.getSyncStatus())
                        .syncComplete(entityDto.isSyncComplete())
                        .singleEntity(true)
                        .build();
                records.add(record);
            }
        }

        Map<String, String> objectNames = loadObjectNames(user, request.getProvider(), request.getAdAccountId(),
                selection.effectiveType(), snapshotDtos);

        Set<String> syncedIds = snapshotDtos.stream().map(InsightSnapshotDto::getObjectExternalId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int selectedCount = selection.explicitIds() != null ? selection.explicitIds().size() : syncedIds.size();
        int withActivity = (int) snapshotDtos.stream()
                .filter(d -> d.getDaysWithActivity() != null && d.getDaysWithActivity() > 0)
                .count();
        AnalyticsScope scope = AnalyticsScope.builder()
                .objectType(selection.effectiveType())
                .selectedObjectIds(selection.explicitIds() != null ? selection.explicitIds() : new ArrayList<>(syncedIds))
                .selectedObjectCount(selectedCount)
                .objectsWithActivity(withActivity)
                .objectsWithoutActivity(Math.max(0, selectedCount - withActivity))
                .build();

        InsightSyncStatus overallStatus = overallSyncStatus(snapshotDtos, selection);

        boolean mixedCurrency = currencies.size() > 1;
        String resolvedCurrency = currencies.size() == 1 ? currencies.iterator().next() : null;
        MetricUnavailableReason currencyReason = mixedCurrency ? MetricUnavailableReason.MIXED_CURRENCY
                : (currencies.isEmpty() ? MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER : null);

        return CanonicalDataset.builder()
                .provider(request.getProvider())
                .adAccountId(request.getAdAccountId())
                .requestedPeriod(new InsightPeriodDto(request.getDateStart(), request.getDateStop()))
                .scope(scope)
                .records(records)
                .objectNames(objectNames)
                .currency(resolvedCurrency)
                .mixedCurrency(mixedCurrency)
                .currencyUnavailableReason(currencyReason)
                .overallSyncStatus(overallStatus)
                .overallSyncComplete(overallStatus == InsightSyncStatus.COMPLETE)
                .warnings(warnings)
                .build();
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private void validateBasics(AnalyticsFilterRequest request) {
        if (request.getProvider() == null) throw BusinessException.badRequest("provider is required");
        if (request.getAdAccountId() == null || request.getAdAccountId().isBlank()) {
            throw BusinessException.badRequest("adAccountId is required");
        }
        if (request.getDateStart() == null || request.getDateStop() == null) {
            throw BusinessException.badRequest("dateStart and dateStop are required");
        }
        if (request.getDateStart().isAfter(request.getDateStop())) {
            throw BusinessException.badRequest("INVALID_DATE_RANGE: dateStart must be on or before dateStop");
        }
        long days = ChronoUnit.DAYS.between(request.getDateStart(), request.getDateStop()) + 1;
        if (days > limits.getMaxDateRangeDays()) {
            throw BusinessException.badRequest("INVALID_DATE_RANGE: range exceeds the maximum of "
                    + limits.getMaxDateRangeDays() + " days");
        }
        if (!strategyRegistryIsSupported(request.getProvider())) {
            throw BusinessException.badRequest("UNSUPPORTED_PROVIDER: " + request.getProvider());
        }
    }

    private boolean strategyRegistryIsSupported(Provider provider) {
        return strategyRegistry.isSupported(provider);
    }

    private record Selection(InsightObjectType effectiveType, List<String> explicitIds) {}

    private Selection resolveSelection(AnalyticsFilterRequest request) {
        List<String> campaignIds = nullToEmpty(request.getCampaignIds());
        List<String> adSetIds = nullToEmpty(request.getAdSetIds());
        List<String> adIds = nullToEmpty(request.getAdIds());

        int populated = (campaignIds.isEmpty() ? 0 : 1) + (adSetIds.isEmpty() ? 0 : 1) + (adIds.isEmpty() ? 0 : 1);
        if (populated > 1) {
            throw BusinessException.badRequest("MIXED_OBJECT_TYPES: only one of campaignIds, adSetIds or adIds may be specified per request");
        }

        if (!campaignIds.isEmpty()) return new Selection(InsightObjectType.CAMPAIGN, campaignIds);
        if (!adSetIds.isEmpty()) return new Selection(InsightObjectType.ADSET, adSetIds);
        if (!adIds.isEmpty()) return new Selection(InsightObjectType.AD, adIds);

        InsightObjectType type = request.getObjectType() != null ? request.getObjectType() : InsightObjectType.CAMPAIGN;
        return new Selection(type, null);
    }

    private void validateSelectionSize(Selection selection) {
        if (selection.explicitIds() == null) return;
        int max = switch (selection.effectiveType()) {
            case CAMPAIGN -> limits.getMaxSelectedCampaigns();
            case ADSET -> limits.getMaxSelectedAdSets();
            case AD -> limits.getMaxSelectedAds();
            case ACCOUNT -> Integer.MAX_VALUE;
        };
        if (selection.explicitIds().size() > max) {
            throw BusinessException.badRequest("Selection of " + selection.explicitIds().size()
                    + " " + selection.effectiveType() + " objects exceeds the maximum of " + max);
        }
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list == null ? List.of() : list.stream().filter(id -> id != null && !id.isBlank()).toList();
    }

    // -----------------------------------------------------------------------
    // Fetch
    // -----------------------------------------------------------------------

    private List<InsightSnapshotEntity> fetchSnapshots(UserEntity user, AnalyticsFilterRequest request, Selection selection) {
        if (selection.explicitIds() != null) {
            List<InsightSnapshotEntity> all = new ArrayList<>();
            for (String id : selection.explicitIds()) {
                all.addAll(snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateRange(
                        user, request.getProvider(), request.getAdAccountId(), selection.effectiveType(),
                        id, request.getDateStart(), request.getDateStop()));
            }
            return all;
        }
        return snapshotRepository.findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                user, request.getProvider(), request.getAdAccountId(), selection.effectiveType(),
                request.getDateStart(), request.getDateStop());
    }

    private InsightSyncStatus overallSyncStatus(List<InsightSnapshotDto> dtos, Selection selection) {
        if (dtos.isEmpty()) return InsightSyncStatus.NOT_SYNCHRONIZED;
        boolean anyFailed = dtos.stream().anyMatch(d -> d.getSyncStatus() == InsightSyncStatus.FAILED);
        if (anyFailed) return InsightSyncStatus.FAILED;
        boolean anyPartial = dtos.stream().anyMatch(d -> d.getSyncStatus() == InsightSyncStatus.PARTIALLY_COMPLETE);
        if (anyPartial) return InsightSyncStatus.PARTIALLY_COMPLETE;
        // Some explicitly requested IDs may have no snapshot at all — a real partial-sync signal.
        if (selection.explicitIds() != null) {
            Set<String> found = dtos.stream().map(InsightSnapshotDto::getObjectExternalId).collect(java.util.stream.Collectors.toSet());
            if (!found.containsAll(selection.explicitIds())) return InsightSyncStatus.PARTIALLY_COMPLETE;
        }
        return InsightSyncStatus.COMPLETE;
    }

    /** Batch name lookup (one query per object type actually present) — never one query per object. */
    private Map<String, String> loadObjectNames(UserEntity user, Provider provider, String adAccountId,
            InsightObjectType objectType, List<InsightSnapshotDto> dtos) {
        List<String> ids = dtos.stream().map(InsightSnapshotDto::getObjectExternalId).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        List<? extends BasePlatformEntity> entities = switch (objectType) {
            case CAMPAIGN -> campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(user, provider.name(), adAccountId, ids);
            case ADSET -> adSetRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(user, provider.name(), adAccountId, ids);
            case AD -> adRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(user, provider.name(), adAccountId, ids);
            case ACCOUNT -> List.of();
        };

        Map<String, String> names = new LinkedHashMap<>();
        for (BasePlatformEntity e : entities) {
            names.put(e.getExternalId(), e.getName());
        }
        return names;
    }
}
