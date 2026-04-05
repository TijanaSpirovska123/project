# Meta OAuth Account Linking — Implementation Specification

> **Scope:** This document covers only the Meta OAuth account-linking flow.
> It does not duplicate material from USE_CASES_AND_FEATURES_V2.md.
> UC-2.1, UC-2.2, UC-2.3 in that document describe *what* this feature does.
> This document describes *how* to implement it correctly.

---

## Why the Current Approach Is Wrong

The current backend returns `/oauth2/authorization/facebook` as the authorization URL.
This means Spring Security's generic `oauth2Login()` entrypoint is handling the redirect —
a flow designed for **logging users into the app**, not for **linking a third-party account
to an already-authenticated user**.

Problems with the hybrid approach:
- State is managed by Spring Security internally, not by your `OAuthConnectRequestEntity`
- The callback lands at `/login/oauth2/code/facebook` which Spring Security intercepts first
- There is no reliable way to know *which local user* initiated the OAuth flow in the callback
- Cookie-based state is secondary to DB-backed state and should not be the source of truth
- Reconnect (updating an existing token) is not idempotent in the current setup

**The correct approach:** Backend fully owns the connect flow end-to-end.
Spring Security oauth2Login is not used for this feature at all.

---

## Architecture Overview

```
User (browser)                  Angular Frontend              Spring Boot Backend          Meta API
     │                               │                              │                        │
     │  clicks "Connect Meta"        │                              │                        │
     │──────────────────────────────>│                              │                        │
     │                               │  POST /oauth/meta/connect    │                        │
     │                               │  { Authorization: Bearer }   │                        │
     │                               │─────────────────────────────>│                        │
     │                               │                              │ generate UUID state     │
     │                               │                              │ persist OAuthConnectReq │
     │                               │                              │ build Meta auth URL     │
     │                               │  { authorizationUrl }        │                        │
     │                               │<─────────────────────────────│                        │
     │  window.location.href = url   │                              │                        │
     │<──────────────────────────────│                              │                        │
     │                                                              │                        │
     │  GET https://facebook.com/dialog/oauth?...&state=UUID        │                        │
     │─────────────────────────────────────────────────────────────────────────────────────>│
     │  user grants permissions                                      │                        │
     │<─────────────────────────────────────────────────────────────────────────────────────│
     │                                                              │                        │
     │  GET /oauth/meta/callback?code=XXX&state=UUID                │                        │
     │─────────────────────────────────────────────────────────────>│                        │
     │                               │                              │ validate state (DB)     │
     │                               │                              │ exchange code→token     │
     │                               │                              │─────────────────────────>
     │                               │                              │ fetch /me/adaccounts   │
     │                               │                              │<─────────────────────────
     │                               │                              │ upsert OAuthAccount     │
     │                               │                              │ upsert AdAccountConns   │
     │                               │                              │ mark request completed  │
     │  redirect → /sync-accounts?status=connected                  │                        │
     │<─────────────────────────────────────────────────────────────│                        │
     │──────────────────────────────>│                              │                        │
     │                               │  GET /api/ad-account-connections                      │
     │                               │─────────────────────────────>│                        │
     │                               │  [{ provider: META, connected: true, ... }]           │
     │                               │<─────────────────────────────│                        │
     │  card shows Connected ✓       │                              │                        │
```

---

## Backend Implementation

### 1. Configuration Properties

```yaml
# application.yml
meta:
  client-id: ${META_CLIENT_ID}
  client-secret: ${META_CLIENT_SECRET}
  redirect-uri: ${META_REDIRECT_URI:http://localhost:8080/oauth/meta/callback}
  scopes: ads_read,ads_management,pages_read_engagement,pages_show_list
  graph-api-version: v23.0
  graph-api-base-url: https://graph.facebook.com

frontend:
  success-redirect-url: ${FRONTEND_SUCCESS_URL:http://localhost:5000/sync-accounts}
  failure-redirect-url: ${FRONTEND_FAILURE_URL:http://localhost:5000/sync-accounts}
```

