package com.example.marketing.insights.analytics.engine;

import com.example.marketing.insights.analytics.config.InsightsAnalyticsFindingsProperties;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.ComparisonMetricDto;
import com.example.marketing.insights.analytics.dto.DeterministicFindingDto;
import com.example.marketing.insights.analytics.dto.FindingEvidenceDto;
import com.example.marketing.insights.analytics.dto.PeriodComparisonResultDto;
import com.example.marketing.insights.analytics.dto.RankingEntryDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.FindingSeverity;
import com.example.marketing.insights.analytics.service.MetricAggregationService;
import com.example.marketing.insights.analytics.service.RankingService;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, rule-based findings (Step 15) — NEVER an LLM. Every rule:
 * <ul>
 *   <li>reads only canonical metrics (never Meta action names);</li>
 *   <li>includes exact evidence values;</li>
 *   <li>respects metric availability (an unavailable metric produces no comparative finding);</li>
 *   <li>respects the configurable minimum-sample-size and percentage-change thresholds;</li>
 *   <li>is a pure function of its inputs — repeated calls on the same data always produce the
 *       same findings, in the same order (Step 24 #52).</li>
 * </ul>
 * Deliberately does NOT: call CTR/CPC "good" or "bad" (no benchmark exists here), claim
 * profitability without ROAS, recommend a budget change from CTR/clicks/impressions alone, claim
 * improvement without comparison data, or infer causes (creative, audience, landing page) from
 * metrics alone. Provider-specific rules never live here — see
 * {@code ProviderAnalyticsStrategy.createProviderSpecificFindings}.
 */
@Service
@RequiredArgsConstructor
public class FindingEngine {

    private final InsightsAnalyticsFindingsProperties thresholds;
    private final RankingService rankingService;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;
    private final MetricAggregationService aggregationService;

    private record MetricDirection(CanonicalMetric metric, String increasedCode, String decreasedCode, String label) {}

    private static final List<MetricDirection> COMPARISON_RULES = List.of(
            new MetricDirection(CanonicalMetric.SPEND, "SPEND_INCREASED", "SPEND_DECREASED", "Spend"),
            new MetricDirection(CanonicalMetric.IMPRESSIONS, "IMPRESSIONS_INCREASED", "IMPRESSIONS_DECREASED", "Impressions"),
            new MetricDirection(CanonicalMetric.CLICKS, "CLICKS_INCREASED", "CLICKS_DECREASED", "Clicks"),
            new MetricDirection(CanonicalMetric.CTR, "CTR_INCREASED", "CTR_DECREASED", "Click-through rate"),
            new MetricDirection(CanonicalMetric.CPC, "CPC_INCREASED", "CPC_DECREASED", "Cost per click"),
            new MetricDirection(CanonicalMetric.CPM, "CPM_INCREASED", "CPM_DECREASED", "Cost per thousand impressions"),
            new MetricDirection(CanonicalMetric.CONVERSIONS, "CONVERSIONS_INCREASED", "CONVERSIONS_DECREASED", "Conversions"),
            new MetricDirection(CanonicalMetric.ROAS, "ROAS_INCREASED", "ROAS_DECREASED", "Return on ad spend"));

    public List<DeterministicFindingDto> evaluate(CanonicalDataset current, PeriodComparisonResultDto comparison) {
        List<DeterministicFindingDto> findings = new ArrayList<>();

        if (current.getRecords().isEmpty()) {
            findings.add(DeterministicFindingDto.builder()
                    .code("NO_ACTIVITY")
                    .severity(FindingSeverity.INFO)
                    .title("No activity in the requested period")
                    .message("No delivery data was found for the selected scope and period.")
                    .evidence(List.of())
                    .confidence("HIGH")
                    .build());
            return findings;
        }

        boolean meetsMinimumSample = meetsMinimumSample(current);

        if (comparison == null) {
            findings.add(DeterministicFindingDto.builder()
                    .code("INSUFFICIENT_COMPARISON_DATA")
                    .severity(FindingSeverity.INFO)
                    .title("No comparison period available")
                    .message("Trend findings (increase/decrease) require a comparison period, which was not provided.")
                    .evidence(List.of())
                    .confidence("HIGH")
                    .build());
        } else if (meetsMinimumSample) {
            for (MetricDirection rule : COMPARISON_RULES) {
                findings.add(comparisonFinding(rule, comparison));
            }
        }

        if (!current.getRecords().get(0).metric(CanonicalMetric.PURCHASES).isAvailable()
                && current.getRecords().stream().noneMatch(r -> r.metric(CanonicalMetric.PURCHASES).isAvailable())) {
            findings.add(DeterministicFindingDto.builder()
                    .code("CONVERSION_DATA_UNAVAILABLE")
                    .severity(FindingSeverity.INFO)
                    .title("Conversion data unavailable")
                    .message("Purchase/conversion tracking data is unavailable for the selected scope — profitability cannot be assessed from this data alone.")
                    .evidence(List.of())
                    .confidence("HIGH")
                    .build());
        }

        if (current.getScope().getSelectedObjectCount() > 1 && meetsMinimumSample) {
            findings.addAll(spendConcentrationFindings(current));
        }

        return findings.stream().filter(java.util.Objects::nonNull).toList();
    }

    private boolean meetsMinimumSample(CanonicalDataset dataset) {
        var strategy = strategyRegistry.getStrategy(dataset.getProvider());
        var agg = aggregationService.aggregate(dataset.getRecords(), strategy, dataset.getScope());
        var impressions = agg.metrics().get(CanonicalMetric.IMPRESSIONS);
        var clicks = agg.metrics().get(CanonicalMetric.CLICKS);
        var spend = agg.metrics().get(CanonicalMetric.SPEND);
        boolean impressionsOk = impressions != null && impressions.available()
                && impressions.rawValue().compareTo(BigDecimal.valueOf(thresholds.getMinimumImpressions())) >= 0;
        boolean clicksOk = clicks != null && clicks.available()
                && clicks.rawValue().compareTo(BigDecimal.valueOf(thresholds.getMinimumClicks())) >= 0;
        boolean spendOk = spend != null && spend.available()
                && spend.rawValue().compareTo(BigDecimal.valueOf(thresholds.getMinimumSpend())) >= 0;
        return impressionsOk || clicksOk || spendOk;
    }

    private DeterministicFindingDto comparisonFinding(MetricDirection rule, PeriodComparisonResultDto comparison) {
        ComparisonMetricDto metric = comparison.getMetrics().stream()
                .filter(m -> m.getMetric() == rule.metric())
                .findFirst().orElse(null);
        if (metric == null || !metric.isAvailable() || metric.getPercentageChange() == null) return null;

        BigDecimal absChangePct = metric.getPercentageChange().abs();
        if (absChangePct.compareTo(BigDecimal.valueOf(thresholds.getPercentageChangeThreshold())) < 0) return null;

        boolean increased = "INCREASED".equals(metric.getDirection());
        String code = increased ? rule.increasedCode() : rule.decreasedCode();
        String direction = increased ? "increased" : "decreased";

        return DeterministicFindingDto.builder()
                .code(code)
                .severity(FindingSeverity.INFO)
                .title(rule.label() + " " + direction)
                .message(rule.label() + " " + direction + " from " + metric.getPreviousValue() + " to " + metric.getCurrentValue()
                        + " (" + metric.getPercentageChange() + "%).")
                .evidence(List.of(FindingEvidenceDto.builder()
                        .metric(rule.metric())
                        .currentValue(metric.getCurrentValue())
                        .previousValue(metric.getPreviousValue())
                        .percentageChange(metric.getPercentageChange())
                        .build()))
                .confidence("HIGH")
                .build();
    }

    private List<DeterministicFindingDto> spendConcentrationFindings(CanonicalDataset current) {
        List<DeterministicFindingDto> findings = new ArrayList<>();
        List<RankingEntryDto> spendRanking = rankingService.rank(current, CanonicalMetric.SPEND, "DESC", 1,
                null, null, false);
        if (spendRanking.isEmpty()) return findings;

        RankingEntryDto top = spendRanking.get(0);
        BigDecimal totalSpend = current.getRecords().stream()
                .map(r -> r.metric(CanonicalMetric.SPEND))
                .filter(com.example.marketing.insights.analytics.dto.MetricSample::isAvailable)
                .map(com.example.marketing.insights.analytics.dto.MetricSample::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (top.getSpend() == null || totalSpend.signum() == 0) return findings;

        BigDecimal share = top.getSpend().divide(totalSpend, MetricAggregationService.INTERMEDIATE_MATH_CONTEXT)
                .multiply(BigDecimal.valueOf(100));

        findings.add(DeterministicFindingDto.builder()
                .code("TOP_SPEND_ENTITY")
                .severity(FindingSeverity.INFO)
                .title("Highest-spend object")
                .message((top.getObjectName() != null ? top.getObjectName() : top.getObjectExternalId())
                        + " accounts for the largest share of spend in the selected scope.")
                .evidence(List.of(FindingEvidenceDto.builder().metric(CanonicalMetric.SPEND).currentValue(top.getSpend()).build()))
                .confidence("HIGH")
                .build());

        if (share.compareTo(BigDecimal.valueOf(thresholds.getHighSpendShareThreshold())) >= 0) {
            findings.add(DeterministicFindingDto.builder()
                    .code("HIGH_SPEND_SHARE")
                    .severity(FindingSeverity.WARNING)
                    .title("Spend concentrated in one object")
                    .message((top.getObjectName() != null ? top.getObjectName() : top.getObjectExternalId())
                            + " accounts for " + share.setScale(2, java.math.RoundingMode.HALF_UP) + "% of total spend in the selected scope.")
                    .evidence(List.of(FindingEvidenceDto.builder().metric(CanonicalMetric.SPEND).currentValue(top.getSpend())
                            .percentageChange(share.setScale(2, java.math.RoundingMode.HALF_UP)).build()))
                    .confidence("HIGH")
                    .build());
        }

        return findings;
    }
}
