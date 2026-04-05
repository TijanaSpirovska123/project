package com.example.marketing.oauth.api;

import com.example.marketing.auth.UserPrincipal;
import com.example.marketing.oauth.dto.OAuthConnectResponse;
import com.example.marketing.oauth.service.MetaOAuthService;
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
     * Disconnect Meta — deactivates all ad account connections and removes token.
     */
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        metaOAuthService.disconnect(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