```java
@ConfigurationProperties(prefix = "meta")
@Component
public class MetaOAuthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scopes;
    private String graphApiVersion;
    private String graphApiBaseUrl;
    // getters + setters
}

@ConfigurationProperties(prefix = "frontend")
@Component
public class FrontendProperties {
    private String successRedirectUrl;
    private String failureRedirectUrl;
    // getters + setters
}
```

---

### 2. OAuthConnectRequestEntity — Required Fields

Ensure the entity has all of these fields. Add any that are missing via Liquibase migration:

```java
@Entity
@Table(name = "oauth_connect_request")
public class OAuthConnectRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String state;              // UUID, the state param sent to Meta

    @Column(nullable = false)
    private Long userId;               // the local user who initiated this request

    @Column(nullable = false)
    private String provider;           // "META"

    @Column(nullable = false)
    private Instant expiresAt;         // 10 minutes from creation

    @Column(nullable = false)
    private boolean consumed;          // true after callback is processed (single-use)

    @Column(nullable = false)
    private boolean completed;         // true if callback succeeded

    @Column
    private String failureReason;      // populated if callback failed

    @Column(nullable = false)
    private Instant createdAt;
}
```

**Liquibase migration if fields are missing:**
```xml
<changeSet id="add-oauth-connect-request-fields" author="tijana">
    <addColumn tableName="oauth_connect_request">
        <column name="consumed" type="BOOLEAN" defaultValueBoolean="false">
            <constraints nullable="false"/>
        </column>
        <column name="completed" type="BOOLEAN" defaultValueBoolean="false">
            <constraints nullable="false"/>
        </column>
        <column name="failure_reason" type="VARCHAR(500)"/>
    </addColumn>
</changeSet>
```

---

### 3. OAuthConnectRequestRepository

```java
public interface OAuthConnectRequestRepository
        extends JpaRepository<OAuthConnectRequestEntity, Long> {

    Optional<OAuthConnectRequestEntity> findByState(String state);

    // clean up expired requests (run via scheduler)
    @Modifying
    @Query("DELETE FROM OAuthConnectRequestEntity r WHERE r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
```

---

### 4. DTOs

```java
// Response to frontend after POST /oauth/meta/connect
public record OAuthConnectResponse(String authorizationUrl) {}

// Internal: result of token exchange with Meta
public record MetaTokenResponse(
    String accessToken,
    String tokenType,
    Long expiresIn          // seconds, present for long-lived tokens
) {}

// Internal: Meta /me response
public record MetaUserInfo(
    String id,              // Meta user ID
    String name
) {}

// Internal: one ad account from /me/adaccounts
public record MetaAdAccount(
    String id,              // "act_1234567890"
    String name,
    Integer accountStatus,
    String currency,
    String timezoneName,
    MetaBusiness business   // nullable
) {}

public record MetaBusiness(String id, String name) {}
```

---

### 5. MetaOAuthService

