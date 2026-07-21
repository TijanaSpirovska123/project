package com.example.marketing.insights.mapper;

import com.example.marketing.infrastructure.mapper.BaseMapper;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.dto.InsightMetricDto;
import com.example.marketing.insights.dto.InsightWarningDto;
import com.example.marketing.insights.entity.InsightMetricEntity;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.mapper.InsightMetricsExtractor.MetricsResult;
import com.example.marketing.insights.strategy.InsightsFetchStrategy;
import com.example.marketing.insights.strategy.InsightsFetchStrategyRegistry;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.example.marketing.insights.util.InsightWarningCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract class (rather than the usual MapStruct interface) so it can hold the
 * injected {@link InsightsFetchStrategyRegistry} — needed to delegate raw-response row
 * extraction to the correct per-provider strategy instead of assuming Meta's envelope shape.
 */
@Mapper(componentModel = "spring", uses = BaseMapper.class)
public abstract class InsightsSnapshotMapper implements BaseMapper<InsightSnapshotDto, InsightSnapshotEntity> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    protected InsightsFetchStrategyRegistry strategyRegistry;

    @Override
    @Mapping(target = "rawData", ignore = true)
    @Mapping(target = "warnings", ignore = true)
    public abstract InsightSnapshotDto convertToBaseDto(InsightSnapshotEntity entity);

    @Override
    @Mapping(target = "rawJson", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "breakdownsJson", ignore = true)
    @Mapping(target = "paginationComplete", ignore = true)
    public abstract InsightSnapshotEntity convertToBaseEntity(InsightSnapshotDto dto);

    @AfterMapping
    protected void fillDerivedFields(InsightSnapshotEntity entity, @MappingTarget InsightSnapshotDto dto) {
        if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
            dto.setMetrics(List.of());
            dto.setNormalizedMetrics(List.of());
            dto.setProviderMetrics(List.of());
            dto.setSyncStatus(InsightSyncStatus.NOT_SYNCHRONIZED);
            dto.setSyncComplete(false);
            dto.setWarnings(List.of(InsightWarningDto.of(InsightWarningCode.INSIGHT_SNAPSHOT_EMPTY,
                    "This snapshot has no stored provider data.")));
            return;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(entity.getRawJson());

            // Merge breakdownsJson into rawData.breakdowns so the frontend can do
            // client-side dimension filtering without a separate API call.
            InsightWarningDto breakdownFetchWarning = null;
            if (entity.getBreakdownsJson() != null && !entity.getBreakdownsJson().isBlank() && root.isObject()) {
                try {
                    JsonNode breakdownsNode = OBJECT_MAPPER.readTree(entity.getBreakdownsJson());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) root).set("breakdowns", breakdownsNode);

                    // "_fetchErrors" is a reserved key InsightsService writes alongside the real
                    // dimension groups (age_gender/country/placement) when the post-sync
                    // breakdown fetch failed for one or more of them — surface it here instead
                    // of leaving the caller to guess why breakdown data never showed up.
                    JsonNode fetchErrors = breakdownsNode.path("_fetchErrors");
                    if (fetchErrors.isArray() && !fetchErrors.isEmpty()) {
                        List<String> reasons = new ArrayList<>();
                        fetchErrors.forEach(n -> reasons.add(n.asText()));
                        breakdownFetchWarning = InsightWarningDto.of(InsightWarningCode.INSIGHT_BREAKDOWN_FETCH_FAILED,
                                "Demographic/placement breakdown fetch failed and was not stored: " + String.join("; ", reasons));
                    }
                } catch (Exception ignored) {}
            }

            dto.setRawData(root);

            // Row extraction (and action-type normalization below) is provider-specific (Meta
            // wraps rows under "data"; other providers will differ) — delegated to that
            // provider's own strategy rather than assumed here, so the shared analytics layer
            // stays provider-independent.
            InsightsFetchStrategy strategy = strategyRegistry.of(entity.getProvider());
            List<JsonNode> rows = strategy.extractDataRows(root);

            // syncStatus/syncComplete are based on whether the fetch itself succeeded and
            // followed every page — NEVER on whether the provider had delivery data throughout
            // the requested range. A sparse-delivery campaign over a long range is completely
            // normal and must not be reported as an incomplete sync.
            boolean paginationComplete = !Boolean.FALSE.equals(entity.getPaginationComplete());
            InsightSyncStatus syncStatus = paginationComplete ? InsightSyncStatus.COMPLETE : InsightSyncStatus.PARTIALLY_COMPLETE;
            dto.setSyncStatus(syncStatus);
            dto.setSyncComplete(syncStatus == InsightSyncStatus.COMPLETE);

            List<InsightWarningDto> warnings = new ArrayList<>();
            if (breakdownFetchWarning != null) {
                warnings.add(breakdownFetchWarning);
            }
            if (!paginationComplete) {
                warnings.add(InsightWarningDto.of(InsightWarningCode.INSIGHT_PAGINATION_INCOMPLETE,
                        "Not all pages of the provider's response could be fetched — this snapshot's data may be incomplete."));
            }

            if (rows.isEmpty()) {
                dto.setMetrics(List.of());
                dto.setNormalizedMetrics(List.of());
                dto.setProviderMetrics(List.of());
                dto.setActivityPeriod(null);
                dto.setDaysWithActivity(0);
                // entity.getPaginationComplete() == null means we have no explicit evidence
                // either way (e.g. a snapshot persisted before this tracking existed) — in that
                // case, don't confidently claim "no activity", since we can't fully vouch for
                // the fetch. Once we DO know the fetch fully succeeded, an empty result is just
                // the provider correctly reporting zero delivery — not an error.
                String code = entity.getPaginationComplete() == null
                        ? InsightWarningCode.INSIGHT_PROVIDER_RESPONSE_EMPTY
                        : InsightWarningCode.INSIGHT_NO_ACTIVITY_IN_PERIOD;
                warnings.add(InsightWarningDto.of(code,
                        code.equals(InsightWarningCode.INSIGHT_NO_ACTIVITY_IN_PERIOD)
                                ? "The request was fully processed, but the provider reported no delivery for this period."
                                : "The provider returned no data rows for this snapshot's date range."));
                dto.setWarnings(warnings);
                return;
            }

            MetricsResult metricsResult = InsightMetricsExtractor.extractMetrics(rows, strategy);
            dto.setNormalizedMetrics(metricsResult.normalizedMetrics());
            dto.setProviderMetrics(metricsResult.providerMetrics());
            dto.setMetrics(metricsResult.combined());
            dto.setConversionDataAvailable(metricsResult.conversionDataAvailable());

            // activityPeriod is ground truth for where DELIVERY occurred (derived from the rows
            // the provider actually returned) — it is deliberately NOT compared against
            // dateStart/dateStop to infer completeness. Meta only returns rows for days with
            // delivery, so a request spanning months with only a handful of active days is
            // expected, not a sign anything went wrong.
            dto.setActivityPeriod(InsightMetricsExtractor.computeActivityPeriod(rows));
            dto.setDaysWithActivity(rows.size());

            dto.setWarnings(warnings);
        } catch (Exception e) {
            dto.setRawData(entity.getRawJson());
            dto.setMetrics(List.of());
            dto.setNormalizedMetrics(List.of());
            dto.setProviderMetrics(List.of());
            dto.setSyncStatus(InsightSyncStatus.FAILED);
            dto.setSyncComplete(false);
            dto.setWarnings(List.of(InsightWarningDto.of(InsightWarningCode.INSIGHT_METRICS_EMPTY,
                    "Stored provider data could not be parsed: " + e.getMessage())));
        }
    }

    /**
     * Explodes one snapshot's stored rawJson into one {@link InsightSnapshotDto} per day-row it
     * actually contains — used by Phase-2 time-series analytics, which needs day-level
     * granularity that {@link #fillDerivedFields} intentionally collapses away (a snapshot's
     * normalizedMetrics are the SUM across every row it stored, exactly like the main mapping
     * path). Reuses the exact same InsightMetricsExtractor logic as that path, just invoked once
     * per row instead of once for the whole snapshot, so day-level and whole-snapshot metrics are
     * always computed identically.
     * <p>
     * A snapshot synced with {@code timeIncrementAllDays} (one row spanning the whole requested
     * range rather than one row per day) explodes to a single "day" entry whose date is that
     * row's own date_start — not truly one calendar day. Callers needing genuine daily
     * granularity should ensure the underlying sync used a daily time_increment.
     */
    public List<InsightSnapshotDto> explodeDailyDtos(InsightSnapshotEntity entity) {
        if (entity.getRawJson() == null || entity.getRawJson().isBlank()) return List.of();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(entity.getRawJson());
            InsightsFetchStrategy strategy = strategyRegistry.of(entity.getProvider());
            List<JsonNode> rows = strategy.extractDataRows(root);

            List<InsightSnapshotDto> result = new ArrayList<>();
            for (JsonNode row : rows) {
                InsightSnapshotDto dayDto = new InsightSnapshotDto();
                dayDto.setProvider(entity.getProvider());
                dayDto.setAdAccountId(entity.getAdAccountId());
                dayDto.setObjectType(entity.getObjectType());
                dayDto.setObjectExternalId(entity.getObjectExternalId());
                dayDto.setDateStart(entity.getDateStart());
                dayDto.setDateStop(entity.getDateStop());

                List<JsonNode> singleRowList = List.of(row);
                InsightMetricsExtractor.MetricsResult metricsResult =
                        InsightMetricsExtractor.extractMetrics(singleRowList, strategy);
                dayDto.setNormalizedMetrics(metricsResult.normalizedMetrics());
                dayDto.setProviderMetrics(metricsResult.providerMetrics());
                dayDto.setMetrics(metricsResult.combined());
                dayDto.setConversionDataAvailable(metricsResult.conversionDataAvailable());

                InsightPeriodDto dayPeriod = InsightMetricsExtractor.computeActivityPeriod(singleRowList);
                dayDto.setActivityPeriod(dayPeriod);
                dayDto.setDaysWithActivity(dayPeriod != null ? 1 : 0);

                boolean paginationComplete = !Boolean.FALSE.equals(entity.getPaginationComplete());
                dayDto.setSyncStatus(paginationComplete ? InsightSyncStatus.COMPLETE : InsightSyncStatus.PARTIALLY_COMPLETE);
                dayDto.setSyncComplete(dayDto.getSyncStatus() == InsightSyncStatus.COMPLETE);
                dayDto.setRawData(row);
                dayDto.setWarnings(List.of());

                result.add(dayDto);
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    protected InsightMetricDto toMetricDto(InsightMetricEntity entity) {
        InsightMetricDto dto = new InsightMetricDto();
        dto.setName(entity.getName());
        dto.setValueNumber(entity.getValueNumber());
        dto.setValueText(entity.getValueText());
        return dto;
    }
}
