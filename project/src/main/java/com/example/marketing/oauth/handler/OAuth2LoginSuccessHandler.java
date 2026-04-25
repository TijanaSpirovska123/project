package com.example.marketing.oauth.handler;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.config.FrontendProperties;
import com.example.marketing.oauth.entity.OAuthAccountEntity;
import com.example.marketing.oauth.entity.OAuthConnectRequestEntity;
import com.example.marketing.oauth.repository.OAuthAccountRepository;
import com.example.marketing.auth.OAuthConnectRequestRepository;
import com.example.marketing.oauth.service.TokenExchangeService;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthConnectRequestRepository connectRepo;
    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final TokenExchangeService tokenExchangeService;
    private final FrontendProperties frontendProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        // Derive our canonical provider from the Spring registration ID (e.g. "facebook" → META).
        // Adding a new platform: register it in application.yml AND add its case to Provider.fromRegistrationId().
        Provider provider;
        try {
            provider = Provider.fromRegistrationId(registrationId);
        } catch (IllegalArgumentException e) {
            log.warn("OAuth2 success for unknown registrationId '{}' — rejecting", registrationId);
            redirectError(response, "UNKNOWN", "unsupported_provider");
            return;
        }

        String providerKey = provider.name(); // the string stored in DB, e.g. "META"

        // State cookie must be present — its absence means the user hit the OAuth endpoint directly
        // rather than via our controlled connect flow.
        Optional<String> stateOpt = readCookie(request, "oauth_connect_state");
        if (stateOpt.isEmpty()) {
            log.warn("OAuth2 success for {} but no oauth_connect_state cookie — aborting", providerKey);
            redirectError(response, providerKey, "missing_state");
            return;
        }

        String state = stateOpt.get();

        OAuthConnectRequestEntity connectReq;
        try {
            connectReq = connectRepo.findByStateAndProvider(state, providerKey)
                    .orElseThrow(() -> new RuntimeException("Invalid connect state"));
        } catch (RuntimeException e) {
            log.warn("No connect request found for state={} provider={}", state, providerKey);
            clearCookie(response, "oauth_connect_state");
            redirectError(response, providerKey, "invalid_state");
            return;
        }

        if (connectReq.getExpiresAt().isBefore(LocalDateTime.now())) {
            connectRepo.deleteById(state);
            clearCookie(response, "oauth_connect_state");
            redirectError(response, providerKey, "state_expired");
            return;
        }

        UserEntity user = userRepository.findById(connectReq.getUserId())
                .orElse(null);
        if (user == null) {
            connectRepo.deleteById(state);
            clearCookie(response, "oauth_connect_state");
            redirectError(response, providerKey, "user_not_found");
            return;
        }

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                registrationId, oauthToken.getName());

        if (client == null || client.getAccessToken() == null) {
            connectRepo.deleteById(state);
            clearCookie(response, "oauth_connect_state");
            redirectError(response, providerKey, "no_access_token");
            return;
        }

        String shortLivedToken = client.getAccessToken().getTokenValue();

        TokenExchangeService.TokenExchangeResult longToken;
        try {
            longToken = tokenExchangeService.exchangeToLongLived(provider, shortLivedToken);
        } catch (Exception e) {
            log.error("Token exchange failed for provider {}: {}", providerKey, e.getMessage());
            connectRepo.deleteById(state);
            clearCookie(response, "oauth_connect_state");
            redirectError(response, providerKey, "token_exchange_failed");
            return;
        }

        // Upsert the OAuthAccountEntity for this user+provider pair.
        OAuthAccountEntity oauthAccount = oauthAccountRepository
                .findByUserAndProvider(user, providerKey)
                .orElse(new OAuthAccountEntity());

        oauthAccount.setUser(user);
        oauthAccount.setProvider(providerKey);
        oauthAccount.setAccessToken(longToken.accessToken());
        oauthAccount.setTokenExpiry(longToken.expiresAt());
        oauthAccount.setExternalUserId(oauthToken.getName());
        oauthAccount.setGrantedScopes(String.join(",", client.getClientRegistration().getScopes()));
        oauthAccount.setUpdatedAt(LocalDateTime.now());
        if (oauthAccount.getCreatedAt() == null) {
            oauthAccount.setCreatedAt(LocalDateTime.now());
        }

        oauthAccountRepository.save(oauthAccount);

        connectRepo.deleteById(state);
        clearCookie(response, "oauth_connect_state");

        log.info("OAuth2 connect completed: userId={} provider={}", user.getId(), providerKey);
        response.sendRedirect(frontendProperties.getSuccessRedirectUrl()
                + "?provider=" + providerKey + "&status=connected");
    }

    private void redirectError(HttpServletResponse response, String provider, String status)
            throws IOException {
        response.sendRedirect(frontendProperties.getSuccessRedirectUrl()
                + "?provider=" + provider + "&status=" + status);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return Optional.ofNullable(c.getValue());
        }
        return Optional.empty();
    }

    private void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
