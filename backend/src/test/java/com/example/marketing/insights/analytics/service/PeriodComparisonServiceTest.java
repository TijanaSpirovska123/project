package com.example.marketing.insights.analytics.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.MetricSample;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
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

/** Step 24 "Comparison" (26-35): previous-period/previous-year date math, percentage-change edge cases, currency safety. */
class PeriodComparisonServiceTest {

    private final PeriodComparisonService service = new PeriodComparisonService(
            new MetricAggregationService(),
            new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy())));

    private CanonicalInsightRecord record(String currency, Map<CanonicalMetric, MetricSample> m) {
        return CanonicalInsightRecord.builder()
                .provider(Provider.META).adAccountId("act_1").objectType(InsightObjectType.CAMPAIGN)
                .objectExternalId("camp1").currency(currency).date(LocalDate.of(2026, 2, 10))
                .syncComplete(true).syncStatus(InsightSyncStatus.COMPLETE).singleEntity(true)
                .baseMetrics(m).build();
    }

    private Map<CanonicalMetric, MetricSample> spendOnly(String value) {
        Map<CanonicalMetric, MetricSample> m = CanonicalInsightRecord.emptyMetrics();
        m.put(CanonicalMetric.SPEND, MetricSample.of(new BigDecimal(value)));
        return m;
    }

    private CanonicalDataset dataset(List<CanonicalInsightRecord> records, LocalDate start, LocalDate stop, boolean complete) {
        return dataset(records, start, stop, complete, "USD");
    }

    private CanonicalDataset dataset(List<CanonicalInsightRecord> records, LocalDate start, LocalDate stop, boolean complete, String datasetCurrency) {
        return CanonicalDataset.builder()
                .provider(Provider.META).adAccountId("act_1")
                .requestedPeriod(new InsightPeriodDto(start, stop))
                .scope(AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(1).build())
                .records(records).objectNames(Map.of()).currency(datasetCurrency).mixedCurrency(false)
                .overallSyncStatus(complete ? InsightSyncStatus.COMPLETE : InsightSyncStatus.PARTIALLY_COMPLETE)
                .overallSyncComplete(complete).warnings(new ArrayList<>()).build();
    }

    @Test
    void previousPeriod_sameInclusiveDayCount_immediatelyBefore() {
        InsightPeriodDto current = new InsightPeriodDto(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19));
        InsightPeriodDto previous = service.previousPeriod(current);
        assertThat(previous.getStart()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(previous.getStop()).isEqualTo(LocalDate.of(2026, 2, 9));
    }

    @Test
    void previousYear_sameMonthDayRange_yearMinusOne() {
        InsightPeriodDto current = new InsightPeriodDto(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19));
        InsightPeriodDto previous = service.previousYearPeriod(current);
        assertThat(previous.getStart()).isEqualTo(LocalDate.of(2025, 2, 10));
        assertThat(previous.getStop()).isEqualTo(LocalDate.of(2025, 2, 19));
    }

    @Test
    void positivePercentageChange_computedCorrectly() {
        var current = dataset(List.of(record("USD", spendOnly("8.62"))), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true);
        var previous = dataset(List.of(record("USD", spendOnly("2.70"))), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), true);

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);
        var spend = result.getMetrics().stream().filter(m -> m.getMetric() == CanonicalMetric.SPEND).findFirst().orElseThrow();

        assertThat(spend.getDirection()).isEqualTo("INCREASED");
        assertThat(spend.getPercentageChange()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void negativePercentageChange_computedCorrectly() {
        var current = dataset(List.of(record("USD", spendOnly("2.70"))), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true);
        var previous = dataset(List.of(record("USD", spendOnly("8.62"))), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), true);

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);
        var spend = result.getMetrics().stream().filter(m -> m.getMetric() == CanonicalMetric.SPEND).findFirst().orElseThrow();

        assertThat(spend.getDirection()).isEqualTo("DECREASED");
        assertThat(spend.getPercentageChange()).isLessThan(BigDecimal.ZERO);
    }

    @Test
    void previousValueZero_currentPositive_percentageChangeNull_reasonSet() {
        var current = dataset(List.of(record("USD", spendOnly("5.00"))), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true);
        var previous = dataset(List.of(record("USD", spendOnly("0.00"))), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), true);

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);
        var spend = result.getMetrics().stream().filter(m -> m.getMetric() == CanonicalMetric.SPEND).findFirst().orElseThrow();

        assertThat(spend.getPercentageChange()).isNull();
        assertThat(spend.getChangeReason()).isEqualTo("PREVIOUS_VALUE_ZERO");
    }

    @Test
    void bothValuesZero_absoluteAndPercentageChangeAreZero() {
        // A genuine zero-valued spend record on both sides (not "no data at all").
        var current = dataset(List.of(record("USD", spendOnly("0.00"))), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true);
        var previous = dataset(List.of(record("USD", spendOnly("0.00"))), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), true);

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);
        var spend = result.getMetrics().stream().filter(m -> m.getMetric() == CanonicalMetric.SPEND).findFirst().orElseThrow();

        assertThat(spend.isAvailable()).isTrue();
        assertThat(spend.getAbsoluteChange()).isEqualByComparingTo("0");
        assertThat(spend.getPercentageChange()).isEqualByComparingTo("0");
    }

    @Test
    void noDataAtAll_metricUnavailable_notFabricatedZero() {
        var current = dataset(List.of(), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true);
        var previous = dataset(List.of(), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), true);

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);
        var spend = result.getMetrics().stream().filter(m -> m.getMetric() == CanonicalMetric.SPEND).findFirst().orElseThrow();

        assertThat(spend.isAvailable()).isFalse();
    }

    @Test
    void unavailableMetric_comparisonUnavailable_preservesReason() {
        // Neither side has any purchases data -> ROAS unavailable on both sides.
        var current = dataset(List.of(record("USD", spendOnly("5.00"))), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true);
        var previous = dataset(List.of(record("USD", spendOnly("2.00"))), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), true);

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);
        var roas = result.getMetrics().stream().filter(m -> m.getMetric() == CanonicalMetric.ROAS).findFirst().orElseThrow();

        assertThat(roas.isAvailable()).isFalse();
        assertThat(roas.getUnavailableReason()).isNotNull();
    }

    @Test
    void differentCurrencies_monetaryComparisonUnavailable() {
        var current = dataset(List.of(record("USD", spendOnly("5.00"))), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true, "USD");
        var previous = dataset(List.of(record("EUR", spendOnly("2.00"))), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), true, "EUR");

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);
        var spend = result.getMetrics().stream().filter(m -> m.getMetric() == CanonicalMetric.SPEND).findFirst().orElseThrow();

        assertThat(spend.isAvailable()).isFalse();
        assertThat(spend.getUnavailableReason()).isEqualTo(com.example.marketing.insights.analytics.enums.MetricUnavailableReason.MIXED_CURRENCY);
    }

    @Test
    void partialPeriodCoverage_reflectedInCoverageMetadata() {
        var current = dataset(List.of(record("USD", spendOnly("5.00"))), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 19), true);
        var previous = dataset(List.of(), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 9), false);

        var result = service.compare(current, previous, com.example.marketing.insights.analytics.enums.ComparisonMode.PREVIOUS_PERIOD);

        assertThat(result.getCurrentCoverage().isSyncComplete()).isTrue();
        assertThat(result.getCurrentCoverage().getDaysWithActivity()).isEqualTo(1);
        assertThat(result.getComparisonCoverage().isSyncComplete()).isFalse();
        assertThat(result.getComparisonCoverage().getDaysWithActivity()).isEqualTo(0);
    }
}
