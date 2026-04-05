# TravelApp — Marketing Campaign Manager

## Project Overview

Angular 21 SSR application for managing Meta/Facebook ad campaigns, creatives, and assets. Users connect their ad accounts via OAuth, sync campaign data, and create ads through a multi-step workflow.

## Tech Stack

- **Framework:** Angular 21 with SSR (Express)
- **UI:** Angular Material 21, PrimeNG 21, Bootstrap 5.3, Font Awesome
- **Forms:** Reactive Forms with FormBuilder
- **State:** RxJS 7.8 (observables, async pipe)
- **Notifications:** ngx-toastr
- **Testing:** Karma + Jasmine

## Project Structure

```
src/app/
├── components/        # Reusable UI components
│   ├── create-ad-workflow/    # Multi-step ad creation UI + logic
│   ├── meta/                  # Meta ads dashboard (campaigns, adsets, ads)
│   ├── analytics/             # Analytics charts
│   ├── insights/              # Insights view
│   └── ...
├── pages/             # Page-level components (routed)
│   ├── sync-accounts/         # Multi-platform account connection
│   └── creative-library/      # Asset library browser
├── services/
│   ├── core/core.service.ts   # Base HTTP service (all services extend this)
│   ├── campaign/              # Campaign CRUD + sync
│   ├── adset/                 # Ad set operations
│   ├── ad/                    # Ad CRUD
│   ├── ad-creative/           # Creative management
│   └── asset/                 # Asset library upload/download
├── models/            # TypeScript interfaces
│   ├── campaign/campaign.ts
│   └── adset/adset.model.ts   # AdSetResponse, AdResponse, StoredAssetDto, CreativeDto
├── data/              # Static enums and lookup data
├── guards/            # authGuard (protects all main routes)
└── configs/
```

## Routing

All routes except `/login`, `/sign-up`, `/home`, `/oauth-success` are protected by `authGuard`.

| Route | Component |
|---|---|
| `/meta` | MetaComponent — main dashboard |
| `/create-ad-workflow` | CreateAdWorkflowComponent |
| `/sync-accounts` | SyncAccountsComponent |
| `/creative-library` | CreativeLibraryComponent |
| `/insights` | InsightsComponent |

## Key Flows

### Ad Creation (`/create-ad-workflow`)
1. Select Name → Platform → Campaign → Ad Set → Status
2. Choose creative:
   - **Picker (existing):** Select from synced Meta creatives
   - **Asset-based:** Pick from asset library → choose variant (1:1, 4:5, 9:16, 1.91:1) → fill Page, Headline, Message, URL → submit to create creative
3. Publish via `AdService.create()` → redirect to `/meta`

### Account Sync (`/sync-accounts`)
1. Connect platform via OAuth (Meta, TikTok, Pinterest, Google Ads)
2. Backend saves long-lived token
3. Sync campaigns → ad sets → ads in order
4. Data stored with FK relationships (Campaign → AdSet → Ad)

## Service Layer Conventions

- All services extend `CoreService` which wraps `HttpClient`
- `CoreService` base methods: `getAll()`, `getByPath()`, `getOneById()`, `post()`, `patch()`
- Auth token is attached via HTTP interceptor
- `actId` (ad account ID) is stored in `AuthStoreService` and passed to API calls

## Models

### Campaign
```typescript
{ id?, name, status, platform, userId?, adAccountId, externalId?, objective, specialAdCategories?, adSets?, rawData? }
```

### AdSetResponse
```typescript
{ id, name, status, platform, adAccountId, externalId, campaignId, dailyBudget, lifetimeBudget, optimizationGoal, billingEvent, targeting?, rawData? }
```

### StoredAssetDto
```typescript
{ id, userId, assetType, originalFilename, mimeType, sizeBytes, hash, status: 'PROCESSING'|'READY'|'FAILED', variants: StoredAssetVariantDto[] }
```

### CreativeDto
```typescript
{ id, name?, status?, object_type?, thumbnail_url?, image_url? }
```

## Dev Commands

```bash
npm start        # Dev server on port 5000
npm run build    # Production build
npm test         # Karma unit tests
```

## Key Patterns

- Use `async` pipe in templates; avoid manual `subscribe` where possible
- Use `finalize()` for cleanup (e.g., resetting loading flags)
- Toast notifications via `ToastrService` for user feedback
- Loading states on all API calls; disable form controls during load
- Cascading dropdowns: campaign selection triggers ad set load
- Object URLs for asset thumbnails — revoke after use
- Reactive Forms with per-field error messages

## Important Notes

- `AD_ACCOUNT_SYNC_GUIDE.md` in `src/` has full OAuth and sync architecture documentation
- Meta standard events list (`SE_LIST`) lives in `meta.component.ts` (100+ conversion events)
- Image variants are generated server-side: `ORIGINAL`, `META_SQUARE_1080`, `META_PORTRAIT_1080`, `META_LANDSCAPE_1080`, `META_STORY_1080`
- Platform key is `'META'` (uppercase enum value) throughout
