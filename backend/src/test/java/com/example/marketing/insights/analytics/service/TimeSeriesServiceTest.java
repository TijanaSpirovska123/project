package com.example.marketing.insights.analytics.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.MetricSample;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
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

/** Step 24 "Time series" (19-25): day/week/month grouping, per-bucket recalculation, inactive-period handling. */
class TimeSeriesServiceTest {

    private final TimeSeriesService service = new TimeSeriesService(
            new MetricAggregationService(),
            new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy())),
            new InsightsAnalyticsLimitsProperties());

    private CanonicalInsightRecord record(LocalDate date, int clicks, int impressions, String spend) {
        Map<CanonicalMetric, MetricSample> m = CanonicalInsightRecord.emptyMetrics();
        m.put(CanonicalMetric.CLICKS, MetricSample.of(BigDecimal.valueOf(clicks)));
        m.put(CanonicalMetric.IMPRESSIONS, MetricSample.of(BigDecimal.valueOf(impressions)));
        m.put(CanonicalMetric.SPEND, MetricSample.of(new BigDecimal(spend)));
        return CanonicalInsightRecord.builder()
                .provider(Provider.META).adAccountId("act_1").objectType(InsightObjectType.CAMPAIGN)
                .objectExternalId("camp1").currency("USD").date(date)
                .syncComplete(true).syncStatus(InsightSyncStatus.COMPLETE).singleEntity(true)
                .baseMetrics(m).build();
    }

    private CanonicalDataset dataset(List<CanonicalInsightRecord> records, LocalDate start, LocalDate stop) {
        return CanonicalDataset.builder()
                .provider(Provider.META).adAccountId("act_1")
                .requestedPeriod(new InsightPeriodDto(start, stop))
                .scope(AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(1).build())
                .records(records)
                .objectNames(Map.of())
                .currency("USD")
                .mixedCurrency(false)
                .overallSyncStatus(InsightSyncStatus.COMPLETE)
                .overallSyncComplete(true)
                .warnings(new ArrayList<>())
                .build();
    }

    @Test
    void dailyGrouping_onePointPerDay() {
        var ds = dataset(List.of(
                record(LocalDate.of(2026, 2, 3), 5, 100, "1.00"),
                record(LocalDate.of(2026, 2, 4), 3, 50, "2.00")
        ), LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 4));

        var result = service.build(ds, TimeGranularity.DAY, false);

        assertThat(result.getSeries()).hasSize(2);
        assertThat(result.getSeries().get(0).getDate()).isEqualTo(LocalDate.of(2026, 2, 3));
        assertThat(result.getSeries().get(0).getMetrics().get("clicks").getValue()).isEqualByComparingTo("5");
    }

    @Test
    void weeklyGrouping_sumsWithinIsoWeek_recalculatesCtr() {
        // Both days fall in the same ISO week (Mon 2026-02-02 .. Sun 2026-02-08).
        var ds = dataset(List.of(
                record(LocalDate.of(2026, 2, 3), 10, 1000, "1.00"),
                record(LocalDate.of(2026, 2, 4), 40, 9000, "2.00")
        ), LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 4));

        var result = service.build(ds, TimeGranularity.WEEK, false);

        assertThat(result.getSeries()).hasSize(1);
        // (10+40)/(1000+9000)*100 = 0.50 -- recalculated, not averaged.
        assertThat(result.getSeries().get(0).getMetrics().get("ctr").getValue()).isEqualByComparingTo("0.50");
    }

    @Test
    void monthlyGrouping_groupsByFirstOfMonth() {
        var ds = dataset(List.of(
                record(LocalDate.of(2026, 2, 3), 1, 10, "1.00"),
                record(LocalDate.of(2026, 2, 27), 2, 20, "2.00")
        ), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        var result = service.build(ds, TimeGranularity.MONTH, false);

        assertThat(result.getSeries()).hasSize(1);
        assertThat(result.getSeries().get(0).getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(result.getSeries().get(0).getMetrics().get("clicks").getValue()).isEqualByComparingTo("3");
    }

    @Test
    void inactivePeriods_excludedByDefault() {
        var ds = dataset(List.of(record(LocalDate.of(2026, 2, 3), 1, 10, "1.00")),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));

        var result = service.build(ds, TimeGranularity.DAY, false);

        assertThat(result.getSeries()).hasSize(1);
    }

    @Test
    void inactivePeriods_includedWhenRequested_zeroFillsDeliveryMetrics_keepsConversionNull() {
        var ds = dataset(List.of(record(LocalDate.of(2026, 2, 3), 1, 10, "1.00")),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3));

        var result = service.build(ds, TimeGranularity.DAY, true);

        assertThat(result.getSeries()).hasSize(3);
        var emptyDay = result.getSeries().get(0); // 2026-02-01
        assertThat(emptyDay.getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(emptyDay.getMetrics().get("spend").getValue()).isEqualByComparingTo("0.00");
        assertThat(emptyDay.getMetrics().get("spend").isAvailable()).isTrue();
        // Conversion metrics must remain unavailable, never zero, on an empty day.
        assertThat(emptyDay.getMetrics().get("purchases").isAvailable()).isFalse();
        assertThat(emptyDay.getMetrics().get("roas").isAvailable()).isFalse();
    }
}
