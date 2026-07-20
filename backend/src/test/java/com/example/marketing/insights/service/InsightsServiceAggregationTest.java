package com.example.marketing.insights.service;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.mapper.InsightsSnapshotMapper;
import com.example.marketing.insights.strategy.InsightsFetchStrategyRegistry;
import com.example.marketing.insights.strategy.MetaInsightsFetchStrategy;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distinguishes missing data (null) from genuine zero, per the "null vs zero" rule: 0 means the
 * provider returned valid data and the result really is zero; null means the metric is
 * unavailable or cannot be calculated (e.g. dividing by a zero/absent denominator).
 * <p>
 * aggregateNormalizedMetrics (backing /compare) also had the same root bug as the empty-metrics
 * issue fixed earlier — it re-parsed rawJson's top level directly instead of its "data" rows, so
 * spend/impressions/clicks always read as 0/absent regardless of what was actually synced. It now
 * reuses the mapper's already-correct extraction instead of duplicating (and re-breaking) it.
 */
class InsightsServiceAggregationTest {

    private final InsightsSnapshotMapper mapper = Mappers.getMapper(InsightsSnapshotMapper.class);

    {
        InsightsFetchStrategyRegistry registry =
                new InsightsFetchStrategyRegistry(new MetaInsightsFetchStrategy(null, null));
        ReflectionTestUtils.setField(mapper, "strategyRegistry", registry);
    }

    private final InsightsService service =
            new InsightsService(null, null, null, mapper, null, null, null);

    private InsightSnapshotEntity snapshotWithRawJson(String rawJson) {
        return InsightSnapshotEntity.builder()
                .provider(Provider.META)
                .dateStart(LocalDate.of(2026, 7, 1))
                .dateStop(LocalDate.of(2026, 7, 1))
                .rawJson(rawJson)
                .build();
    }

    @Test
    void noSnapshots_returnsNullForEverything_notZero() {
        Map<String, Object> result = service.aggregateNormalizedMetrics(List.of());

        assertThat(result.get("spend")).isNull();
        assertThat(result.get("impressions")).isNull();
        assertThat(result.get("clicks")).isNull();
        assertThat(result.get("ctr")).isNull();
        assertThat(result.get("cpc")).isNull();
    }

    @Test
    void realData_extractsFromDataRows_notTopLevelEnvelope() {
        // This exact shape used to return all zeros — spend/impressions/clicks live in
        // data[0], not at the envelope's top level, mirroring the empty-metrics bug.
        InsightSnapshotEntity snap = snapshotWithRawJson("""
                {"data": [{"date_start": "2026-07-01", "date_stop": "2026-07-01",
                           "spend": "200.00", "impressions": "5000", "clicks": "50"}],
                 "paging": {}}
                """);

        Map<String, Object> result = service.aggregateNormalizedMetrics(List.of(snap));

        assertThat((BigDecimal) result.get("spend")).isEqualByComparingTo("200.00");
        assertThat(result.get("impressions")).isEqualTo(5000L);
        assertThat(result.get("clicks")).isEqualTo(50L);
        assertThat((BigDecimal) result.get("ctr")).isEqualByComparingTo("1.00"); // 50/5000 * 100
        assertThat((BigDecimal) result.get("cpc")).isEqualByComparingTo("4.00"); // 200/50
    }

    @Test
    void zeroClicks_cpcIsNull_ctrIsRealZero() {
        // impressions > 0 so ctr=0 is a real, computable zero; clicks=0 makes cpc undefined.
        InsightSnapshotEntity snap = snapshotWithRawJson("""
                {"data": [{"date_start": "2026-07-01", "date_stop": "2026-07-01",
                           "spend": "10.00", "impressions": "1000", "clicks": "0"}],
                 "paging": {}}
                """);

        Map<String, Object> result = service.aggregateNormalizedMetrics(List.of(snap));

        assertThat((BigDecimal) result.get("ctr")).isEqualByComparingTo("0.00");
        assertThat(result.get("cpc")).isNull();
    }

    @Test
    void snapshotWithNoParseableMetrics_stillYieldsNulls() {
        InsightSnapshotEntity snap = snapshotWithRawJson("{\"data\": [], \"paging\": {}}");

        Map<String, Object> result = service.aggregateNormalizedMetrics(List.of(snap));

        assertThat(result.get("spend")).isNull();
        assertThat(result.get("impressions")).isNull();
    }

    @Test
    void safeRatio_bigDecimal_zeroDenominator_returnsNull() {
        assertThat(InsightsService.safeRatio(BigDecimal.TEN, BigDecimal.ZERO, null)).isNull();
        assertThat(InsightsService.safeRatio(BigDecimal.TEN, null, null)).isNull();
        assertThat(InsightsService.safeRatio(null, BigDecimal.TEN, null)).isNull();
    }

    @Test
    void safeRatio_bigDecimal_validInputs_computesCorrectly() {
        BigDecimal result = InsightsService.safeRatio(new BigDecimal("50"), new BigDecimal("5000"), BigDecimal.valueOf(100));
        assertThat(result).isEqualByComparingTo("1.0000000000");
    }

    // The double-based safeRatio/round2 helpers moved to BreakdownAnalyticsService along with
    // the rest of the breakdown() logic — see BreakdownAnalyticsServiceTest.
}