```java
@Service
@RequiredArgsConstructor
public class MetaOAuthService {

    private final MetaOAuthProperties metaProps;
    private final FrontendProperties frontendProps;
    private final OAuthConnectRequestRepository connectRequestRepo;
    private final OAuthAccountRepository oauthAccountRepo;
    private final AdAccountConnectionRepository adAccountConnectionRepo;
    private final RestClient restClient;

    // ─── STEP 1: initiate connect ──────────────────────────────────────────

    @Transactional
    public OAuthConnectResponse initiateConnect(Long userId) {
        // invalidate any pending (not yet consumed) requests for this user
        // to avoid stale state accumulation
        connectRequestRepo.findPendingByUserId(userId).ifPresent(existing -> {
            existing.setConsumed(true);
            existing.setFailureReason("superseded by new connect request");
            connectRequestRepo.save(existing);
        });

        String state = UUID.randomUUID().toString();

        OAuthConnectRequestEntity request = new OAuthConnectRequestEntity();
        request.setState(state);
        request.setUserId(userId);
        request.setProvider("META");
        request.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        request.setConsumed(false);
        request.setCompleted(false);
        request.setCreatedAt(Instant.now());
        connectRequestRepo.save(request);

        String authorizationUrl = buildAuthorizationUrl(state);
        return new OAuthConnectResponse(authorizationUrl);
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

    @Transactional
    public String handleCallback(String code, String state, String error) {

        // user denied permissions
        if (error != null) {
            return buildFailureRedirect("user_denied");
        }

        // missing params
        if (code == null || state == null) {
            return buildFailureRedirect("missing_params");
        }

        // validate state from DB
        OAuthConnectRequestEntity connectRequest = connectRequestRepo
            .findByState(state)
            .orElse(null);

        if (connectRequest == null) {
            return buildFailureRedirect("invalid_state");
        }
        if (connectRequest.isConsumed()) {
            return buildFailureRedirect("state_already_used");
        }
        if (connectRequest.getExpiresAt().isBefore(Instant.now())) {
            markRequestFailed(connectRequest, "state_expired");
            return buildFailureRedirect("state_expired");
        }

        // mark consumed immediately — single-use, do this before any API calls
        connectRequest.setConsumed(true);
        connectRequestRepo.save(connectRequest);

        try {
            // exchange code for short-lived token
            MetaTokenResponse shortLivedToken = exchangeCodeForToken(code);

            // exchange short-lived for long-lived token (60 days)
            MetaTokenResponse longLivedToken = exchangeForLongLivedToken(
                shortLivedToken.accessToken()
            );

            Instant tokenExpiry = Instant.now()
                .plus(longLivedToken.expiresIn() != null
                    ? longLivedToken.expiresIn()
                    : 5_184_000L,   // fallback: 60 days in seconds
                    ChronoUnit.SECONDS);

            // fetch Meta user info
            MetaUserInfo userInfo = fetchMetaUserInfo(longLivedToken.accessToken());

            // upsert OAuthAccountEntity
            upsertOAuthAccount(
                connectRequest.getUserId(),
                userInfo.id(),
                longLivedToken.accessToken(),
                tokenExpiry
            );

            // fetch and upsert ad accounts
            List<MetaAdAccount> adAccounts = fetchAdAccounts(longLivedToken.accessToken());
            upsertAdAccountConnections(connectRequest.getUserId(), adAccounts);

            // mark request completed
            connectRequest.setCompleted(true);
            connectRequestRepo.save(connectRequest);

            return buildSuccessRedirect();

        } catch (Exception ex) {
            markRequestFailed(connectRequest, ex.getMessage());
            return buildFailureRedirect("internal_error");
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

        // Meta returns { data: [...] }
        MetaAdAccountListResponse response = restClient.get()
            .uri(url)
            .retrieve()
            .body(MetaAdAccountListResponse.class);

        return response != null && response.data() != null
            ? response.data()
            : List.of();
    }

    // ─── Upsert local entities ─────────────────────────────────────────────

    private void upsertOAuthAccount(Long userId, String metaUserId,
                                     String accessToken, Instant tokenExpiry) {
        OAuthAccountEntity account = oauthAccountRepo
            .findByUserIdAndProvider(userId, "META")
            .orElseGet(OAuthAccountEntity::new);

        account.setUserId(userId);
        account.setProvider("META");
        account.setExternalUserId(metaUserId);
        account.setAccessToken(accessToken);
        account.setTokenExpiry(tokenExpiry);
        account.setGrantedScopes(metaProps.getScopes());
        account.setUpdatedAt(Instant.now());
        if (account.getId() == null) {
            account.setCreatedAt(Instant.now());
        }

        oauthAccountRepo.save(account);
    }

    @Transactional
    private void upsertAdAccountConnections(Long userId,
                                             List<MetaAdAccount> adAccounts) {
        // fetch all existing connections for this user+provider
        List<AdAccountConnectionEntity> existing =
            adAccountConnectionRepo.findByUserIdAndProvider(userId, "META");

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
            conn.setUpdatedAt(Instant.now());
            if (conn.getId() == null) {
                conn.setCreatedAt(Instant.now());
            }
            adAccountConnectionRepo.save(conn);
        }
    }

    // ─── Disconnect ────────────────────────────────────────────────────────

    @Transactional
    public void disconnect(Long userId) {
        // deactivate all ad account connections (do not delete — preserve history)
        List<AdAccountConnectionEntity> connections =
            adAccountConnectionRepo.findByUserIdAndProvider(userId, "META");
        connections.forEach(c -> c.setActive(false));
        adAccountConnectionRepo.saveAll(connections);

        // remove oauth account (token no longer valid after disconnect)
        oauthAccountRepo.findByUserIdAndProvider(userId, "META")
            .ifPresent(oauthAccountRepo::delete);

        // optional: call Meta to revoke the token
        // DELETE /{user-id}/permissions with the access token
        // skip gracefully if it fails — local cleanup still happens
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private void markRequestFailed(OAuthConnectRequestEntity req, String reason) {
        req.setConsumed(true);
        req.setCompleted(false);
        req.setFailureReason(reason);
        connectRequestRepo.save(req);
    }

    private String buildSuccessRedirect() {
        return UriComponentsBuilder
            .fromHttpUrl(frontendProps.getSuccessRedirectUrl())
            .queryParam("status", "connected")
            .queryParam("provider", "META")
            .build().toUriString();
    }

    private String buildFailureRedirect(String reason) {
        return UriComponentsBuilder
            .fromHttpUrl(frontendProps.getFailureRedirectUrl())
            .queryParam("status", "error")
            .queryParam("provider", "META")
            .queryParam("reason", reason)
            .build().toUriString();
    }
}
```

