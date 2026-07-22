package com.example.marketing.insights.analytics.service;

import com.example.marketing.auth.AdAccountConnectionRepository;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.insights.analytics.dto.AnalysisContextCapabilitiesDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextRequestDto;
import com.example.marketing.insights.analytics.dto.AnalyticsBreakdownDto;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.AnalyticsRankingsDto;
import com.example.marketing.insights.analytics.dto.BreakdownRequestDto;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.CoverageDto;
import com.example.marketing.insights.analytics.dto.DataQualityIssueDto;
import com.example.marketing.insights.analytics.dto.DeterministicFindingDto;
import com.example.marketing.insights.analytics.dto.MetricValueDto;
import com.example.marketing.insights.analytics.dto.PeriodComparisonResultDto;
import com.example.marketing.insights.analytics.dto.RankingEntryDto;
import com.example.marketing.insights.analytics.dto.RankingRequestDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesPointDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesRequestDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesResponseDto;
import com.example.marketing.insights.analytics.enums.BreakdownDimension;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.ComparisonMode;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.analytics.engine.FindingEngine;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategy;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.dto.InsightPeriodDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the stable, provider-independent {@link AnalysisContextDto} — the contract a later Smart
 * Goal integration phase may consume. Loads the {@link CanonicalDataset} exactly once (twice if a
 * comparison period is requested — current + comparison, each loaded once) and reuses that SAME
 * dataset for every section (summary/comparison/time-series/rankings/breakdowns/findings/
 * data-quality), so no section can ever disagree with another by having independently re-loaded
 * or re-parsed different data. A section that was not requested/enabled comes back {@code null},
 * never an empty list standing in for "not requested". Never calls any external AI/FastAPI/MCP/
 * Smart-Goal service — this method only assembles already-computed canonical data.
 */
@Service
@RequiredArgsConstructor
public class AnalysisContextBuilder {

    public static final String SCHEMA_VERSION = "1.0";

    /** Documented fallback when the ad account's own synced timezone isn't known yet — never the JVM/server timezone or one inferred from the user. */
    public static final String DEFAULT_TIMEZONE = "UTC";

    private final CanonicalDatasetLoader datasetLoader;
    private final AnalyticsSummaryService summaryService;
    private final PeriodComparisonService comparisonService;
    private final TimeSeriesService timeSeriesService;
    private final RankingService rankingService;
    private final BreakdownAnalyticsService breakdownAnalyticsService;
    private final DataQualityService dataQualityService;
    private final FindingEngine findingEngine;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;
    private final AdAccountConnectionRepository adAccountConnectionRepository;

    public AnalysisContextDto build(UserEntity user, AnalysisContextRequestDto request) {
        if (request.getFilter() == null) {
            throw BusinessException.badRequest("filter is required");
        }

        CanonicalDataset current = datasetLoader.load(user, request.getFilter());
        ProviderAnalyticsStrategy strategy = strategyRegistry.getStrategy(current.getProvider());
        validateRequest(strategy, request);

        PeriodComparisonResultDto comparisonResult = null;
        InsightPeriodDto comparisonPeriod = null;
        ComparisonRequestDtoResolved comparisonRequested = resolveComparison(request);
        if (comparisonRequested != null) {
            comparisonPeriod = switch (comparisonRequested.mode()) {
                case PREVIOUS_PERIOD -> comparisonService.previousPeriod(current.getRequestedPeriod());
                case PREVIOUS_YEAR -> comparisonService.previousYearPeriod(current.getRequestedPeriod());
                case CUSTOM -> comparisonRequested.customPeriod();
            };
            if (comparisonPeriod != null) {
                AnalyticsFilterRequest comparisonFilter = copyWithPeriod(request.getFilter(), comparisonPeriod);
                CanonicalDataset previous = datasetLoader.load(user, comparisonFilter);
                comparisonResult = comparisonService.compare(current, previous, comparisonRequested.mode());
            }
        }

        TimeSeriesResponseDto timeSeries = buildTimeSeries(current, request.getTimeSeries());
        AnalyticsRankingsDto rankings = buildRankings(current, request.getRankings());
        List<AnalyticsBreakdownDto> breakdowns = buildBreakdowns(current, request.getBreakdowns());

        List<DeterministicFindingDto> findings = null;
        if (request.isIncludeFindings()) {
            findings = new ArrayList<>(findingEngine.evaluate(current, comparisonResult));
            findings.addAll(strategy.createProviderSpecificFindings(
                    partialContext(current, comparisonPeriod, comparisonResult)));
        }

        List<DataQualityIssueDto> dataQualityIssues =
                request.isIncludeDataQuality() ? dataQualityService.analyze(current) : null;

        return AnalysisContextDto.builder()
                .schemaVersion(SCHEMA_VERSION)
                .provider(current.getProvider())
                .adAccountId(current.getAdAccountId())
                .currency(current.getCurrency())
                .timezone(resolveTimezone(user, current))
                .generatedAt(Instant.now())
                .scope(current.getScope())
                .currentPeriod(current.getRequestedPeriod())
                .comparisonPeriod(comparisonPeriod)
                .coverage(CoverageDto.of(current))
                .summary(summaryService.summarize(current))
                .comparison(comparisonResult)
                .timeSeries(timeSeries)
                .rankings(rankings)
                .breakdowns(breakdowns)
                .findings(findings)
                .dataQualityIssues(dataQualityIssues)
                .capabilities(AnalysisContextCapabilitiesDto.builder()
                        .supportedMetrics(strategy.getSupportedMetrics())
                        .supportedBreakdowns(strategy.getSupportedBreakdowns())
                        .conversionMetricsAvailable(strategy.getCapabilities().isConversionMetricsAvailable())
                        .supportsDailyTimeSeries(strategy.getCapabilities().isSupportsDailyTimeSeries())
                        .supportsComparison(strategy.getCapabilities().isSupportsComparison())
                        .supportsExactAggregateReach(strategy.supportsReachAggregation(current.getScope()))
                        .build())
                .build();
    }

