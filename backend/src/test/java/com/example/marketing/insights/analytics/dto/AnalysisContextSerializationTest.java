package com.example.marketing.insights.analytics.dto;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the public AnalysisContext JSON contract — the shape a later Python/Pydantic integration
 * will parse directly as datetime.date/datetime.datetime — using the REAL Spring-managed
 * ObjectMapper (the same bean MappingJackson2HttpMessageConverter uses for every controller
 * response, autowired here via {@code @JsonTest}'s Jackson auto-configuration slice). A
 * hand-built {@code new ObjectMapper()} would only prove what a differently-configured mapper
 * does, not what the API actually returns, so this test never constructs its own.
 */
@JsonTest
class AnalysisContextSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private AnalyticsScope scope() {
        return AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN)
                .selectedObjectIds(List.of("120244089191690082")).selectedObjectCount(1)
                .objectsWithActivity(1).objectsWithoutActivity(0).build();
    }

    private AnalysisContextDto fullContext() {
        InsightPeriodDto currentPeriod = new InsightPeriodDto(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 21));
        InsightPeriodDto comparisonPeriod = new InsightPeriodDto(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 13));

        Map<String, MetricValueDto> summaryMetrics = new LinkedHashMap<>();
        summaryMetrics.put(CanonicalMetric.SPEND.publicName(), MetricValueDto.available(new BigDecimal("50.50"), "currency", "USD"));
        summaryMetrics.put(CanonicalMetric.OUTBOUND_CLICKS.publicName(),
                MetricValueDto.unavailable(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER, "count"));

        AnalyticsSummaryDto summary = AnalyticsSummaryDto.builder()
                .provider(Provider.META).adAccountId("act_481686937778670")
                .requestedPeriod(currentPeriod).scope(scope()).currency("USD")
                .metrics(summaryMetrics).syncStatus(InsightSyncStatus.COMPLETE).warnings(List.of())
                .build();

        PeriodComparisonResultDto comparison = PeriodComparisonResultDto.builder()
                .currentPeriod(currentPeriod).comparisonPeriod(comparisonPeriod)
                .metrics(List.of(ComparisonMetricDto.builder().metric(CanonicalMetric.SPEND)
                        .currentValue(new BigDecimal("50.50")).previousValue(new BigDecimal("18.00"))
                        .absoluteChange(new BigDecimal("32.50")).percentageChange(new BigDecimal("180.56"))
                        .direction("INCREASED").available(true).build()))
                .currentCoverage(CoverageDto.builder().syncComplete(true).daysWithActivity(2).build())
                .comparisonCoverage(CoverageDto.builder().syncComplete(true).daysWithActivity(1).build())
                .build();

        TimeSeriesResponseDto timeSeries = TimeSeriesResponseDto.builder()
                .provider(Provider.META).granularity(TimeGranularity.DAY).period(currentPeriod)
                .series(List.of(TimeSeriesPointDto.builder().date(LocalDate.of(2026, 7, 14))
                        .metrics(Map.of(CanonicalMetric.SPEND.publicName(),
                                MetricValueDto.available(new BigDecimal("24.10"), "currency", "USD")))
                        .build()))
                .build();

        AnalyticsRankingsDto rankings = AnalyticsRankingsDto.builder()
                .metric(CanonicalMetric.SPEND)
                .results(List.of(RankingEntryDto.builder()
                        .rank(1).objectType(InsightObjectType.CAMPAIGN).objectExternalId("120244089191690082")
                        .objectName("Summer Sale - Retargeting").value(new BigDecimal("50.50")).currency("USD")
                        .activityPeriod(new InsightPeriodDto(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 15)))
                        .spend(new BigDecimal("50.50")).impressions(8000L).clicks(510L)
                        .build()))
                .build();

        List<AnalyticsBreakdownDto> breakdowns = List.of(AnalyticsBreakdownDto.builder()
                .dimension(BreakdownDimension.COUNTRY).shareMetric("SPEND")
                .rows(List.of(
                        // Real, measured zero-or-more reach for US...
                        InsightsBreakdownRowDto.builder().dimension("country").dimensionValue("US")
                                .spend(38.2).impressions(6200).clicks(410).reach(5100L)
                                .ctr(6.61).share(75.94).shareMetric("SPEND").conversionDataAvailable(false).build(),
                        // ...vs. DE, where the provider never returned reach at all — must stay null.
                        InsightsBreakdownRowDto.builder().dimension("country").dimensionValue("DE")
                                .spend(12.1).impressions(1900).clicks(95).reach(null)
                                .ctr(5.0).share(24.06).shareMetric("SPEND").conversionDataAvailable(false).build()))
                .build());

        return AnalysisContextDto.builder()
                .schemaVersion("1.0").provider(Provider.META).adAccountId("act_481686937778670")
                .currency("USD").timezone("UTC").generatedAt(Instant.parse("2026-07-21T12:37:13.307931200Z"))
                .scope(scope())
                .currentPeriod(currentPeriod).comparisonPeriod(comparisonPeriod)
                .coverage(CoverageDto.builder().syncComplete(true).daysWithActivity(2).build())
                .summary(summary).comparison(comparison).timeSeries(timeSeries).rankings(rankings)
                .breakdowns(breakdowns).findings(List.of()).dataQualityIssues(List.of())
                .capabilities(AnalysisContextCapabilitiesDto.builder()
                        .supportedMetrics(Set.of(CanonicalMetric.SPEND))
                        .supportedBreakdowns(Set.of(BreakdownDimension.COUNTRY))
                        .conversionMetricsAvailable(true).supportsDailyTimeSeries(true).supportsComparison(true)
                        .supportsExactAggregateReach(true).build())
                .build();
    }

    // -----------------------------------------------------------------------
    // 1-4: LocalDate as "yyyy-MM-dd" (never an array), generatedAt as ISO-8601 (never numeric)
    // -----------------------------------------------------------------------

    @Test
    void localDate_serializesAsIsoDateString_neverAnArray() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(fullContext()));

        assertThat(root.at("/currentPeriod/start").isTextual()).isTrue();
        assertThat(root.at("/currentPeriod/start").asText()).isEqualTo("2026-07-14");
        assertThat(root.at("/currentPeriod/start").isArray()).isFalse();
    }

    @Test
    void generatedAt_serializesAsIso8601String_neverNumeric() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(fullContext()));

        assertThat(root.get("generatedAt").isTextual()).isTrue();
        assertThat(root.get("generatedAt").isNumber()).isFalse();
        assertThat(root.get("generatedAt").asText()).startsWith("2026-07-21T12:37:13.3079312");
    }

    // -----------------------------------------------------------------------
    // 5-10: every other date field named in the contract
    // -----------------------------------------------------------------------

    @Test
    void everyDateField_isIsoString() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(fullContext()));

        assertThat(root.at("/currentPeriod/stop").asText()).isEqualTo("2026-07-21");
        assertThat(root.at("/comparisonPeriod/start").asText()).isEqualTo("2026-07-06");
        assertThat(root.at("/comparisonPeriod/stop").asText()).isEqualTo("2026-07-13");
        assertThat(root.at("/summary/requestedPeriod/start").asText()).isEqualTo("2026-07-14");
        assertThat(root.at("/summary/requestedPeriod/stop").asText()).isEqualTo("2026-07-21");
        assertThat(root.at("/comparison/currentPeriod/start").asText()).isEqualTo("2026-07-14");
        assertThat(root.at("/comparison/comparisonPeriod/start").asText()).isEqualTo("2026-07-06");
        assertThat(root.at("/timeSeries/period/start").asText()).isEqualTo("2026-07-14");
        assertThat(root.at("/timeSeries/period/stop").asText()).isEqualTo("2026-07-21");
        assertThat(root.at("/timeSeries/series/0/date").asText()).isEqualTo("2026-07-14");
        assertThat(root.at("/rankings/results/0/activityPeriod/start").asText()).isEqualTo("2026-07-14");
        assertThat(root.at("/rankings/results/0/activityPeriod/stop").asText()).isEqualTo("2026-07-15");

        String json = objectMapper.writeValueAsString(fullContext());
        assertThat(json).doesNotContain("[ 2026,").doesNotContain("[2026,");
    }

    // -----------------------------------------------------------------------
    // 11-12: selectedObjectIds populated and consistent with selectedObjectCount
    // -----------------------------------------------------------------------

    @Test
    void selectedObjectIds_populated_andConsistentWithCount() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(fullContext()));

        assertThat(root.at("/scope/selectedObjectIds/0").asText()).isEqualTo("120244089191690082");
        assertThat(root.at("/scope/selectedObjectCount").asInt()).isEqualTo(root.at("/scope/selectedObjectIds").size());
    }

    // -----------------------------------------------------------------------
    // 13-14: canonical metric names only — no Meta raw action-path key
    // -----------------------------------------------------------------------

    @Test
    void canonicalMetricNames_used_noRawMetaActionPath() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(fullContext()));

        assertThat(root.at("/summary/metrics/outboundClicks").isMissingNode()).isFalse();
        assertThat(root.at("/summary/metrics/outbound_clicks.outbound_click").isMissingNode()).isTrue();
        assertThat(objectMapper.writeValueAsString(fullContext())).doesNotContain("outbound_clicks.outbound_click");
    }

    // -----------------------------------------------------------------------
    // 15-16: unavailable breakdown reach is null, a real zero-or-more value is preserved
    // -----------------------------------------------------------------------

    @Test
    void breakdownReach_null_whenUnavailable_realValue_whenMeasured() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(fullContext()));

        assertThat(root.at("/breakdowns/0/rows/0/reach").asLong()).isEqualTo(5100L);
        assertThat(root.at("/breakdowns/0/rows/1/reach").isNull()).isTrue();
    }

    // -----------------------------------------------------------------------
    // 17-18: no raw provider JSON, no credentials/tokens
    // -----------------------------------------------------------------------

    @Test
    void serializedContext_neverContainsRawProviderJsonOrCredentials() throws Exception {
        String json = objectMapper.writeValueAsString(fullContext());

        assertThat(json).doesNotContain("access_token").doesNotContain("rawJson").doesNotContain("breakdownsJson");
    }
}