---

### 6. OAuthController

```java
@RestController
@RequestMapping("/oauth/meta")
@RequiredArgsConstructor
public class MetaOAuthController {

    private final MetaOAuthService metaOAuthService;

    /**
     * Called by the frontend when user clicks "Connect Meta".
     * Returns the full Meta authorization URL.
     * Frontend must perform a full-page browser redirect to this URL.
     */
    @PostMapping("/connect")
    public ResponseEntity<OAuthConnectResponse> initiateConnect(
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        OAuthConnectResponse response = metaOAuthService.initiateConnect(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Meta redirects here after the user grants or denies permissions.
     * This endpoint is public (no JWT) — the state param ties it to the local user.
     * Always responds with a redirect to the frontend (never returns JSON).
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
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        Long userId = extractUserId(authentication);
        metaOAuthService.disconnect(userId);
        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(Authentication authentication) {
        // adapt to however your JWT/UserDetails stores the user ID
        // e.g. ((CustomUserDetails) authentication.getPrincipal()).getUserId()
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return Long.parseLong(userDetails.getUsername()); // or your custom method
    }
}
```

---

### 7. Security Configuration — Permit the Callback

The callback endpoint must be public (no JWT), because Meta redirects to it directly.
Everything else stays protected.

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
        // public routes
        .requestMatchers("/api/auth/**", "/oauth/meta/callback").permitAll()
        // everything else requires JWT
        .anyRequest().authenticated()
    );
    // your existing JWT filter configuration...
    return http.build();
}
```

**Important:** Do NOT add any `oauth2Login()` configuration for this flow.

---

### 8. AdAccountConnectionRepository — Extra Methods Needed

```java
public interface AdAccountConnectionRepository
        extends JpaRepository<AdAccountConnectionEntity, Long> {

    List<AdAccountConnectionEntity> findByUserIdAndProvider(Long userId, String provider);

    // used by GET /api/ad-account-connections (status check by frontend)
    List<AdAccountConnectionEntity> findByUserIdAndActive(Long userId, boolean active);

    Optional<AdAccountConnectionEntity> findByUserIdAndProviderAndAdAccountId(
        Long userId, String provider, String adAccountId);
}
```

---

### 9. AdAccountConnectionController — Status Endpoint

```java
@GetMapping("/api/ad-account-connections")
public ResponseEntity<List<AdAccountConnectionSummary>> getConnections(
        Authentication authentication) {

    Long userId = extractUserId(authentication);
    List<AdAccountConnectionEntity> connections =
        adAccountConnectionRepo.findByUserIdAndActive(userId, true);

    // group by provider and return one entry per platform
    Map<String, AdAccountConnectionSummary> byProvider = connections.stream()
        .collect(Collectors.groupingBy(
            AdAccountConnectionEntity::getProvider,
            Collectors.collectingAndThen(
                Collectors.toList(),
                list -> new AdAccountConnectionSummary(
                    list.get(0).getProvider(),
                    true,
                    list.get(0).getUpdatedAt()  // last synced approximation
                )
            )
        ));

    // also include disconnected platforms as { connected: false }
    List<String> allPlatforms = List.of("META", "TIKTOK", "PINTEREST", "GOOGLE", "LINKEDIN", "REDDIT");
    List<AdAccountConnectionSummary> result = allPlatforms.stream()
        .map(p -> byProvider.getOrDefault(p,
            new AdAccountConnectionSummary(p, false, null)))
        .toList();

    return ResponseEntity.ok(result);
}

