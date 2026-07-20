package com.example.marketing.insights.analytics.service;

import com.example.marketing.insights.analytics.dto.AnalysisContextCapabilitiesDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.DeterministicFindingDto;
import com.example.marketing.insights.analytics.dto.PeriodComparisonResultDto;
import com.example.marketing.insights.analytics.engine.FindingEngine;
import com.example.marketing.insights.analytics.enums.ComparisonMode;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the stable, provider-independent {@link AnalysisContextDto} (Step 17) — the contract a
 * later Smart Goal integration phase may consume. Loads the {@link CanonicalDataset} exactly
 * once (twice if a comparison period is requested — current + comparison, each loaded once) and
 * passes that SAME dataset into summary/comparison/findings/data-quality, so those pieces can
 * never disagree by having independently re-loaded different data (requirement #12). Never calls
 * any external AI/FastAPI/MCP/Smart-Goal service — this method only assembles already-computed
 * canonical data.
 */
@Service
@RequiredArgsConstructor
public class AnalysisContextBuilder {

    public static final String SCHEMA_VERSION = "1.0";

    private final CanonicalDatasetLoader datasetLoader;
    private final AnalyticsSummaryService summaryService;
    private final PeriodComparisonService comparisonService;
    private final DataQualityService dataQualityService;
    private final FindingEngine findingEngine;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;

    public AnalysisContextDto build(UserEntity user, AnalyticsFilterRequest request,
            ComparisonMode comparisonMode, InsightPeriodDto customComparisonPeriod) {

        CanonicalDataset current = datasetLoader.load(user, request);
        ProviderAnalyticsStrategy strategy = strategyRegistry.getStrategy(current.getProvider());

        PeriodComparisonResultDto comparisonResult = null;
        InsightPeriodDto comparisonPeriod = null;
        if (comparisonMode != null) {
            comparisonPeriod = switch (comparisonMode) {
                case PREVIOUS_PERIOD -> comparisonService.previousPeriod(current.getRequestedPeriod());
                case PREVIOUS_YEAR -> comparisonService.previousYearPeriod(current.getRequestedPeriod());
                case CUSTOM -> customComparisonPeriod;
            };
            if (comparisonPeriod != null) {
                AnalyticsFilterRequest comparisonRequest = copyWithPeriod(request, comparisonPeriod);
                CanonicalDataset previous = datasetLoader.load(user, comparisonRequest);
                comparisonResult = comparisonService.compare(current, previous, comparisonMode);
            }
        }

        List<DeterministicFindingDto> findings = new ArrayList<>(findingEngine.evaluate(current, comparisonResult));
        findings.addAll(strategy.createProviderSpecificFindings(
                partialContext(current, comparisonPeriod, comparisonResult)));

        return AnalysisContextDto.builder()
                .schemaVersion(SCHEMA_VERSION)
                .provider(current.getProvider())
                .adAccountId(current.getAdAccountId())
                .currency(current.getCurrency())
                .timezone(request.getTimezone())
                .generatedAt(Instant.now())
                .scope(current.getScope())
                .currentPeriod(current.getRequestedPeriod())
                .comparisonPeriod(comparisonPeriod)
                .summary(summaryService.summarize(current))
                .comparison(comparisonResult)
                .timeSeries(List.of())
                .rankings(List.of())
                .breakdowns(List.of())
                .findings(findings)
                .dataQualityIssues(dataQualityService.analyze(current))
                .capabilities(AnalysisContextCapabilitiesDto.builder()
                        .conversionMetricsAvailable(strategy.getCapabilities().isConversionMetricsAvailable())
                        .supportsExactAggregateReach(strategy.supportsReachAggregation(current.getScope()))
                        .build())
                .build();
    }

    /** A minimal context (no provider-specific findings yet) passed to the strategy so it can read summary/comparison without a circular dependency on its own output. */
    private AnalysisContextDto partialContext(CanonicalDataset current, InsightPeriodDto comparisonPeriod, PeriodComparisonResultDto comparisonResult) {
        return AnalysisContextDto.builder()
                .schemaVersion(SCHEMA_VERSION)
                .provider(current.getProvider())
                .adAccountId(current.getAdAccountId())
                .currency(current.getCurrency())
                .scope(current.getScope())
                .currentPeriod(current.getRequestedPeriod())
                .comparisonPeriod(comparisonPeriod)
                .summary(summaryService.summarize(current))
                .comparison(comparisonResult)
                .timeSeries(List.of())
                .rankings(List.of())
                .breakdowns(List.of())
                .findings(List.of())
                .dataQualityIssues(List.of())
                .build();
    }

    private AnalyticsFilterRequest copyWithPeriod(AnalyticsFilterRequest original, InsightPeriodDto period) {
        AnalyticsFilterRequest copy = new AnalyticsFilterRequest();
        copy.setProvider(original.getProvider());
        copy.setAdAccountId(original.getAdAccountId());
        copy.setObjectType(original.getObjectType());
        copy.setCampaignIds(original.getCampaignIds());
        copy.setAdSetIds(original.getAdSetIds());
        copy.setAdIds(original.getAdIds());
        copy.setDateStart(period.getStart());
        copy.setDateStop(period.getStop());
        copy.setTimezone(original.getTimezone());
        copy.setMetrics(original.getMetrics());
        copy.setBreakdowns(original.getBreakdowns());
        copy.setGranularity(original.getGranularity());
        copy.setIncludeInactivePeriods(original.isIncludeInactivePeriods());
        return copy;
    }
}