    // -----------------------------------------------------------------------
    // Validation — clear, structured rejections for anything this provider/config doesn't support.
    // -----------------------------------------------------------------------

    private void validateRequest(ProviderAnalyticsStrategy strategy, AnalysisContextRequestDto request) {
        TimeSeriesRequestDto ts = request.getTimeSeries();
        if (ts != null && ts.isEnabled()) {
            if (ts.getGranularity() == TimeGranularity.DAY
                    && !strategy.getCapabilities().isSupportsDailyTimeSeries()) {
                throw BusinessException.badRequest("UNSUPPORTED_GRANULARITY: " + strategy.getProvider() + " does not support daily time series");
            }
            if (ts.getMetrics() != null) {
                for (CanonicalMetric m : ts.getMetrics()) {
                    if (!strategy.getSupportedMetrics().contains(m)) {
                        throw BusinessException.badRequest("UNSUPPORTED_METRIC: " + m + " is not supported for " + strategy.getProvider());
                    }
                }
            }
        }

        RankingRequestDto rk = request.getRankings();
        if (rk != null && rk.isEnabled() && rk.getMetric() != null && !strategy.getSupportedMetrics().contains(rk.getMetric())) {
            throw BusinessException.badRequest("UNSUPPORTED_METRIC: " + rk.getMetric() + " is not supported for " + strategy.getProvider());
        }

        if (request.getBreakdowns() != null) {
            for (BreakdownRequestDto b : request.getBreakdowns()) {
                if (b.getDimension() == null || !strategy.getSupportedBreakdowns().contains(b.getDimension())) {
                    throw BusinessException.badRequest("UNSUPPORTED_BREAKDOWN: " + b.getDimension() + " is not supported for " + strategy.getProvider());
                }
                // BreakdownAnalyticsService only ever computes share against SPEND today (see its
                // javadoc) — silently accepting a different shareMetric would make the response's
                // shareMetric label lie about what was actually calculated, so reject it clearly
                // instead of ignoring it.
                if (b.getShareMetric() != null && b.getShareMetric() != CanonicalMetric.SPEND) {
                    throw BusinessException.badRequest("UNSUPPORTED_SHARE_METRIC: " + b.getShareMetric()
                            + " is not supported — breakdown share is currently only computed against SPEND");
                }
            }
        }
    }

