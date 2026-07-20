package com.example.marketing.insights.analytics.engine;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsFindingsProperties;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.ComparisonMetricDto;
import com.example.marketing.insights.analytics.dto.MetricSample;
import com.example.marketing.insights.analytics.dto.PeriodComparisonResultDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.service.MetricAggregationService;
import com.example.marketing.insights.analytics.service.RankingService;
import com.example.marketing.insights.analytics.strategy.MetaAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 24 "Findings" (43-54): comparison-based findings, minimum-sample gating, the explicit
 * restriction against unsupported profitability/budget claims, and deterministic (repeatable)
 * output.
 */
class FindingEngineTest {

    private final InsightsAnalyticsFindingsProperties thresholds = new InsightsAnalyticsFindingsProperties();
    private final ProviderAnalyticsStrategyRegistry strategyRegistry = new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy()));
    private final MetricAggregationService aggregationService = new MetricAggregationService();
    private final RankingService rankingService = new RankingService(aggregationService, strategyRegistry, new com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties());
    private final FindingEngine engine = new FindingEngine(thresholds, rankingService, strategyRegistry, aggregationService);

    private CanonicalInsightRecord record(String objectId, String spend, int clicks, int impressions) {
        Map<CanonicalMetric, MetricSample> m = CanonicalInsightRecord.emptyMetrics();
        m.put(CanonicalMetric.SPEND, MetricSample.of(new BigDecimal(spend)));
        m.put(CanonicalMetric.CLICKS, MetricSample.of(BigDecimal.valueOf(clicks)));
        m.put(CanonicalMetric.IMPRESSIONS, MetricSample.of(BigDecimal.valueOf(impressions)));
        return CanonicalInsightRecord.builder()
                .provider(Provider.META).adAccountId("act_1").objectType(InsightObjectType.CAMPAIGN)
                .objectExternalId(objectId).currency("USD").date(LocalDate.of(2026, 2, 3))
                .syncComplete(true).syncStatus(InsightSyncStatus.COMPLETE).singleEntity(true)
                .baseMetrics(m).build();
    }

    private CanonicalDataset dataset(List<CanonicalInsightRecord> records, int selectedCount) {
        return CanonicalDataset.builder()
                .provider(Provider.META).adAccountId("act_1")
                .requestedPeriod(new InsightPeriodDto(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .scope(AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(selectedCount).build())
                .records(records).objectNames(Map.of()).currency("USD").mixedCurrency(false)
                .overallSyncStatus(InsightSyncStatus.COMPLETE).overallSyncComplete(true).warnings(new ArrayList<>()).build();
    }

    private PeriodComparisonResultDto comparisonWith(CanonicalMetric metric, String current, String previous, String pctChange, String direction) {
        return PeriodComparisonResultDto.builder()
                .currentPeriod(new InsightPeriodDto(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .comparisonPeriod(new InsightPeriodDto(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 28)))
                .metrics(List.of(ComparisonMetricDto.builder()
                        .metric(metric)
                        .currentValue(new BigDecimal(current))
                        .previousValue(new BigDecimal(previous))
                        .percentageChange(new BigDecimal(pctChange))
                        .direction(direction)
                        .available(true)
                        .build()))
                .build();
    }

    @Test
    void ctrIncreased_producesFindingWithEvidence() {
        var ds = dataset(List.of(record("camp1", "100.00", 100, 1000)), 1);
        var comparison = comparisonWith(CanonicalMetric.CTR, "1.50", "1.00", "50.00", "INCREASED");

        var findings = engine.evaluate(ds, comparison);

        var ctrFinding = findings.stream().filter(f -> f.getCode().equals("CTR_INCREASED")).findFirst().orElseThrow();
        assertThat(ctrFinding.getEvidence()).isNotEmpty();
        assertThat(ctrFinding.getEvidence().get(0).getCurrentValue()).isEqualByComparingTo("1.50");
        assertThat(ctrFinding.getEvidence().get(0).getPreviousValue()).isEqualByComparingTo("1.00");
    }

    @Test
    void ctrDecreased_producesFinding() {
        var ds = dataset(List.of(record("camp1", "100.00", 100, 1000)), 1);
        var comparison = comparisonWith(CanonicalMetric.CTR, "1.00", "1.50", "-33.33", "DECREASED");

        var findings = engine.evaluate(ds, comparison);

        assertThat(findings).extracting(f -> f.getCode()).contains("CTR_DECREASED");
    }

    @Test
    void belowPercentageChangeThreshold_noFinding() {
        var ds = dataset(List.of(record("camp1", "100.00", 100, 1000)), 1);
        // Only 2% change — below the default 10% threshold.
        var comparison = comparisonWith(CanonicalMetric.CTR, "1.02", "1.00", "2.00", "INCREASED");

        var findings = engine.evaluate(ds, comparison);

        assertThat(findings).extracting(f -> f.getCode()).doesNotContain("CTR_INCREASED", "CTR_DECREASED");
    }

    @Test
    void noComparisonData_producesInsufficientComparisonDataFinding() {
        var ds = dataset(List.of(record("camp1", "100.00", 100, 1000)), 1);

        var findings = engine.evaluate(ds, null);

        assertThat(findings).extracting(f -> f.getCode()).contains("INSUFFICIENT_COMPARISON_DATA");
    }

    @Test
    void conversionDataUnavailable_producesFinding_neverClaimsUnprofitable() {
        var ds = dataset(List.of(record("camp1", "100.00", 100, 1000)), 1); // no PURCHASES metric at all
        var findings = engine.evaluate(ds, null);

        var finding = findings.stream().filter(f -> f.getCode().equals("CONVERSION_DATA_UNAVAILABLE")).findFirst().orElseThrow();
        assertThat(finding.getMessage().toLowerCase()).doesNotContain("unprofitable").doesNotContain("bad roas").doesNotContain("losing money");
    }

    @Test
    void noFindingEverClaimsProfitabilityOrRecommendsBudgetChange() {
        var ds = dataset(List.of(record("camp1", "100.00", 100, 1000), record("camp2", "50.00", 50, 500)), 2);
        var comparison = comparisonWith(CanonicalMetric.CTR, "1.50", "1.00", "50.00", "INCREASED");

        var findings = engine.evaluate(ds, comparison);

        for (var f : findings) {
            String text = (f.getTitle() + " " + f.getMessage()).toLowerCase();
            assertThat(text).doesNotContain("increase your budget", "increase budget", "profitable", "unprofitable",
                    "good ctr", "bad ctr", "too high", "too low", "poor creative", "wrong audience");
        }
    }

    @Test
    void belowMinimumSampleThreshold_noComparisonFindings() {
        // 5 impressions, 1 click, $0.10 spend — below every configured minimum.
        var ds = dataset(List.of(record("camp1", "0.10", 1, 5)), 1);
        var comparison = comparisonWith(CanonicalMetric.CTR, "1.50", "1.00", "50.00", "INCREASED");

        var findings = engine.evaluate(ds, comparison);

        assertThat(findings).extracting(f -> f.getCode()).doesNotContain("CTR_INCREASED");
    }

    @Test
    void deterministicRepeatedOutput_sameInputsSameFindings() {
        var ds = dataset(List.of(record("camp1", "100.00", 100, 1000)), 1);
        var comparison = comparisonWith(CanonicalMetric.CTR, "1.50", "1.00", "50.00", "INCREASED");

        var first = engine.evaluate(ds, comparison);
        var second = engine.evaluate(ds, comparison);

        assertThat(first).extracting(f -> f.getCode()).containsExactlyElementsOf(
                second.stream().map(f -> f.getCode()).toList());
    }

    @Test
    void noActivity_producesNoActivityFinding_onlyOne() {
        var ds = dataset(List.of(), 1);
        var findings = engine.evaluate(ds, null);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getCode()).isEqualTo("NO_ACTIVITY");
    }
}
