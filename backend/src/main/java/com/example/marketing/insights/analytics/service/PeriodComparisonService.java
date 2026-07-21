package com.example.marketing.insights.analytics.service;

import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.ComparisonMetricDto;
import com.example.marketing.insights.analytics.dto.CoverageDto;
import com.example.marketing.insights.analytics.dto.PeriodComparisonResultDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.ComparisonMode;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Period comparison (Step 11). Lives at POST /api/insights/analytics/compare — deliberately NOT
 * at the existing /api/insights/compare path, which Step 23 requires preserving unchanged for
 * its own (cross-platform) meaning. Both the current and comparison {@link CanonicalDataset} are
 * loaded once each by the caller via {@code CanonicalDatasetLoader} and passed in here — this
 * service performs no loading/parsing of its own.
 */
@Service
@RequiredArgsConstructor
public class PeriodComparisonService {

    private final MetricAggregationService aggregationService;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;

    /** Same number of inclusive days immediately before currentPeriod.start. */
    public InsightPeriodDto previousPeriod(InsightPeriodDto currentPeriod) {
        long days = ChronoUnit.DAYS.between(currentPeriod.getStart(), currentPeriod.getStop()) + 1;
        LocalDate stop = currentPeriod.getStart().minusDays(1);
        LocalDate start = stop.minusDays(days - 1);
        return new InsightPeriodDto(start, stop);
    }

    public InsightPeriodDto previousYearPeriod(InsightPeriodDto currentPeriod) {
        return new InsightPeriodDto(currentPeriod.getStart().minusYears(1), currentPeriod.getStop().minusYears(1));
    }

    public PeriodComparisonResultDto compare(CanonicalDataset current, CanonicalDataset previous, ComparisonMode mode) {
        ProviderAnalyticsStrategy strategy = strategyRegistry.getStrategy(current.getProvider());
        var currentAgg = aggregationService.aggregate(current.getRecords(), strategy, current.getScope());
        var previousAgg = aggregationService.aggregate(previous.getRecords(), strategy, previous.getScope());

        boolean crossPeriodCurrencyMismatch = current.getCurrency() != null && previous.getCurrency() != null
                && !current.getCurrency().equals(previous.getCurrency());

        List<ComparisonMetricDto> metrics = new ArrayList<>();
        for (CanonicalMetric metric : CanonicalMetric.values()) {
            metrics.add(compareOne(metric, currentAgg, previousAgg, crossPeriodCurrencyMismatch));
        }

        return PeriodComparisonResultDto.builder()
                .currentPeriod(current.getRequestedPeriod())
                .comparisonPeriod(previous.getRequestedPeriod())
                .metrics(metrics)
                .currentCoverage(coverageOf(current))
                .comparisonCoverage(coverageOf(previous))
                .build();
    }

    private ComparisonMetricDto compareOne(CanonicalMetric metric, MetricAggregationService.AggregationResult currentAgg,
            MetricAggregationService.AggregationResult previousAgg, boolean crossPeriodCurrencyMismatch) {

        var curr = currentAgg.metrics().get(metric);
        var prev = previousAgg.metrics().get(metric);
        boolean monetary = "currency".equals(AnalyticsSummaryService.unitFor(metric)) || metric == CanonicalMetric.ROAS;

        if (monetary && crossPeriodCurrencyMismatch) {
            return unavailableComparison(metric, MetricUnavailableReason.MIXED_CURRENCY);
        }
        if (curr == null || prev == null || !curr.available() || !prev.available()) {
            MetricUnavailableReason reason = curr != null && !curr.available() && curr.unavailableReason() != null
                    ? curr.unavailableReason()
                    : (prev != null && prev.unavailableReason() != null ? prev.unavailableReason() : MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER);
            return unavailableComparison(metric, reason);
        }

        BigDecimal currentValue = curr.rawValue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousValue = prev.rawValue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal absoluteChange = currentValue.subtract(previousValue);

        BigDecimal percentageChange;
        String changeReason = null;
        if (previousValue.signum() == 0) {
            if (currentValue.signum() == 0) {
                percentageChange = BigDecimal.ZERO.setScale(2);
            } else {
                percentageChange = null;
                changeReason = "PREVIOUS_VALUE_ZERO";
            }
        } else {
            percentageChange = currentValue.subtract(previousValue)
                    .divide(previousValue.abs(), MetricAggregationService.INTERMEDIATE_MATH_CONTEXT)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        String direction = absoluteChange.signum() > 0 ? "INCREASED" : absoluteChange.signum() < 0 ? "DECREASED" : "UNCHANGED";

        return ComparisonMetricDto.builder()
                .metric(metric)
                .currentValue(currentValue)
                .previousValue(previousValue)
                .absoluteChange(absoluteChange)
                .percentageChange(percentageChange)
                .changeReason(changeReason)
                .direction(direction)
                .available(true)
                .build();
    }

    private ComparisonMetricDto unavailableComparison(CanonicalMetric metric, MetricUnavailableReason reason) {
        return ComparisonMetricDto.builder()
                .metric(metric)
                .available(false)
                .unavailableReason(reason)
                .direction("UNKNOWN")
                .build();
    }

    private CoverageDto coverageOf(CanonicalDataset dataset) {
        return CoverageDto.of(dataset);
    }
}
