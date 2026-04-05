# Auth & Token Expiry — Validation & Implementation Guide

---

## Part 1 — Auth Flow Validation

### What is correct ✅

| Area | Assessment |
|---|---|
| JWT-based auth, stateless | Correct — no session cookies needed for protected API calls |
| `AuthStoreService` as single source of truth | Correct pattern |
| HTTP interceptor auto-attaches JWT to every request | Correct |
| Interceptor handles 401 → logout + redirect to `/login` | Correct |
| `authGuard` using functional `CanActivateFn` | Correct for Angular 17+ |
| Saving intended URL to `sessionStorage` before redirect | Correct |
| Backend holds Meta token in DB, never exposed to frontend | Correct — this is the right security model |
| OAuth callback redirect carries JWT + userId + actId | Correct for first connect |
| `getActId()` strips `act_` prefix before returning | Correct — but see issue below |

---

### Issues found ⚠️

#### Issue 1 — `actId` prefix inconsistency (your current bug)

`getActId()` strips the `act_` prefix before returning, so when any service
uses `authStore.getActId()` as the `adAccountId` parameter, it sends `776876404753178`.
But some backend paths store and query with `act_776876404753178`.

**This is the root cause of the hash lookup bug you just fixed.**
The fix you applied (stripping `act_` in the specific lookup) is correct as a hotfix,
but the long-term fix is to normalize `adAccountId` consistently on the backend
so it never depends on whether the frontend sends `act_` or not.

