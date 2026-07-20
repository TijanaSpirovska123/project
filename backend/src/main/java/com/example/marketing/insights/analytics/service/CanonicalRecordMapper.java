package com.example.marketing.insights.analytics.service;

import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.MetricSample;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricCategory;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.dto.InsightMetricDto;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.util.InsightUnavailableReason;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts a Phase-1 {@link InsightSnapshotDto} (already normalized by InsightsSnapshotMapper)
 * into the provider-independent {@link CanonicalInsightRecord} every Phase-2 analytics service
 * consumes. Reads ONLY normalizedMetrics — never providerMetrics or rawData. Currency and
 * object-name resolution (which DO need provider-specific/DB lookups) are deliberately NOT done
 * here — they belong to {@code CanonicalDatasetLoader}, the one place allowed to combine this
 * mapper's output with those extra lookups, so this class stays a pure, single-snapshot
 * transformation with no side effects or extra queries.
 */
@Component
public class CanonicalRecordMapper {

    /** Metrics carried as base (additive/non-additive) samples — DERIVED_RATIO metrics are recalculated later, never copied through. */
    private static final Set<CanonicalMetric> BASE_METRICS = EnumSet.complementOf(
            EnumSet.copyOf(java.util.Arrays.stream(CanonicalMetric.values())
                    .filter(m -> m.category() == MetricCategory.DERIVED_RATIO)
                    .toList()));

    public CanonicalInsightRecord toRecord(InsightSnapshotDto dto) {
        Map<String, InsightMetricDto> byName = new java.util.HashMap<>();
        if (dto.getNormalizedMetrics() != null) {
            for (InsightMetricDto m : dto.getNormalizedMetrics()) {
                byName.put(m.getName(), m);
            }
        }

        Map<CanonicalMetric, MetricSample> baseMetrics = CanonicalInsightRecord.emptyMetrics();
        for (CanonicalMetric metric : BASE_METRICS) {
            baseMetrics.put(metric, sampleFor(byName.get(metric.normalizedName())));
        }

        return CanonicalInsightRecord.builder()
                .provider(dto.getProvider())
                .adAccountId(dto.getAdAccountId())
                .objectType(dto.getObjectType())
                .objectExternalId(dto.getObjectExternalId())
                .objectName(null)
                .objective(null)
                .currency(null)
                .date(dto.getActivityPeriod() != null ? dto.getActivityPeriod().getStart() : null)
                .requestedPeriod(new InsightPeriodDto(dto.getDateStart(), dto.getDateStop()))
                .activityPeriod(dto.getActivityPeriod())
                .daysWithActivity(dto.getDaysWithActivity())
                .syncComplete(dto.isSyncComplete())
                .syncStatus(dto.getSyncStatus())
                .singleEntity(true)
                .baseMetrics(baseMetrics)
                .build();
    }

    private MetricSample sampleFor(InsightMetricDto metricDto) {
        if (metricDto == null) {
            return MetricSample.unavailable(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER);
        }
        if (!metricDto.isAvailable() || metricDto.getValueNumber() == null) {
            return MetricSample.unavailable(translateReason(metricDto.getUnavailableReason()));
        }
        return MetricSample.of(metricDto.getValueNumber());
    }

    private MetricUnavailableReason translateReason(String phase1Reason) {
        if (phase1Reason == null) return MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER;
        try {
            return MetricUnavailableReason.valueOf(phase1Reason);
        } catch (IllegalArgumentException e) {
            // Phase-1's INCOMPATIBLE_OBJECTIVE/INSUFFICIENT_DATA aren't in the canonical enum's
            // current set of values used here — fall back rather than throw.
            return switch (phase1Reason) {
                case InsightUnavailableReason.INSUFFICIENT_DATA -> MetricUnavailableReason.INSUFFICIENT_DATA;
                default -> MetricUnavailableReason.NOT_SUPPORTED;
            };
        }
    }
}
