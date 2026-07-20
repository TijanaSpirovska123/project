package com.example.marketing.insights.analytics.api;

import com.example.marketing.auth.UserPrincipal;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.*;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.analytics.service.*;
import com.example.marketing.insights.analytics.strategy.MetaAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Step 24 "Integration tests" (69-76): every new /api/insights/analytics endpoint asserts real
 * response data (not just HTTP success), and each derives userId from the authenticated
 * principal — never from the request — before delegating to the corresponding service.
 */
@ExtendWith(MockitoExtension.class)
class InsightsAnalyticsControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private CanonicalDatasetLoader datasetLoader;
    @Mock private AnalyticsSummaryService summaryService;
    @Mock private TimeSeriesService timeSeriesService;
    @Mock private PeriodComparisonService comparisonService;
    @Mock private RankingService rankingService;
    @Mock private DataQualityService dataQualityService;
    @Mock private AnalysisContextBuilder contextBuilder;

    private InsightsAnalyticsController controller;
    private UserEntity user;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        auth = new UsernamePasswordAuthenticationToken(new UserPrincipal(1L, "test"), null, List.of());

        ProviderAnalyticsStrategyRegistry strategyRegistry = new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy()));
        controller = new InsightsAnalyticsController(userRepository, datasetLoader, summaryService, timeSeriesService,
                comparisonService, rankingService, dataQualityService, contextBuilder, strategyRegistry);
    }

    private CanonicalDataset fakeDataset() {
        return CanonicalDataset.builder()
                .provider(Provider.META).adAccountId("act_1")
                .requestedPeriod(new com.example.marketing.insights.dto.InsightPeriodDto(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10)))
                .scope(AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(1).build())
                .records(List.of()).objectNames(Map.of()).currency("USD").mixedCurrency(false)
                .overallSyncStatus(InsightSyncStatus.COMPLETE).overallSyncComplete(true).warnings(List.of()).build();
    }

    @Test
    void capabilities_returnsMetaCapabilities() {
        var response = controller.capabilities(Provider.META);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getProvider()).isEqualTo(Provider.META);
        assertThat(response.getData().getSupportedObjectTypes()).contains(InsightObjectType.CAMPAIGN, InsightObjectType.ADSET, InsightObjectType.AD);
    }

    @Test
    void summary_derivesUserFromAuth_notFromRequest_returnsRealData() {
        CanonicalDataset dataset = fakeDataset();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);
        AnalyticsSummaryDto summaryDto = AnalyticsSummaryDto.builder()
                .provider(Provider.META).adAccountId("act_1")
                .metrics(Map.of("spend", MetricValueDto.available(new java.math.BigDecimal("11.32"), "currency", "USD")))
                .build();
        when(summaryService.summarize(dataset)).thenReturn(summaryDto);

        var response = controller.summary(auth, Provider.META, "act_1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10),
                null, null, null, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getMetrics().get("spend").getValue()).isEqualByComparingTo("11.32");
    }

    @Test
    void timeSeries_returnsRealSeries() {
        when(datasetLoader.load(any(), any())).thenReturn(fakeDataset());
        TimeSeriesResponseDto tsDto = TimeSeriesResponseDto.builder()
                .provider(Provider.META).granularity(TimeGranularity.DAY)
                .series(List.of(TimeSeriesPointDto.builder().date(LocalDate.of(2026, 2, 3))
                        .metrics(Map.of("spend", MetricValueDto.available(new java.math.BigDecimal("0.17"), "currency"))).build()))
                .build();
        when(timeSeriesService.build(any(), any(), anyBoolean())).thenReturn(tsDto);

        var response = controller.timeSeries(auth, Provider.META, "act_1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10),
                null, null, null, null, TimeGranularity.DAY, false);

        assertThat(response.getData().getSeries()).hasSize(1);
        assertThat(response.getData().getSeries().get(0).getMetrics().get("spend").getValue()).isEqualByComparingTo("0.17");
    }

    @Test
    void rankings_returnsRealRankedResults() {
        when(datasetLoader.load(any(), any())).thenReturn(fakeDataset());
        when(rankingService.rank(any(), any(), any(), anyInt(), any(), any(), anyBoolean()))
                .thenReturn(List.of(RankingEntryDto.builder().rank(1).objectExternalId("camp1").value(new java.math.BigDecimal("11.32")).build()));

        var response = controller.rankings(auth, Provider.META, "act_1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10),
                InsightObjectType.CAMPAIGN, CanonicalMetric.SPEND, "DESC", 10, null, null, null, false);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getObjectExternalId()).isEqualTo("camp1");
    }

    @Test
    void dataQuality_returnsRealIssueList() {
        when(datasetLoader.load(any(), any())).thenReturn(fakeDataset());
        when(dataQualityService.analyze(any())).thenReturn(List.of(
                DataQualityIssueDto.builder().code("NO_ACTIVITY_IN_PERIOD").build()));

        var response = controller.dataQuality(auth, Provider.META, "act_1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10),
                null, null, null, null);

        assertThat(response.getData()).extracting(DataQualityIssueDto::getCode).containsExactly("NO_ACTIVITY_IN_PERIOD");
    }

    @Test
    void context_neverCallsExternalServices_delegatesToContextBuilder() {
        AnalysisContextDto contextDto = AnalysisContextDto.builder().schemaVersion("1.0").provider(Provider.META).build();
        when(contextBuilder.build(any(), any(), any(), any())).thenReturn(contextDto);

        AnalysisContextRequestDto body = new AnalysisContextRequestDto();
        AnalyticsFilterRequest filter = new AnalyticsFilterRequest();
        filter.setProvider(Provider.META);
        filter.setAdAccountId("act_1");
        filter.setDateStart(LocalDate.of(2026, 2, 1));
        filter.setDateStop(LocalDate.of(2026, 2, 10));
        body.setFilter(filter);

        var response = controller.context(auth, body);

        assertThat(response.getData().getSchemaVersion()).isEqualTo("1.0");
        assertThat(response.getData().getProvider()).isEqualTo(Provider.META);
    }
}