public record AdAccountConnectionSummary(
    String provider,
    boolean connected,
    Instant lastSynced
) {}
```

---

### 10. Reconnect Behaviour (Idempotency)

Reconnect uses the exact same `POST /oauth/meta/connect` → callback flow.
It is idempotent because:
- `upsertOAuthAccount` uses `findByUserIdAndProvider` → updates existing row if found, creates if not
- `upsertAdAccountConnections` loops through returned accounts → updates existing by `adAccountId`, creates new ones, deactivates any that are no longer returned
- No duplicate rows are created on repeated connect/reconnect

---

### 11. State Lifecycle Summary

```
POST /oauth/meta/connect
  → state generated (UUID)
  → OAuthConnectRequestEntity saved: consumed=false, completed=false

GET /oauth/meta/callback?code=X&state=UUID
  → find by state (must exist, not consumed, not expired)
  → set consumed=true IMMEDIATELY (before any API call)
  → exchange code for token (may fail)
  → if success: set completed=true
  → if failure: leave completed=false, set failureReason

Any second call with the same state:
  → consumed=true → reject with "state_already_used"
```

---

## Frontend Implementation

### 1. Typed Response Model

```typescript
// models/oauth.model.ts
export interface OAuthConnectResponse {
  authorizationUrl: string;
}

export interface AdAccountConnectionSummary {
  provider: 'META' | 'TIKTOK' | 'PINTEREST' | 'GOOGLE' | 'LINKEDIN' | 'REDDIT';
  connected: boolean;
  lastSynced: string | null;
}
```

---

### 2. OAuthService

```typescript
// services/oauth/oauth.service.ts
@Injectable({ providedIn: 'root' })
export class OAuthService extends CoreService {

  initiateMetaConnect(): Observable<OAuthConnectResponse> {
    return this.post<OAuthConnectResponse>('/oauth/meta/connect', {});
  }

  disconnectMeta(): Observable<void> {
    return this.delete<void>('/oauth/meta/disconnect');
  }

  getConnectionStatus(): Observable<AdAccountConnectionSummary[]> {
    return this.getAll<AdAccountConnectionSummary[]>('/api/ad-account-connections');
  }
}
```

---

### 3. SyncAccountsComponent — Connect/Disconnect Logic

```typescript
// sync-accounts.component.ts
@Component({ ... })
export class SyncAccountsComponent implements OnInit {

  connections: AdAccountConnectionSummary[] = [];
  connectingPlatform: string | null = null;  // which platform is mid-connect
  disconnecting = false;

  constructor(
    private oauthService: OAuthService,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.loadConnectionStatus();
  }

  loadConnectionStatus(): void {
    this.oauthService.getConnectionStatus().subscribe({
      next: connections => this.connections = connections,
      error: () => this.toastr.error('Failed to load connection status')
    });
  }

  isConnected(provider: string): boolean {
    return this.connections.find(c => c.provider === provider)?.connected ?? false;
  }

  lastSynced(provider: string): string | null {
    return this.connections.find(c => c.provider === provider)?.lastSynced ?? null;
  }

