package com.example.marketing.oauth.service;

import com.example.marketing.infrastructure.util.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {

    @Value("${spring.security.oauth2.client.registration.facebook.client-id}")
    private String facebookClientId;

    @Value("${spring.security.oauth2.client.registration.facebook.client-secret}")
    private String facebookClientSecret;

    @Value("${meta.graph.version:v23.0}")
    private String graphVersion;

    private final RestTemplate restTemplate = new RestTemplate();

    public record TokenExchangeResult(String accessToken, LocalDateTime expiresAt) {}

    /**
     * Exchanges a short-lived OAuth2 access token for a long-lived one.
     * Each provider has its own exchange mechanism — add a new case when
     * onboarding a new platform.
     */
    public TokenExchangeResult exchangeToLongLived(Provider provider, String shortLivedToken) {
        return switch (provider) {
            case META, FACEBOOK, INSTAGRAM -> exchangeMetaToken(shortLivedToken);
            // Google issues long-lived tokens (+ refresh tokens) directly from its OAuth2 flow.
            // The access token from Spring is already suitable; store as-is.
            case GOOGLE -> passthrough(shortLivedToken, 60);
            // TikTok tokens are valid for 24 hours; refresh is handled separately.
            case TIKTOK -> passthrough(shortLivedToken, 1);
            // LinkedIn tokens are valid for 60 days.
            case LINKEDIN -> passthrough(shortLivedToken, 60);
            default -> passthrough(shortLivedToken, 30);
        };
    }

    private TokenExchangeResult exchangeMetaToken(String shortLivedToken) {
        String url = String.format(
                "https://graph.facebook.com/%s/oauth/access_token" +
                        "?grant_type=fb_exchange_token" +
                        "&client_id=%s" +
                        "&client_secret=%s" +
                        "&fb_exchange_token=%s",
                graphVersion, facebookClientId, facebookClientSecret, shortLivedToken
        );

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Meta token exchange failed — no access_token in response");
        }

        String longToken = String.valueOf(response.get("access_token"));
        Number expiresInNum = (Number) response.getOrDefault("expires_in", 60L * 24 * 60 * 60);

        return new TokenExchangeResult(longToken, LocalDateTime.now().plusSeconds(expiresInNum.longValue()));
    }

    private TokenExchangeResult passthrough(String token, int validDays) {
        return new TokenExchangeResult(token, LocalDateTime.now().plusDays(validDays));
    }
}
