package com.example.marketing.oauth.service;

import com.example.marketing.auth.AdAccountConnectionEntity;
import com.example.marketing.auth.AdAccountConnectionRepository;
import com.example.marketing.auth.JwtService;
import com.example.marketing.auth.OAuthConnectRequestRepository;
import com.example.marketing.oauth.config.FrontendProperties;
import com.example.marketing.oauth.config.MetaOAuthProperties;
import com.example.marketing.oauth.dto.*;
import com.example.marketing.oauth.entity.OAuthAccountEntity;
import com.example.marketing.oauth.entity.OAuthConnectRequestEntity;
import com.example.marketing.oauth.entity.TokenStatus;
import com.example.marketing.oauth.repository.OAuthAccountRepository;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaOAuthService {

    private static final Logger log = LoggerFactory.getLogger(MetaOAuthService.class);

    private final MetaOAuthProperties metaProps;
    private final FrontendProperties frontendProps;
    private final OAuthConnectRequestRepository connectRequestRepo;
    private final OAuthAccountRepository oauthAccountRepo;
    private final AdAccountConnectionRepository adAccountConnectionRepo;
    private final UserRepository userRepository;
    private final RestClient restClient;
    private final JwtService jwtService;

    // ─── STEP 1: initiate connect ──────────────────────────────────────────

    @Transactional
    public OAuthConnectResponse initiateConnect(Long userId) {
        connectRequestRepo.findPendingByUserId(userId, LocalDateTime.now()).ifPresent(existing -> {
            existing.setConsumed(true);
            existing.setFailureReason("superseded by new connect request");
            connectRequestRepo.save(existing);
        });

        String state = UUID.randomUUID().toString();

        OAuthConnectRequestEntity request = new OAuthConnectRequestEntity();
        request.setState(state);
        request.setUserId(userId);
        request.setProvider("META");
        request.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        request.setConsumed(false);
        request.setCompleted(false);
        request.setCreatedAt(LocalDateTime.now());
        connectRequestRepo.save(request);

        return new OAuthConnectResponse(buildAuthorizationUrl(state));
    }

    private String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder
                .fromHttpUrl("https://www.facebook.com/dialog/oauth")
                .queryParam("client_id", metaProps.getClientId())
                .queryParam("redirect_uri", metaProps.getRedirectUri())
                .queryParam("scope", metaProps.getScopes())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    // ─── STEP 2: handle callback ───────────────────────────────────────────

    public String handleCallback(String code, String state, String error) {
        if (error != null) {
            return buildFailureRedirect();
        }

        if (code == null || state == null) {
            return buildFailureRedirect();
        }

        OAuthConnectRequestEntity connectRequest = connectRequestRepo
                .findByState(state)
                .orElse(null);

        if (connectRequest == null) {
            return buildFailureRedirect();
        }
        if (connectRequest.isConsumed()) {
            return buildFailureRedirect();
        }
        if (connectRequest.getExpiresAt().isBefore(LocalDateTime.now())) {
            markRequestFailed(connectRequest, "state_expired");
            return buildFailureRedirect();
        }

        // mark consumed immediately — single-use, before any API calls
        connectRequest.setConsumed(true);
        connectRequestRepo.save(connectRequest);

        // detect whether this user already had a Meta connection before this callback
        boolean isReconnecting = oauthAccountRepo
                .findByUserIdAndProvider(connectRequest.getUserId(), "META")
                .isPresent();

        try {
            MetaTokenResponse shortLivedToken = exchangeCodeForToken(code);
            MetaTokenResponse longLivedToken = exchangeForLongLivedToken(shortLivedToken.accessToken());

            long expiresInSeconds = longLivedToken.expiresIn() != null
                    ? longLivedToken.expiresIn()
                    : 5_184_000L; // fallback: 60 days
            LocalDateTime tokenExpiry = LocalDateTime.now().plusSeconds(expiresInSeconds);

            MetaUserInfo userInfo = fetchMetaUserInfo(longLivedToken.accessToken());

            upsertOAuthAccount(connectRequest.getUserId(), userInfo.id(),
                    longLivedToken.accessToken(), tokenExpiry);

            List<MetaAdAccount> adAccounts = fetchAdAccounts(longLivedToken.accessToken());
            upsertAdAccountConnections(connectRequest.getUserId(), adAccounts);

            connectRequest.setCompleted(true);
            connectRequestRepo.save(connectRequest);

            if (isReconnecting) {
                return buildSuccessRedirect(null, null);
            }

            UserEntity user = userRepository.findById(connectRequest.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + connectRequest.getUserId()));
            String jwt = jwtService.generateToken(user.getUsername(), user.getRole().name(), user.getId());
            return buildSuccessRedirect(jwt, user.getId());

        } catch (Exception ex) {
            log.error("Meta OAuth callback failed for state={}: {}", state, ex.getMessage(), ex);
            markRequestFailed(connectRequest, ex.getMessage());
            return buildFailureRedirect();
        }
    }

    // ─── Token exchange ────────────────────────────────────────────────────

    private MetaTokenResponse exchangeCodeForToken(String code) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphApiBaseUrl() + "/oauth/access_token")
                .queryParam("client_id", metaProps.getClientId())
                .queryParam("client_secret", metaProps.getClientSecret())
                .queryParam("redirect_uri", metaProps.getRedirectUri())
                .queryParam("code", code)
                .build().toUriString();

        return restClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new RuntimeException("Meta token exchange failed: " + res.getStatusCode());
                })
                .body(MetaTokenResponse.class);
    }

    private MetaTokenResponse exchangeForLongLivedToken(String shortLivedToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(metaProps.getGraphApiBaseUrl() + "/oauth/access_token")
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", metaProps.getClientId())
                .queryParam("client_secret", metaProps.getClientSecret())
                .queryParam("fb_exchange_token", shortLivedToken)
                .build().toUriString();

        return restClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new RuntimeException("Meta long-lived token exchange failed");
                })
                .body(MetaTokenResponse.class);
    }

    // ─── Meta API calls ────────────────────────────────────────────────────

    private MetaUserInfo fetchMetaUserInfo(String accessToken) {
        String url = metaProps.getGraphApiBaseUrl()
                + "/" + metaProps.getGraphApiVersion()
                + "/me?fields=id,name&access_token=" + accessToken;

        return restClient.get()
                .uri(url)
                .retrieve()
                .body(MetaUserInfo.class);
    }

    private List<MetaAdAccount> fetchAdAccounts(String accessToken) {
        String url = metaProps.getGraphApiBaseUrl()
                + "/" + metaProps.getGraphApiVersion()
                + "/me/adaccounts"
                + "?fields=id,name,account_id,account_status,currency,timezone_name,business"
                + "&access_token=" + accessToken;

        MetaAdAccountListResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(MetaAdAccountListResponse.class);

        return response != null && response.data() != null ? response.data() : List.of();
    }

    // ─── Upsert local entities ─────────────────────────────────────────────

    private void upsertOAuthAccount(Long userId, String metaUserId,
                                     String accessToken, LocalDateTime tokenExpiry) {
        OAuthAccountEntity account = oauthAccountRepo
                .findByUserIdAndProvider(userId, "META")
                .orElseGet(OAuthAccountEntity::new);

        UserEntity user = userRepository.getReferenceById(userId);
        account.setUser(user);
        account.setProvider("META");
        account.setExternalUserId(metaUserId);
        account.setAccessToken(accessToken);
        account.setTokenExpiry(tokenExpiry);
        account.setTokenStatus(TokenStatus.VALID);
        account.setGrantedScopes(metaProps.getScopes());
        account.setUpdatedAt(LocalDateTime.now());
        if (account.getId() == null) {
            account.setCreatedAt(LocalDateTime.now());
        }

        oauthAccountRepo.save(account);
    }

    @Transactional
    public void upsertAdAccountConnections(Long userId, List<MetaAdAccount> adAccounts) {
        List<AdAccountConnectionEntity> existing =
                adAccountConnectionRepo.findAllByUserIdAndProvider(userId, "META");

        Set<String> returnedIds = adAccounts.stream()
                .map(MetaAdAccount::id)
                .collect(Collectors.toSet());

        // mark stale connections inactive
        existing.stream()
                .filter(c -> !returnedIds.contains(c.getAdAccountId()))
                .forEach(c -> {
                    c.setActive(false);
                    adAccountConnectionRepo.save(c);
                });

        // upsert each returned ad account
        for (MetaAdAccount adAccount : adAccounts) {
            AdAccountConnectionEntity conn = existing.stream()
                    .filter(c -> c.getAdAccountId().equals(adAccount.id()))
                    .findFirst()
                    .orElseGet(AdAccountConnectionEntity::new);

            conn.setUserId(userId);
            conn.setProvider("META");
            conn.setAdAccountId(adAccount.id());
            conn.setAdAccountName(adAccount.name());
            conn.setAccountStatus(adAccount.accountStatus());
            conn.setCurrency(adAccount.currency());
            conn.setTimezoneName(adAccount.timezoneName());
            if (adAccount.business() != null) {
                conn.setBusinessId(adAccount.business().id());
            }
            conn.setActive(true);
            conn.setUpdatedAt(LocalDateTime.now());
            if (conn.getId() == null) {
                conn.setCreatedAt(LocalDateTime.now());
            }
            adAccountConnectionRepo.save(conn);
        }
    }

    // ─── Disconnect ────────────────────────────────────────────────────────

    @Transactional
    public void disconnect(Long userId) {
        List<AdAccountConnectionEntity> connections =
                adAccountConnectionRepo.findAllByUserIdAndProvider(userId, "META");
        connections.forEach(c -> c.setActive(false));
        adAccountConnectionRepo.saveAll(connections);

        oauthAccountRepo.findByUserIdAndProvider(userId, "META")
                .ifPresent(oauthAccountRepo::delete);
    }

    // ─── Ad account connections list ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdAccountConnectionEntity> getActiveConnections(Long userId) {
        return adAccountConnectionRepo.findAllByUserIdAndActive(userId, true);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private void markRequestFailed(OAuthConnectRequestEntity req, String reason) {
        req.setConsumed(true);
        req.setCompleted(false);
        req.setFailureReason(reason);
        connectRequestRepo.save(req);
    }

    private String buildSuccessRedirect(String jwt, Long userId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(frontendProps.getSuccessRedirectUrl());
        if (jwt != null && userId != null) {
            builder.queryParam("token", jwt)
                   .queryParam("userId", userId);
        }
        return builder.build().toUriString();
    }

    private String buildFailureRedirect() {
        return UriComponentsBuilder
                .fromHttpUrl(frontendProps.getLoginUrl())
                .queryParam("error", "oauth_failed")
                .build().toUriString();
    }
}