**Recommendation:** pick one format across the entire backend (`act_` prefix everywhere
is Meta's canonical format) and normalize on ingress in a single place.

---

#### Issue 2 — `accessToken` slot in localStorage is misleading

The document states: "this slot exists in localStorage but its value is only
populated when the backend explicitly returns one (currently unused)."

This is a dead field that should either be removed or repurposed.
Having a field called `accessToken` in localStorage that holds nothing useful
is confusing to anyone reading the code and risks accidentally being populated
with a real Meta token in the future (which would be a security problem).

**Recommendation:** remove this field entirely from `AuthStoreService` and
`localStorage`. The Meta token never belongs in the browser.

---

#### Issue 3 — No token expiry awareness on the frontend

Currently the frontend has no mechanism to detect that the Meta access token
stored in the backend database is approaching expiry. When it expires, the next
sync or creative operation will return an error from Meta, which the backend
will propagate as a 4xx/5xx. The frontend just shows a generic error.

This is the main gap to fix — covered in detail in Part 2.

---

#### Issue 4 — Development mode token has no expiry

When your app is in development mode, Meta issues a token with no expiry date.
The `tokenExpiry` field in `OAuthAccountEntity` is either null or set to a
very far future date. This means `tokenExpiry` is currently untested.

When you go Live, Meta will issue long-lived tokens that expire in **60 days**.
You need the expiry handling working before that happens.

---

#### Issue 5 — Logout is client-side only

`authStore.logout()` clears localStorage and redirects. No backend call is made.
This is fine for JWT (stateless), but it means the JWT remains technically valid
until its server-side expiry. For a marketing platform this is acceptable,
but worth noting.

If you ever add token blacklisting, the logout flow would need a backend call.
For now, the current approach is fine.

---

## Part 2 — Meta Token Expiry: Full Implementation Guide

### Background

Meta long-lived tokens (issued when app is Live) expire after **60 days**.
When a token expires, every call your backend makes to Meta Graph API
(`/campaigns`, `/adsets`, `/insights`, `/adcreatives`, etc.) will return:

```json
{
  "error": {
    "code": 190,
    "type": "OAuthException",
    "message": "Error validating access token: Session has expired..."
  }
}
```

Your backend must catch this, mark the token as expired in the database,
and signal the frontend so the user is prompted to reconnect.

---

### Token Expiry States

```
VALID         tokenExpiry > now + 2 days        All API calls work normally
EXPIRING_SOON tokenExpiry within 2 days          Show "Reconnect soon" warning banner
EXPIRED       tokenExpiry < now                  Block sync, show reconnect prompt
              OR Meta returns error code 190
NONE          No OAuthAccountEntity row           User has never connected
```

---

### Backend Implementation

#### 1. Add `tokenStatus` field to `OAuthAccountEntity`

```java
@Column(nullable = true)
private Instant tokenExpiry;        // already exists

@Column(nullable = false)
@Enumerated(EnumType.STRING)
private TokenStatus tokenStatus = TokenStatus.VALID;  // add this

public enum TokenStatus {
    VALID,
    EXPIRING_SOON,
    EXPIRED
}
```

Liquibase migration:
```xml
<changeSet id="add-token-status-to-oauth-account" author="tijana">
    <addColumn tableName="oauth_accounts">
        <column name="token_status" type="VARCHAR(20)" defaultValue="VALID">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>
```

---

#### 2. Scheduled job — update token status daily

Create a scheduled service that runs once per day and updates `tokenStatus`
for all `OAuthAccountEntity` rows based on their `tokenExpiry`:

```java
@Service
@RequiredArgsConstructor
public class TokenExpiryScheduler {

    private final OAuthAccountRepository oauthAccountRepo;

    // Runs every day at 08:00
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void updateTokenStatuses() {
        Instant now = Instant.now();
        Instant soonThreshold = now.plus(2, ChronoUnit.DAYS);

        List<OAuthAccountEntity> accounts = oauthAccountRepo.findAll();

        for (OAuthAccountEntity account : accounts) {
            if (account.getTokenExpiry() == null) {
                // Development mode token — no expiry, always valid
                account.setTokenStatus(TokenStatus.VALID);
                continue;
            }

            if (account.getTokenExpiry().isBefore(now)) {
                account.setTokenStatus(TokenStatus.EXPIRED);
            } else if (account.getTokenExpiry().isBefore(soonThreshold)) {
                account.setTokenStatus(TokenStatus.EXPIRING_SOON);
            } else {
                account.setTokenStatus(TokenStatus.VALID);
            }
        }

        oauthAccountRepo.saveAll(accounts);
    }
}
```

Enable scheduling in your main application class:
```java
@SpringBootApplication
@EnableScheduling   // add this if not already present
public class MarketingApplication { ... }
```

---

#### 3. Catch Meta error code 190 at runtime

When any backend service calls Meta Graph API and receives error code 190,
mark the token as expired immediately (do not wait for the daily scheduler):

Create a shared method in your Meta API client / service base:

```java
private void handleMetaApiError(ResponseEntity<?> response, Long userId, String provider) {
    // Parse the response body for Meta error code
    // If error.code == 190 (OAuthException / token expired):

    oauthAccountRepo.findByUserIdAndProvider(userId, provider)
        .ifPresent(account -> {
            account.setTokenStatus(TokenStatus.EXPIRED);
            oauthAccountRepo.save(account);
        });

    throw new MetaTokenExpiredException(
        "Meta access token has expired. Please reconnect your Meta account."
    );
}
```

Create a custom exception:
```java
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class MetaTokenExpiredException extends RuntimeException {
    public MetaTokenExpiredException(String message) {
        super(message);
    }
}
```

Add a global exception handler in your `@ControllerAdvice`:
```java
@ExceptionHandler(MetaTokenExpiredException.class)
public ResponseEntity<ErrorResponse> handleMetaTokenExpired(MetaTokenExpiredException ex) {
    ErrorResponse error = new ErrorResponse(
        "META_TOKEN_EXPIRED",
        ex.getMessage(),
        HttpStatus.UNAUTHORIZED.value()
    );
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
}
```

Return a consistent error body:
```json
{
  "code": "META_TOKEN_EXPIRED",
  "description": "Meta access token has expired. Please reconnect your Meta account.",
  "status": 401
}
```

---

#### 4. Include token status in `GET /api/ad-account-connections`

Update `AdAccountConnectionSummary` to include the token status:

```java
public record AdAccountConnectionSummary(
    String provider,
    boolean connected,
    Instant lastSynced,
    String tokenStatus     // "VALID", "EXPIRING_SOON", "EXPIRED", or null if not connected
) {}
```

In the controller that builds this response, look up the `OAuthAccountEntity`
for the user and include its `tokenStatus`:

```java
oauthAccountRepo.findByUserIdAndProvider(userId, "META")
    .map(account -> account.getTokenStatus().name())
    // include in summary DTO
```

---

#### 5. Token refresh — attempt before expiry (optional but recommended)

When `tokenStatus == EXPIRING_SOON`, the backend can proactively refresh the token
by calling Meta's token refresh endpoint. Add this to `TokenExpiryScheduler`:

```java
// In the same daily job, after updating statuses:
for (OAuthAccountEntity account : accounts) {
    if (account.getTokenStatus() == TokenStatus.EXPIRING_SOON) {
        tryRefreshToken(account);
    }
}

private void tryRefreshToken(OAuthAccountEntity account) {
    try {
        // Call Meta's long-lived token refresh:
        // GET /oauth/access_token
        //   ?grant_type=fb_exchange_token
        //   &client_id={app_id}
        //   &client_secret={app_secret}
        //   &fb_exchange_token={current_token}

        MetaTokenResponse refreshed = metaOAuthService.exchangeForLongLivedToken(
            account.getAccessToken()
        );

        account.setAccessToken(refreshed.accessToken());
        account.setTokenExpiry(Instant.now().plus(60, ChronoUnit.DAYS));
        account.setTokenStatus(TokenStatus.VALID);
        oauthAccountRepo.save(account);

    } catch (Exception e) {
        // Refresh failed — mark expired, user must reconnect manually
        account.setTokenStatus(TokenStatus.EXPIRED);
        oauthAccountRepo.save(account);
    }
}
```

---

### Frontend Implementation

#### 1. Update `AdAccountConnectionSummary` model

```typescript
// models/oauth.model.ts
export interface AdAccountConnectionSummary {
  provider: 'META' | 'TIKTOK' | 'PINTEREST' | 'GOOGLE' | 'LINKEDIN' | 'REDDIT';
  connected: boolean;
  lastSynced: string | null;
  tokenStatus: 'VALID' | 'EXPIRING_SOON' | 'EXPIRED' | null;
}
```

---

#### 2. Token status helper in `SyncAccountsComponent`

```typescript
getTokenStatus(provider: string): string | null {
  return this.connections.find(c => c.provider === provider)?.tokenStatus ?? null;
}

isTokenExpired(provider: string): boolean {
  return this.getTokenStatus(provider) === 'EXPIRED';
}

isTokenExpiringSoon(provider: string): boolean {
  return this.getTokenStatus(provider) === 'EXPIRING_SOON';
}
```

---

#### 3. Visual states on the platform card

Update the Meta platform card template to show token status:

```html
<div class="platform-card" [class.token-expired]="isTokenExpired('META')">

  <div class="card-header">
    <img src="assets/icons/meta.svg" alt="Meta" />
    <span class="platform-name">Meta</span>
    <span *ngIf="isConnected('META') && !isTokenExpired('META')"
          class="badge-connected">✓ Connected</span>
    <span *ngIf="isTokenExpired('META')"
          class="badge-expired">⚠ Token expired</span>
  </div>

  <!-- Warning banner: expiring soon -->
  <div *ngIf="isTokenExpiringSoon('META')" class="token-warning-banner">
    <i class="fa fa-exclamation-triangle"></i>
    Your Meta connection expires in less than 2 days. Reconnect to avoid interruption.
  </div>

  <!-- Error banner: already expired -->
  <div *ngIf="isTokenExpired('META')" class="token-error-banner">
    <i class="fa fa-times-circle"></i>
    Your Meta connection has expired. Reconnect to continue syncing.
  </div>

  <p *ngIf="isConnected('META') && lastSynced('META') && !isTokenExpired('META')"
     class="last-synced-text">
    Last synced {{ lastSynced('META') | date:'short' }}
  </p>

  <div class="card-actions">
    <!-- Connect: shown when not connected -->
    <button *ngIf="!isConnected('META')"
            class="btn btn-outline"
            [disabled]="connectingPlatform === 'META'"
            (click)="connectMeta()">
      <i class="fa fa-link"></i> Connect
    </button>

    <!-- Reconnect: shown when connected OR expired -->
    <button *ngIf="isConnected('META') || isTokenExpired('META')"
            class="btn btn-outline"
            [class.btn-warning]="isTokenExpired('META') || isTokenExpiringSoon('META')"
            [disabled]="connectingPlatform === 'META'"
            (click)="connectMeta()">
      <span *ngIf="connectingPlatform !== 'META'">
        {{ isTokenExpired('META') ? 'Reconnect now' : 'Reconnect' }}
      </span>
      <span *ngIf="connectingPlatform === 'META'">
        <i class="fa fa-spinner fa-spin"></i> Connecting…
      </span>
    </button>

    <!-- Sync: disabled if not connected OR token expired -->
    <button class="btn btn-primary"
            [disabled]="!isConnected('META') || isTokenExpired('META') || isSyncing"
            [matTooltip]="getSyncTooltip('META')"
            (click)="syncData('META')">
      <i class="fa fa-sync" [class.fa-spin]="isSyncing"></i>
      {{ isSyncing ? 'Syncing…' : 'Sync Data' }}
    </button>

    <!-- Disconnect: only shown when connected and not expired -->
    <button *ngIf="isConnected('META') && !isTokenExpired('META')"
            class="btn btn-ghost btn-danger"
            [disabled]="disconnecting"
            (click)="disconnectMeta()">
      {{ disconnecting ? 'Disconnecting…' : 'Disconnect' }}
    </button>
  </div>
</div>
```

Helper for sync tooltip:
```typescript
getSyncTooltip(provider: string): string {
  if (!this.isConnected(provider)) return 'Connect your account first';
  if (this.isTokenExpired(provider)) return 'Reconnect your account — token has expired';
  return '';
}
```

---

#### 4. Global handler for `META_TOKEN_EXPIRED` API error

In your HTTP interceptor (`http.token.interceptor.ts`), extend the `catchError`
to handle the `META_TOKEN_EXPIRED` error code returned by the backend:

```typescript
catchError((error: HttpErrorResponse) => {
  if (error.status === 401) {
    const errorCode = error.error?.code;

    if (errorCode === 'META_TOKEN_EXPIRED') {
      // Do NOT log the user out — their app JWT is still valid
      // Mark the Meta connection as expired in local state
      this.syncAccountsStateService.markMetaTokenExpired();

      // Show a persistent toast prompting reconnect
      this.toastr.error(
        'Your Meta connection has expired. Go to Sync Accounts to reconnect.',
        'Meta reconnection required',
        { disableTimeOut: true, closeButton: true }
      );

      return throwError(() => error);
    }

    // Regular 401 — app JWT expired, full logout
    this.authStore.logout();
    this.router.navigate(['/login']);
  }

  return throwError(() => error);
})
```

Create a simple `SyncAccountsStateService` to communicate the expired state
across components without a full page reload:

```typescript
@Injectable({ providedIn: 'root' })
export class SyncAccountsStateService {
  private metaTokenExpired$ = new BehaviorSubject<boolean>(false);

  markMetaTokenExpired(): void {
    this.metaTokenExpired$.next(true);
  }

  isMetaTokenExpired(): Observable<boolean> {
    return this.metaTokenExpired$.asObservable();
  }

  reset(): void {
    this.metaTokenExpired$.next(false);
  }
}
```

In `SyncAccountsComponent`, subscribe to this and reload connection status
when the token is marked expired:

```typescript
ngOnInit(): void {
  // Subscribe to runtime token expiry signals from the interceptor
  this.syncStateService.isMetaTokenExpired()
    .pipe(takeUntilDestroyed(this.destroyRef))
    .subscribe(expired => {
      if (expired) {
        this.loadConnectionStatus();  // refreshes the card UI
      }
    });

  this.loadConnectionStatus();
  this.handleOAuthCallback();
}
```

After a successful reconnect, reset the state:
```typescript
// In ngOnInit, after handling the OAuth callback:
if (status === 'connected' && provider === 'META') {
  this.syncStateService.reset();
  this.toastr.success('Meta account reconnected successfully!');
}
```

---

#### 5. CSS for token status indicators

```scss
.token-warning-banner {
  background: #FEF3C7;
  color: #92400E;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
  margin: 8px 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.token-error-banner {
  background: #FEE2E2;
  color: #991B1B;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
  margin: 8px 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.badge-expired {
  background: #FEE2E2;
  color: #991B1B;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
}

.platform-card.token-expired {
  border: 2px solid #FCA5A5;
  background: #FFF5F5;
}

.btn-warning {
  border-color: #F59E0B !important;
  color: #92400E !important;
}
```

---

## Part 3 — Summary: What to Do Right Now vs Later

### Right now (development mode — no expiry)

| Action | Priority |
|---|---|
| Remove the unused `accessToken` field from `AuthStoreService` and localStorage | Medium |
| Normalize `adAccountId` format (with/without `act_`) consistently on the backend | High — already causing bugs |
| Add `tokenStatus` column to `OAuthAccountEntity` with default `VALID` | High — prepare before going Live |
| Add `tokenStatus` to `GET /api/ad-account-connections` response | High |
| Add frontend visual states (expired/expiring banners) even if they never trigger yet | Medium |

### Before going Live (when Meta issues 60-day tokens)

| Action | Priority |
|---|---|
| Enable `@EnableScheduling` and deploy `TokenExpiryScheduler` | Critical |
| Add `MetaTokenExpiredException` and global handler | Critical |
| Catch Meta error code 190 in all Graph API calls | Critical |
| Add token refresh logic in the daily scheduler | High |
| Test the full expiry → reconnect → success flow end-to-end | Critical |

### The expiry → reconnect flow in plain English

```
Day 0:   User connects Meta → long-lived token stored (expires Day 60)
Day 58:  Scheduler runs → tokenStatus = EXPIRING_SOON
         → Next page load shows amber warning banner on Meta card
         → User sees "Reconnect soon" but can still sync normally

Day 60:  Token expires
         → Either: scheduler ran → tokenStatus = EXPIRED
         → Or: next Meta API call returns error 190
               → interceptor catches it → marks expired → shows toast

         Either way:
         → Sync button disabled
         → Red "Token expired" banner on Meta card
         → "Reconnect now" button prominent

User clicks Reconnect → same OAuth flow as first connect
         → New 60-day token stored → tokenStatus = VALID
         → Banners disappear → Sync re-enabled
```