    /**
     * Resolves the ad account's own synced timezone (Meta's {@code timezone_name}, captured on
     * AdAccountConnectionEntity at connect/sync time — see MetaOAuthService) rather than trusting
     * a client-supplied value or the JVM/server default, which would silently be wrong for an
     * account in a different timezone. Falls back to {@link #DEFAULT_TIMEZONE} — never fabricated
     * from the authenticated user's own location — when the account hasn't been synced yet or its
     * timezone wasn't captured.
     */
    private String resolveTimezone(UserEntity user, CanonicalDataset current) {
        return adAccountConnectionRepository
                .findByUserIdAndProviderAndAdAccountId(user.getId(), current.getProvider().name(), current.getAdAccountId())
                .map(conn -> conn.getTimezoneName())
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(DEFAULT_TIMEZONE);
    }

    // -----------------------------------------------------------------------
    // Section builders — every one reads ONLY the already-loaded CanonicalDataset.
    // -----------------------------------------------------------------------

    private record ComparisonRequestDtoResolved(ComparisonMode mode, InsightPeriodDto customPeriod) {}

    private ComparisonRequestDtoResolved resolveComparison(AnalysisContextRequestDto request) {
        var comparison = request.getComparison();
        if (comparison == null || !comparison.isEnabled() || comparison.getMode() == null) return null;
        if (comparison.getMode() == ComparisonMode.CUSTOM
                && comparison.getCustomPeriod() == null) {
            throw BusinessException.badRequest("comparison.customPeriod is required when comparison.mode is CUSTOM");
        }
        return new ComparisonRequestDtoResolved(comparison.getMode(), comparison.getCustomPeriod());
    }

    private TimeSeriesResponseDto buildTimeSeries(CanonicalDataset current, TimeSeriesRequestDto request) {
        if (request == null || !request.isEnabled()) return null;
        TimeSeriesResponseDto full = timeSeriesService.build(current, request.getGranularity(), request.isIncludeInactivePeriods());
        if (request.getMetrics() == null || request.getMetrics().isEmpty()) return full;

        List<String> allowedNames = request.getMetrics().stream().map(CanonicalMetric::publicName).toList();
        List<TimeSeriesPointDto> filteredPoints = full.getSeries().stream()
                .map(point -> TimeSeriesPointDto.builder()
                        .date(point.getDate())
                        .metrics(filterMetrics(point.getMetrics(), allowedNames))
                        .build())
                .toList();
        return TimeSeriesResponseDto.builder()
                .provider(full.getProvider())
                .granularity(full.getGranularity())
                .period(full.getPeriod())
                .series(filteredPoints)
                .build();
    }

    private Map<String, MetricValueDto> filterMetrics(Map<String, MetricValueDto> metrics, List<String> allowedNames) {
        Map<String, MetricValueDto> filtered = new LinkedHashMap<>();
        for (String name : allowedNames) {
            if (metrics.containsKey(name)) filtered.put(name, metrics.get(name));
        }
        return filtered;
    }

    private AnalyticsRankingsDto buildRankings(CanonicalDataset current, RankingRequestDto request) {
        if (request == null || !request.isEnabled()) return null;
        InsightObjectType objectType = request.getObjectType() != null ? request.getObjectType() : current.getScope().getObjectType();
        if (objectType != current.getScope().getObjectType()) {
            throw BusinessException.badRequest("rankings.objectType must match the filter's own object type/selection");
        }
        CanonicalMetric metric = request.getMetric() != null ? request.getMetric() : CanonicalMetric.SPEND;
        List<RankingEntryDto> results = rankingService.rank(
                current, metric, request.getDirection(), request.getLimit(),
                request.getMinimumSpend(), request.getMinimumImpressions(), request.isIncludeUnavailable());
        return AnalyticsRankingsDto.builder().metric(metric).results(results).build();
    }

    private List<AnalyticsBreakdownDto> buildBreakdowns(CanonicalDataset current, List<BreakdownRequestDto> requests) {
        if (requests == null || requests.isEmpty()) return null;
        List<AnalyticsBreakdownDto> result = new ArrayList<>();
        for (BreakdownRequestDto req : requests) {
            BreakdownDimension dimension = req.getDimension();
            var rows = breakdownAnalyticsService.computeBreakdown(current.getSnapshotEntities(), dimension.wireName());
            result.add(AnalyticsBreakdownDto.builder()
                    .dimension(dimension)
                    .shareMetric(req.getShareMetric() != null ? req.getShareMetric().name() : CanonicalMetric.SPEND.name())
                    .rows(rows)
                    .build());
        }
        return result;
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
                .findings(List.of())
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