  connectMeta(): void {
    if (this.connectingPlatform) return;  // prevent double-click

    this.connectingPlatform = 'META';

    this.oauthService.initiateMetaConnect().pipe(
      finalize(() => this.connectingPlatform = null)
    ).subscribe({
      next: response => {
        // full-page browser redirect — Angular takes no further action
        window.location.href = response.authorizationUrl;
      },
      error: err => {
        this.toastr.error('Could not start Meta connection. Please try again.');
        // connectingPlatform cleared by finalize()
      }
    });
  }

  disconnectMeta(): void {
    if (!confirm('Disconnect Meta? Your synced data will remain but you won\'t be able to sync new data.')) {
      return;
    }

    this.disconnecting = true;

    this.oauthService.disconnectMeta().pipe(
      finalize(() => this.disconnecting = false)
    ).subscribe({
      next: () => {
        this.toastr.success('Meta account disconnected');
        this.loadConnectionStatus();  // refresh card state
      },
      error: () => this.toastr.error('Failed to disconnect Meta account')
    });
  }
}
```

---

### 4. Template — Meta Platform Card

```html
<!-- sync-accounts.component.html (Meta card only) -->
<div class="platform-card"
     [class.connected]="isConnected('META')"
     [class.connecting]="connectingPlatform === 'META'">

  <!-- platform logo + name -->
  <div class="card-header">
    <img src="assets/icons/meta.svg" alt="Meta" class="platform-icon">
    <span class="platform-name">Meta</span>

    <!-- connected badge -->
    <span *ngIf="isConnected('META')" class="badge-connected">
      ✓ Connected
    </span>
  </div>

  <!-- last synced -->
  <p *ngIf="isConnected('META') && lastSynced('META')" class="last-synced">
    Last synced {{ lastSynced('META') | timeAgo }}
  </p>

  <!-- actions -->
  <div class="card-actions">

    <!-- connect button (shown when not connected) -->
    <button
      *ngIf="!isConnected('META')"
      class="btn btn-outline"
      [disabled]="connectingPlatform === 'META'"
      (click)="connectMeta()">
      <span *ngIf="connectingPlatform !== 'META'">
        <i class="fa fa-link"></i> Connect
      </span>
      <span *ngIf="connectingPlatform === 'META'">
        <i class="fa fa-spinner fa-spin"></i> Connecting…
      </span>
    </button>

    <!-- reconnect button (shown when connected) -->
    <button
      *ngIf="isConnected('META')"
      class="btn btn-outline"
      [disabled]="connectingPlatform === 'META'"
      (click)="connectMeta()">
      <span *ngIf="connectingPlatform !== 'META'">Reconnect</span>
      <span *ngIf="connectingPlatform === 'META'">
        <i class="fa fa-spinner fa-spin"></i> Connecting…
      </span>
    </button>

    <!-- sync button (only enabled when connected) -->
    <button
      class="btn btn-primary"
      [disabled]="!isConnected('META') || syncInProgress"
      [matTooltip]="!isConnected('META') ? 'Connect your account first' : ''"
      (click)="syncData('META')">
      <i class="fa fa-sync" [class.fa-spin]="syncInProgress"></i>
      {{ syncInProgress ? 'Syncing…' : 'Sync Data' }}
    </button>

    <!-- disconnect option (shown when connected) -->
    <button
      *ngIf="isConnected('META')"
      class="btn btn-ghost btn-danger"
      [disabled]="disconnecting"
      (click)="disconnectMeta()">
      {{ disconnecting ? 'Disconnecting…' : 'Disconnect' }}
    </button>

  </div>
</div>
```

---

### 5. OAuth Callback Landing Page

The backend redirects to `/sync-accounts?status=connected&provider=META`
or `/sync-accounts?status=error&provider=META&reason=user_denied`.

Handle this in `SyncAccountsComponent.ngOnInit()`:

```typescript
ngOnInit(): void {
  // handle OAuth return
  const params = new URLSearchParams(window.location.search);
  const status = params.get('status');
  const provider = params.get('provider');
  const reason = params.get('reason');

  if (status === 'connected' && provider === 'META') {
    this.toastr.success('Meta account connected successfully!');
    // clean URL (remove query params without page reload)
    window.history.replaceState({}, '', '/sync-accounts');
  } else if (status === 'error') {
    const message = this.getErrorMessage(reason);
    this.toastr.error(message);
    window.history.replaceState({}, '', '/sync-accounts');
  }

  // always reload connection status on page init
  this.loadConnectionStatus();
}

