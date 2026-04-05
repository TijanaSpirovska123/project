# Platform Sync Architecture

How this application creates, pushes, and pulls ad objects (Campaigns, AdSets, Ads) between the local database and external advertising platforms (currently Meta/Facebook).

---

## Table of Contents

1. [Overview](#overview)
2. [Core Concepts](#core-concepts)
3. [How Sync Works — Step by Step](#how-sync-works--step-by-step)
   - [Push: Creating / Updating on Platform](#push-creating--updating-on-platform)
   - [Pull: Syncing from Platform to DB](#pull-syncing-from-platform-to-db)
4. [Object Hierarchy & Dependency Order](#object-hierarchy--dependency-order)
5. [Caching Layer (Redis)](#caching-layer-redis)
6. [OAuth & Token Management](#oauth--token-management)
7. [API Endpoints](#api-endpoints)
8. [Current Meta Integration Details](#current-meta-integration-details)
9. [Future Platform Recommendations](#future-platform-recommendations)
   - [Meta (Improvements)](#meta-improvements)
   - [Instagram](#instagram)
   - [TikTok](#tiktok)
10. [Adding a New Platform — Checklist](#adding-a-new-platform--checklist)

---

## Overview

The application manages a **Campaign → AdSet → Ad** hierarchy. Each object can exist:

- **Locally only** — created in the app but not yet pushed to a platform
- **On platform only** — exists in Meta/TikTok but not yet synced to local DB
- **Both** — the normal state after a push or pull sync

External platform identity is tracked via `externalId` (the ID the platform assigns, e.g. a Meta campaign ID like `120210012345678`). All sync operations resolve objects by `(user, platform, adAccountId, externalId)`.

---

## Core Concepts

### AbstractPlatformService

The central template class (`infrastructure/service/platformserviceimpl/AbstractPlatformService.java`) implements all sync logic. Domain services (CampaignService, AdSetService, AdService) extend it and implement a set of hooks:

| Hook | Purpose |
|---|---|
| `newEntity()` | Returns a blank entity instance |
| `dtoExternalId(dto)` | Extracts the platform ID from a DTO |
| `dtoRawData(dto)` | Extracts the full raw API response map |
| `applyDtoToNewEntity(...)` | Maps a fetched DTO onto a brand-new entity |
| `applyDtoToExistingEntity(...)` | Updates an already-persisted entity from a DTO |
| `cacheRawData(...)` | Writes raw data into Redis |
| `findExisting(...)` | Queries the DB for entities matching a set of external IDs |

### Strategy Pattern

Each platform gets its own strategy class implementing `PlatformStrategy<D>`:

| Method | Returns |
|---|---|
| `createPath(accountId)` | URL path for POST create, e.g. `act_123/campaigns` |
| `listPath(accountId)` | URL path for GET list |
| `updatePath(externalId)` | URL path for POST update/delete |
| `baseListQuery()` | Default query params, e.g. `{fields: "id,name,status"}` |
| `toCreateForm(dto)` | `MultiValueMap` request body for create |
| `toUpdateForm(dto, isDelete)` | `MultiValueMap` request body for update or delete |
| `mapGetRow(row, userId, adAccountId)` | Converts a raw API row into a DTO |

A `PlatformClient` (e.g. `MetaPlatformClient`) handles the raw HTTP calls (GET, POST, multipart). Strategies are resolved at runtime from a registry (`CampaignStrategyRegistry`, `AdSetStrategyRegistry`, `AdStrategyRegistry`) keyed by `Provider` enum.

---

## How Sync Works — Step by Step

### Push: Creating / Updating on Platform

Called when a user explicitly creates or edits an object through the app.

```
User POSTs /api/campaigns
    ↓
CampaignService.createCampaign(dto)
    ├─ Validate user, build CampaignEntity
    ├─ campaignRepository.save(entity)          ← persist locally first
    └─ AbstractPlatformService.createOnPlatform(entity)
           ├─ TokenService.getAccessToken(user, META)
           ├─ MetaPlatformClient.postForm(
           │      "act_123/campaigns",
           │      {name: "...", status: "ACTIVE"},
           │      bearerToken)
           │      → POST https://graph.facebook.com/v23.0/act_123/campaigns
           ├─ Extract {id: "120210012345678"} from response
           └─ entity.setExternalId("120210012345678")
              campaignRepository.save(entity)   ← update with platform ID
```

**Update flow** (`updateOnPlatform`) follows the same pattern but POSTs to the object's own ID path.

**Delete flow** (`deleteOnPlatform`) sets `status=DELETED` and POSTs to the update path (Meta convention — objects are soft-deleted, not HTTP-DELETEd).

### Pull: Syncing from Platform to DB

Triggered when a user requests the list endpoint with `platform/{platform}/{accountId}`. The controller calls sync first, then returns the refreshed DB state.

```
GET /api/campaigns/platform/META/act_123
    ↓
CampaignService.syncCampaigns(userId, META, "act_123")
    ↓
AbstractPlatformService.syncFromPlatform(user, META, "act_123")
    │
    ├─ 1. FETCH from platform (paginated)
    │       MetaPlatformClient.get("act_123/campaigns",
    │           {fields: "id,name,status"}, token)
    │       → GET https://graph.facebook.com/v23.0/act_123/campaigns?fields=...
    │
    │       While paging.cursors.after exists:
    │           MetaPlatformClient.get(..., {after: cursor}, ...)
    │       → collects all pages into List<CampaignDto>
    │
    ├─ 2. RESOLVE existing local records
    │       campaignRepository.findByUserAndPlatformAndAdAccountIdAndExternalIdIn(
    │           user, "META", "act_123", [all fetched externalIds])
    │
    ├─ 3. UPSERT each fetched DTO
    │       For each DTO:
    │         ├─ cacheRawData → Redis SET rawdata:campaign:META:act_123:ID (TTL 300s)
    │         ├─ IF externalId found locally → applyDtoToExistingEntity + updateTimestamp
    │         └─ IF new               → newEntity + applyDtoToNewEntity + set metadata
    │
    ├─ 4. BATCH SAVE
    │       campaignRepository.saveAll(inserts)
    │       campaignRepository.saveAll(updates)
    │
    └─ 5. RETURN SyncResult {fetched, inserted, updated, skipped}

Then: CampaignService.getAllCampaigns(userId)
    └─ For each local campaign:
         ├─ Convert to DTO
         └─ PlatformRawDataCache.getCampaign(...)  ← Redis GET (null if miss/Redis down)
            → populate dto.rawData for frontend
```

---

## Object Hierarchy & Dependency Order

The hierarchy on Meta is strict. An AdSet must reference a Campaign, and an Ad must reference an AdSet. This is enforced locally:

```
Campaign (no parent required)
    └── AdSet (requires campaign.externalId to be set)
            └── Ad (requires adSet.externalId to be set)
```

**Sync order matters.** Always sync in this order:

```
1. GET /api/campaigns/platform/META/act_123      ← sync campaigns first
2. GET /api/adsets/platform/META/act_123         ← then ad sets
3. GET /api/ads/platform/META/act_123            ← then ads
```

AdSets synced before campaigns will have a null `campaign` FK (the `campaignExternalId` column is still populated so the link can be resolved on a subsequent sync or lazy lookup).

---

## Caching Layer (Redis)

Raw API response data is cached in Redis separately from the main DB sync. This avoids repeated API calls when the frontend wants to display platform-native fields not stored in the DB schema.

**Key pattern:** `rawdata:{type}:{platform}:{adAccountId}:{externalId}`

Example: `rawdata:campaign:META:act_123:120210012345678`

| Entity | TTL |
|---|---|
| Campaign | 300 s (5 min) |
| AdSet | 300 s (5 min) |
| Ad | 120 s (2 min) |

If Redis is unavailable, all cache reads return `null` and all cache writes are silently skipped (logged as WARN). The application continues to function; `rawData` in the response will be `null`.

---

## OAuth & Token Management

Each user connects a platform account via OAuth 2.0. Tokens are stored per user per provider in `oauth_accounts`:

| Column | Description |
|---|---|
| `provider` | `"META"`, `"INSTAGRAM"`, etc. |
| `access_token` | Bearer token used in all API calls |
| `token_expiry` | When the token expires |
| `external_user_id` | Platform user ID |
| `granted_scopes` | Scopes granted at auth time |

`TokenService.getAccessToken(user, provider)` throws `INVALID_CREDENTIALS` if:
- No OAuth account linked for that provider
- Token expiry is within 2 days (force reconnection before expiry)

Meta long-lived tokens expire after ~60 days. The app currently relies on the user reconnecting manually when the token nears expiry.

---

## API Endpoints

### Campaign

| Method | Path | Action |
|---|---|---|
| `POST` | `/api/campaigns` | Create locally + push to platform |
| `PUT` | `/api/campaigns/{id}` | Update locally + push to platform |
| `DELETE` | `/api/campaigns/{id}` | Delete locally + soft-delete on platform |
| `GET` | `/api/campaigns` | List from local DB (with Redis rawData) |
| `GET` | `/api/campaigns/platform/{platform}` | List local by platform filter |
| `GET` | `/api/campaigns/platform/{platform}/{accountId}` | **Sync from platform then return DB** |

### AdSet

| Method | Path | Action |
|---|---|---|
| `POST` | `/api/adsets` | Create locally + push to platform |
| `PUT` | `/api/adsets/{id}` | Update locally + push to platform |
| `DELETE` | `/api/adsets/{id}` | Delete locally + soft-delete on platform |
| `GET` | `/api/adsets` | List from local DB |
| `GET` | `/api/adsets/platform/{platform}/{accountId}` | **Sync from platform then return DB** |

### Ad

| Method | Path | Action |
|---|---|---|
| `POST` | `/api/ads` | Create locally + push to platform |
| `PUT` | `/api/ads/{id}` | Update locally + push to platform |
| `DELETE` | `/api/ads/{id}` | Delete locally + soft-delete on platform |
| `GET` | `/api/ads` | List from local DB |
| `GET` | `/api/ads/platform/{platform}/{accountId}` | **Sync from platform then return DB** |

---

## Current Meta Integration Details

- **Graph API version:** `v23.0` (configurable via `FACEBOOK_MARKETING_API_VERSION`)
- **Base URL:** `https://graph.facebook.com/{version}/`
- **Auth:** Bearer token in `Authorization` header
- **Pagination:** Cursor-based (`paging.cursors.after`)
- **Delete convention:** POST `/{id}` with `status=DELETED` (not HTTP DELETE)
- **Ad account format:** Always prefixed with `act_` (enforced by `MetaForm.normalizeAct()`)

**Current fields synced:**

| Object | Fields pulled |
|---|---|
| Campaign | `id`, `name`, `status` |
| AdSet | `id`, `name`, `status`, `campaign_id` |
| Ad | `id`, `name`, `status`, `adset_id` |

Full raw API responses are stored in Redis keyed by external ID for frontend display.

---

## Future Platform Recommendations

### Meta (Improvements)

The current integration is functional but minimal. Recommended improvements:

#### 1. Webhook-based real-time sync

Instead of polling on every list request, subscribe to Meta's [Real-Time Updates (Webhooks)](https://developers.facebook.com/docs/marketing-api/webhooks):

```
Meta → POST /webhooks/meta
    └─ AbstractPlatformService.syncFromPlatform() for changed objects only
```

Benefits: no unnecessary API calls, immediate consistency, avoids rate limits.

#### 2. Token auto-refresh

Meta long-lived tokens expire in ~60 days. Add a scheduled job:

```java
@Scheduled(cron = "0 0 3 * * *")  // daily at 3am
public void refreshExpiringTokens() {
    // Find tokens expiring within 7 days
    // Call GET /oauth/access_token?grant_type=fb_exchange_token
    // Update oauth_accounts
}
```

#### 3. Sync more fields

Currently only `id`, `name`, `status` are synced. Add:

- Campaign: `objective`, `special_ad_categories`, `daily_budget`, `lifetime_budget`
- AdSet: `targeting`, `optimization_goal`, `billing_event`, `bid_amount`, `start_time`, `end_time`
- Ad: `creative` (image_hash, video_id, body, title, call_to_action)

These require schema changes (Liquibase migrations) and DTO/entity updates, but the strategy pattern means only `mapGetRow()`, `toCreateForm()`, and `FIELDS` constants need updating per entity.

#### 4. Bulk/Batch API

For accounts with thousands of objects, use Meta's [Batch API](https://developers.facebook.com/docs/graph-api/batch-requests/):

```
POST https://graph.facebook.com/
    batch=[
      {"method":"GET","relative_url":"act_123/campaigns?fields=id,name"},
      {"method":"GET","relative_url":"act_123/adsets?fields=id,name,campaign_id"},
      {"method":"GET","relative_url":"act_123/ads?fields=id,name,adset_id"}
    ]
```

Syncs all three levels in a single HTTP round-trip.

#### 5. Async sync with status tracking

Large accounts can have 10,000+ objects. Move sync to a background job:

```java
// POST /api/sync/trigger → returns jobId
// GET  /api/sync/status/{jobId} → returns progress
```

Use Spring's `@Async` or a job queue (e.g. Redis + Spring Batch).

---

### Instagram

Instagram Ads run **through the Meta Ads Manager** — the same Graph API and the same ad account. Instagram is a placement, not a separate platform.

**What this means for the integration:**

1. **No separate OAuth flow needed.** The existing Facebook `access_token` already covers Instagram placements if `instagram_basic` / `ads_management` scopes are granted.

2. **No separate strategy needed for basic Campaign/AdSet/Ad.** The same `MetaCampaignStrategy`, `MetaAdSetStrategy`, `MetaAdStrategy` apply. Instagram-targeting is controlled at the AdSet level via the `targeting` field:

```json
{
  "publisher_platforms": ["instagram"],
  "instagram_positions": ["stream", "story", "reels"]
}
```

3. **What IS different — Instagram-native content:**
   - Instagram organic posts, stories, and reels fetched via `/{ig-user-id}/media`
   - These use a different base URL: `https://graph.instagram.com/`
   - Requires `instagram_basic` scope and a separate IG User ID

**Recommended approach:** Add `Provider.INSTAGRAM` as a placement filter on AdSet, not a completely separate platform. Add an `InstagramContentStrategy` only if you need to manage organic IG content separately.

**New strategy files needed:**
- `MetaAdSetStrategy` update: add `instagram_placements` field support
- Optional: `InstagramOrganicStrategy` for fetching/posting organic content via `graph.instagram.com`

---

### TikTok

TikTok has its own Marketing API ([TikTok for Business API](https://ads.tiktok.com/marketing_api/docs)), completely separate from Meta.

**Hierarchy on TikTok:**

```
Advertiser Account
    └── Campaign
            └── Ad Group  (≈ AdSet)
                    └── Ad
```

This maps well to the existing Campaign → AdSet → Ad structure.

**Key differences from Meta:**

| Aspect | Meta | TikTok |
|---|---|---|
| Auth | OAuth 2.0 Bearer token | OAuth 2.0 with `Access-Token` header |
| Base URL | `graph.facebook.com/v23.0/` | `business-api.tiktok.com/open_api/v1.3/` |
| Account prefix | `act_123` | `advertiser_id=123` (query param) |
| List response | `data.data[]` | `data.list[]` |
| Pagination | `paging.cursors.after` | `page` + `page_size` + `total_number` |
| Delete | POST `status=DELETED` | POST `{operation_status: "DELETE"}` |
| Create response | `{id: "123"}` | `{data: {campaign_id: "123"}}` |

**Steps to add TikTok support:**

1. **OAuth registration** — Add TikTok app credentials, implement TikTok OAuth callback (`/login/oauth2/code/tiktok`). TikTok uses a non-standard OAuth flow (custom `auth_code` param).

2. **Add `TikTokPlatformClient`** implementing `PlatformClient`:
   - Base URL: `https://business-api.tiktok.com/open_api/v1.3`
   - Auth header: `Access-Token: {token}` (not `Bearer`)
   - Response unwrapping: `response.data.list[]` vs Meta's `response.data[]`

3. **Add strategies** (one per domain):

```java
// TikTokCampaignStrategy
createPath  → "campaign/create/"        (POST body JSON, not form-encoded)
listPath    → "campaign/get/"
updatePath  → "campaign/update/"
FIELDS      → campaign_id, campaign_name, status, objective_type

// TikTokAdGroupStrategy  (= AdSet equivalent)
createPath  → "adgroup/create/"
listPath    → "adgroup/get/"

// TikTokAdStrategy
createPath  → "ad/create/"
listPath    → "ad/get/"
```

> **Important:** TikTok API uses JSON bodies (not `application/x-www-form-urlencoded`). The `PlatformClient` interface may need a `postJson(path, body, token)` method added.

4. **Register in registries:**

```java
// In CampaignStrategyRegistry
case TIKTOK -> tiktokCampaignStrategy;
```

5. **Page-based pagination** — TikTok uses numeric pages, not cursor strings. Override the pagination loop in a `TikTokPlatformClient` helper or add a pagination strategy abstraction.

6. **Provider enum** — `Provider.TIKTOK` already exists in the codebase.

---

## Adding a New Platform — Checklist

```
[ ] Add OAuth credentials to application.yml + environment variables
[ ] Implement MetaOAuthService equivalent for the new platform
[ ] Add OAuthAccountEntity row for Provider.NEW_PLATFORM
[ ] Create NewPlatformClient implements PlatformClient
[ ] Register in PlatformClientRegistry
[ ] Create NewPlatformCampaignStrategy implements CampaignStrategy
[ ] Create NewPlatformAdSetStrategy implements AdSetStrategy
[ ] Create NewPlatformAdStrategy implements AdStrategy
[ ] Register each in their StrategyRegistry (campaignStrategyRegistry, etc.)
[ ] Verify Provider enum has the new value
[ ] Update TokenService if auth header format differs
[ ] Add cache TTL config in application.yml (app.cache.*)
[ ] Test: sync → create → update → delete round-trip
```

The template method (`AbstractPlatformService`) and hook pattern means zero changes are needed to sync logic, caching, or database persistence — only the strategy and client layers change per platform.
