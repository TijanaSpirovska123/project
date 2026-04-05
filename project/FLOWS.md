# Application Flows

## Table of Contents

1. [Registration](#1-registration)
2. [Login](#2-login)
3. [JWT Authentication (per-request)](#3-jwt-authentication-per-request)
4. [Meta OAuth Connect](#4-meta-oauth-connect)
5. [Password Reset](#5-password-reset)
6. [Campaign CRUD](#6-campaign-crud)
7. [Ad Set CRUD](#7-ad-set-crud)
8. [Ad CRUD](#8-ad-crud)
9. [Sync Flow (Campaign → Ad Set → Ad)](#9-sync-flow-campaign--ad-set--ad)
10. [Security — What Is Public vs Protected](#10-security--what-is-public-vs-protected)

---

## 1. Registration

**Endpoint:** `POST /api/auth/register`
**Auth required:** No

```
Frontend
  └─▶ POST /api/auth/register  { email, password, ... }
        └─▶ AuthController.register()
              └─▶ UserService.register()
                    ├─ Hash password (BCrypt)
                    ├─ Save UserEntity to DB
                    └─ Return UserResponseDto  { id, email, role }
```

No JWT is issued on registration. The user must log in separately.

---

## 2. Login

**Endpoint:** `POST /api/auth/login`
**Auth required:** No

```
Frontend
  └─▶ POST /api/auth/login  { email, password }
        └─▶ AuthController.login()
              └─▶ AuthenticationService.login()
                    ├─ Look up UserEntity by email
                    ├─ BCrypt.matches(password, hash)  → 401 if wrong
                    ├─ JwtService.generateToken(username, role, userId)
                    │     └─ Signs HS512 JWT: { sub=username, role, userId, exp }
                    │        Expiry controlled by jwt.expiration in application.yml
                    ├─ Look up active META AdAccountConnection for user
                    └─ Return LoginResponseDto
                          { token, userId, role, actId (nullable) }
```

The frontend stores the JWT and sends it as `Authorization: Bearer <token>` on every subsequent request.
`actId` is the Meta ad account id (`act_123...`) already connected, or `null` if the user has not yet connected Meta.

---

## 3. JWT Authentication (per-request)

Applies to **every request** except the public paths listed in [Section 10](#10-security--what-is-public-vs-protected).

```
Incoming HTTP request
  └─▶ JwtAuthenticationFilter.doFilterInternal()
        ├─ Read Authorization header
        ├─ No "Bearer " prefix → pass through (Spring Security will reject if route is protected)
        ├─ JwtService.validateToken(token)  → 401 if invalid/expired
        ├─ Extract userId, username, role from claims
        ├─ Build UserPrincipal(userId, username)
        ├─ Set UsernamePasswordAuthenticationToken in SecurityContext
        └─ Continue filter chain → reach controller

Controllers read identity via:
  extractUserId(auth)  →  ((UserPrincipal) auth.getPrincipal()).userId()
```

---

## 4. Meta OAuth Connect

This is a two-phase flow that links an already-logged-in user to their Meta/Facebook ad account.

### Phase 1 — Initiate connect

**Endpoint:** `POST /api/oauth/meta/connect`
**Auth required:** JWT

```
Frontend (user is logged in)
  └─▶ POST /api/oauth/meta/connect
        └─▶ OAuthConnectController.connectMeta()
              └─▶ MetaOAuthService.handleMetaConnect(userId, response)
                    ├─ Generate UUID state token
                    ├─ Save OAuthConnectRequestEntity { state, userId, provider=META, expiresAt=+10min }
                    ├─ Set HttpOnly cookie:  oauth_connect_state=<state>  (maxAge 10 min)
                    └─ Return { authorizationUrl: "/oauth2/authorization/facebook" }

Frontend redirects the browser to /oauth2/authorization/facebook
```

### Phase 2 — Facebook redirects back

```
Browser → GET /oauth2/authorization/facebook
  └─▶ Spring Security OAuth2 redirects browser to Facebook login/consent

Facebook → GET /login/oauth2/code/facebook?code=...&state=...
  └─▶ Spring Security exchanges code for access token
        └─▶ OAuth2LoginSuccessHandler.onAuthenticationSuccess()
              ├─ Read cookie oauth_connect_state
              │    └─ Missing cookie → redirect to frontend?provider=META&status=missing_state
              ├─ Look up OAuthConnectRequestEntity by state + provider
              │    └─ Not found / expired → redirect ...&status=state_expired
              ├─ Load OAuth2AuthorizedClient (contains short-lived Facebook token)
              ├─ TokenExchangeService.exchangeToLongLived(META, shortToken)
              │    └─ (currently stores as-is with +30 day expiry; real exchange is for other providers)
              ├─ Upsert OAuthAccountEntity { user, provider=META, accessToken, tokenExpiry, externalUserId, scopes }
              ├─ Delete OAuthConnectRequestEntity (cleanup)
              ├─ Clear oauth_connect_state cookie
              └─ Redirect browser to:
                   {app.frontend.oauth-success-url}?provider=META&status=connected
                   (default: http://localhost:5000/oauth-success?provider=META&status=connected)
```

After this flow the user's Meta long-lived access token is stored in `oauth_accounts` and all API calls to Meta use it via `TokenService.getAccessToken()`.

### Token validation on every platform call

```
TokenService.getAccessToken(user, provider)
  ├─ Load OAuthAccountEntity by user + provider
  ├─ Token expiry < now + 2 days → throw 401 "Please reconnect"
  └─ Return accessToken string
```

---

## 5. Password Reset

**Auth required:** No

```
Step 1 — Request reset token
  POST /api/user/request-password-reset  { email }
    └─▶ UserService.sendResetToken(email)
          ├─ Generate reset token
          ├─ Store token + expiry on UserEntity
          └─ Send email with reset link

Step 2 — Submit new password
  POST /api/user/reset-password  { token, newPassword }
    └─▶ UserService.verifyToken(request)  →  true/false
          └─ true → UserService.resetPassword(request)
                      ├─ BCrypt new password
                      └─ Save UserEntity
```

---

## 6. Campaign CRUD

All endpoints under `/api/campaigns`. JWT required.

| Method | Path | Action |
|--------|------|--------|
| `POST` | `/api/campaigns` | Create campaign locally + push to Meta |
| `PUT` | `/api/campaigns/{id}` | Update by local DB id + push to Meta |
| `DELETE` | `/api/campaigns/{id}` | Delete locally + delete on Meta |
| `GET` | `/api/campaigns/{platform}` | List from DB for authenticated user |
| `PATCH` | `/api/campaigns/external/{platform}/{externalId}` | Update by Meta external id |
| `GET` | `/api/campaigns/platform/{platform}/{accountId}` | Sync from Meta then return DB list |
| `GET` | `/api/campaigns/platform/{platform}/campaign/{campaignId}/adsets` | List ad sets for a campaign |

### Create

```
POST /api/campaigns  { name, status, userId, adAccountId, ... }
  └─▶ CampaignService.createCampaign(dto)
        ├─ Load UserEntity
        ├─ Build CampaignEntity via campaignMapper.convertToBaseEntity(dto)
        ├─ Set user, timestamps, platform defaults
        ├─ Save to DB (no externalId yet)
        ├─ AbstractPlatformService.createOnPlatform(entity)
        │     ├─ Get access token via TokenService
        │     ├─ POST to Meta Graph API  /act_{accountId}/campaigns
        │     └─ Meta returns { id: "..." } → set entity.externalId, save again
        └─ Return CampaignDto (via campaignMapper.convertToBaseDto)
```

### Update

```
PUT /api/campaigns/{id}
  └─▶ CampaignService.updateCampaign(id, dto)
        ├─ Load entity by local id
        ├─ Apply name/status changes, save to DB
        ├─ If externalId present → AbstractPlatformService.updateOnPlatform(entity)
        │     └─ POST to Meta Graph API  /{externalId}  with updated fields
        └─ Return CampaignDto
```

### Delete

```
DELETE /api/campaigns/{id}
  └─▶ CampaignService.deleteCampaign(id)
        ├─ If externalId present → AbstractPlatformService.deleteOnPlatform(entity)
        │     └─ POST to Meta Graph API  /{externalId}  with { status: DELETED }
        └─ campaignRepository.delete(entity)
```

---

## 7. Ad Set CRUD

All endpoints under `/api/ad-sets`. JWT required.

| Method | Path | Action |
|--------|------|--------|
| `POST` | `/api/ad-sets` | Create ad set locally + push to Meta |
| `PUT` | `/api/ad-sets/{id}` | Update by local DB id + push to Meta |
| `DELETE` | `/api/ad-sets/{id}` | Delete locally + delete on Meta |
| `GET` | `/api/ad-sets/{platform}` | List from DB with nested ads |
| `PATCH` | `/api/ad-sets/external/{platform}/{externalId}` | Update by Meta external id |
| `GET` | `/api/ad-sets/platform/{platform}/{accountId}` | Sync from Meta then return DB list |
| `GET` | `/api/ad-sets/platform/{platform}/adset/{adSetId}/ads` | List ads for an ad set |

### Create

```
POST /api/ad-sets  { userId, campaignId, name, status }
  └─▶ AdSetService.createAdSet(dto)
        ├─ Load UserEntity + CampaignEntity
        ├─ Verify campaign belongs to user and has externalId (must be synced first)
        ├─ Build AdSetEntity (sets campaign FK, campaignExternalId, platform, adAccountId)
        ├─ Save to DB
        ├─ AbstractPlatformService.createOnPlatform(entity)
        │     └─ POST to Meta  /act_{accountId}/adsets  → set externalId
        └─ Return AdSetDto (via adSetMapper.convertToBaseDto)
```

### Get all (with nested ads)

```
GET /api/ad-sets/{platform}
  └─▶ AdSetService.getAllAdSetsByPlatform(userId, platform)
        └─ For each AdSetEntity:
             ├─ adSetMapper.convertToBaseDto(adSet)
             ├─ rawData fallback: if entity.rawData null → try Redis cache
             └─ Load child ads via adRepository.findByAdSetId
                  └─ adMapper.convertToBaseDto(adEntities)  (nested in dto.ads)
```

---

## 8. Ad CRUD

All endpoints under `/api/ads`. JWT required.

| Method | Path | Action |
|--------|------|--------|
| `POST` | `/api/ads` | Create ad locally + push to Meta |
| `PUT` | `/api/ads/{id}` | Update by local DB id + push to Meta |
| `DELETE` | `/api/ads/{id}` | Delete locally + delete on Meta |
| `GET` | `/api/ads/{platform}` | List from DB for authenticated user |
| `PATCH` | `/api/ads/external/{platform}/{externalId}` | Update by Meta external id |
| `GET` | `/api/ads/platform/{platform}/{adAccountId}` | Sync from Meta then return DB list |

### Create

```
POST /api/ads  { name, creativeId, adSetId OR adSetExternalId, status }
  └─▶ AdService.createAd(userId, dto)
        ├─ Resolve AdSetEntity (by local id or by platform+external id)
        ├─ Verify adSet belongs to user and has externalId
        ├─ Build AdEntity (sets adSet FK, adSetExternalId, adSetName, platform, adAccountId)
        ├─ Save to DB
        ├─ AbstractPlatformService.createOnPlatform(entity)
        │     └─ POST to Meta  /act_{accountId}/ads  → set externalId
        └─ Return AdDto (via adMapper.convertToBaseDto)
```

---

## 9. Sync Flow (Campaign → Ad Set → Ad)

The frontend triggers sync in sequence: campaigns first, then ad sets, then ads.
Each sync call hits a `GET /platform/{platform}/{accountId}` endpoint which syncs **and** cascades.

### Campaign sync endpoint

```
GET /api/campaigns/platform/{platform}/{accountId}
  └─▶ CampaignService.syncCampaigns(userId, platform, adAccountId)
        ├─ AbstractPlatformService.syncFromPlatform()
        │     ├─ Paginate Meta Graph API  /act_{accountId}/campaigns
        │     ├─ For each returned campaign:
        │     │     ├─ Already in DB (by externalId)?
        │     │     │     YES → update name/status/rawData, save
        │     │     │     NO  → insert new CampaignEntity
        │     └─ Return SyncResult { fetched, inserted, skipped, updated }
        ├─ CampaignService.syncAdSetsForCampaigns()  ← CASCADE
        │     ├─ Paginate Meta  /act_{accountId}/adsets
        │     ├─ For each ad set:
        │     │     ├─ Already in DB? YES → update, NO → insert
        │     │     └─ Resolve campaign FK by campaignExternalId
        │     └─ (no further cascade here)
        └─ Return all campaigns from DB  (campaignService.getAllCampaigns)
```

### Ad Set sync endpoint

```
GET /api/ad-sets/platform/{platform}/{accountId}
  └─▶ AdSetService.syncAdSets(userId, platform, adAccountId)
        ├─ AbstractPlatformService.syncFromPlatform()  (same upsert logic for ad sets)
        ├─ AdSetService.syncAdsForAdSets()  ← CASCADE
        │     ├─ Paginate Meta  /act_{accountId}/ads
        │     ├─ For each ad:
        │     │     ├─ Already in DB? YES → update, NO → insert
        │     │     └─ Resolve adSet FK by adSetExternalId
        │     └─ (no further cascade)
        └─ Return all ad sets from DB
```

### Ad sync endpoint

```
GET /api/ads/platform/{platform}/{adAccountId}
  └─▶ AdService.syncAds(userId, platform, adAccountId)
        └─ AbstractPlatformService.syncFromPlatform()
              ├─ Paginate Meta  /act_{accountId}/ads
              └─ Upsert each ad (no cascade, ads are leaf nodes)
        Return all ads from DB
```

### Duplicate-safe upsert (AbstractPlatformService.syncFromPlatform)

```
For each item fetched from Meta:
  1. Collect all externalIds from the response
  2. One DB query: findExisting(user, platform, adAccountId, externalIds)
  3. Build a Map<externalId → entity> from existing records
  4. For each DTO:
       externalId in map?
         YES → applyDtoToExistingEntity (update fields) + saveAll
         NO  → build new entity + saveAll
  5. rawData is written to entity field AND cached in Redis
```

Syncing the same account 10 times is safe — existing records are updated, never duplicated,
because the unique constraint `(user_id, platform, ad_account_id, external_id)` and the
pre-check in `syncFromPlatform` both prevent inserts for already-known external IDs.

---

## 10. Security — What Is Public vs Protected

### Public (no JWT required)

| Path | Reason |
|------|--------|
| `POST /api/auth/register` | Registration |
| `POST /api/auth/login` | Login |
| `POST /api/user/request-password-reset` | Password reset step 1 |
| `POST /api/user/reset-password` | Password reset step 2 |
| `POST /api/auth/logout` | Logout (clears session/cookies) |
| `/oauth2/**` | Spring OAuth2 authorization redirect |
| `/login/oauth2/**` | Spring OAuth2 callback from Facebook |
| `/v3/api-docs/**`, `/swagger-ui/**` | Swagger docs |
| `/images/**` | Static images |
| `OPTIONS /**` | CORS preflight |

### Protected (JWT required)

Everything else — all campaign, ad set, ad, OAuth connect, insights, and user management endpoints.

### JWT filter bypass paths

`JwtAuthenticationFilter.shouldNotFilter()` skips JWT parsing for:
- `/api/auth/**`
- `/oauth2/**`
- `/login/oauth2/**`
- `/v3/api-docs`, `/swagger-ui`

These paths either handle their own auth or are intentionally open.

---

## Key DB Tables

| Table | Purpose |
|-------|---------|
| `users` | Application users |
| `oauth_accounts` | Stored long-lived Meta access tokens (one per user per provider) |
| `oauth_connect_requests` | Temporary state tokens used during OAuth connect (TTL 10 min) |
| `ad_account_connection` | Which Meta ad accounts are linked and active for a user |
| `campaign` | Synced/created campaigns (unique on user+platform+adAccountId+externalId) |
| `ad_set` | Synced/created ad sets (same unique constraint) |
| `ad` | Synced/created ads (same unique constraint) |

---

## rawData Field

All three entities (`campaign`, `ad_set`, `ad`) store a `rawData` JSON column.

- **Written on sync:** `syncFromPlatform` sets `entity.rawData = dto.getRawData()` for every upserted record.
- **Written on cascade sync:** `syncAdSetsForCampaigns` / `syncAdsForAdSets` also set rawData on each entity.
- **Also cached in Redis:** `PlatformRawDataCache` stores the same data under key `rawdata:{type}:{platform}:{adAccountId}:{externalId}`.
- **Returned in API responses:** Mapper includes `rawData` in every DTO response.
  If `entity.rawData` is null (old records before migration), the service falls back to Redis.
