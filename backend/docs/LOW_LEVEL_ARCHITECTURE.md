# Low-Level Architecture — Current State

## Overview

This document describes the current technical architecture of the Marketing Campaign Manager platform. The system is a full-stack SaaS application composed of an Angular 21 SSR frontend and a Spring Boot 3.2 backend, integrated with the Meta Graph API for ad campaign management.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend framework | Angular 21 with SSR (Express) |
| UI libraries | Angular Material 21, PrimeNG 21, Bootstrap 5.3 |
| Forms & state | Reactive Forms, RxJS 7.8 |
| Backend framework | Spring Boot 3.2 / Java 21 |
| ORM & migrations | Spring Data JPA, Liquibase |
| Object mapping | MapStruct |
| Authentication | JWT (custom) |
| Cache | Redis (`PlatformRawDataCache`) |
| Database | PostgreSQL |
| File storage | MinIO (S3-compatible, Docker) |
| External API | Meta Graph API v23.0 |

---

## Frontend Architecture

### Layer Structure

```
src/app/
├── components/
│   ├── create-ad-workflow/     # Multi-step ad creation UI + logic
│   ├── meta/                   # Campaigns, ad sets, ads dashboard
│   ├── analytics/              # Charts
│   └── insights/               # Insights view
├── pages/
│   ├── sync-accounts/          # OAuth connection flow
│   └── creative-library/       # Asset library browser
├── services/
│   ├── core/core.service.ts    # Base HTTP service (all services extend this)
│   ├── campaign/               # Campaign CRUD + sync
│   ├── adset/                  # Ad set operations
│   ├── ad/                     # Ad CRUD
│   ├── ad-creative/            # Creative management
│   └── asset/                  # Asset library upload/download
├── models/                     # TypeScript interfaces
├── data/                       # Static enums and lookup data
├── guards/                     # authGuard (protects all main routes)
└── configs/
```

### Routing

All routes except `/login`, `/sign-up`, `/home`, and `/oauth-success` are protected by `authGuard`.

| Route | Component | Description |
|---|---|---|
| `/meta` | `MetaComponent` | Main campaign dashboard |
| `/create-ad-workflow` | `CreateAdWorkflowComponent` | Multi-step ad builder |
| `/sync-accounts` | `SyncAccountsComponent` | Platform OAuth connection |
| `/creative-library` | `CreativeLibraryComponent` | Asset browser |
| `/insights` | `InsightsComponent` | Performance insights |

### Key Frontend Patterns

- All services extend `CoreService`, which wraps `HttpClient` with base methods: `getAll()`, `getByPath()`, `getOneById()`, `post()`, `patch()`
- JWT token is attached to every request via an HTTP interceptor
- `actId` (ad account ID) is stored in `AuthStoreService` and passed to API calls
- `async` pipe used in templates to avoid manual subscriptions
- `finalize()` used for cleanup (e.g. resetting loading flags)
- Reactive Forms with per-field error messages
- Object URLs for asset thumbnails — revoked after use

### Ad Creation Flow

```
1. Select Name → Platform → Campaign → Ad Set → Status
2. Choose creative:
   a. Picker — select from synced Meta creatives
   b. Asset-based — pick from asset library → choose variant
      (1:1 / 4:5 / 9:16 / 1.91:1) → fill Page, Headline, Message, URL
3. Publish via AdService.create() → redirect to /meta
```

---

## Backend Architecture

### Layer Structure per Domain

Each domain (`campaign`, `adset`, `ad`, `ad-creative`, `asset`, etc.) follows:

```
{domain}/
├── api/          # REST controller (extends BaseController)
├── service/      # Business logic (extends AbstractPlatformService)
├── strategy/     # Platform-specific API calls (e.g. MetaCampaignStrategy)
├── entity/       # JPA entity (extends BasePlatformEntity)
├── dto/          # Request / response DTOs
├── mapper/       # MapStruct mapper (Entity ↔ DTO)
└── repository/   # Spring Data JPA repository
```

### Key Abstractions

#### `AbstractPlatformService<E, D, S>`

Located in `infrastructure/service/platformserviceimpl/`. Template method base for all platform services. Implements common logic for:

- `createOnPlatform`
- `updateOnPlatform`
- `deleteOnPlatform`
- `listFromPlatform`
- `syncFromPlatform`

Subclasses override hooks: `newEntity()`, `applyDtoToNewEntity()`, `findExisting()`, `cacheRawData()`.

#### Strategy Pattern — `MetaXxxStrategy`

Encapsulates all Meta Graph API calls per entity type (campaign, ad set, ad, creative, asset). Adding a new platform requires only a new strategy implementation — no changes to service logic.

#### `BasePlatformEntity`

Mapped superclass with shared fields across all platform entities:

| Field | Type | Description |
|---|---|---|
| `externalId` | String | Platform-side ID |
| `platform` | Enum | `META`, `TIKTOK`, etc. |
| `adAccountId` | String | Ad account reference |
| `status` | String | Entity status |
| `createdAt` | Timestamp | Record created |
| `updatedAt` | Timestamp | Record updated |

#### `PlatformRawDataCache` (Redis)

Located in `infrastructure/cache/`. Caches raw API responses from Meta.

- Key pattern: `rawdata:{type}:{platform}:{adAccountId}:{externalId}`
- TTLs configured per entity type in `application.yml` under `app.cache.*`

