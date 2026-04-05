package com.example.marketing.oauth.handler;

import com.example.marketing.infrastructure.util.Provider;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthConnectRequestRepository connectRepo;
    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final TokenExchangeService tokenExchangeService;

    @Value("${app.frontend.oauth-success-url:http://localhost:5000/oauth-success}")
    private String frontendSuccessUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "facebook"
        String providerKey = "META"; // ✅ your DB provider is META, not FACEBOOK

        // ✅ If missing cookie, this was NOT a "connect" flow (user clicked OAuth directly)
        Optional<String> stateOpt = readCookie(request, "oauth_connect_state");
        if (stateOpt.isEmpty()) {
            response.sendRedirect(frontendSuccessUrl + "?provider=" + providerKey + "&status=missing_state");
            return;
        }

        String state = stateOpt.get();

        OAuthConnectRequestEntity connectReq =
                connectRepo.findByStateAndProvider(state, providerKey)
                        .orElseThrow(() -> new RuntimeException("Invalid connect state"));

        if (connectReq.getExpiresAt().isBefore(LocalDateTime.now())) {
            connectRepo.deleteById(state);
            clearCookie(response, "oauth_connect_state");
            response.sendRedirect(frontendSuccessUrl + "?provider=" + providerKey + "&status=state_expired");
            return;
        }

        Long userId = connectReq.getUserId();

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found for connect request"));

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                registrationId,
                oauthToken.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            throw new RuntimeException("OAuth2AuthorizedClient missing access token");
        }

        String shortLivedToken = client.getAccessToken().getTokenValue();

        // ✅ exchange token (Meta short -> long)
        TokenExchangeService.TokenExchangeResult longToken =
                tokenExchangeService.exchangeToLongLived(Provider.META, shortLivedToken);

        OAuthAccountEntity oauthAccount = oauthAccountRepository
                .findByUserAndProvider(user, providerKey)
                .orElse(new OAuthAccountEntity());

        oauthAccount.setUser(user);
        oauthAccount.setProvider(providerKey);
        oauthAccount.setAccessToken(longToken.accessToken());
        oauthAccount.setTokenExpiry(longToken.expiresAt());

        // ✅ For Meta this should be the Meta user ID (best effort)
        oauthAccount.setExternalUserId(oauthToken.getName());

        // ✅ Save granted scopes (you can replace later with real granted scopes)
        String scopes = String.join(",", client.getClientRegistration().getScopes());
        oauthAccount.setGrantedScopes(scopes);

        oauthAccount.setUpdatedAt(LocalDateTime.now());
        if (oauthAccount.getCreatedAt() == null) {
            oauthAccount.setCreatedAt(LocalDateTime.now());
        }

        oauthAccountRepository.save(oauthAccount);

        // ✅ cleanup connect request + cookie (always)
        connectRepo.deleteById(state);
        clearCookie(response, "oauth_connect_state");

        response.sendRedirect(frontendSuccessUrl + "?provider=" + providerKey + "&status=connected");
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
