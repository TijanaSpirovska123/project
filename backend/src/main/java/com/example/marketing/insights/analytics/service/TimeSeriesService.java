package com.example.marketing.insights.analytics.service;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.MetricValueDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesPointDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesResponseDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds day/week/month time-series (Step 10) from an already-loaded {@link CanonicalDataset}.
 * Grouping keys are derived directly from each record's own {@code date} — the provider's own
 * already-localized calendar date (see CanonicalInsightRecord) — never reinterpreted through the
 * JVM's default timezone. Never averages a bucket's ratio metrics; every bucket is aggregated
 * fresh via {@link MetricAggregationService}, exactly like the whole-dataset summary.
 */
@Service
@RequiredArgsConstructor
public class TimeSeriesService {

    private final MetricAggregationService aggregationService;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;
    private final InsightsAnalyticsLimitsProperties limits;

    public TimeSeriesResponseDto build(CanonicalDataset dataset, TimeGranularity granularity, boolean includeInactivePeriods) {
        ProviderAnalyticsStrategy strategy = strategyRegistry.getStrategy(dataset.getProvider());

        Map<LocalDate, List<CanonicalInsightRecord>> grouped = new TreeMap<>();
        for (CanonicalInsightRecord r : dataset.getRecords()) {
            if (r.getDate() == null) continue;
            grouped.computeIfAbsent(bucketKeyFor(r.getDate(), granularity), k -> new ArrayList<>()).add(r);
        }

        List<LocalDate> bucketKeys = includeInactivePeriods
                ? allBucketKeysInRange(dataset.getRequestedPeriod(), granularity)
                : new ArrayList<>(grouped.keySet());

        if (bucketKeys.size() > limits.getMaxTimeSeriesBuckets()) {
            throw BusinessException.badRequest("Requested range produces " + bucketKeys.size()
                    + " time-series buckets, exceeding the maximum of " + limits.getMaxTimeSeriesBuckets());
        }

        List<TimeSeriesPointDto> points = new ArrayList<>();
        for (LocalDate key : bucketKeys) {
            List<CanonicalInsightRecord> bucketRecords = grouped.getOrDefault(key, List.of());
            Map<String, MetricValueDto> metrics = bucketRecords.isEmpty()
                    ? zeroFilledMetrics(dataset.getCurrency())
                    : toMetricMap(aggregationService.aggregate(bucketRecords, strategy, dataset.getScope()));
            points.add(TimeSeriesPointDto.builder().date(key).metrics(metrics).build());
        }

        return TimeSeriesResponseDto.builder()
                .provider(dataset.getProvider())
                .granularity(granularity)
                .period(dataset.getRequestedPeriod())
                .series(points)
                .build();
    }

    private Map<String, MetricValueDto> toMetricMap(MetricAggregationService.AggregationResult aggregation) {
        Map<String, MetricValueDto> metrics = new LinkedHashMap<>();
        for (CanonicalMetric metric : CanonicalMetric.values()) {
            metrics.put(metric.normalizedName(), aggregation.toDto(metric, AnalyticsSummaryService.unitFor(metric)));
        }
        return metrics;
    }

    /**
     * A bucket with zero contributing records means confirmed zero delivery (real zero, not
     * unavailable) for additive delivery metrics — but conversion/revenue metrics stay
     * unavailable, since a day with no activity row is not the same as a day with confirmed-zero
     * conversion tracking.
     */
    private Map<String, MetricValueDto> zeroFilledMetrics(String currency) {
        Map<String, MetricValueDto> metrics = new LinkedHashMap<>();
        for (CanonicalMetric metric : CanonicalMetric.values()) {
            String unit = AnalyticsSummaryService.unitFor(metric);
            boolean isZeroableDelivery = metric == CanonicalMetric.SPEND || metric == CanonicalMetric.IMPRESSIONS
                    || metric == CanonicalMetric.CLICKS || metric == CanonicalMetric.LANDING_PAGE_VIEWS;
            if (isZeroableDelivery) {
                BigDecimal zero = BigDecimal.ZERO.setScale(2);
                metrics.put(metric.normalizedName(), "currency".equals(unit)
                        ? MetricValueDto.available(zero, unit, currency)
                        : MetricValueDto.available(zero, unit));
            } else {
                metrics.put(metric.normalizedName(), MetricValueDto.unavailable(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER, unit));
            }
        }
        return metrics;
    }

    private static LocalDate bucketKeyFor(LocalDate date, TimeGranularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(DayOfWeek.MONDAY);
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static List<LocalDate> allBucketKeysInRange(InsightPeriodDto period, TimeGranularity granularity) {
        List<LocalDate> keys = new ArrayList<>();
        LocalDate cursor = bucketKeyFor(period.getStart(), granularity);
        LocalDate lastKey = bucketKeyFor(period.getStop(), granularity);
        while (!cursor.isAfter(lastKey)) {
            keys.add(cursor);
            cursor = switch (granularity) {
                case DAY -> cursor.plusDays(1);
                case WEEK -> cursor.plusWeeks(1);
                case MONTH -> cursor.plusMonths(1);
            };
        }
        return keys;
    }
}
