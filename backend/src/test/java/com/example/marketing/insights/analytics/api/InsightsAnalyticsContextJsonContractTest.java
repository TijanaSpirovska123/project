package com.example.marketing.insights.analytics.api;

import com.example.marketing.auth.UserPrincipal;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.AnalysisContextCapabilitiesDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalyticsBreakdownDto;
import com.example.marketing.insights.analytics.dto.AnalyticsRankingsDto;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.AnalyticsSummaryDto;
import com.example.marketing.insights.analytics.dto.CoverageDto;
import com.example.marketing.insights.analytics.dto.MetricValueDto;
import com.example.marketing.insights.analytics.dto.RankingEntryDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesPointDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesResponseDto;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.MetricUnavailableReason;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.analytics.service.AnalysisContextBuilder;
import com.example.marketing.insights.analytics.service.AnalyticsSummaryService;
import com.example.marketing.insights.analytics.service.CanonicalDatasetLoader;
import com.example.marketing.insights.analytics.service.DataQualityService;
import com.example.marketing.insights.analytics.service.PeriodComparisonService;
import com.example.marketing.insights.analytics.service.RankingService;
import com.example.marketing.insights.analytics.service.TimeSeriesService;
import com.example.marketing.insights.analytics.strategy.MetaAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level proof of the public AnalysisContext JSON contract for POST
 * /api/insights/analytics/context — asserts the actual serialized HTTP response body (jsonPath),
 * not just Java DTO values, using {@code @JsonTest}'s real Spring-managed ObjectMapper wired into
 * the same {@link MappingJackson2HttpMessageConverter} the running application uses. Runs the
 * real {@link InsightsAnalyticsController} through standalone MockMvc (no DB/security filter
 * chain — every collaborator below it is mocked, exactly like the existing
 * {@code InsightsAnalyticsControllerTest} unit test) so the request-mapping/argument-resolution/
 * JSON-serialization pipeline is exercised end-to-end without needing a full application context.
 */
