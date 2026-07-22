package com.example.marketing.insights.analytics.service;

import com.example.marketing.auth.AdAccountConnectionEntity;
import com.example.marketing.auth.AdAccountConnectionRepository;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsFindingsProperties;
import com.example.marketing.insights.analytics.config.InsightsAnalyticsLimitsProperties;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextRequestDto;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.AnalyticsScope;
import com.example.marketing.insights.analytics.dto.BreakdownRequestDto;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.CanonicalInsightRecord;
import com.example.marketing.insights.analytics.dto.ComparisonRequestDto;
import com.example.marketing.insights.analytics.dto.MetricSample;
import com.example.marketing.insights.analytics.dto.RankingRequestDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesRequestDto;
import com.example.marketing.insights.analytics.engine.FindingEngine;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.ComparisonMode;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.analytics.strategy.MetaAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.repository.InsightSnapshotRepository;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.util.InsightSyncStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Part 11 coverage: every AnalysisContext section, the single-canonical-load guarantee, null-vs-
 * zero semantics, mixed currency, no-activity, and that the serialized response never carries raw
 * provider JSON or credentials. Provider/breakdown/metric rejection and cross-user/ownership
 * scoping are exercised at the loader/registry level (SnapshotCanonicalDatasetLoaderTest,
 * ProviderAnalyticsStrategyRegistryTest) since the builder only ever sees whatever CanonicalDataset
 * the loader already validated and returned — this suite mocks that loader by design.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisContextBuilderTest {

    @Mock private CanonicalDatasetLoader datasetLoader;
    @Mock private AdAccountConnectionRepository adAccountConnectionRepository;

    private AnalysisContextBuilder builder;
    private RankingService rankingService;
    private BreakdownAnalyticsService breakdownAnalyticsService;
    private InsightSnapshotRepository mockedBreakdownRepository;
    private final com.example.marketing.user.entity.UserEntity user = new com.example.marketing.user.entity.UserEntity();

    @BeforeEach
    void setUp() {
        user.setId(1L);
        MetricAggregationService aggregation = new MetricAggregationService();
        ProviderAnalyticsStrategyRegistry strategyRegistry = new ProviderAnalyticsStrategyRegistry(List.of(new MetaAnalyticsStrategy()));
        InsightsAnalyticsLimitsProperties limits = new InsightsAnalyticsLimitsProperties();
        InsightsAnalyticsFindingsProperties thresholds = new InsightsAnalyticsFindingsProperties();

        AnalyticsSummaryService summaryService = new AnalyticsSummaryService(aggregation, strategyRegistry);
        PeriodComparisonService comparisonService = new PeriodComparisonService(aggregation, strategyRegistry);
        TimeSeriesService timeSeriesService = new TimeSeriesService(aggregation, strategyRegistry, limits);
        rankingService = new RankingService(aggregation, strategyRegistry, limits);
        mockedBreakdownRepository = mock(InsightSnapshotRepository.class);
        breakdownAnalyticsService = new BreakdownAnalyticsService(mockedBreakdownRepository, new ObjectMapper());
        DataQualityService dataQualityService = new DataQualityService();
        FindingEngine findingEngine = new FindingEngine(thresholds, rankingService, strategyRegistry, aggregation);
        lenient().when(adAccountConnectionRepository.findByUserIdAndProviderAndAdAccountId(any(), any(), any()))
                .thenReturn(Optional.empty());

        builder = new AnalysisContextBuilder(datasetLoader, summaryService, comparisonService, timeSeriesService,
                rankingService, breakdownAnalyticsService, dataQualityService, findingEngine, strategyRegistry,
                adAccountConnectionRepository);
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private CanonicalInsightRecord record(LocalDate date, String spend, int impressions, int clicks, Integer purchases, String currency) {
        Map<CanonicalMetric, MetricSample> m = CanonicalInsightRecord.emptyMetrics();
        m.put(CanonicalMetric.SPEND, MetricSample.of(new BigDecimal(spend)));
        m.put(CanonicalMetric.IMPRESSIONS, MetricSample.of(BigDecimal.valueOf(impressions)));
        m.put(CanonicalMetric.CLICKS, MetricSample.of(BigDecimal.valueOf(clicks)));
        if (purchases != null) {
            m.put(CanonicalMetric.PURCHASES, MetricSample.of(BigDecimal.valueOf(purchases)));
            m.put(CanonicalMetric.PURCHASE_VALUE, MetricSample.of(BigDecimal.valueOf(purchases * 20L)));
        }
        return CanonicalInsightRecord.builder()
                .provider(Provider.META).adAccountId("act_1").objectType(InsightObjectType.CAMPAIGN)
                .objectExternalId("camp1").currency(currency).date(date)
                .syncComplete(true).syncStatus(InsightSyncStatus.COMPLETE).singleEntity(true)
                .baseMetrics(m).build();
    }

    private CanonicalDataset.CanonicalDatasetBuilder baseDataset(List<CanonicalInsightRecord> records, String currency, boolean mixed) {
        return CanonicalDataset.builder()
                .provider(Provider.META).adAccountId("act_1")
                .requestedPeriod(new InsightPeriodDto(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 10)))
                .scope(AnalyticsScope.builder().objectType(InsightObjectType.CAMPAIGN).selectedObjectCount(1).objectsWithActivity(records.isEmpty() ? 0 : 1).build())
                .records(records)
                .snapshotEntities(List.of())
                .objectNames(Map.of("camp1", "My Campaign"))
                .currency(currency)
                .mixedCurrency(mixed)
                .overallSyncStatus(InsightSyncStatus.COMPLETE)
                .overallSyncComplete(true)
                .warnings(new ArrayList<>());
    }

    private AnalyticsFilterRequest filter() {
        AnalyticsFilterRequest f = new AnalyticsFilterRequest();
        f.setProvider(Provider.META);
        f.setAdAccountId("act_1");
        f.setDateStart(LocalDate.of(2026, 2, 1));
        f.setDateStop(LocalDate.of(2026, 2, 10));
        return f;
    }

    private AnalysisContextRequestDto request() {
        AnalysisContextRequestDto r = new AnalysisContextRequestDto();
        r.setFilter(filter());
        return r;
    }

    // -----------------------------------------------------------------------
    // 1-9: section presence
    // -----------------------------------------------------------------------

    @Test
    void contextWithSummary_alwaysPopulated() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getSummary()).isNotNull();
        assertThat(ctx.getSummary().getMetrics().get("spend").getValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void contextWithTimeSeries_populatedWhenEnabled() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        TimeSeriesRequestDto ts = new TimeSeriesRequestDto();
        ts.setEnabled(true);
        ts.setGranularity(TimeGranularity.DAY);
        req.setTimeSeries(ts);

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getTimeSeries()).isNotNull();
        assertThat(ctx.getTimeSeries().getSeries()).hasSize(1);
    }

    @Test
    void contextWithComparison_populatedWhenEnabled() {
        var current = baseDataset(List.of(record(LocalDate.of(2026, 2, 5), "20.00", 200, 10, null, "USD")), "USD", false).build();
        var previous = baseDataset(List.of(record(LocalDate.of(2026, 1, 25), "10.00", 100, 5, null, "USD")), "USD", false)
                .requestedPeriod(new InsightPeriodDto(LocalDate.of(2026, 1, 22), LocalDate.of(2026, 1, 31))).build();

        when(datasetLoader.load(eq(user), any())).thenAnswer(inv -> {
            AnalyticsFilterRequest f = inv.getArgument(1);
            return f.getDateStart().equals(LocalDate.of(2026, 2, 1)) ? current : previous;
        });

        AnalysisContextRequestDto req = request();
        ComparisonRequestDto cmp = new ComparisonRequestDto();
        cmp.setEnabled(true);
        cmp.setMode(ComparisonMode.PREVIOUS_PERIOD);
        req.setComparison(cmp);

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getComparison()).isNotNull();
        assertThat(ctx.getComparisonPeriod()).isNotNull();
        verify(datasetLoader, times(2)).load(any(), any());
    }

    @Test
    void contextWithRankings_populatedWhenEnabled() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        RankingRequestDto rk = new RankingRequestDto();
        rk.setEnabled(true);
        rk.setObjectType(InsightObjectType.CAMPAIGN);
        rk.setMetric(CanonicalMetric.SPEND);
        req.setRankings(rk);

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getRankings()).isNotNull();
        assertThat(ctx.getRankings().getMetric()).isEqualTo(CanonicalMetric.SPEND);
        assertThat(ctx.getRankings().getResults()).hasSize(1);
        assertThat(ctx.getRankings().getResults().get(0).getObjectName()).isEqualTo("My Campaign");
    }

    @Test
    void contextWithOneBreakdown_populated() {
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder().provider(Provider.META)
                .breakdownsJson("{\"country\": [{\"country\": \"US\", \"spend\": \"10.00\", \"impressions\": \"100\", \"clicks\": \"5\"}]}")
                .build();
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false)
                .snapshotEntities(List.of(snap)).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        BreakdownRequestDto b = new BreakdownRequestDto();
        b.setDimension(BreakdownDimension.COUNTRY);
        req.setBreakdowns(List.of(b));

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getBreakdowns()).hasSize(1);
        assertThat(ctx.getBreakdowns().get(0).getDimension()).isEqualTo(BreakdownDimension.COUNTRY);
        assertThat(ctx.getBreakdowns().get(0).getRows()).extracting(r -> r.getDimensionValue()).containsExactly("US");
        verifyNoInteractions(mockedBreakdownRepository);
    }

    @Test
    void contextWithMultipleBreakdowns_populated() {
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder().provider(Provider.META)
                .breakdownsJson("""
                        {"country": [{"country": "US", "spend": "10.00", "impressions": "100", "clicks": "5"}],
                         "age_gender": [{"age": "25-34", "spend": "10.00", "impressions": "100", "clicks": "5"}]}
                        """)
                .build();
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false)
                .snapshotEntities(List.of(snap)).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        BreakdownRequestDto country = new BreakdownRequestDto();
        country.setDimension(BreakdownDimension.COUNTRY);
        BreakdownRequestDto age = new BreakdownRequestDto();
        age.setDimension(BreakdownDimension.AGE);
        req.setBreakdowns(List.of(country, age));

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getBreakdowns()).hasSize(2);
        assertThat(ctx.getBreakdowns()).extracting(b -> b.getDimension()).containsExactly(BreakdownDimension.COUNTRY, BreakdownDimension.AGE);
    }

    @Test
    void contextWithFindings_populatedByDefault() {
        var dataset = baseDataset(List.of(), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getFindings()).isNotNull();
        assertThat(ctx.getFindings()).extracting("code").contains("NO_ACTIVITY");
    }

    @Test
    void contextWithDataQualityIssues_populatedByDefault() {
        var dataset = baseDataset(List.of(), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getDataQualityIssues()).isNotNull();
        assertThat(ctx.getDataQualityIssues()).extracting("code").contains("NO_ACTIVITY_IN_PERIOD");
    }

    @Test
    void contextWithAllSections_everySectionPopulated() {
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder().provider(Provider.META)
                .breakdownsJson("{\"country\": [{\"country\": \"US\", \"spend\": \"10.00\", \"impressions\": \"100\", \"clicks\": \"5\"}]}")
                .build();
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, 2, "USD")), "USD", false)
                .snapshotEntities(List.of(snap)).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        TimeSeriesRequestDto ts = new TimeSeriesRequestDto();
        ts.setEnabled(true);
        req.setTimeSeries(ts);
        RankingRequestDto rk = new RankingRequestDto();
        rk.setEnabled(true);
        rk.setObjectType(InsightObjectType.CAMPAIGN);
        req.setRankings(rk);
        BreakdownRequestDto b = new BreakdownRequestDto();
        b.setDimension(BreakdownDimension.COUNTRY);
        req.setBreakdowns(List.of(b));

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getSummary()).isNotNull();
        assertThat(ctx.getTimeSeries()).isNotNull();
        assertThat(ctx.getRankings()).isNotNull();
        assertThat(ctx.getBreakdowns()).isNotNull();
        assertThat(ctx.getFindings()).isNotNull();
        assertThat(ctx.getDataQualityIssues()).isNotNull();
        assertThat(ctx.getCoverage()).isNotNull();
        assertThat(ctx.getCapabilities()).isNotNull();
        assertThat(ctx.getCapabilities().getSupportedMetrics()).contains(CanonicalMetric.SPEND);
    }

    // -----------------------------------------------------------------------
    // 10: disabled sections come back null, not empty
    // -----------------------------------------------------------------------

    @Test
    void disabledOptionalSections_returnNull_notEmptyList() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        req.setIncludeFindings(false);
        req.setIncludeDataQuality(false);

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getTimeSeries()).isNull();
        assertThat(ctx.getRankings()).isNull();
        assertThat(ctx.getBreakdowns()).isNull();
        assertThat(ctx.getComparison()).isNull();
        assertThat(ctx.getFindings()).isNull();
        assertThat(ctx.getDataQualityIssues()).isNull();
        assertThat(ctx.getSummary()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // 11-12: single canonical load, no repeated parsing
    // -----------------------------------------------------------------------

    @Test
    void singleCanonicalLoad_noComparisonRequested() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        TimeSeriesRequestDto ts = new TimeSeriesRequestDto();
        ts.setEnabled(true);
        req.setTimeSeries(ts);
        RankingRequestDto rk = new RankingRequestDto();
        rk.setEnabled(true);
        rk.setObjectType(InsightObjectType.CAMPAIGN);
        req.setRankings(rk);

        builder.build(user, req);

        verify(datasetLoader, times(1)).load(any(), any());
    }

    // -----------------------------------------------------------------------
    // 13-14: null vs. real zero
    // -----------------------------------------------------------------------

    @Test
    void nullRoas_staysNull_whenNoPurchaseData() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        var roas = ctx.getSummary().getMetrics().get("roas");
        assertThat(roas.isAvailable()).isFalse();
        assertThat(roas.getValue()).isNull();
    }

    @Test
    void realZeroSpend_staysZero_notUnavailable() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "0.00", 100, 0, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        var spend = ctx.getSummary().getMetrics().get("spend");
        assertThat(spend.isAvailable()).isTrue();
        assertThat(spend.getValue()).isEqualByComparingTo("0.00");
    }

    // -----------------------------------------------------------------------
    // 15-16: mixed currency / no activity
    // -----------------------------------------------------------------------

    @Test
    void mixedCurrency_spendUnavailable_dataQualityIssueRaised() {
        var dataset = baseDataset(List.of(
                record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "EUR"),
                record(LocalDate.of(2026, 2, 4), "5.00", 50, 2, null, "USD")
        ), null, true).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getSummary().getMetrics().get("spend").isAvailable()).isFalse();
        assertThat(ctx.getDataQualityIssues()).extracting("code").contains("MIXED_CURRENCY");
    }

    @Test
    void noActivity_producesNoActivityFindingAndDataQualityIssue() {
        var dataset = baseDataset(List.of(), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getFindings()).extracting("code").contains("NO_ACTIVITY");
        assertThat(ctx.getDataQualityIssues()).extracting("code").contains("NO_ACTIVITY_IN_PERIOD");
    }

    // -----------------------------------------------------------------------
    // 18: unsupported breakdown/metric — using a restricted fake strategy
    // -----------------------------------------------------------------------

    private ProviderAnalyticsStrategy restrictedMetaStrategy() {
        return new ProviderAnalyticsStrategy() {
            @Override public Provider getProvider() { return Provider.META; }
            @Override public com.example.marketing.insights.analytics.dto.ProviderAnalyticsCapabilitiesDto getCapabilities() {
                return com.example.marketing.insights.analytics.dto.ProviderAnalyticsCapabilitiesDto.builder()
                        .provider(Provider.META).supportedObjectTypes(Set.of(InsightObjectType.CAMPAIGN))
                        .supportedBreakdowns(Set.of(BreakdownDimension.COUNTRY))
                        .supportedMetrics(Set.of(CanonicalMetric.SPEND))
                        .conversionMetricsAvailable(true).supportsDailyTimeSeries(true).supportsComparison(true)
                        .supportsExactReachAggregation(false).build();
            }
            @Override public Set<CanonicalMetric> getSupportedMetrics() { return Set.of(CanonicalMetric.SPEND); }
            @Override public Set<BreakdownDimension> getSupportedBreakdowns() { return Set.of(BreakdownDimension.COUNTRY); }
            @Override public Set<InsightObjectType> getSupportedObjectTypes() { return Set.of(InsightObjectType.CAMPAIGN); }
            @Override public boolean supportsReachAggregation(AnalyticsScope scope) { return false; }
            @Override public boolean supportsMetric(CanonicalMetric metric, InsightObjectType objectType) { return metric == CanonicalMetric.SPEND; }
            @Override public Optional<String> validateAnalyticsRequest(AnalyticsFilterRequest request) { return Optional.empty(); }
            @Override public List<com.example.marketing.insights.analytics.dto.DeterministicFindingDto> createProviderSpecificFindings(AnalysisContextDto context) { return List.of(); }
        };
    }

    private AnalysisContextBuilder builderWithRestrictedStrategy() {
        ProviderAnalyticsStrategyRegistry restrictedRegistry = new ProviderAnalyticsStrategyRegistry(List.of(restrictedMetaStrategy()));
        MetricAggregationService aggregation = new MetricAggregationService();
        InsightsAnalyticsLimitsProperties limits = new InsightsAnalyticsLimitsProperties();
        InsightsAnalyticsFindingsProperties thresholds = new InsightsAnalyticsFindingsProperties();
        return new AnalysisContextBuilder(datasetLoader,
                new AnalyticsSummaryService(aggregation, restrictedRegistry),
                new PeriodComparisonService(aggregation, restrictedRegistry),
                new TimeSeriesService(aggregation, restrictedRegistry, limits),
                new RankingService(aggregation, restrictedRegistry, limits),
                breakdownAnalyticsService,
                new DataQualityService(),
                new FindingEngine(thresholds, rankingService, restrictedRegistry, aggregation),
                restrictedRegistry,
                adAccountConnectionRepository);
    }

    @Test
    void unsupportedBreakdown_rejectedClearly() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        BreakdownRequestDto b = new BreakdownRequestDto();
        b.setDimension(BreakdownDimension.AGE); // not in the restricted strategy's supported set
        req.setBreakdowns(List.of(b));

        assertThatThrownBy(() -> builderWithRestrictedStrategy().build(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNSUPPORTED_BREAKDOWN");
    }

    @Test
    void unsupportedShareMetric_rejectedClearly_notSilentlyIgnored() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        BreakdownRequestDto b = new BreakdownRequestDto();
        b.setDimension(BreakdownDimension.COUNTRY);
        b.setShareMetric(CanonicalMetric.CLICKS); // only SPEND is actually computed — must be rejected, not silently ignored
        req.setBreakdowns(List.of(b));

        assertThatThrownBy(() -> builder.build(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNSUPPORTED_SHARE_METRIC");
    }

    @Test
    void unsupportedMetric_rejectedClearly_forRankings() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        RankingRequestDto rk = new RankingRequestDto();
        rk.setEnabled(true);
        rk.setMetric(CanonicalMetric.ROAS); // not in the restricted strategy's supported set
        req.setRankings(rk);

        assertThatThrownBy(() -> builderWithRestrictedStrategy().build(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNSUPPORTED_METRIC");
    }

    // -----------------------------------------------------------------------
    // 21: schema version
    // -----------------------------------------------------------------------

    @Test
    void schemaVersion_isSet() {
        var dataset = baseDataset(List.of(), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getSchemaVersion()).isEqualTo("1.0");
    }

    // -----------------------------------------------------------------------
    // 22-23: no raw provider JSON / no credentials in the serialized response
    // -----------------------------------------------------------------------

    @Test
    void serializedContext_neverContainsRawProviderJsonOrCredentials() throws Exception {
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder().provider(Provider.META)
                .breakdownsJson("{\"country\": [{\"country\": \"US\", \"spend\": \"10.00\", \"impressions\": \"100\", \"clicks\": \"5\"}]}")
                .rawJson("{\"data\": [], \"access_token\": \"SECRET_TOKEN_VALUE_ABC123\"}")
                .build();
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false)
                .snapshotEntities(List.of(snap)).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        BreakdownRequestDto b = new BreakdownRequestDto();
        b.setDimension(BreakdownDimension.COUNTRY);
        req.setBreakdowns(List.of(b));

        AnalysisContextDto ctx = builder.build(user, req);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(ctx);

        assertThat(json).doesNotContain("SECRET_TOKEN_VALUE_ABC123");
        assertThat(json).doesNotContain("access_token");
        assertThat(json).doesNotContain("rawJson");
    }

    // -----------------------------------------------------------------------
    // 24-25: timezone — synced ad-account timezone, documented UTC fallback
    // -----------------------------------------------------------------------

    @Test
    void timezone_fallsBackToUtc_whenAccountNotSynced() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getTimezone()).isEqualTo(AnalysisContextBuilder.DEFAULT_TIMEZONE);
    }

    @Test
    void timezone_usesSyncedAdAccountTimezone_whenAvailable() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AdAccountConnectionEntity conn = new AdAccountConnectionEntity();
        conn.setTimezoneName("Europe/Berlin");
        when(adAccountConnectionRepository.findByUserIdAndProviderAndAdAccountId(1L, "META", "act_1"))
                .thenReturn(Optional.of(conn));

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getTimezone()).isEqualTo("Europe/Berlin");
    }

    // -----------------------------------------------------------------------
    // 26: canonical metric names — never a raw Meta action path
    // -----------------------------------------------------------------------

    @Test
    void summaryMetrics_useCanonicalPublicNames_notRawProviderActionPaths() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextDto ctx = builder.build(user, request());

        assertThat(ctx.getSummary().getMetrics()).containsKey("outboundClicks");
        assertThat(ctx.getSummary().getMetrics()).doesNotContainKey("outbound_clicks.outbound_click");
    }

    @Test
    void timeSeriesMetrics_useCanonicalPublicNames_notRawProviderActionPaths() {
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        TimeSeriesRequestDto ts = new TimeSeriesRequestDto();
        ts.setEnabled(true);
        req.setTimeSeries(ts);

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getTimeSeries().getSeries().get(0).getMetrics()).containsKey("outboundClicks");
        assertThat(ctx.getTimeSeries().getSeries().get(0).getMetrics()).doesNotContainKey("outbound_clicks.outbound_click");
    }

    // -----------------------------------------------------------------------
    // 27-28: breakdown reach — missing stays null, a real zero stays zero
    // -----------------------------------------------------------------------

    @Test
    void breakdownReach_null_whenProviderNeverReturnedIt() {
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder().provider(Provider.META)
                .breakdownsJson("{\"country\": [{\"country\": \"US\", \"spend\": \"10.00\", \"impressions\": \"100\", \"clicks\": \"5\"}]}")
                .build();
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false)
                .snapshotEntities(List.of(snap)).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        BreakdownRequestDto b = new BreakdownRequestDto();
        b.setDimension(BreakdownDimension.COUNTRY);
        req.setBreakdowns(List.of(b));

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getBreakdowns().get(0).getRows().get(0).getReach()).isNull();
    }

    @Test
    void breakdownReach_realZero_staysZero_notNull() {
        InsightSnapshotEntity snap = InsightSnapshotEntity.builder().provider(Provider.META)
                .breakdownsJson("{\"country\": [{\"country\": \"US\", \"spend\": \"10.00\", \"impressions\": \"100\", \"clicks\": \"5\", \"reach\": \"0\"}]}")
                .build();
        var dataset = baseDataset(List.of(record(LocalDate.of(2026, 2, 3), "10.00", 100, 5, null, "USD")), "USD", false)
                .snapshotEntities(List.of(snap)).build();
        when(datasetLoader.load(any(), any())).thenReturn(dataset);

        AnalysisContextRequestDto req = request();
        BreakdownRequestDto b = new BreakdownRequestDto();
        b.setDimension(BreakdownDimension.COUNTRY);
        req.setBreakdowns(List.of(b));

        AnalysisContextDto ctx = builder.build(user, req);

        assertThat(ctx.getBreakdowns().get(0).getRows().get(0).getReach()).isZero();
    }
}