### Authentication Flow

```
1. User registers via POST /api/auth/register
2. User logs in via POST /api/auth/login → receives JWT
3. JWT attached by frontend HTTP interceptor on all subsequent requests
4. Backend validates JWT on every protected endpoint
```

### OAuth + Ad Account Sync Flow

```
1. POST /api/oauth/meta/connect — backend creates state, returns OAuth URL
2. User redirects to Meta OAuth consent screen
3. Meta redirects to GET /login/oauth2/code/facebook?code=XXX&state=YYY
4. Backend exchanges short-lived token for long-lived token (60 days)
5. OAuthAccountEntity saved (userId, provider, accessToken, tokenExpiry)
6. Frontend redirected to /oauth-success?provider=META&status=connected
7. User selects ad account → POST /api/ad-account-connections
8. Sync in order:
   GET /api/campaigns/platform/META/{adAccountId}
   GET /api/ad-sets/platform/META/{adAccountId}
   GET /api/ads/platform/META/{adAccountId}
```

All sync operations are idempotent — re-running updates existing records.

### Asset Pipeline

```
1. User uploads image/video via creative library
2. Backend receives file → stores original in MinIO
3. Image processor generates 5 Meta variants:
   - ORIGINAL
   - META_SQUARE_1080      (1:1)
   - META_PORTRAIT_1080    (4:5)
   - META_LANDSCAPE_1080   (1.91:1)
   - META_STORY_1080       (9:16)
4. All variants stored in MinIO
5. URLs served back to frontend for preview and ad creation
```

---

## Database Schema

### Core Tables

```
users
  id, email, password, created_at

oauth_accounts
  id, user_id (FK), provider, external_user_id,
  access_token, token_expiry, granted_scopes

ad_account_connection
  id, user_id (FK), provider, ad_account_id,
  ad_account_name, currency, timezone_name, active

campaign
  id, user_id (FK), external_id, name, status,
  platform, ad_account_id, objective

ad_set
  id, campaign_id (FK), external_id, name, status,
  platform, ad_account_id, daily_budget, optimization_goal

ad
  id, ad_set_id (FK), external_id, name, status,
  platform, ad_account_id, creative_id

stored_asset
  id, user_id (FK), asset_type, original_filename,
  mime_type, size_bytes, hash, status

stored_asset_variant
  id, asset_id (FK), variant_type, storage_key, width, height
```

### Foreign Key Hierarchy

```
users
 └── oauth_accounts       (user_id → users.id)
 └── ad_account_connection (user_id → users.id)
 └── campaign             (user_id → users.id)
      └── ad_set          (campaign_id → campaign.id)
           └── ad         (ad_set_id → ad_set.id)
 └── stored_asset         (user_id → users.id)
      └── stored_asset_variant (asset_id → stored_asset.id)
```

### Schema Migrations

Managed by Liquibase. Master changelog at `src/main/resources/db.changelog/db.changelog-master.xml`. New migration files go in `src/main/resources/db.changelog/changes/`.

---

## Infrastructure

| Component | Purpose | Config |
|---|---|---|
| PostgreSQL | Primary data store | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Redis | Platform raw data cache | `REDIS_HOST`, `REDIS_PORT` (default: localhost:6379) |
| MinIO | Asset file storage (S3-compatible) | Started via `docker-compose.yml` |
| Meta Graph API | Campaign sync and ad management | `FB_ACCESS_TOKEN`, `FB_AD_ACCOUNT_ID`, `FACEBOOK_MARKETING_API_VERSION` |

### Environment Variables

```
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
FB_ACCESS_TOKEN
FB_AD_ACCOUNT_ID
FACEBOOK_MARKETING_API_VERSION   # default: v23.0
JWT_SECRET
```

---

## API Endpoints Reference

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Login, returns JWT |

### OAuth & Ad Accounts

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/oauth/meta/connect` | Initiate Meta OAuth |
| `GET` | `/login/oauth2/code/facebook` | OAuth callback (auto-handled) |
| `POST` | `/api/ad-account-connections` | Save ad account connection |
| `GET` | `/api/ad-account-connections` | List connected accounts |
| `PUT` | `/api/ad-account-connections/{id}/activate` | Set active account |

### Campaign Data

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/campaigns/platform/{platform}/{adAccountId}` | Sync campaigns |
| `GET` | `/api/ad-sets/platform/{platform}/{adAccountId}` | Sync ad sets |
| `GET` | `/api/ads/platform/{platform}/{adAccountId}` | Sync ads |
| `PATCH` | `/api/campaigns/{id}/status` | Update campaign status |
| `PATCH` | `/api/ad-sets/{id}/status` | Update ad set status |
| `PATCH` | `/api/ads/{id}/status` | Update ad status |

### Creatives & Assets

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/page/asset-creative/assets/upload` | Upload asset |
| `GET` | `/api/page/asset-creative/assets` | List assets |
| `POST` | `/api/ad-creative` | Create ad creative |
| `POST` | `/api/ads` | Create ad |

---

## Build & Run

```bash
# Frontend
npm start          # Dev server on port 5000
npm run build      # Production build
npm test           # Karma unit tests

# Backend
./mvnw clean install
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=ClassName#methodName

# Infrastructure
docker-compose up  # Starts MinIO
```
