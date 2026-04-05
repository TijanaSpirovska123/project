package com.example.marketing.adcreative.api;


import com.example.marketing.adcreative.dto.AdAssetDto;
import com.example.marketing.adcreative.dto.CreativeDto;
import com.example.marketing.adcreative.service.AdCreativeService;
import com.example.marketing.infrastructure.Endpoints;
import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.infrastructure.util.Provider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping(Endpoints.BASE_PAGE)
public class AdCreativeController extends BaseController {

    private final AdCreativeService adCreativeService;
    private static final Logger logger = LoggerFactory.getLogger(AdCreativeController.class);

    @PostMapping("/creative/{postId}/{platform}")
    public BaseResponse<CreativeDto> createOrUpdateCreative(
            @PathVariable String postId,
            @PathVariable Provider platform,
            @RequestBody(required = false) CreativeDto creativeDto,
            @RequestParam String pageName,
            Authentication auth,
            @RequestParam(required = false) String linkUrl) {
        Long userId = extractUserId(auth);
        logger.info("Creating creative for post: {} by user: {}", postId, userId);
        String name = (creativeDto != null) ? creativeDto.getName() : null;
        CreativeDto creative = adCreativeService.createCreative(postId, name, userId, pageName, linkUrl, platform);
        return ok(creative);
    }

    @PatchMapping("/creative/{creativeId}")
    public BaseResponse<CreativeDto> updateCreative(
            @PathVariable String creativeId,
            @RequestBody(required = false) CreativeDto creativeDto,
            @RequestParam(required = false) String linkUrl) {
        logger.info("Updating creative with id: {}", creativeId);
        String name = (creativeDto != null) ? creativeDto.getName() : null;
        CreativeDto creative = adCreativeService.updateCreative(creativeId, name, linkUrl);
        return ok(creative);
    }

    @GetMapping("/creatives/ids")
    public BaseResponse<List<String>> getAllAdCreativeIds(
            Authentication auth,
            @RequestParam String adAccountId
    ) {
        Long userId = extractUserId(auth);
        logger.info("Fetching all creative IDs for user: {} and account: {}", userId, adAccountId);
        List<String> creativeIds = adCreativeService.getAllAdCreativeIds(userId, adAccountId);
        logger.info("Fetched {} creative IDs", creativeIds.size());
        return ok(creativeIds);
    }

    @GetMapping("/creatives/{creativeId}/details")
    public BaseResponse<Map<String, Object>> getAdCreativeDetails(
            Authentication auth, @RequestParam String adAccountId,
            @PathVariable String creativeId
    ) {
        Long userId = extractUserId(auth);
        logger.info("Fetching creative details for ID: {} by user: {}", creativeId, userId);
        Map<String, Object> creativeDetails = adCreativeService.getAdCreativeDetails(userId, adAccountId, creativeId);
        logger.info("Fetched creative details for ID: {}", creativeId);
        return ok(creativeDetails);
    }

    @GetMapping("/creatives/all-with-details")
    public BaseResponse<List<Map<String, Object>>> getAllAdCreativesWithDetails(
            Authentication auth,
            @RequestParam String adAccountId,
            @RequestParam(required = false, defaultValue = "false") boolean forceRefresh
    ) {
        Long userId = extractUserId(auth);
        logger.info("Fetching all creatives with details for user: {}", userId);
        List<Map<String, Object>> creativesWithDetails = adCreativeService.getAllAdCreativesWithDetails(userId, adAccountId);
        logger.info("Fetched {} creatives with details", creativesWithDetails.size());
        return ok(creativesWithDetails);
    }


    @PostMapping("/creative/from-asset")
    public BaseResponse<CreativeDto> createCreativeFromAsset(
            Authentication auth,
            @RequestBody CreativeDto body,
            @RequestParam String adAccountId,   // or omit if you always use default
            @RequestParam Provider platform
    ) {
        Long userId = extractUserId(auth);
        CreativeDto created = adCreativeService.createCreativeFromAsset(userId, body, adAccountId, platform);
        return ok(created);
    }

    @PostMapping("/upload-image/from-stored-asset")
    public BaseResponse<AdAssetDto> uploadAdImageFromStoredAsset(
            Authentication auth,
            @RequestParam String adAccountId,
            @RequestParam Long assetId,
            @RequestParam String variantKey,
            @RequestParam(required = false) String pageName
    ) {
        Long userId = extractUserId(auth);
        return ok(adCreativeService.uploadAdImageFromStoredAsset(
                userId, adAccountId, assetId, variantKey, pageName
        ));
    }

    @PostMapping("/creative/from-stored-asset")
    public BaseResponse<CreativeDto> createCreativeFromStoredAsset(
            Authentication auth,
            @RequestParam Long storedAssetId,
            @RequestParam String variantKey,
            @RequestParam Provider platform,
            @RequestParam(required = false) String adAccountId,
            @RequestBody CreativeDto body
    ) {
        Long userId = extractUserId(auth);
        return ok(adCreativeService.createCreativeFromStoredAsset(
                userId, body, adAccountId, platform, storedAssetId, variantKey
        ));
    }


    @GetMapping("/assets")
    public BaseResponse<List<AdAssetDto>> list(
            Authentication auth,
            @RequestParam(required = false) String adAccountId
    ) {
        Long userId = extractUserId(auth);
        logger.info("Listing ad assets for user: {} account filter: {}", userId, adAccountId);
        List<AdAssetDto> out = adCreativeService.listAdAssets(userId, adAccountId);
        return ok(out);
    }

    @GetMapping("/platform/{platform}/{accountId}")
    public BaseResponse<List<CreativeDto>> getAllAdsFromPlatformAndPersist(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable String accountId
    ) {
        Long userId = extractUserId(auth);

        // IMPORTANT: campaigns must be synced first, otherwise FK resolution fails
        adCreativeService.syncAdCreatives(userId, platform, accountId);

        return ok(adCreativeService.getAllAdCreatives(userId));
    }

}
