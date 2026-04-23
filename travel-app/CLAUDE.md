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

## Dark Mode & Light Mode — Mandatory Rule

This app always runs in two modes: **light** (default) and **dark** (`[data-theme="dark"]`).

**Every new component, panel, modal, drawer, or UI element MUST have dark mode styles at the time it is created — not as an afterthought.**

### Where to add dark mode styles

All dark mode overrides live in `src/styles.scss` inside the `[data-theme="dark"] { }` block.
Component `.scss` files use hardcoded SCSS variables for light mode — do NOT add `:host` or component-level dark overrides. Keep all dark mode in `styles.scss`.

### Active / selected button rule

All active/selected state buttons must use **solid teal** (`#1ca698` / `$primary` / `$clr-primary-a0`) — never the gradient `linear-gradient(135deg, #1ca698, #27ae60)`. This keeps them consistent with the platform tab (Meta, TikTok, etc.) active state.

- **Light mode:** `background: #1ca698; color: white; border-color: #1ca698;`
- **Dark mode** (in `styles.scss`): `background: $clr-primary-a0 !important; color: $clr-surface-a0 !important;`
- Applies to: tab buttons, preset/period pills, sync/action buttons, any toggle that has an active state

### Modal header rule

Modal headers must **never** use the teal/green gradient. They must blend with the modal background:
- **Light mode:** `background: white; color: #20233a; border-bottom: 1px solid #e8ebf2;`
- **Dark mode** (in `styles.scss`): `background: $clr-surface-tonal-a0; color: $clr-primary-a40; border-bottom-color: $clr-surface-tonal-a20;`
- Close buttons: light → `color: #64748b`, hover `rgba(0,0,0,0.08)`; dark → `color: $clr-surface-tonal-a50`, hover `rgba(255,255,255,0.08)`

### Pattern for every new element

1. Write the light mode styles in the component `.scss` file using the existing SCSS variables (`$bg-card`, `$text-primary`, `$border`, etc.)
2. Immediately add a matching dark mode block inside the component's own `:host-context([data-theme="dark"])` block using these actual hex values:
   - Backgrounds: `#1b2323` (card/panel), `#303838` (input/subtle/hover), `#3a4444` (deep hover)
   - Text (body): `#bbf2ed`
   - Text (muted/secondary): `#919595`
   - Text (hover accent): `#48e0d6`
   - Borders: `#464d4d`
   - Accent / active: `#48e0d6` (teal)

### Checklist before finishing any new UI work

- [ ] Opened the app in dark mode and verified every element is visible
- [ ] No white/light backgrounds in dark mode
- [ ] No dark text on dark backgrounds
- [ ] Added dark mode block in `styles.scss` under the correct component comment section
- [ ] Slide-in panels, modals, and drawers styled for both modes

## Session Expiry Behavior

When a 401 is received from the backend due to JWT expiry:
1. AuthStoreService.markSessionExpired() is called
2. All existing toasts are cleared (toastr.clear())
3. User is redirected to /login
4. No error toast messages are shown during this redirect

When showing error toasts in any service or component, always check before showing:

  if (!this.authStore.isSessionExpiredRedirect()) {
    this.toastr.error('message');
  }

The sessionExpired flag resets automatically when the user logs in again.

Note: META_TOKEN_EXPIRED (401 with code META_TOKEN_EXPIRED) is handled
separately — it does not log the user out and has its own notification flow.
