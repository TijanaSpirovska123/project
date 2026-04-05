package com.example.marketing.oauth.api;

import com.example.marketing.auth.AdAccountConnectionEntity;
import com.example.marketing.auth.UserPrincipal;
import com.example.marketing.oauth.dto.AdAccountConnectionSummary;
import com.example.marketing.oauth.repository.OAuthAccountRepository;
import com.example.marketing.oauth.service.MetaOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ad-account-connections")
@RequiredArgsConstructor
public class AdAccountConnectionController {

    private final MetaOAuthService metaOAuthService;
    private final OAuthAccountRepository oauthAccountRepo;

    private static final List<String> ALL_PLATFORMS =
            List.of("META", "TIKTOK", "PINTEREST", "GOOGLE", "LINKEDIN", "REDDIT");

    /**
     * Returns one entry per known platform with connected status.
     * Active connections are marked connected=true; others connected=false.
     */
    @GetMapping
    public ResponseEntity<List<AdAccountConnectionSummary>> getConnections(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        List<AdAccountConnectionEntity> active = metaOAuthService.getActiveConnections(principal.userId());

        Map<String, AdAccountConnectionSummary> byProvider = active.stream()
                .collect(Collectors.groupingBy(
                        AdAccountConnectionEntity::getProvider,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    String provider = list.get(0).getProvider();
                                    String tokenStatus = oauthAccountRepo
                                            .findByUserIdAndProvider(principal.userId(), provider)
                                            .map(a -> a.getTokenStatus() != null ? a.getTokenStatus().name() : null)
                                            .orElse(null);
                                    return new AdAccountConnectionSummary(
                                            provider,
                                            true,
                                            list.get(0).getLastSynced(),
                                            tokenStatus
                                    );
                                }
                        )
                ));

        List<AdAccountConnectionSummary> result = ALL_PLATFORMS.stream()
                .map(p -> byProvider.getOrDefault(p, new AdAccountConnectionSummary(p, false, null, null)))
                .toList();

        return ResponseEntity.ok(result);
    }
}
