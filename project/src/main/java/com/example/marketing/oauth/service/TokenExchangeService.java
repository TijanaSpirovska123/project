package com.example.marketing.oauth.service;



import com.example.marketing.infrastructure.util.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

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

    public TokenExchangeResult exchangeToLongLived(Provider provider, String shortLivedToken) {

        // ✅ Only Meta needs this exchange (Facebook/Instagram under Meta)
        if (provider == Provider.META) {
            return new TokenExchangeResult(shortLivedToken, LocalDateTime.now().plusDays(30));
        }

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
            throw new RuntimeException("Failed to exchange token with Meta");
        }

        String token = String.valueOf(response.get("access_token"));

        // ✅ Safe cast: Integer/Long/Double all work
        Number expiresInNum = (Number) response.getOrDefault("expires_in", 60L * 24 * 60 * 60);
        long expiresIn = expiresInNum.longValue();

        return new TokenExchangeResult(token, LocalDateTime.now().plusSeconds(expiresIn));
    }
}