@JsonTest
class InsightsAnalyticsContextJsonContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private AnalysisContextBuilder contextBuilder;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        CanonicalDatasetLoader datasetLoader = mock(CanonicalDatasetLoader.class);
        AnalyticsSummaryService summaryService = mock(AnalyticsSummaryService.class);
        TimeSeriesService timeSeriesService = mock(TimeSeriesService.class);
        PeriodComparisonService comparisonService = mock(PeriodComparisonService.class);
        RankingService rankingService = mock(RankingService.class);
        DataQualityService dataQualityService = mock(DataQualityService.class);
        contextBuilder = mock(AnalysisContextBuilder.class);
        ProviderAnalyticsStrategyRegistry strategyRegistry =
                new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy()));

        UserEntity user = new UserEntity();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        InsightsAnalyticsController controller = new InsightsAnalyticsController(userRepository, datasetLoader,
                summaryService, timeSeriesService, comparisonService, rankingService, dataQualityService,
                contextBuilder, strategyRegistry);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private AnalyticsScope scope() {
        return AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN)
                .selectedObjectIds(List.of("120244089191690082")).selectedObjectCount(1)
                .objectsWithActivity(1).objectsWithoutActivity(0).build();
    }

    private AnalysisContextDto realisticContext() {
        InsightPeriodDto currentPeriod = new InsightPeriodDto(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 21));
        InsightPeriodDto comparisonPeriod = new InsightPeriodDto(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 13));

        AnalyticsSummaryDto summary = AnalyticsSummaryDto.builder()
                .provider(Provider.META).adAccountId("act_481686937778670")
                .requestedPeriod(currentPeriod).scope(scope()).currency("USD")
                .metrics(Map.of(
                        CanonicalMetric.SPEND.publicName(), MetricValueDto.available(new BigDecimal("50.50"), "currency", "USD"),
                        CanonicalMetric.OUTBOUND_CLICKS.publicName(),
                        MetricValueDto.unavailable(MetricUnavailableReason.NOT_RETURNED_BY_PROVIDER, "count")))
                .syncStatus(InsightSyncStatus.COMPLETE).warnings(List.of())
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
                        InsightsBreakdownRowDto.builder().dimension("country").dimensionValue("US")
                                .spend(38.2).impressions(6200).clicks(410).reach(5100L)
                                .ctr(6.61).share(75.94).shareMetric("SPEND").conversionDataAvailable(false).build(),
                        InsightsBreakdownRowDto.builder().dimension("country").dimensionValue("DE")
                                .spend(12.1).impressions(1900).clicks(95).reach(null)
                                .ctr(5.0).share(24.06).shareMetric("SPEND").conversionDataAvailable(false).build()))
                .build());

        return AnalysisContextDto.builder()
                .schemaVersion("1.0").provider(Provider.META).adAccountId("act_481686937778670")
                .currency("USD").timezone("UTC").generatedAt(java.time.Instant.parse("2026-07-21T12:37:13.307931200Z"))
                .scope(scope())
                .currentPeriod(currentPeriod).comparisonPeriod(comparisonPeriod)
                .coverage(CoverageDto.builder().syncComplete(true).daysWithActivity(2).build())
                .summary(summary).timeSeries(timeSeries).rankings(rankings).breakdowns(breakdowns)
                .findings(List.of()).dataQualityIssues(List.of())
                .capabilities(AnalysisContextCapabilitiesDto.builder()
                        .supportedMetrics(Set.of(CanonicalMetric.SPEND))
                        .supportedBreakdowns(Set.of(BreakdownDimension.COUNTRY))
                        .conversionMetricsAvailable(true).supportsDailyTimeSeries(true).supportsComparison(true)
                        .supportsExactAggregateReach(true).build())
                .build();
    }

    @Test
    void context_serializesRealJsonContract_datesAsIsoStrings_canonicalMetricNames_noRawProviderPath() throws Exception {
        when(contextBuilder.build(any(), any())).thenReturn(realisticContext());
        Authentication auth = new UsernamePasswordAuthenticationToken(new UserPrincipal(1L, "test"), null, List.of());

        String body = """
                {"filter": {"provider": "META", "adAccountId": "act_481686937778670", "dateStart": "2026-07-14", "dateStop": "2026-07-21"}}
                """;

        mockMvc.perform(post("/api/insights/analytics/context")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentPeriod.start").value("2026-07-14"))
                .andExpect(jsonPath("$.data.currentPeriod.stop").value("2026-07-21"))
                .andExpect(jsonPath("$.data.comparisonPeriod.start").value("2026-07-06"))
                .andExpect(jsonPath("$.data.generatedAt").isString())
                .andExpect(jsonPath("$.data.summary.requestedPeriod.start").value("2026-07-14"))
                .andExpect(jsonPath("$.data.timeSeries.period.start").value("2026-07-14"))
                .andExpect(jsonPath("$.data.timeSeries.series[0].date").value("2026-07-14"))
                .andExpect(jsonPath("$.data.rankings.results[0].activityPeriod.start").value("2026-07-14"))
                .andExpect(jsonPath("$.data.rankings.results[0].activityPeriod.stop").value("2026-07-15"))
                .andExpect(jsonPath("$.data.scope.selectedObjectIds[0]").value("120244089191690082"))
                .andExpect(jsonPath("$.data.scope.selectedObjectCount").value(1))
                .andExpect(jsonPath("$.data.summary.metrics.outboundClicks").exists())
                .andExpect(jsonPath("$.data.summary.metrics['outbound_clicks.outbound_click']").doesNotExist())
                .andExpect(jsonPath("$.data.breakdowns[0].rows[0].reach").value(5100))
                .andExpect(jsonPath("$.data.breakdowns[0].rows[1].reach").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.timezone").value("UTC"));
    }
}
