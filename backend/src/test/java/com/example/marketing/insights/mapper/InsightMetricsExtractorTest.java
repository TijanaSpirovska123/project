package com.example.marketing.insights.mapper;

import com.example.marketing.insights.dto.InsightMetricDto;
import com.example.marketing.insights.strategy.InsightsFetchStrategy;
import com.example.marketing.insights.strategy.MetaInsightsFetchStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies exact calculated values for every RECALCULATED_RATIO formula (CTR, CPC, CPM,
 * frequency, CPA, cost per lead, cost per purchase, ROAS, conversion rate), safe division by
 * zero (null, never 0/Infinity/NaN), and 2-decimal-place rounding — per the "implement correct
 * metric calculations" rule: derive ratios from summed totals, never average pre-computed ones.
 */
class InsightMetricsExtractorTest {

    private final InsightsFetchStrategy strategy = new MetaInsightsFetchStrategy(null, null);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<JsonNode> rows(String... rowsJson) throws Exception {
        return List.of(objectMapper.readTree(rowsJson[0]));
    }

    private List<JsonNode> multiRows(String... rowsJson) throws Exception {
        return List.of(objectMapper.readTree(rowsJson[0]), objectMapper.readTree(rowsJson[1]));
    }

    private Optional<BigDecimal> value(List<InsightMetricDto> metrics, String name) {
        // findFirst() before map(): mapping to a null valueNumber inside the stream would NPE
        // inside Stream.findFirst()'s internal Optional.of(); Optional.map() correctly collapses
        // a null-valued present metric to Optional.empty() instead.
        return metrics.stream().filter(m -> m.getName().equals(name)).findFirst()
                .map(InsightMetricDto::getValueNumber);
    }

    private List<InsightMetricDto> combined(List<JsonNode> rows) {
        return InsightMetricsExtractor.extractMetrics(rows, strategy).combined();
    }

