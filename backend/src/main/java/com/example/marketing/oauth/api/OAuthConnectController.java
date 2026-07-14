package com.example.marketing.oauth.api;

import com.example.marketing.ad.service.AdService;
import com.example.marketing.adset.service.AdSetService;
import com.example.marketing.auth.AdAccountConnectionRepository;
import com.example.marketing.auth.UserPrincipal;
import com.example.marketing.campaign.service.CampaignService;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.dto.OAuthConnectResponse;
import com.example.marketing.oauth.service.MetaOAuthService;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("api/oauth/meta")
@RequiredArgsConstructor
public class OAuthConnectController {

    private final MetaOAuthService metaOAuthService;
    private final UserRepository userRepository;
    private final AdAccountConnectionRepository adAccountConnectionRepository;
    private final CampaignService campaignService;
    private final AdSetService adSetService;
    private final AdService adService;

    /**
     * Called by the frontend when user clicks "Connect Meta".
     * Returns the full Meta authorization URL for a browser redirect.
     */
    @PostMapping("/connect")
    public ResponseEntity<OAuthConnectResponse> initiateConnect(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return ResponseEntity.ok(metaOAuthService.initiateConnect(principal.userId()));
    }

    /**
     * Meta redirects here after the user grants or denies permissions.
     * Public endpoint — state param ties it to the local user. Always responds with a redirect.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        String redirectUrl = metaOAuthService.handleCallback(code, state, error);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    /**
     * Disconnect Meta entirely — wipes all synced data, deactivates all ad account
     * connections, and removes the OAuth token.
     */
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Long userId = principal.userId();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        campaignService.wipePlatform(user, Provider.META);
        adSetService.wipePlatform(user, Provider.META);
        adService.wipePlatform(user, Provider.META);

        metaOAuthService.disconnect(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Disconnect a single Meta ad account — wipes its synced data and marks the
     * ad_account_connection row inactive. Other ad accounts are untouched.
     */
    @DeleteMapping("/connections/{adAccountId}")
    public ResponseEntity<?> disconnectAdAccount(
            @PathVariable String adAccountId,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Long userId = principal.userId();

        boolean owned = adAccountConnectionRepository
                .existsByUserIdAndProviderAndAdAccountId(userId, "META", adAccountId);
        if (!owned) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        campaignService.wipeAdAccount(user, Provider.META, adAccountId);
        adSetService.wipeAdAccount(user, Provider.META, adAccountId);
        adService.wipeAdAccount(user, Provider.META, adAccountId);

        adAccountConnectionRepository
                .findByUserIdAndProviderAndAdAccountId(userId, "META", adAccountId)
                .ifPresent(conn -> {
                    conn.setActive(false);
                    adAccountConnectionRepository.save(conn);
                });

        return ResponseEntity.ok().build();
    }
}
