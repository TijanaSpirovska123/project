package com.example.marketing.insights.analytics.service;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.RankingEntryDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rankings (Step 12) — groups an already-loaded {@link CanonicalDataset}'s day-level records by
 * object, aggregates each object's own totals, then ranks. Unavailable metric values are always
 * excluded unless includeUnavailable=true (never treated as zero/worst-case).
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final MetricAggregationService aggregationService;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;
    private final InsightsAnalyticsLimitsProperties limits;

    public List<RankingEntryDto> rank(CanonicalDataset dataset, CanonicalMetric metric, String direction, int limit,
            BigDecimal minimumSpend, BigDecimal minimumImpressions, boolean includeUnavailable) {

        ProviderAnalyticsStrategy strategy = strategyRegistry.getStrategy(dataset.getProvider());

        Map<String, List<CanonicalInsightRecord>> byObject = new LinkedHashMap<>();
        for (CanonicalInsightRecord r : dataset.getRecords()) {
            byObject.computeIfAbsent(r.getObjectExternalId(), k -> new ArrayList<>()).add(r);
        }

        // A single-object aggregation scope is always "single entity" for reach purposes.
        AnalyticsScope singleEntityScope = AnalyticsScope.builder()
                .objectType(dataset.getScope().getObjectType())
                .selectedObjectCount(1)
                .build();

        List<RankingEntryDto> entries = new ArrayList<>();
        for (var entry : byObject.entrySet()) {
            String objectId = entry.getKey();
            List<CanonicalInsightRecord> records = entry.getValue();
            var agg = aggregationService.aggregate(records, strategy, records.size() == 1 ? singleEntityScope : dataset.getScope());

            var metricResult = agg.metrics().get(metric);
            var spendResult = agg.metrics().get(CanonicalMetric.SPEND);
            var impressionsResult = agg.metrics().get(CanonicalMetric.IMPRESSIONS);
            var clicksResult = agg.metrics().get(CanonicalMetric.CLICKS);

            if (!includeUnavailable && (metricResult == null || !metricResult.available())) continue;

            BigDecimal spend = spendResult != null && spendResult.available() ? spendResult.rawValue() : null;
            BigDecimal impressions = impressionsResult != null && impressionsResult.available() ? impressionsResult.rawValue() : null;
            BigDecimal clicks = clicksResult != null && clicksResult.available() ? clicksResult.rawValue() : null;

            if (minimumSpend != null && (spend == null || spend.compareTo(minimumSpend) < 0)) continue;
            if (minimumImpressions != null && (impressions == null || impressions.compareTo(minimumImpressions) < 0)) continue;

            LocalDateRange range = dateRangeOf(records);

            entries.add(RankingEntryDto.builder()
                    .objectType(dataset.getScope().getObjectType())
                    .objectExternalId(objectId)
                    .objectName(dataset.getObjectNames().get(objectId))
                    .value(metricResult != null && metricResult.available() ? metricResult.rawValue().setScale(2, java.math.RoundingMode.HALF_UP) : null)
                    .currency(dataset.getCurrency())
                    .activityPeriod(range == null ? null : new InsightPeriodDto(range.start(), range.stop()))
                    .spend(spend != null ? spend.setScale(2, java.math.RoundingMode.HALF_UP) : null)
                    .impressions(impressions != null ? impressions.longValue() : null)
                    .clicks(clicks != null ? clicks.longValue() : null)
                    .build());
        }

        Comparator<RankingEntryDto> comparator = Comparator.comparing(
                (RankingEntryDto e) -> e.getValue() == null ? BigDecimal.ZERO : e.getValue());
        comparator = "ASC".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
        entries.sort(comparator);

        int effectiveLimit = Math.min(limit > 0 ? limit : limits.getMaxRankingResults(), limits.getMaxRankingResults());
        if (limit > limits.getMaxRankingResults()) {
            throw BusinessException.badRequest("Requested ranking limit " + limit + " exceeds the maximum of " + limits.getMaxRankingResults());
        }

        List<RankingEntryDto> limited = entries.stream().limit(effectiveLimit).toList();
        List<RankingEntryDto> ranked = new ArrayList<>();
        int rank = 1;
        for (RankingEntryDto e : limited) {
            ranked.add(e.toBuilder().rank(rank++).build());
        }
        return ranked;
    }

    private record LocalDateRange(java.time.LocalDate start, java.time.LocalDate stop) {}

    private LocalDateRange dateRangeOf(List<CanonicalInsightRecord> records) {
        java.time.LocalDate min = null, max = null;
        for (CanonicalInsightRecord r : records) {
            if (r.getDate() == null) continue;
            if (min == null || r.getDate().isBefore(min)) min = r.getDate();
            if (max == null || r.getDate().isAfter(max)) max = r.getDate();
        }
        return min == null ? null : new LocalDateRange(min, max);
    }
}
