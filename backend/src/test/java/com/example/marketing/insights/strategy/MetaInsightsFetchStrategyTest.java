package com.example.marketing.insights.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaInsightsFetchStrategyTest {

    private final MetaInsightsFetchStrategy strategy = new MetaInsightsFetchStrategy(null, null);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractDataRows_returnsEachRowInDataArray() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"data": [{"spend": "1"}, {"spend": "2"}], "paging": {}}
                """);

        List<JsonNode> rows = strategy.extractDataRows(root);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("spend").asText()).isEqualTo("1");
        assertThat(rows.get(1).get("spend").asText()).isEqualTo("2");
    }

    @Test
    void extractDataRows_emptyDataArray_returnsEmptyList() throws Exception {
        JsonNode root = objectMapper.readTree("{\"data\": [], \"paging\": {}}");

        assertThat(strategy.extractDataRows(root)).isEmpty();
    }

    @Test
    void extractDataRows_missingDataField_returnsEmptyList() throws Exception {
        JsonNode root = objectMapper.readTree("{\"paging\": {}}");

        assertThat(strategy.extractDataRows(root)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // normalizeActionMetrics
    // -------------------------------------------------------------------------

    private JsonNode row(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    void normalizeActionMetrics_multipleActionTypes_mapsEachToItsNormalizedName() throws Exception {
        JsonNode row = row("""
                {
                  "actions": [
                    {"action_type": "lead", "value": "3"},
                    {"action_type": "add_to_cart", "value": "7"},
                    {"action_type": "initiate_checkout", "value": "2"},
                    {"action_type": "landing_page_view", "value": "40"}
                  ]
                }
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result.get("leads")).isEqualByComparingTo("3");
        assertThat(result.get("addToCart")).isEqualByComparingTo("7");
        assertThat(result.get("checkoutInitiated")).isEqualByComparingTo("2");
        assertThat(result.get("landingPageViews")).isEqualByComparingTo("40");
        assertThat(result).doesNotContainKey("purchases");
        assertThat(result).doesNotContainKey("registrations");
    }

    @Test
    void normalizeActionMetrics_missingActionTypes_omitsThoseKeysEntirely() throws Exception {
        JsonNode row = row("{\"actions\": [{\"action_type\": \"lead\", \"value\": \"1\"}]}");

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result).containsOnlyKeys("leads");
    }

    @Test
    void normalizeActionMetrics_duplicateEquivalentPurchaseTypes_doesNotDoubleCount() throws Exception {
        // omni_purchase is Meta's own grouped/deduplicated total across channel-specific
        // purchase types — if both appear for the same conversions, summing both would
        // double-count. omni_purchase must win, and the channel-specific one must be ignored.
        JsonNode row = row("""
                {
                  "actions": [
                    {"action_type": "omni_purchase", "value": "10"},
                    {"action_type": "offsite_conversion.fb_pixel_purchase", "value": "10"}
                  ],
                  "action_values": [
                    {"action_type": "omni_purchase", "value": "500.00"},
                    {"action_type": "offsite_conversion.fb_pixel_purchase", "value": "500.00"}
                  ]
                }
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result.get("purchases")).isEqualByComparingTo("10"); // not 20
        assertThat(result.get("purchaseValue")).isEqualByComparingTo("500.00"); // not 1000.00
    }

    @Test
    void normalizeActionMetrics_purchaseCountAndValue_bothPresentAndPaired() throws Exception {
        JsonNode row = row("""
                {
                  "actions": [{"action_type": "purchase", "value": "4"}],
                  "action_values": [{"action_type": "purchase", "value": "199.96"}]
                }
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result.get("purchases")).isEqualByComparingTo("4");
        assertThat(result.get("purchaseValue")).isEqualByComparingTo("199.96");
        // costPerPurchase is NOT computed here — it's always recalculated from summed spend/
        // purchases by InsightMetricsExtractor (see InsightsSnapshotMapperTest), uniformly for
        // single- and multi-row snapshots alike.
        assertThat(result).doesNotContainKey("costPerPurchase");
    }

    @Test
    void normalizeActionMetrics_leadsOnly_onlyLeadsKeyPresent() throws Exception {
        JsonNode row = row("{\"actions\": [{\"action_type\": \"lead\", \"value\": \"5\"}]}");

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result).containsOnlyKeys("leads");
        assertThat(result.get("leads")).isEqualByComparingTo("5");
    }

    @Test
    void normalizeActionMetrics_emptyActionsArray_returnsEmptyMap() throws Exception {
        JsonNode row = row("{\"actions\": []}");

        assertThat(strategy.normalizeActionMetrics(row)).isEmpty();
    }

    @Test
    void normalizeActionMetrics_noActionsFieldAtAll_returnsEmptyMap() throws Exception {
        JsonNode row = row("{\"spend\": \"10.00\"}");

        assertThat(strategy.normalizeActionMetrics(row)).isEmpty();
    }

    @Test
    void normalizeActionMetrics_unsupportedActionTypes_areIgnoredNotError() throws Exception {
        JsonNode row = row("""
                {"actions": [
                    {"action_type": "some_unrecognized_future_action_type", "value": "99"},
                    {"action_type": "lead", "value": "1"}
                ]}
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result).containsOnlyKeys("leads");
        assertThat(result.get("leads")).isEqualByComparingTo("1");
    }

    @Test
    void normalizeActionMetrics_conversionsField_summedAcrossEntries() throws Exception {
        JsonNode row = row("""
                {"conversions": [
                    {"action_type": "offsite_conversion.custom.111", "value": "3"},
                    {"action_type": "offsite_conversion.custom.222", "value": "2"}
                ]}
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result.get("conversions")).isEqualByComparingTo("5");
    }

    @Test
    void normalizeActionMetrics_leadGroupedAndLead_doesNotDoubleCount_groupedWins() throws Exception {
        // onsite_conversion.lead_grouped is Meta's own grouped/deduplicated lead total — same
        // "grouped total across channels" pattern as omni_purchase. Summing it alongside the
        // channel-specific "lead" action type would double-count the same leads.
        JsonNode row = row("""
                {
                  "actions": [
                    {"action_type": "onsite_conversion.lead_grouped", "value": "6"},
                    {"action_type": "lead", "value": "6"}
                  ]
                }
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result.get("leads")).isEqualByComparingTo("6"); // not 12
    }

    @Test
    void normalizeActionMetrics_omniLandingPageViewAndLandingPageView_doesNotDoubleCount_omniWins() throws Exception {
        // omni_landing_page_view is Meta's grouped/deduplicated total, same pattern as
        // omni_purchase — must win over the channel-specific landing_page_view, not be summed
        // alongside it.
        JsonNode row = row("""
                {
                  "actions": [
                    {"action_type": "omni_landing_page_view", "value": "50"},
                    {"action_type": "landing_page_view", "value": "50"}
                  ]
                }
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result.get("landingPageViews")).isEqualByComparingTo("50"); // not 100
    }

    @Test
    void normalizeActionMetrics_neverComputesRoas_alwaysRecalculatedElsewhere() throws Exception {
        // roas is always recalculated from summed purchaseValue/spend by InsightMetricsExtractor
        // (see InsightsSnapshotMapperTest) — never read from Meta's own purchase_roas/
        // website_purchase_roas pass-through, so behavior is identical for single- and
        // multi-row snapshots alike.
        JsonNode row = row("""
                {
                  "purchase_roas": [{"action_type": "omni_purchase", "value": "4.5"}],
                  "website_purchase_roas": [{"action_type": "omni_purchase", "value": "9.9"}]
                }
                """);

        Map<String, BigDecimal> result = strategy.normalizeActionMetrics(row);

        assertThat(result).doesNotContainKey("roas");
    }
}
