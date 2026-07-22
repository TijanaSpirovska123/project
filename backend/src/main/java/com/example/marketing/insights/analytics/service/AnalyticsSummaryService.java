package com.example.marketing.insights.analytics.service;

import com.example.marketing.insights.analytics.dto.AnalyticsSummaryDto;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.MetricValueDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricCategory;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the aggregated summary (Step 9) from an already-loaded {@link CanonicalDataset} — never
 * queries or parses anything itself. Contains no provider checks; all provider knowledge is
 * obtained via {@link ProviderAnalyticsStrategyRegistry}.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsSummaryService {

    private final MetricAggregationService aggregationService;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;

    public AnalyticsSummaryDto summarize(CanonicalDataset dataset) {
        ProviderAnalyticsStrategy strategy = strategyRegistry.getStrategy(dataset.getProvider());
        var aggregation = aggregationService.aggregate(dataset.getRecords(), strategy, dataset.getScope());

        Map<String, MetricValueDto> metrics = new LinkedHashMap<>();
        for (CanonicalMetric metric : CanonicalMetric.values()) {
            String unit = unitFor(metric);
            metrics.put(metric.publicName(), aggregation.toDto(metric, unit));
        }

        return AnalyticsSummaryDto.builder()
                .provider(dataset.getProvider())
                .adAccountId(dataset.getAdAccountId())
                .requestedPeriod(dataset.getRequestedPeriod())
                .scope(dataset.getScope())
                .currency(dataset.getCurrency())
                .metrics(metrics)
                .syncStatus(dataset.getOverallSyncStatus())
                .warnings(dataset.getWarnings())
                .build();
    }

    static String unitFor(CanonicalMetric metric) {
        if (metric.category() == MetricCategory.CURRENCY || metric.category() == MetricCategory.REVENUE) return "currency";
        return switch (metric) {
            case CTR, CONVERSION_RATE -> "percentage";
            case CPC, CPM, COST_PER_LEAD, COST_PER_CONVERSION, COST_PER_PURCHASE -> "currency";
            case ROAS, FREQUENCY -> "ratio";
            default -> "count";
        };
    }
}
