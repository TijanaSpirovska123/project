package com.example.marketing.campaign.api;

import com.example.marketing.campaign.dto.BulkStatusRequestDto;
import com.example.marketing.campaign.dto.CampaignDto;
import com.example.marketing.campaign.service.CampaignService;
import com.example.marketing.infrastructure.Endpoints;
import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.infrastructure.util.Provider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(Endpoints.BASE_CAMPAIGN)
@RequiredArgsConstructor
public class CampaignController extends BaseController {
    private final CampaignService campaignService;

    private static final Logger logger = LoggerFactory.getLogger(CampaignController.class);

    @PostMapping
    public BaseResponse<CampaignDto> create(@RequestBody CampaignDto dto) {
        logger.info("Creating campaign with details: {}", dto);
        CampaignDto created = campaignService.createCampaign(dto);
        logger.info("Campaign created with id: {}", created.getId());
        return ok(created);
    }

    @PutMapping("/{id}")
    public BaseResponse<CampaignDto> update(@PathVariable Long id, @RequestBody CampaignDto dto) {
        logger.info("Updating campaign with id: {} and details: {}", id, dto);
        CampaignDto updated = campaignService.updateCampaign(id, dto);
        logger.info("Campaign updated with id: {}", updated.getId());
        return ok(updated);
    }

    @DeleteMapping("/{id}")
    public BaseResponse<String> delete(@PathVariable Long id) {
        logger.info("Deleting campaign with id: {}", id);
        campaignService.deleteCampaign(id);
        logger.info("Campaign deleted with id: {}", id);
        return ok("Campaign with id " + id + " deleted successfully");
    }

    @GetMapping("/{platform}")
    public BaseResponse<List<CampaignDto>> getAll(Authentication auth,@PathVariable Provider platform) {
        Long userId = extractUserId(auth);
        logger.info("Fetching all campaigns for user id: {}", userId);
        List<CampaignDto> campaigns = campaignService.getAllCampaignsByPlatform(userId,platform);
        logger.info("Fetched {} campaigns for user id: {}", campaigns.size(), userId);
        return ok(campaigns);
    }

    @PatchMapping("/external/{platform}/{externalId}")
    public BaseResponse<CampaignDto> updateByExternalId(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable String externalId,
            @RequestBody CampaignDto dto
    ) {
        Long userId = extractUserId(auth);
        CampaignDto updated = campaignService.updateByExternalId(userId, platform, externalId, dto);
        return ok(updated);
    }

    @GetMapping("/platform/{platform}/{accountId}")
    public BaseResponse<List<CampaignDto>> getAllCampaignsFromPlatformAndPersist(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable String accountId
    ) {
        Long userId = extractUserId(auth);
        campaignService.syncCampaigns(userId, platform, accountId); // persist silently
        return ok(campaignService.getAllCampaigns(userId));         // return DB results (or platform results)
    }

    @PatchMapping("/bulk-status")
    public BaseResponse<Integer> bulkUpdateStatus(Authentication auth,
                                                   @RequestBody BulkStatusRequestDto req) {
        Long userId = extractUserId(auth);
        int updated = campaignService.bulkUpdateStatus(userId, req);
        return ok(updated);
    }

    /**
     * Get ad sets for a specific campaign filtered by platform.
     * Example: GET /api/campaigns/platform/META/campaign/123/adsets
     */
    @GetMapping("/platform/{platform}/campaign/{campaignId}/adsets")
    public BaseResponse<List<com.example.marketing.adset.dto.AdSetDto>> getAdSetsByCampaign(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable Long campaignId
    ) {
        Long userId = extractUserId(auth);
        logger.info("Fetching ad sets for campaign {} with platform {} for user {}", campaignId, platform, userId);
        List<com.example.marketing.adset.dto.AdSetDto> adSets = 
                campaignService.getAdSetsByCampaign(userId, platform, campaignId);
        logger.info("Fetched {} ad sets for campaign {}", adSets.size(), campaignId);
        return ok(adSets);
    }



}