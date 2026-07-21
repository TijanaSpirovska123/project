package com.example.marketing.insights.analytics.api;

import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.analytics.dto.AnalysisContextDto;
import com.example.marketing.insights.analytics.dto.AnalysisContextRequestDto;
import com.example.marketing.insights.analytics.dto.AnalyticsFilterRequest;
import com.example.marketing.insights.analytics.dto.AnalyticsSummaryDto;
import com.example.marketing.insights.analytics.dto.CanonicalDataset;
import com.example.marketing.insights.analytics.dto.DataQualityIssueDto;
import com.example.marketing.insights.analytics.dto.PeriodComparisonRequestDto;
import com.example.marketing.insights.analytics.dto.PeriodComparisonResultDto;
import com.example.marketing.insights.analytics.dto.ProviderAnalyticsCapabilitiesDto;
import com.example.marketing.insights.analytics.dto.RankingEntryDto;
import com.example.marketing.insights.analytics.dto.TimeSeriesResponseDto;
import com.example.marketing.insights.analytics.enums.CanonicalMetric;
import com.example.marketing.insights.analytics.enums.TimeGranularity;
import com.example.marketing.insights.analytics.service.AnalysisContextBuilder;
import com.example.marketing.insights.analytics.service.AnalyticsSummaryService;
import com.example.marketing.insights.analytics.service.CanonicalDatasetLoader;
import com.example.marketing.insights.analytics.service.DataQualityService;
import com.example.marketing.insights.analytics.service.PeriodComparisonService;
import com.example.marketing.insights.analytics.service.RankingService;
import com.example.marketing.insights.analytics.service.TimeSeriesService;
import com.example.marketing.insights.analytics.strategy.ProviderAnalyticsStrategyRegistry;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import com.example.marketing.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Phase-2 analytics endpoints, all under /api/insights/analytics — additive only, never
 * replacing or renaming any existing /api/insights endpoint (Step 23). Every endpoint derives
 * userId from the authenticated principal server-side (never from the request body/params) and
 * every downstream repository query is scoped to that user, matching existing Phase-1
 * conventions (see SnapshotCanonicalDatasetLoader's javadoc for how ownership is enforced).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/insights/analytics")
public class InsightsAnalyticsController extends BaseController {

    private final UserRepository userRepository;
    private final CanonicalDatasetLoader datasetLoader;
    private final AnalyticsSummaryService summaryService;
    private final TimeSeriesService timeSeriesService;
    private final PeriodComparisonService comparisonService;
    private final RankingService rankingService;
    private final DataQualityService dataQualityService;
    private final AnalysisContextBuilder contextBuilder;
    private final ProviderAnalyticsStrategyRegistry strategyRegistry;

    private UserEntity currentUser(Authentication auth) {
        Long userId = extractUserId(auth);
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));
    }

    // -------------------------------------------------------------------------
    // GET /api/insights/analytics/capabilities?provider=META
    // -------------------------------------------------------------------------

    @GetMapping("/capabilities")
    public BaseResponse<ProviderAnalyticsCapabilitiesDto> capabilities(@RequestParam Provider provider) {
        return ok(strategyRegistry.getStrategy(provider).getCapabilities());
    }

    // -------------------------------------------------------------------------
    // GET /api/insights/analytics/summary
    // -------------------------------------------------------------------------

    @GetMapping("/summary")
    public BaseResponse<AnalyticsSummaryDto> summary(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop,
            @RequestParam(required = false) InsightObjectType objectType,
            @RequestParam(required = false) List<String> campaignIds,
            @RequestParam(required = false) List<String> adSetIds,
            @RequestParam(required = false) List<String> adIds) {

        UserEntity user = currentUser(auth);
        AnalyticsFilterRequest request = filterRequest(provider, adAccountId, dateStart, dateStop, objectType, campaignIds, adSetIds, adIds);
        CanonicalDataset dataset = datasetLoader.load(user, request);
        return ok(summaryService.summarize(dataset));
    }

    // -------------------------------------------------------------------------
    // GET /api/insights/analytics/time-series
    // -------------------------------------------------------------------------

    @GetMapping("/time-series")
    public BaseResponse<TimeSeriesResponseDto> timeSeries(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop,
            @RequestParam(required = false) InsightObjectType objectType,
            @RequestParam(required = false) List<String> campaignIds,
            @RequestParam(required = false) List<String> adSetIds,
            @RequestParam(required = false) List<String> adIds,
            @RequestParam(defaultValue = "DAY") TimeGranularity granularity,
            @RequestParam(defaultValue = "false") boolean includeInactivePeriods) {

        UserEntity user = currentUser(auth);
        AnalyticsFilterRequest request = filterRequest(provider, adAccountId, dateStart, dateStop, objectType, campaignIds, adSetIds, adIds);
        CanonicalDataset dataset = datasetLoader.load(user, request);
        return ok(timeSeriesService.build(dataset, granularity, includeInactivePeriods));
    }

    // -------------------------------------------------------------------------
    // POST /api/insights/analytics/compare — deliberately NOT at /api/insights/compare,
    // which is preserved unchanged for its existing (cross-platform) meaning.
    // -------------------------------------------------------------------------

    @PostMapping("/compare")
    public BaseResponse<PeriodComparisonResultDto> compare(Authentication auth, @RequestBody PeriodComparisonRequestDto body) {
        UserEntity user = currentUser(auth);

        AnalyticsFilterRequest currentRequest = filterRequest(body.getProvider(), body.getAdAccountId(),
                body.getCurrentPeriodStart(), body.getCurrentPeriodStop(), body.getObjectType(),
                body.getCampaignIds(), body.getAdSetIds(), body.getAdIds());
        CanonicalDataset current = datasetLoader.load(user, currentRequest);

        var currentPeriod = current.getRequestedPeriod();
        var comparisonPeriod = switch (body.getComparisonMode()) {
            case PREVIOUS_PERIOD -> comparisonService.previousPeriod(currentPeriod);
            case PREVIOUS_YEAR -> comparisonService.previousYearPeriod(currentPeriod);
            case CUSTOM -> {
                if (body.getCustomComparisonStart() == null || body.getCustomComparisonStop() == null) {
                    throw BusinessException.badRequest("customComparisonStart/customComparisonStop are required for CUSTOM comparisonMode");
                }
                yield new com.example.marketing.insights.dto.InsightPeriodDto(body.getCustomComparisonStart(), body.getCustomComparisonStop());
            }
        };

        AnalyticsFilterRequest comparisonRequest = filterRequest(body.getProvider(), body.getAdAccountId(),
                comparisonPeriod.getStart(), comparisonPeriod.getStop(), body.getObjectType(),
                body.getCampaignIds(), body.getAdSetIds(), body.getAdIds());
        CanonicalDataset previous = datasetLoader.load(user, comparisonRequest);

        return ok(comparisonService.compare(current, previous, body.getComparisonMode()));
    }

    // -------------------------------------------------------------------------
    // GET /api/insights/analytics/rankings
    // -------------------------------------------------------------------------

    @GetMapping("/rankings")
    public BaseResponse<List<RankingEntryDto>> rankings(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop,
            @RequestParam InsightObjectType objectType,
            @RequestParam CanonicalMetric metric,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) List<String> objectIds,
            @RequestParam(required = false) BigDecimal minimumSpend,
            @RequestParam(required = false) BigDecimal minimumImpressions,
            @RequestParam(defaultValue = "false") boolean includeUnavailable) {

        UserEntity user = currentUser(auth);
        AnalyticsFilterRequest request = filterRequest(provider, adAccountId, dateStart, dateStop, objectType,
                objectType == InsightObjectType.CAMPAIGN ? objectIds : null,
                objectType == InsightObjectType.ADSET ? objectIds : null,
                objectType == InsightObjectType.AD ? objectIds : null);
        CanonicalDataset dataset = datasetLoader.load(user, request);
        return ok(rankingService.rank(dataset, metric, direction, limit, minimumSpend, minimumImpressions, includeUnavailable));
    }

    // -------------------------------------------------------------------------
    // GET /api/insights/analytics/data-quality
    // -------------------------------------------------------------------------

    @GetMapping("/data-quality")
    public BaseResponse<List<DataQualityIssueDto>> dataQuality(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop,
            @RequestParam(required = false) InsightObjectType objectType,
            @RequestParam(required = false) List<String> campaignIds,
            @RequestParam(required = false) List<String> adSetIds,
            @RequestParam(required = false) List<String> adIds) {

        UserEntity user = currentUser(auth);
        AnalyticsFilterRequest request = filterRequest(provider, adAccountId, dateStart, dateStop, objectType, campaignIds, adSetIds, adIds);
        CanonicalDataset dataset = datasetLoader.load(user, request);
        return ok(dataQualityService.analyze(dataset));
    }

    // -------------------------------------------------------------------------
    // POST /api/insights/analytics/context — returns the context only; never calls
    // FastAPI/Gemini/Smart Goal/MCP.
    // -------------------------------------------------------------------------

    @PostMapping("/context")
    public BaseResponse<AnalysisContextDto> context(Authentication auth, @RequestBody AnalysisContextRequestDto body) {
        UserEntity user = currentUser(auth);
        return ok(contextBuilder.build(user, body));
    }

    private AnalyticsFilterRequest filterRequest(Provider provider, String adAccountId, LocalDate dateStart, LocalDate dateStop,
            InsightObjectType objectType, List<String> campaignIds, List<String> adSetIds, List<String> adIds) {
        AnalyticsFilterRequest request = new AnalyticsFilterRequest();
        request.setProvider(provider);
        request.setAdAccountId(adAccountId);
        request.setDateStart(dateStart);
        request.setDateStop(dateStop);
        request.setObjectType(objectType);
        request.setCampaignIds(campaignIds);
        request.setAdSetIds(adSetIds);
        request.setAdIds(adIds);
        return request;
    }
}
