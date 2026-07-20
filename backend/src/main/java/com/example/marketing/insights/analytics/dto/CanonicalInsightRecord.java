package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-independent input to every analytics service (summary, time-series, comparison,
 * rankings, findings). Built once per {@code InsightSnapshotDto} by {@code CanonicalRecordMapper}
 * — this is the ONLY shape shared analytics code may read; it never touches providerMetrics or
 * rawData. Only base (additive) canonical metrics are carried here — derived ratios (ctr, cpc,
 * roas, ...) are always recalculated by {@code MetricAggregationService} from these numerators/
 * denominators, never read pre-computed from a snapshot and then aggregated further.
 */
@Value
@Builder(toBuilder = true)
public class CanonicalInsightRecord {
    Provider provider;
    String adAccountId;
    InsightObjectType objectType;
    String objectExternalId;
    String objectName;
    String objective;
    String currency;

    /** The date this record represents — one calendar day for daily-granularity records. Null for whole-range (all_days) records. */
    LocalDate date;

    /** requestedPeriod (dateStart/dateStop as synced). */
    InsightPeriodDto requestedPeriod;
    /** Range actually covered by delivery data — null if no delivery. */
    InsightPeriodDto activityPeriod;
    Integer daysWithActivity;
    boolean syncComplete;
    InsightSyncStatus syncStatus;

    /** True only when this record represents exactly one underlying entity (not a merge of several) — reach may only be read directly (not summed) when true. */
    boolean singleEntity;

    /** Additive/base canonical metrics only (spend, impressions, clicks, reach, purchases, purchaseValue, leads, conversions, landingPageViews, uniqueClicks, outboundClicks). */
    Map<CanonicalMetric, MetricSample> baseMetrics;

    public MetricSample metric(CanonicalMetric metric) {
        MetricSample s = baseMetrics.get(metric);
        return s != null ? s : MetricSample.unavailable(null);
    }

    public static Map<CanonicalMetric, MetricSample> emptyMetrics() {
        return new EnumMap<>(CanonicalMetric.class);
    }
}
