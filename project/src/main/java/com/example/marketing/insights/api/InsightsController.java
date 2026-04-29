package com.example.marketing.insights.api;

import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.dto.*;
import com.example.marketing.insights.dto.CompareRequestDto;
import com.example.marketing.insights.dto.InsightsBreakdownRowDto;
import com.example.marketing.insights.util.FetchMode;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.insights.service.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/insights")
public class InsightsController extends BaseController {

    private final InsightsService service;

    // -------------------------------------------------------------------------
    // SYNC — general (full control, caller specifies everything)
    // POST /api/insights/sync
    // -------------------------------------------------------------------------

    @PostMapping("/sync")
    public BaseResponse<List<InsightSnapshotDto>> sync(
            Authentication auth,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // SYNC — Account level
    // /act_{accountId}/insights?time_range=...&fields=spend,account_currency
    // POST /api/insights/sync/account/{adAccountId}
    // Body: { "provider": "META", "dateStart": "2026-02-01", "dateStop": "2026-02-21" }
    //    OR { "provider": "META", "datePreset": "last_month" }
    // -------------------------------------------------------------------------

    @PostMapping("/sync/account/{adAccountId}")
    public BaseResponse<List<InsightSnapshotDto>> syncAccount(
            Authentication auth,
            @PathVariable String adAccountId,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        body.setAdAccountId(adAccountId);
        body.setObjectType(InsightObjectType.CAMPAIGN);
        body.setFetchMode(FetchMode.ACCOUNT);
        body.setTimeIncrementAllDays(true);
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // SYNC — Campaign (single)
    // /{campaignId}/insights?fields=...&breakdowns=age,gender
    // POST /api/insights/sync/campaigns/{campaignId}
    // Body: { "provider": "META", "adAccountId": "act_...", "datePreset": "last_month",
    //         "breakdowns": {"breakdowns": "age,gender"} }
    // -------------------------------------------------------------------------

    @PostMapping("/sync/campaigns/{campaignId}")
    public BaseResponse<List<InsightSnapshotDto>> syncCampaign(
            Authentication auth,
            @PathVariable String campaignId,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        body.setObjectExternalIds(List.of(campaignId));
        body.setObjectType(InsightObjectType.CAMPAIGN);
        body.setFetchMode(FetchMode.PER_OBJECT);
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // SYNC — Campaign batch
    // POST /api/insights/sync/campaigns/batch
    // Body: { "provider": "META", "adAccountId": "act_...",
    //         "objectExternalIds": ["camp1","camp2"], "datePreset": "last_month" }
    // -------------------------------------------------------------------------

    @PostMapping("/sync/campaigns/batch")
    public BaseResponse<List<InsightSnapshotDto>> syncCampaignsBatch(
            Authentication auth,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        body.setObjectType(InsightObjectType.CAMPAIGN);
        body.setFetchMode(FetchMode.BATCH_IDS);
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // SYNC — Ad Set (single)
    // /{adsetId}/insights?date_preset=last_month
    // POST /api/insights/sync/adsets/{adsetId}
    // Body: { "provider": "META", "adAccountId": "act_...", "datePreset": "last_month" }
    //    OR { "provider": "META", "adAccountId": "act_...", "datePreset": "last_quarter" }
    // -------------------------------------------------------------------------

    @PostMapping("/sync/adsets/{adsetId}")
    public BaseResponse<List<InsightSnapshotDto>> syncAdSet(
            Authentication auth,
            @PathVariable String adsetId,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        body.setObjectExternalIds(List.of(adsetId));
        body.setObjectType(InsightObjectType.ADSET);
        body.setFetchMode(FetchMode.PER_OBJECT);
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // SYNC — Ad Set batch
    // POST /api/insights/sync/adsets/batch
    // -------------------------------------------------------------------------

    @PostMapping("/sync/adsets/batch")
    public BaseResponse<List<InsightSnapshotDto>> syncAdSetsBatch(
            Authentication auth,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        body.setObjectType(InsightObjectType.ADSET);
        body.setFetchMode(FetchMode.BATCH_IDS);
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // SYNC — Ad (single)
    // /{adId}/insights?fields=...&action_report_time=conversion&date_preset=this_year
    // POST /api/insights/sync/ads/{adId}
    // Body: { "provider": "META", "adAccountId": "act_...", "datePreset": "this_year",
    //         "actionReportTime": "conversion" }
    // -------------------------------------------------------------------------

    @PostMapping("/sync/ads/{adId}")
    public BaseResponse<List<InsightSnapshotDto>> syncAd(
            Authentication auth,
            @PathVariable String adId,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        body.setObjectExternalIds(List.of(adId));
        body.setObjectType(InsightObjectType.AD);
        body.setFetchMode(FetchMode.PER_OBJECT);
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // SYNC — Ad batch
    // /insights?ids=id1,id2&action_breakdowns=action_type&date_preset=maximum
    // POST /api/insights/sync/ads/batch
    // Body: { "provider": "META", "adAccountId": "act_...",
    //         "objectExternalIds": ["id1","id2"],
    //         "datePreset": "maximum", "limit": 100,
    //         "actionBreakdowns": "action_type",
    //         "timeIncrementAllDays": true }
    // -------------------------------------------------------------------------

    @PostMapping("/sync/ads/batch")
    public BaseResponse<List<InsightSnapshotDto>> syncAdsBatch(
            Authentication auth,
            @RequestBody InsightSyncRequestDto body) {
        Long userId = extractUserId(auth);
        body.setObjectType(InsightObjectType.AD);
        body.setFetchMode(FetchMode.BATCH_IDS);
        body.setTimeIncrementAllDays(true);
        if (body.getDatePreset() == null) body.setDatePreset("maximum");
        if (body.getLimit() == null) body.setLimit(100);
        if (body.getActionBreakdowns() == null) body.setActionBreakdowns("action_type");
        return ok(service.sync(userId, body));
    }

    // -------------------------------------------------------------------------
    // QUERY — read from DB
    // -------------------------------------------------------------------------

    @GetMapping("/query")
    public BaseResponse<List<InsightSnapshotDto>> query(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam InsightObjectType objectType,
            @RequestParam String objectExternalId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop) {
        Long userId = extractUserId(auth);
        return ok(service.query(userId, provider, adAccountId, objectType, objectExternalId, dateStart, dateStop));
    }

    @GetMapping("/campaigns")
    public BaseResponse<List<InsightSnapshotDto>> queryCampaigns(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop,
            @RequestParam(required = false) String campaignId) {
        Long userId = extractUserId(auth);
        return ok(service.queryByType(userId, provider, adAccountId,
                InsightObjectType.CAMPAIGN, campaignId, dateStart, dateStop));
    }

    @GetMapping("/adsets")
    public BaseResponse<List<InsightSnapshotDto>> queryAdSets(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop,
            @RequestParam(required = false) String adsetId) {
        Long userId = extractUserId(auth);
        return ok(service.queryByType(userId, provider, adAccountId,
                InsightObjectType.ADSET, adsetId, dateStart, dateStop));
    }

    @GetMapping("/ads")
    public BaseResponse<List<InsightSnapshotDto>> queryAds(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop,
            @RequestParam(required = false) String adId) {
        Long userId = extractUserId(auth);
        return ok(service.queryByType(userId, provider, adAccountId,
                InsightObjectType.AD, adId, dateStart, dateStop));
    }

    // -------------------------------------------------------------------------
    // SNAPSHOT management
    // -------------------------------------------------------------------------

    @GetMapping("/snapshots/{snapshotId}")
    public BaseResponse<InsightSnapshotDto> getSnapshot(
            Authentication auth,
            @PathVariable Long snapshotId) {
        Long userId = extractUserId(auth);
        return ok(service.getSnapshot(userId, snapshotId));
    }

    @DeleteMapping("/snapshots/{snapshotId}")
    public BaseResponse<Void> deleteSnapshot(
            Authentication auth,
            @PathVariable Long snapshotId) {
        Long userId = extractUserId(auth);
        service.deleteSnapshot(userId, snapshotId);
        return ok(null);
    }

    // -------------------------------------------------------------------------
    // METRICS
    // -------------------------------------------------------------------------

    @GetMapping("/metrics")
    public BaseResponse<List<String>> metricNames() {
        return ok(service.listMetricNames());
    }

    // -------------------------------------------------------------------------
    // BREAKDOWN by dimension
    // GET /api/insights/breakdown?provider=META&adAccountId=...&dimension=age&dateStart=...&dateStop=...
    // -------------------------------------------------------------------------

    @GetMapping("/breakdown")
    public BaseResponse<List<InsightsBreakdownRowDto>> breakdown(
            Authentication auth,
            @RequestParam Provider provider,
            @RequestParam String adAccountId,
            @RequestParam String dimension,
            @RequestParam LocalDate dateStart,
            @RequestParam LocalDate dateStop) {
        Long userId = extractUserId(auth);
        return ok(service.breakdown(userId, provider, adAccountId, dimension, dateStart, dateStop));
    }

    // -------------------------------------------------------------------------
    // CROSS-PLATFORM COMPARE
    // POST /api/insights/compare
    // Body: { platforms: ["META","TIKTOK"], entityType: "CAMPAIGN", adAccountId, dateFrom, dateTo }
    // -------------------------------------------------------------------------

    @PostMapping("/compare")
    public BaseResponse<Map<String, Map<String, Object>>> compare(
            Authentication auth,
            @RequestBody CompareRequestDto body) {
        Long userId = extractUserId(auth);
        return ok(service.compare(userId, body));
    }
}