    @Test
    void ctr_computedFromClicksAndImpressions() throws Exception {
        List<JsonNode> rows = rows("{\"clicks\": \"50\", \"impressions\": \"5000\"}");

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "ctr")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1.00"));
    }

    @Test
    void cpc_computedFromSpendAndClicks() throws Exception {
        List<JsonNode> rows = rows("{\"spend\": \"200.00\", \"clicks\": \"50\"}");

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "cpc")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("4.00"));
    }

    @Test
    void cpm_computedFromSpendAndImpressions() throws Exception {
        List<JsonNode> rows = rows("{\"spend\": \"200.00\", \"impressions\": \"5000\"}");

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "cpm")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("40.00"));
    }

    @Test
    void frequency_computedFromImpressionsAndReach_singleRowOnly() throws Exception {
        List<JsonNode> rows = rows("{\"impressions\": \"1000\", \"reach\": \"500\"}");

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "frequency")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("2.00"));
    }

    @Test
    void cpa_computedFromSpendAndConversions() throws Exception {
        List<JsonNode> rows = rows("""
                {"spend": "100.00", "conversions": [{"action_type": "offsite_conversion.custom.1", "value": "5"}]}
                """);

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "cpa")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("20.00"));
    }

    @Test
    void costPerLead_computedFromSpendAndLeads() throws Exception {
        List<JsonNode> rows = rows("""
                {"spend": "100.00", "actions": [{"action_type": "lead", "value": "4"}]}
                """);

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "costPerLead")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("25.00"));
    }

    @Test
    void costPerPurchase_computedFromSpendAndPurchases() throws Exception {
        List<JsonNode> rows = rows("""
                {"spend": "100.00", "actions": [{"action_type": "purchase", "value": "2"}]}
                """);

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "costPerPurchase")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("50.00"));
    }

    @Test
    void roas_computedFromPurchaseValueAndSpend() throws Exception {
        List<JsonNode> rows = rows("""
                {"spend": "100.00",
                 "actions": [{"action_type": "purchase", "value": "1"}],
                 "action_values": [{"action_type": "purchase", "value": "300.00"}]}
                """);

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "roas")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("3.00"));
    }

    @Test
    void conversionRate_computedFromConversionsAndClicks() throws Exception {
        List<JsonNode> rows = rows("""
                {"clicks": "100", "conversions": [{"action_type": "offsite_conversion.custom.1", "value": "10"}]}
                """);

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "conversionRate")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("10.00"));
    }

    @Test
    void divisionByZero_everyRatioReturnsNull_neverZeroInfinityOrNaN() throws Exception {
        List<JsonNode> rows = rows("""
                {"spend": "100.00", "clicks": "0", "impressions": "0", "reach": "0"}
                """);

        List<InsightMetricDto> metrics = combined(rows);

        // Absent, or present-but-marked-unavailable (never a fabricated 0/Infinity/NaN value) is
        // how "cannot be calculated" is represented — getValueNumber() must be empty either way.
        assertThat(value(metrics, "ctr")).isEmpty();
        assertThat(value(metrics, "cpc")).isEmpty();
        assertThat(value(metrics, "cpm")).isEmpty();
        assertThat(value(metrics, "frequency")).isEmpty();
        assertThat(value(metrics, "cpa")).isEmpty();
        assertThat(value(metrics, "costPerLead")).isEmpty();
        assertThat(value(metrics, "costPerPurchase")).isEmpty();
        assertThat(value(metrics, "roas")).isEmpty();
        assertThat(value(metrics, "conversionRate")).isEmpty();
    }

    @Test
    void spendZero_withRealClicks_cpcIsRealZero_notAbsent() throws Exception {
        // spend is the numerator here, not the denominator — a real 0 spend with real clicks
        // is a genuine, computable cpc of 0, not "cannot calculate".
        List<JsonNode> rows = rows("{\"spend\": \"0.00\", \"clicks\": \"10\"}");

        List<InsightMetricDto> metrics = combined(rows);

        assertThat(value(metrics, "cpc")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.00"));
    }

    @Test
    void rounding_alwaysTwoDecimalPlaces_evenForRepeatingDecimals() throws Exception {
        // 1/3 * 100 = 33.333... — must round to exactly 2 decimal places, not truncate or
        // return a long repeating decimal.
        List<JsonNode> rows = rows("{\"clicks\": \"1\", \"impressions\": \"3\"}");

        List<InsightMetricDto> metrics = combined(rows);

        BigDecimal ctr = value(metrics, "ctr").orElseThrow();
        assertThat(ctr.scale()).isEqualTo(2);
        assertThat(ctr).isEqualByComparingTo("33.33");
    }

    @Test
    void multiRow_ratiosRecalculatedFromSummedTotals_notAveragedPerRow() throws Exception {
        // Day 1: 10 clicks / 1000 impressions (ctr 1.0%). Day 2: 40 clicks / 1000 impressions
        // (ctr 4.0%). A naive average would give 2.5%; the correct weighted result from summed
        // totals is 50/2000*100 = 2.5% here by coincidence of these numbers — use asymmetric
        // impressions instead to prove it's not a per-row average.
        List<JsonNode> rows = multiRows(
                "{\"clicks\": \"10\", \"impressions\": \"1000\"}",
                "{\"clicks\": \"40\", \"impressions\": \"9000\"}");

        List<InsightMetricDto> metrics = combined(rows);

        // Correct: (10+40)/(1000+9000)*100 = 0.50. A naive average of each day's own ctr
        // (1.0% and 0.444...%) would give a different, wrong number.
        assertThat(value(metrics, "ctr")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.50"));
    }

    @Test
    void conversionMetrics_alwaysPresent_markedUnavailableWithReason_whenNoTrackingData() throws Exception {
        List<JsonNode> rows = rows("{\"spend\": \"100.00\", \"clicks\": \"10\", \"impressions\": \"1000\"}");

        InsightMetricsExtractor.MetricsResult result = InsightMetricsExtractor.extractMetrics(rows, strategy);

        assertThat(result.conversionDataAvailable()).isFalse();
        var purchases = result.normalizedMetrics().stream()
                .filter(m -> m.getName().equals("purchases")).findFirst().orElseThrow();
        assertThat(purchases.getValueNumber()).isNull();
        assertThat(purchases.isAvailable()).isFalse();
        assertThat(purchases.getUnavailableReason()).isEqualTo(
                com.example.marketing.insights.util.InsightUnavailableReason.CONVERSION_TRACKING_UNAVAILABLE);

        var roas = result.normalizedMetrics().stream()
                .filter(m -> m.getName().equals("roas")).findFirst().orElseThrow();
        assertThat(roas.getValueNumber()).isNull();
        assertThat(roas.isAvailable()).isFalse();
        assertThat(roas.getUnavailableReason()).isEqualTo(
                com.example.marketing.insights.util.InsightUnavailableReason.PURCHASE_VALUE_UNAVAILABLE);
    }

    @Test
    void normalizedAndProviderMetrics_areStructurallySeparated_noOverlapInNames() throws Exception {
        List<JsonNode> rows = rows("""
                {"spend": "50.00", "impressions": "1000", "clicks": "10",
                 "actions": [{"action_type": "purchase", "value": "2"}],
                 "action_values": [{"action_type": "purchase", "value": "80.00"}]}
                """);

        InsightMetricsExtractor.MetricsResult result = InsightMetricsExtractor.extractMetrics(rows, strategy);

        var normalizedNames = result.normalizedMetrics().stream().map(InsightMetricDto::getName)
                .collect(Collectors.toSet());
        var providerNames = result.providerMetrics().stream().map(InsightMetricDto::getName)
                .collect(Collectors.toSet());

        assertThat(normalizedNames).contains("spend", "impressions", "clicks", "purchases", "purchaseValue", "roas");
        assertThat(providerNames).contains("actions.purchase", "action_values.purchase");
        assertThat(java.util.Collections.disjoint(normalizedNames, providerNames)).isTrue();
    }
}
