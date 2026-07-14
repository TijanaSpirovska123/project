package com.example.marketing.ad.api;

import com.example.marketing.ad.dto.AdDto;
import com.example.marketing.ad.service.AdService;
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
@RequestMapping(Endpoints.BASE_AD)
@RequiredArgsConstructor
public class AdController extends BaseController {

    private final AdService adService;

    @PostMapping
    public BaseResponse<AdDto> createAd(Authentication auth, @RequestBody AdDto dto) {
        Long userId = extractUserId(auth);
        return ok(adService.createAd(userId, dto));
    }

    @PutMapping("/{id}")
    public BaseResponse<AdDto> updateAd(Authentication auth, @PathVariable Long id, @RequestBody AdDto dto) {
        Long userId = extractUserId(auth);
        return ok(adService.updateAd(userId, id, dto));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<String> deleteAd(Authentication auth, @PathVariable Long id) {
        Long userId = extractUserId(auth);
        adService.deleteAd(userId, id);
        return ok("Deleted");
    }

    @GetMapping("/{platform}")
    public BaseResponse<List<AdDto>> getAllAds(Authentication auth, @PathVariable Provider platform) {
        Long userId = extractUserId(auth);
        return ok(adService.getAllAdsByPlatform(userId, platform));
    }

    @PatchMapping("/external/{platform}/{externalId}")
    public BaseResponse<AdDto> updateByExternalId(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable String externalId,
            @RequestBody AdDto dto
    ) {
        Long userId = extractUserId(auth);
        AdDto updated = adService.updateByExternalId(userId, platform, externalId, dto);
        return ok(updated);
    }

    @GetMapping("/platform/{platform}/{adAccountId}")
    public BaseResponse<List<AdDto>> getAllAdsFromPlatform(
            Authentication auth,
            @PathVariable Provider platform,
            @PathVariable String adAccountId
    ) {
        Long userId = extractUserId(auth);
        adService.syncAds(userId, platform, adAccountId);

        return ok(adService.getAllAds(userId));
    }

}