private getErrorMessage(reason: string | null): string {
  const messages: Record<string, string> = {
    user_denied:       'You cancelled the Meta connection.',
    state_expired:     'The connection request expired. Please try again.',
    state_already_used:'This connection link has already been used. Please try again.',
    invalid_state:     'Invalid connection request. Please try again.',
    internal_error:    'Something went wrong connecting to Meta. Please try again.',
    missing_params:    'The connection was interrupted. Please try again.'
  };
  return messages[reason ?? ''] ?? 'Failed to connect to Meta. Please try again.';
}
```

---

### 6. Routing — No New Routes Needed

The backend redirects to `/sync-accounts` (which already exists) with query params.
`SyncAccountsComponent.ngOnInit()` reads the params and shows the appropriate toast.

If you prefer a dedicated success/error page instead, add to `app.routes.ts`:

```typescript
{
  path: 'oauth-success',
  component: OAuthSuccessComponent,
  canActivate: [authGuard]
}
```

And in `FrontendProperties`:
```yaml
frontend:
  success-redirect-url: http://localhost:5000/oauth-success
```

```typescript
// oauth-success.component.ts
@Component({ ... })
export class OAuthSuccessComponent implements OnInit {
  ngOnInit(): void {
    const params = new URLSearchParams(window.location.search);
    const status = params.get('status');
    // show message, then navigate to /sync-accounts after 2 seconds
    setTimeout(() => this.router.navigate(['/sync-accounts']), 2000);
  }
}
```

---

## Error Handling Summary

| Scenario | Backend response | Frontend result |
|---|---|---|
| User denies permissions | redirect → `?status=error&reason=user_denied` | Toast: "You cancelled the Meta connection" |
| State expired (>10 min) | redirect → `?status=error&reason=state_expired` | Toast: "Request expired, try again" |
| State reused (replay) | redirect → `?status=error&reason=state_already_used` | Toast: "Link already used, try again" |
| Invalid state | redirect → `?status=error&reason=invalid_state` | Toast: "Invalid request, try again" |
| Token exchange fails | redirect → `?status=error&reason=internal_error` | Toast: "Something went wrong, try again" |
| Meta API fails | redirect → `?status=error&reason=internal_error` | Toast: "Something went wrong, try again" |
| Missing code/state | redirect → `?status=error&reason=missing_params` | Toast: "Connection interrupted, try again" |
| POST /connect fails | `500` JSON response | Toast in Angular error handler |
| Success | redirect → `?status=connected&provider=META` | Toast: "Meta connected successfully!" |

---

## Key Design Decisions

1. **State is DB-backed, not cookie-backed.** The cookie is not used. `OAuthConnectRequestEntity` is the single source of truth. This survives server restarts, works in multi-instance deployments, and is auditable.

2. **State is marked consumed before API calls.** This prevents race conditions and replay attacks — even if the Meta API call fails after, the state cannot be reused.

3. **Callback is public but tied to a local user via state.** No JWT is needed at the callback URL because the `userId` is stored in `OAuthConnectRequestEntity.userId`. The state param is the link.

4. **Callback always responds with a redirect.** It never returns JSON. The browser always ends up back on the frontend.

5. **Reconnect is the same as connect.** No special code path. `upsertOAuthAccount` and `upsertAdAccountConnections` handle idempotency.

6. **Disconnect deactivates, not deletes, ad account connections.** This preserves history. The `OAuthAccountEntity` (token) is deleted because it is no longer valid.

7. **Stale ad accounts are deactivated.** If a user disconnects an ad account on Meta's side and reconnects, the removed account is automatically marked `active=false`.

8. **`window.location.href` in Angular.** This is correct and intentional for this flow. Do not use Angular Router here — the browser must leave the Angular app to reach Meta.

9. **Do not expose access tokens to the frontend.** Tokens live only in the backend DB. The frontend only ever sees connection status summaries.
