package com.example.marketing.insights.analytics.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties;
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

/** Step 24 "Findings"-adjacent rankings coverage: excludes unavailable by default, applies minimum-volume filters, ranks correctly, includes sample-size fields. */
class RankingServiceTest {

    private final RankingService service = new RankingService(
            new MetricAggregationService(),
            new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy())),
            new InsightsAnalyticsLimitsProperties());

    private CanonicalInsightRecord record(String objectId, String spend, Integer purchases) {
        Map<CanonicalMetric, MetricSample> m = CanonicalInsightRecord.emptyMetrics();
        m.put(CanonicalMetric.SPEND, MetricSample.of(new BigDecimal(spend)));
        m.put(CanonicalMetric.IMPRESSIONS, MetricSample.of(BigDecimal.valueOf(1000)));
        m.put(CanonicalMetric.CLICKS, MetricSample.of(BigDecimal.valueOf(10)));
        if (purchases != null) {
            m.put(CanonicalMetric.PURCHASES, MetricSample.of(BigDecimal.valueOf(purchases)));
            m.put(CanonicalMetric.PURCHASE_VALUE, MetricSample.of(BigDecimal.valueOf(purchases * 50L)));
        }
        return CanonicalInsightRecord.builder()
                .provider(Provider.META).adAccountId("act_1").objectType(InsightObjectType.CAMPAIGN)
                .objectExternalId(objectId).currency("USD").date(LocalDate.of(2026, 2, 3))
                .syncComplete(true).syncStatus(InsightSyncStatus.COMPLETE).singleEntity(true)
                .baseMetrics(m).build();
    }

    private CanonicalDataset dataset(List<CanonicalInsightRecord> records) {
        return CanonicalDataset.builder()
                .provider(Provider.META).adAccountId("act_1")
                .requestedPeriod(new InsightPeriodDto(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .scope(AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(3).build())
                .records(records).objectNames(Map.of("camp1", "Campaign One")).currency("USD").mixedCurrency(false)
                .overallSyncStatus(InsightSyncStatus.COMPLETE).overallSyncComplete(true).warnings(new ArrayList<>()).build();
    }

    @Test
    void ranksBySpendDescending() {
        var ds = dataset(List.of(record("camp1", "100.00", null), record("camp2", "50.00", null), record("camp3", "200.00", null)));
        var results = service.rank(ds, CanonicalMetric.SPEND, "DESC", 10, null, null, false);

        assertThat(results).extracting(r -> r.getObjectExternalId()).containsExactly("camp3", "camp1", "camp2");
        assertThat(results.get(0).getRank()).isEqualTo(1);
        assertThat(results.get(0).getObjectName()).isNull();
        assertThat(results.get(1).getObjectName()).isEqualTo("Campaign One");
    }

    @Test
    void excludesUnavailableMetricByDefault_neverTreatedAsZero() {
        // camp1 has no purchases at all (unavailable ROAS); camp2 has purchases/spend, so ROAS resolves.
        var ds = dataset(List.of(record("camp1", "100.00", null), record("camp2", "100.00", 1)));
        var results = service.rank(ds, CanonicalMetric.ROAS, "DESC", 10, null, null, false);

        assertThat(results).extracting(r -> r.getObjectExternalId()).containsExactly("camp2");
    }

    @Test
    void includeUnavailable_true_includesThemWithNullValue() {
        var ds = dataset(List.of(record("camp1", "100.00", null), record("camp2", "100.00", 1)));
        var results = service.rank(ds, CanonicalMetric.ROAS, "DESC", 10, null, null, true);

        assertThat(results).hasSize(2);
        var camp1 = results.stream().filter(r -> r.getObjectExternalId().equals("camp1")).findFirst().orElseThrow();
        assertThat(camp1.getValue()).isNull();
    }

    @Test
    void minimumSpendFilter_excludesBelowThreshold() {
        var ds = dataset(List.of(record("camp1", "10.00", null), record("camp2", "500.00", null)));
        var results = service.rank(ds, CanonicalMetric.SPEND, "DESC", 10, new BigDecimal("100"), null, false);

        assertThat(results).extracting(r -> r.getObjectExternalId()).containsExactly("camp2");
    }

    @Test
    void sampleSizeFieldsIncluded() {
        var ds = dataset(List.of(record("camp1", "100.00", null)));
        var results = service.rank(ds, CanonicalMetric.SPEND, "DESC", 10, null, null, false);

        assertThat(results.get(0).getSpend()).isEqualByComparingTo("100.00");
        assertThat(results.get(0).getImpressions()).isEqualTo(1000L);
        assertThat(results.get(0).getClicks()).isEqualTo(10L);
    }
}
