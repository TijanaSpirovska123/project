package com.example.marketing.insights.analytics.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.MetricSample;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.analytics.strategy.MetaAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Step 24's "Metric aggregation" (1-14) and "Reach" (15-18) required test scenarios: every
 * ratio formula recalculated from aggregated totals (never averaged), zero-denominator/null-
 * numerator/unavailable handling, BigDecimal precision, and non-additive reach.
 */
class MetricAggregationServiceTest {

    private final MetricAggregationService service = new MetricAggregationService();
    private final ProviderAnalyticsStrategy metaStrategy = new MetaAnalyticsStrategy();

    private CanonicalInsightRecord record(LocalDate date, Map<CanonicalMetric, MetricSample> metrics) {
        return CanonicalInsightRecord.builder()
                .provider(Provider.META)
                .adAccountId("act_1")
                .objectType(InsightObjectType.CAMPAIGN)
                .objectExternalId("camp1")
                .currency("USD")
                .date(date)
                .syncComplete(true)
                .syncStatus(InsightSyncStatus.COMPLETE)
                .singleEntity(true)
                .baseMetrics(metrics)
                .build();
    }

    private Map<CanonicalMetric, MetricSample> metrics(Object... kv) {
        Map<CanonicalMetric, MetricSample> m = CanonicalInsightRecord.emptyMetrics();
        for (int i = 0; i < kv.length; i += 2) {
            CanonicalMetric metric = (CanonicalMetric) kv[i];
            Object val = kv[i + 1];
            m.put(metric, val == null ? MetricSample.unavailable(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER)
                    : MetricSample.of(new BigDecimal(val.toString())));
        }
        return m;
    }

    private AnalyticsScope multiScope() {
        return AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(2).build();
    }

    private AnalyticsScope singleScope() {
        return AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(1).build();
    }

