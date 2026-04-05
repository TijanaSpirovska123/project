package com.example.marketing.adset.api;


import com.example.marketing.adset.dto.AdSetDto;
import com.example.marketing.adset.service.AdSetService;
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
@RequestMapping(Endpoints.BASE_AD_SET)
@RequiredArgsConstructor
public class AdSetController extends BaseController {
    private final AdSetService adSetService;
    private static final Logger log = LoggerFactory.getLogger(AdSetController.class);

    @PostMapping
    public BaseResponse<AdSetDto> createAdSet(@RequestBody AdSetDto dto) {
        AdSetDto created = adSetService.createAdSet(dto);
        return ok(created);
    }

    @PutMapping("/{id}")
    public BaseResponse<AdSetDto> updateAdSet(@PathVariable Long id, @RequestBody AdSetDto dto) {
        AdSetDto updated = adSetService.updateAdSet(id, dto);
        return ok(updated);
    }

    @DeleteMapping("/{id}")
    public BaseResponse<String> deleteAdSet(@PathVariable Long id) {
        adSetService.deleteAdSet(id);
        return ok("Ad set with id " + id + " deleted successfully");
    }

    @GetMapping("/{platform}")
    public BaseResponse<List<AdSetDto>> getAllAdSets(Authentication auth,@PathVariable Provider platform) {
        Long userId = extractUserId(auth);
        List<AdSetDto> adSets = adSetService.getAllAdSetsByPlatform(userId, platform);
        return ok(adSets);
    }

    @PatchMapping("/external/{platform}/{externalId}")
    public BaseResponse<AdSetDto> updateByExternalId(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable String externalId,
            @RequestBody AdSetDto dto
    ) {
        Long userId = extractUserId(auth);
        AdSetDto updated = adSetService.updateByExternalId(userId, platform, externalId, dto);
        return ok(updated);
    }

    @GetMapping("/platform/{platform}/{accountId}")
    public BaseResponse<List<AdSetDto>> getAllAdSetsFromPlatformAndPersist(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable String accountId
    ) {
        Long userId = extractUserId(auth);

        // IMPORTANT: campaigns must be synced first, otherwise FK resolution fails
        adSetService.syncAdSets(userId, platform, accountId);

        return ok(adSetService.getAllAdSets(userId));
    }

    /**
     * Get ads for a specific ad set filtered by platform.
     * Example: GET /api/ad-sets/platform/META/adset/456/ads
     */
    @GetMapping("/platform/{platform}/adset/{adSetId}/ads")
    public BaseResponse<List<com.example.marketing.ad.dto.AdDto>> getAdsByAdSet(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable Long adSetId
    ) {
        Long userId = extractUserId(auth);
        log.info("Fetching ads for ad set {} with platform {} for user {}", adSetId, platform, userId);
        List<com.example.marketing.ad.dto.AdDto> ads = 
                adSetService.getAdsByAdSet(userId, platform, adSetId);
        log.info("Fetched {} ads for ad set {}", ads.size(), adSetId);
        return ok(ads);
    }

}