    @Test
    void ctr_recalculatedFromAggregatedClicksAndImpressions() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.CLICKS, 10, CanonicalMetric.IMPRESSIONS, 100)),
                record(LocalDate.of(2026, 1, 2), metrics(CanonicalMetric.CLICKS, 1, CanonicalMetric.IMPRESSIONS, 900)));

        var result = service.aggregate(records, metaStrategy, multiScope());

        // Correct: (10+1)/(100+900)*100 = 1.1 — NOT the average of 10% and 1.111% (5.5%+).
        assertThat(result.metrics().get(CanonicalMetric.CTR).rawValue()).isEqualByComparingTo("1.1");
    }

    @Test
    void cpc_fromAggregatedSpendAndClicks() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "200.00", CanonicalMetric.CLICKS, 50)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.CPC).rawValue()).isEqualByComparingTo("4.00");
    }

    @Test
    void cpm_fromAggregatedSpendAndImpressions() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "200.00", CanonicalMetric.IMPRESSIONS, 5000)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.CPM).rawValue()).isEqualByComparingTo("40.00");
    }

    @Test
    void conversionRate_fromAggregatedConversionsAndClicks() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.CONVERSIONS, 10, CanonicalMetric.CLICKS, 100)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.CONVERSION_RATE).rawValue()).isEqualByComparingTo("10");
    }

    @Test
    void costPerLead_fromAggregatedSpendAndLeads() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "100.00", CanonicalMetric.LEADS, 4)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.COST_PER_LEAD).rawValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void costPerConversion_fromAggregatedSpendAndConversions() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "100.00", CanonicalMetric.CONVERSIONS, 5)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.COST_PER_CONVERSION).rawValue()).isEqualByComparingTo("20.00");
    }

    @Test
    void costPerPurchase_fromAggregatedSpendAndPurchases() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "100.00", CanonicalMetric.PURCHASES, 2)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.COST_PER_PURCHASE).rawValue()).isEqualByComparingTo("50.00");
    }

    @Test
    void roas_fromAggregatedPurchaseValueAndSpend() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.PURCHASE_VALUE, "300.00", CanonicalMetric.SPEND, "100.00")));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.ROAS).rawValue()).isEqualByComparingTo("3");
    }

    @Test
    void zeroDenominator_neverFabricatesZeroOrInfinity_returnsUnavailable() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "100.00", CanonicalMetric.CLICKS, 0, CanonicalMetric.IMPRESSIONS, 0)));
        var result = service.aggregate(records, metaStrategy, singleScope());

        assertThat(result.metrics().get(CanonicalMetric.CTR).available()).isFalse();
        assertThat(result.metrics().get(CanonicalMetric.CTR).unavailableReason()).isEqualTo(MetricUnavailableReason.DIVISION_BY_ZERO);
        assertThat(result.metrics().get(CanonicalMetric.CPC).available()).isFalse();
        assertThat(result.metrics().get(CanonicalMetric.CPC).unavailableReason()).isEqualTo(MetricUnavailableReason.DIVISION_BY_ZERO);
    }

    @Test
    void nullNumerator_unavailable_notZero() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.CLICKS, 10, CanonicalMetric.IMPRESSIONS, 1000)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        // spend never provided -> cpc/cpm unavailable, not zero
        assertThat(result.metrics().get(CanonicalMetric.CPC).available()).isFalse();
        assertThat(result.metrics().get(CanonicalMetric.CPM).available()).isFalse();
    }

    @Test
    void realZero_vs_unavailable_distinguished() {
        // spend=0 with real clicks present -> cpc is a genuine, computable 0.
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "0.00", CanonicalMetric.CLICKS, 10)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.CPC).available()).isTrue();
        assertThat(result.metrics().get(CanonicalMetric.CPC).rawValue()).isEqualByComparingTo("0");
    }

    @Test
    void bigDecimalPrecision_repeatingDecimal_notLostBeforeRounding() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.CLICKS, 1, CanonicalMetric.IMPRESSIONS, 3)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        BigDecimal ctr = result.metrics().get(CanonicalMetric.CTR).rawValue();
        // 1/3*100 = 33.3333... at wide precision, rounds to 33.33 only at the DTO boundary.
        assertThat(result.toDto(CanonicalMetric.CTR, "percentage").getValue()).isEqualByComparingTo("33.33");
    }

    @Test
    void ratiosAreNeverAveraged_recalculatedFromTotals() {
        // Day1: 10 clicks/1000 impr (1%). Day2: 40 clicks/9000 impr (0.444%). Naive average would be 0.72%.
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.CLICKS, 10, CanonicalMetric.IMPRESSIONS, 1000)),
                record(LocalDate.of(2026, 1, 2), metrics(CanonicalMetric.CLICKS, 40, CanonicalMetric.IMPRESSIONS, 9000)));
        var result = service.aggregate(records, metaStrategy, multiScope());
        // Correct: (10+40)/(1000+9000)*100 = 0.50
        assertThat(result.metrics().get(CanonicalMetric.CTR).rawValue()).isEqualByComparingTo("0.5");
    }

    @Test
    void reach_singleEntity_usesOwnValueDirectly() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.REACH, 500, CanonicalMetric.IMPRESSIONS, 1000)));
        var result = service.aggregate(records, metaStrategy, singleScope());
        assertThat(result.metrics().get(CanonicalMetric.REACH).available()).isTrue();
        assertThat(result.metrics().get(CanonicalMetric.REACH).rawValue()).isEqualByComparingTo("500");
        assertThat(result.metrics().get(CanonicalMetric.FREQUENCY).rawValue()).isEqualByComparingTo("2");
    }

    @Test
    void reach_multiEntity_neverSummed_unavailableWithNonAdditiveReason() {
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.REACH, 500, CanonicalMetric.IMPRESSIONS, 1000)),
                record(LocalDate.of(2026, 1, 2), metrics(CanonicalMetric.REACH, 400, CanonicalMetric.IMPRESSIONS, 900)));

        var result = service.aggregate(records, metaStrategy, multiScope());

        assertThat(result.metrics().get(CanonicalMetric.REACH).available()).isFalse();
        assertThat(result.metrics().get(CanonicalMetric.REACH).unavailableReason()).isEqualTo(MetricUnavailableReason.NON_ADDITIVE_AGGREGATION);
        // Frequency must ALSO be unavailable when aggregate reach is unavailable, even though impressions are fine.
        assertThat(result.metrics().get(CanonicalMetric.FREQUENCY).available()).isFalse();
    }

    @Test
    void reach_providerSupportsExactAggregate_summedAsAdditive() {
        ProviderAnalyticsStrategy alwaysAggregatable = new MetaAnalyticsStrategy() {
            @Override
            public boolean supportsReachAggregation(AnalyticsScope scope) {
                return true;
            }
        };
        List<CanonicalInsightRecord> records = List.of(
                record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.REACH, 500, CanonicalMetric.IMPRESSIONS, 1000)),
                record(LocalDate.of(2026, 1, 2), metrics(CanonicalMetric.REACH, 400, CanonicalMetric.IMPRESSIONS, 900)));

        var result = service.aggregate(records, alwaysAggregatable, multiScope());

        assertThat(result.metrics().get(CanonicalMetric.REACH).available()).isTrue();
        assertThat(result.metrics().get(CanonicalMetric.REACH).rawValue()).isEqualByComparingTo("900");
    }

    @Test
    void mixedCurrency_disablesEveryMonetaryMetric() {
        CanonicalInsightRecord usd = record(LocalDate.of(2026, 1, 1), metrics(CanonicalMetric.SPEND, "10.00", CanonicalMetric.CLICKS, 10)).toBuilder().currency("USD").build();
        CanonicalInsightRecord eur = record(LocalDate.of(2026, 1, 2), metrics(CanonicalMetric.SPEND, "20.00", CanonicalMetric.CLICKS, 5)).toBuilder().currency("EUR").build();

        var result = service.aggregate(List.of(usd, eur), metaStrategy, multiScope());

        assertThat(result.mixedCurrency()).isTrue();
        assertThat(result.metrics().get(CanonicalMetric.SPEND).available()).isFalse();
        assertThat(result.metrics().get(CanonicalMetric.SPEND).unavailableReason()).isEqualTo(MetricUnavailableReason.MIXED_CURRENCY);
        assertThat(result.metrics().get(CanonicalMetric.CPC).available()).isFalse();
        // Non-monetary metrics are unaffected.
        assertThat(result.metrics().get(CanonicalMetric.CLICKS).available()).isTrue();
        assertThat(result.metrics().get(CanonicalMetric.CLICKS).rawValue()).isEqualByComparingTo("15");
    }
}
