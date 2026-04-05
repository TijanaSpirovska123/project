# Use Cases & Features — Marketing Campaign Manager
### Backend / Frontend separated throughout

---

## Table of Contents

1. [Authentication & User Management](#1-authentication--user-management)
2. [Sync Accounts — Platform Connection](#2-sync-accounts--platform-connection)
3. [Creative Library](#3-creative-library)
4. [Insights](#4-insights)
5. [Meta Overview — Campaigns, Ad Sets, Ads](#5-meta-overview--campaigns-ad-sets-ads)
6. [Create Ad Workflow](#6-create-ad-workflow)
7. [Navigation & Layout](#7-navigation--layout)
8. [Cross-Cutting: Loading States & Error Handling](#8-cross-cutting-loading-states--error-handling)
9. [Master Implementation Checklist](#9-master-implementation-checklist)

---

## 1. Authentication & User Management

### Current State
- User registers with email and password
- User logs in and receives JWT
- JWT is attached to all requests via HTTP interceptor
- `authGuard` protects all routes except `/login`, `/sign-up`, `/home`, `/oauth-success`

---

### UC-1.1 Register

**Actor:** New user

**Steps:**
1. User navigates to `/sign-up`
2. Enters email and password
3. System creates account, returns JWT
4. User is redirected to `/sync-accounts`

#### 🔧 Backend
- `POST /api/auth/register` — accepts `{ email, password }`, validates uniqueness, hashes password (BCrypt), creates `UserEntity`, returns JWT
- Return `400` with field-level error if email already exists: `{ field: "email", message: "Email already in use" }`
- Password minimum length enforced server-side (8 characters)

#### 🖥️ Frontend
- Reactive Form with `email` and `password` controls
- Inline error shown below email field if duplicate: "An account with this email already exists"
- Password strength indicator (weak / medium / strong) shown below password field
- On success: auto-login (store JWT in memory), redirect to `/sync-accounts`
- Submit button disabled while request is in flight, shows spinner inside button

---

### UC-1.2 Login

**Actor:** Existing user

**Steps:**
1. User navigates to `/login`
2. Enters email and password
3. System validates credentials, returns JWT
4. User redirected to last visited page or `/meta` as default

#### 🔧 Backend
- `POST /api/auth/login` — validates credentials, returns JWT + expiry timestamp
- Return `401` with message "Invalid email or password" (do not specify which field is wrong — security)
- JWT expiry: configurable, default 24 hours

#### 🖥️ Frontend
- Store last visited route in `sessionStorage` before redirect to login
- After successful login, redirect to stored route or `/meta`
- Show generic error "Invalid email or password" below the form (not per-field) on `401`
- JWT stored in memory (Angular service), never in `localStorage`

---

### Missing / To Implement

#### 🔧 Backend
- [ ] Password reset endpoint: `POST /api/auth/forgot-password` → sends reset email
- [ ] Reset token validation: `POST /api/auth/reset-password`

#### 🖥️ Frontend
- [ ] "Forgot password?" link on login page
- [ ] Session expiry warning modal: "You will be logged out in 2 minutes" — show 2 minutes before JWT expiry
- [ ] Remember last visited route and restore after login

---

## 2. Sync Accounts — Platform Connection

### Current State
- Four platform cards: Meta (active), TikTok / Pinterest / Google Ads (coming soon)
- Meta has Connect and Sync Data buttons
- **Bug:** Sync button is always enabled regardless of connection state
- **Bug:** No connected/disconnected indicator on the Meta card after OAuth
- Other platforms correctly show "Coming Soon" / "Not Available"

---

### UC-2.1 Connect Meta Account

**Actor:** Authenticated user
**Precondition:** User is not yet connected to Meta

**Steps:**
1. User clicks Connect on Meta card
2. System initiates OAuth flow
3. User grants permissions on Meta
4. System stores long-lived token
5. User returns to app with connection confirmed

#### 🔧 Backend
- `POST /api/oauth/meta/connect` (authenticated) — creates `OAuthConnectRequestEntity` with UUID state, 10-min expiry, sets `oauth_connect_state` cookie, returns `{ authorizationUrl }`
- OAuth callback `GET /login/oauth2/code/facebook` — validates state cookie, exchanges short-lived token for long-lived (60-day) token, saves/updates `OAuthAccountEntity`, redirects to `/oauth-success?provider=META&status=connected`
- `GET /api/ad-account-connections` — returns list of connected platforms for the authenticated user: `[{ provider: "META", connected: true, lastSynced: "..." }]`
- `DELETE /api/oauth/meta/disconnect` — revokes token, deletes `OAuthAccountEntity`

#### 🖥️ Frontend
- On `/sync-accounts` page init: call `GET /api/ad-account-connections`, derive connected state per platform
- Meta card renders in one of three states based on API response:
  - `not-connected`: Connect button (teal outlined), Sync button disabled with tooltip "Connect your account first"
  - `connected`: green "Connected ✓" badge top-right of card, last synced time, Connect replaced by "Manage" button, Sync button enabled (teal filled)
  - `syncing`: Sync button replaced by spinner + "Syncing…", all buttons disabled
- On `/oauth-success`: read query params, show success toast, redirect to `/sync-accounts`
- Do not derive connection state from local storage — always fetch from API on page load

---

### UC-2.2 Sync Campaign Data

**Actor:** Authenticated user (connected)

#### 🔧 Backend
- `GET /api/campaigns/platform/META/{adAccountId}` — fetches from Meta Graph API, upserts to DB, returns count
- `GET /api/ad-sets/platform/META/{adAccountId}` — same pattern
- `GET /api/ads/platform/META/{adAccountId}` — same pattern
- `PATCH /api/ad-account-connections/{id}/last-synced` — updates `lastSynced` timestamp after successful sync
- All sync endpoints are idempotent (upsert by `externalId`)

#### 🖥️ Frontend
- Sync button disabled unless `connected === true` (derived from `GET /api/ad-account-connections`)
- On click: button shows spinner + "Syncing…", disable all card buttons
- Call sync endpoints sequentially (campaigns → ad sets → ads), show progress: "Syncing campaigns (1/3)…"
- On full success: toast "Sync complete — 34 campaigns, 20 ad sets, 7 ads", update last synced time on card
- On partial failure: warning toast per failed step "Ad sets sync failed — campaigns and ads were synced"
- On full failure: error toast with Retry button

---

### UC-2.3 Disconnect Meta Account

#### 🔧 Backend
- `DELETE /api/oauth/meta/disconnect` — revokes Meta token via Graph API, deletes `OAuthAccountEntity` and associated `AdAccountConnectionEntity`

#### 🖥️ Frontend
- Disconnect option shown as a secondary button or in a "Manage" dropdown when connected
- Show confirmation dialog: "Disconnect Meta? Your synced data will remain but you won't be able to sync new data."
- On confirm: call `DELETE`, update card to `not-connected` state, disable Sync button

---

### Missing / To Implement

#### 🔧 Backend
- [ ] `GET /api/ad-account-connections` — return connection status per platform per user *(currently missing)*
- [ ] `DELETE /api/oauth/meta/disconnect` — token revocation and cleanup *(currently missing)*
- [ ] `PATCH /api/ad-account-connections/{id}/last-synced` — update last sync timestamp *(currently missing)*

#### 🖥️ Frontend
- [ ] Fetch connection status on page load — derive Sync button enabled/disabled from API
- [ ] Three visual states for Meta card: `not-connected`, `connected`, `syncing`
- [ ] Sync progress indicator (step 1/3, 2/3, 3/3)
- [ ] Last synced timestamp shown on card
- [ ] Disconnect confirmation dialog

---

## 3. Creative Library

### Current State
- Grid of uploaded images/videos, status badges (READY / PROCESSING / FAILED)
- Filter by type (All / Images / Videos) and status
- Variant count badge per asset (e.g. "4 variants")
- Clicking asset opens Push to Platform modal
- Publish to Meta modal: ad variant selector, creative name, destination URL
- Currently generates Meta variants only: 1:1, 4:5, 9:16, 1.91:1
- Tags not implemented
- Platform filter tabs not implemented

---

### UC-3.1 Upload Creative Asset

#### 🔧 Backend
- `POST /api/page/asset-creative/assets/upload` (multipart) — accepts image (JPG, PNG, GIF, WebP) and video (MP4, MOV)
- On receipt: save original to MinIO, create `StoredAssetEntity` with status `PROCESSING`
- Background job: generate platform variants — for each connected platform crop to its required dimensions:
  - Meta: ORIGINAL, META_SQUARE_1080 (1:1), META_PORTRAIT_1080 (4:5), META_LANDSCAPE_1080 (1.91:1), META_STORY_1080 (9:16)
  - TikTok (future): TIKTOK_FEED (9:16 1080×1920), TIKTOK_SQUARE (1:1)
  - Pinterest (future): PIN_STANDARD (2:3 1000×1500), PIN_SQUARE (1:1)
- Update status to `READY` (or `FAILED`) when all variants processed
- `GET /api/page/asset-creative/assets/{id}/status` — polling endpoint returning current status and variant list

#### 🖥️ Frontend
- Upload tile and "Upload Creative" button both trigger file picker
- Support drag-and-drop onto the grid (Angular CDK DragDrop or native dragover/drop events)
- Multiple file upload supported — each file gets its own card immediately as `PROCESSING`
- Show upload progress bar per file (use `HttpRequest` with `reportProgress: true`)
- Poll `GET /api/.../assets/{id}/status` every 3 seconds until status is `READY` or `FAILED`
- On READY: card badge changes from `PROCESSING` (blue) to `READY` (teal), variant count updates
- On FAILED: card badge changes to `FAILED` (red), show retry option on hover

---

### UC-3.2 Filter Assets by Platform (New)

#### 🔧 Backend
- `GET /api/page/asset-creative/assets?platform=META` — filter assets that have at least one variant of type matching the platform
- Alternatively: filter client-side since all variant data is returned per asset

#### 🖥️ Frontend
- Platform filter pill buttons above the grid: All | Meta | TikTok | Pinterest | Google Ads | LinkedIn | Reddit
- Client-side filter: show only assets that have at least one variant for the selected platform
- "All" tab shows every asset
- Platform filter persists for the session (stored in component state)
- Variant badge on card updates to show count for the selected platform (e.g. "3 Meta variants")

---

### UC-3.3 Tag Assets (New — Not Implemented)

#### 🔧 Backend
- Add `tags` field (`TEXT[]` or junction table) to `stored_asset` table — Liquibase migration required
- `POST /api/assets/{id}/tags` — body `{ tags: ["summer", "product"] }`, replaces existing tags
- `GET /api/page/asset-creative/assets?tags=summer,product` — filter by one or more tags (AND logic)
- Auto-tag on upload: assign tags based on detected aspect ratio ("portrait", "landscape", "square")

#### 🖥️ Frontend
- Tag pills shown on asset card thumbnail (bottom-left, max 2 visible, "+N more" if more)
- Right-click or context menu on card → "Edit Tags" opens a tag editor popover
- Tag editor: chip-style input — type tag + press Enter to add, click × on chip to remove
- Tag filter bar below the platform filter: tag chips appear when tags exist, click to filter
- Tags searchable in the existing "Search by filename" search bar

---

### UC-3.4 Push Asset to Platform

#### 🔧 Backend
- `POST /api/assets/{id}/publish` — body `{ platform: "META", variantType: "META_SQUARE_1080", creativeName?: string, destinationUrl?: string }`
- Backend selects the correct variant from MinIO, calls Meta Graph API to upload the image hash
- Returns `{ success: true, creativeId: "..." }` or `{ success: false, error: "..." }`
- For future platforms: same endpoint with different `platform` value, strategy pattern routes to correct API

#### 🖥️ Frontend
- Clicking an asset card opens Push to Platform modal
- Platform grid: Meta, TikTok, Pinterest, Google Ads — greyed out with lock icon if user is not connected
- On platform selection: show platform-specific form (variant selector, creative name, URL)
- Variant selector shows thumbnail preview + dimensions for each variant
- Destination URL field: validate format on blur
- Publish button shows spinner while in flight
- On success: toast "Published to Meta successfully"
- On partial failure (one platform fails): show per-platform status in modal

---

### Missing / To Implement

#### 🔧 Backend
- [ ] Tags field on `stored_asset` — Liquibase migration + `POST /api/assets/{id}/tags` + `GET ?tags=`
- [ ] Platform-specific variant generation (TikTok, Pinterest etc.) when those platforms are added
- [ ] `GET /api/assets/{id}/status` polling endpoint for processing status

#### 🖥️ Frontend
- [ ] Platform filter tabs in Creative Library
- [ ] Tag management UI (edit tags popover, tag filter bar, tags on card)
- [ ] Drag-and-drop upload
- [ ] Upload progress bar per file
- [ ] Asset status polling (PROCESSING → READY / FAILED without page refresh)
- [ ] Hover overlay on cards with quick actions (Publish, Edit Tags, Delete)

---

## 4. Insights

### Current State
- Object Selection: three-column checkboxes (Campaigns / Ad Sets / Ads)
- Date filters: 7d / 14d / 30d / 90d + custom From/To pickers
- Metric cards (Impressions, Reach, Clicks, Spend, CTR, CPM) — metric is customizable
- Performance Over Time chart (line/bar toggle)
- Top Performers table + Spend Distribution chart
- **Bug:** Sync button always enabled — should be disabled when nothing is selected
- **Missing:** Platform tabs (all insights are Meta-only currently)
- **Missing:** Metric card customization not persisted to DB

---

### UC-4.1 Load Insights for Selected Objects

#### 🔧 Backend
- `POST /api/insights/sync` — body `{ platform, entityIds: [{ type: "CAMPAIGN", id: "..." }], dateFrom, dateTo }`
- Calls Meta Insights API for each selected entity, stores results in `insight_snapshot` table
- Returns aggregated metrics: `{ impressions, reach, clicks, spend, ctr, cpm, cpc, roas }`
- `GET /api/insights?entityIds=...&dateFrom=...&dateTo=...` — returns stored insight data for charts
- `GET /api/insights/top-performers?entityIds=...&dateFrom=...&dateTo=...` — returns ranked rows for Top Performers table

#### 🖥️ Frontend
- Object Selection panel loads campaigns, ad sets, ads from local DB on page load (fast, no API call)
- Sync button disabled until at least one campaign, ad set, or ad is checked — show tooltip "Select at least one object"
- Date range: quick selectors (7d / 14d / 30d / 90d) and custom From/To pickers grouped together below platform tabs
- On Sync click: button shows spinner, all metric cards show skeleton loaders, charts show skeleton
- On data received: skeleton disappears, metric cards and charts populate
- On error: show error state in each card individually with Retry

---

### UC-4.2 Platform Tabs (New)

#### 🔧 Backend
- No new endpoints needed — existing insights endpoints accept `platform` parameter
- Object Selection endpoints (`GET /api/campaigns`, `GET /api/ad-sets`, `GET /api/ads`) already have platform field in DB — filter by `?platform=META`

#### 🖥️ Frontend
- Platform tabs at the very top of the Insights page: Meta | TikTok | Google Ads | LinkedIn | Pinterest | Reddit
- On page load: fetch connected platforms from `GET /api/ad-account-connections`, grey out + lock icon disconnected tabs
- Switching tab: clears object selection, reloads object lists filtered by new platform, resets date range to 30d, clears metric cards/charts
- Active tab: teal background, white text
- Disconnected tab: grey text, lock icon, tooltip "Connect this platform in Sync Accounts"

---

### UC-4.3 Persist Metric Card Customization

#### 🔧 Backend
- `PATCH /api/users/insights-config` — body `{ metricCards: ["impressions", "reach", "clicks", "spend", "ctr", "cpm"] }` (ordered array of 6 metric IDs)
- `GET /api/users/insights-config` — returns saved config or null (use default if null)
- Store in new `user_config` table: `user_id`, `config_type` (`INSIGHTS_METRICS`), `config_json`

#### 🖥️ Frontend
- On Insights page init: `GET /api/users/insights-config` → apply saved metric card order/selection
- On metric card click → show dropdown of available metrics → on selection: update card immediately + `PATCH /api/users/insights-config`
- If no saved config: use default order [impressions, reach, clicks, spend, ctr, cpm]

---

### UC-4.4 Cross-Platform Comparison (New)

#### 🔧 Backend
- `POST /api/insights/compare` — body `{ platforms: ["META", "TIKTOK"], entityType: "CAMPAIGN", dateFrom, dateTo }`
- Returns normalized metrics per platform: `{ META: { spend, impressions, clicks, ctr }, TIKTOK: { ... } }`
- Normalization: map platform-specific field names to internal schema (see HIGH_LEVEL_ARCHITECTURE.md)

#### 🖥️ Frontend
- "Compare Platforms" toggle button top-right of page (only enabled if 2+ platforms connected)
- On enable: secondary platform selector appears, user picks second platform
- Chart renders two data series, colour-coded per platform (Meta=teal, TikTok=black, Google=blue etc.)
- Metric cards show two values with platform badges: "Meta: $1,240 | TikTok: $880"
- Only normalised metrics shown in comparison mode (spend, impressions, clicks, CTR, CPC)

---

### Missing / To Implement

#### 🔧 Backend
- [ ] `PATCH /api/users/insights-config` and `GET /api/users/insights-config` — user_config table + endpoints
- [ ] `POST /api/insights/compare` — cross-platform normalized comparison endpoint
- [ ] `insight_snapshot` table — Liquibase migration to store fetched insight data

#### 🖥️ Frontend
- [ ] Platform tabs at the top of Insights page
- [ ] Sync button disabled when no object is selected
- [ ] Load and apply persisted metric card config on page init
- [ ] Save metric card changes immediately via PATCH
- [ ] Cross-platform comparison UI (toggle, secondary platform selector, dual-series chart)
- [ ] Skeleton loaders on metric cards and charts while data loads
- [ ] Consolidate date range controls (quick selectors + date pickers in one visual group)
- [ ] Custom date range picker component (see UI_COMPONENT_PROMPTS.md)

---

## 5. Meta Overview — Campaigns, Ad Sets, Ads

### Current State
- Tabs: Campaigns / Ad Sets / Ads
- Table with On/Off toggle, Status, Reach, Actions, + (customize columns)
- Customize Columns modal working — **not persisted to DB (bug)**
- Search, Export, Pagination all working

---

### UC-5.1 View and Manage Table Data

#### 🔧 Backend
- `GET /api/campaigns?page=0&size=10&search=&sort=name,asc` — paginated campaigns from DB
- `GET /api/ad-sets?campaignId=&page=&size=&search=` — paginated ad sets
- `GET /api/ads?adSetId=&page=&size=&search=` — paginated ads
- `PATCH /api/campaigns/{id}/status` — update status on Meta + update local DB
- `PATCH /api/ad-sets/{id}/status` — same
- `PATCH /api/ads/{id}/status` — same
- All status patches return the updated entity so frontend can update the row optimistically

#### 🖥️ Frontend
- Table loads from local DB — fast, no Meta API call
- Status badges: filled pill — Active (teal), Paused (amber), Deleted (red), Archived (grey)
- On/Off toggle: optimistic update (flip immediately), revert on API error with toast
- Toggle shows spinner instead of the toggle itself while API call is in flight
- Switching Campaigns/Ad Sets/Ads tabs reloads the respective table from DB
- Search: debounce 300ms, client-side filter on already-loaded data, clear (×) button

---

### UC-5.2 Persist Column Customization

#### 🔧 Backend
- `PATCH /api/users/column-config` — body `{ entityType: "CAMPAIGN" | "AD_SET" | "AD", columns: ["name", "status", "spend", "impressions", ...] }` (ordered array)
- `GET /api/users/column-config?entityType=CAMPAIGN` — returns saved column list or null
- Store in `user_config` table (same table as insights config, different `config_type`)
- Default column sets defined server-side, returned when user has no saved config

#### 🖥️ Frontend
- On Meta overview page init: `GET /api/users/column-config?entityType=CAMPAIGN` → build column definitions
- Customize Columns modal: on Apply → immediately update table + `PATCH /api/users/column-config`
- Separate config per entity type (campaigns, ad sets, ads)
- If GET returns null: use hardcoded default column set

---

### Missing / To Implement

#### 🔧 Backend
- [ ] `PATCH /api/users/column-config` and `GET /api/users/column-config` *(currently missing — customization not persisted)*
- [ ] Bulk status update: `PATCH /api/campaigns/bulk-status` — body `{ ids: [], status: "PAUSED" }`

#### 🖥️ Frontend
- [ ] Load persisted column config on page init
- [ ] Save column config via PATCH on Apply
- [ ] Spinner on toggle during status update (replace toggle with spinner, restore on response)
- [ ] Bulk action: checkbox per row + "Pause selected" / "Enable selected" bulk action bar

---

## 6. Create Ad Workflow

### Current State
- Two-panel: Create Ad form (left) + Select Ad Creative panel (right)
- Fields: Ad Name, Platform (Meta only), Campaign, Ad Set, Status
- Choose Creative (existing) or Create Ad Creative (from asset library)
- **Bug:** Spinner does not disappear cleanly after data loads
- **Missing:** Page selector dropdown (required for ad creation)
- **Blocked:** Creating from new post requires app to exit Meta development mode

---

### UC-6.1 Create Ad from Existing Creative

#### 🔧 Backend
- `GET /api/campaigns?platform=META` — returns campaigns for platform dropdown (from DB)
- `GET /api/ad-sets?campaignId={id}` — returns ad sets for selected campaign (from DB)
- `GET /api/ad-creative?platform=META&adAccountId={id}` — fetches existing creatives from Meta Graph API, returns list
- `POST /api/ads` — body `{ name, platform, campaignId, adSetId, adCreativeId, status }` — creates ad on Meta, stores locally
- `GET /api/meta/pages` — returns list of Facebook pages for connected ad account *(currently missing)*

#### 🖥️ Frontend
- Campaign dropdown: load from DB on form init, uses custom `SearchableDropdownComponent` (see UI_COMPONENT_PROMPTS.md)
- Ad Set dropdown: disabled until campaign selected, show loading spinner while fetching, enable when loaded
- **Page dropdown: add to form — required field — load from `GET /api/meta/pages`, show spinner while loading, error if none found**
- Creative panel right side: show skeleton loader while creatives load, replace with list when ready, show error with Retry if fetch fails
- Publish Ad button: disabled until all required fields filled, shows spinner during publish, re-enables on error
- On success: toast "Ad published successfully", navigate to `/meta`

---

### UC-6.2 Create Ad from Asset Library

#### 🔧 Backend
- `POST /api/ad-creative` — body `{ assetId, variantType, pageId, headline?, primaryText?, destinationUrl? }` — creates Meta ad creative from asset, returns `{ creativeId }`
- This calls Meta Graph API: uploads image hash (if not already uploaded), creates creative with `object_story_spec`
- Returns created creative DTO

#### 🖥️ Frontend
- "Create Ad Creative" button switches right panel to Creative Library inline view
- User selects an asset → variant selector appears (shows thumbnail + dimensions per variant)
- Page dropdown appears (same as UC-6.1 — reuse component)
- Optional fields: Headline, Primary Text, Destination URL
- "Create Creative" button: shows spinner while API call in flight, disables all fields
- On success: creative appears in the Choose Creative area of the left panel automatically
- On failure: inline error "Creative creation failed — {reason}" with Retry

---

### UC-6.3 Loading State Consistency (This Screen)

#### 🔧 Backend
- All endpoints must return meaningful error messages in the response body, not just HTTP status codes
- e.g. `{ error: "No Meta pages found for this ad account", code: "NO_PAGES" }`

#### 🖥️ Frontend

The following spinners are currently broken or missing. Each must be fixed:

| Element | Trigger | Fix needed |
|---|---|---|
| Creative list (right panel) | On open/tab switch | Show skeleton, disappear immediately when list renders |
| Campaign dropdown | On form load | Uses `SearchableDropdownComponent` with `[loading]="true"` until data arrives |
| Ad Set dropdown | After campaign selected | Disabled + loading indicator, enable when data arrives |
| Page dropdown | On form load | Same as campaign — disabled + spinner until pages loaded |
| Publish Ad button | On Publish click | Spinner inside button, disable during request, re-enable on error |

**Angular pattern for all dropdowns:**
```typescript
isLoadingCampaigns = true;
this.campaignService.getAll().pipe(
  finalize(() => this.isLoadingCampaigns = false)
).subscribe(campaigns => this.campaigns = campaigns);
```

---

### Missing / To Implement

#### 🔧 Backend
- [ ] `GET /api/meta/pages` — fetch pages for connected ad account and return list *(currently missing)*
- [ ] Ensure all error responses include a human-readable `message` field

#### 🖥️ Frontend
- [ ] Add Page selector dropdown (required field) to Create Ad form
- [ ] Fix spinner on campaign/ad set/page dropdowns — use `finalize()`, disappear immediately on load
- [ ] Fix creative list loading — skeleton shown, removed as soon as list renders
- [ ] Replace campaign dropdown with custom `SearchableDropdownComponent`
- [ ] Mobile layout: single-panel stepper (Step 1: Details → Step 2: Creative → Step 3: Review)
- [ ] Empty state in right panel when no creative selected: illustration + "Select or create a creative"

---

## 7. Navigation & Layout

### Current State
- Collapsible left sidebar: icons only (collapsed) or icons + labels (expanded)
- Page shifts slightly on expand/collapse
- Menu: Create Ad, Meta, Insights, Creative Library, Sync Accounts
- Bottom: Dark Mode, Log Out

---

### UC-7.1 Sidebar

#### 🔧 Backend
- No backend work needed for sidebar

#### 🖥️ Frontend
- Sidebar width: 64px collapsed, 220px expanded
- CSS transition: `width 200ms cubic-bezier(0.4, 0, 0.2, 1)` on sidebar, `margin-left` transition on content — avoids layout reflow
- Active route: left border `3px solid #10B981` + teal background on item
- Tooltip on collapsed icons (Angular Material `matTooltip` positioned right)
- On tablet (768–1024px): starts collapsed
- On mobile (<768px): bottom navigation bar with 5 tabs, OR hamburger → full-height drawer overlay

---

### UC-7.2 Dark Mode

#### 🔧 Backend
- No backend work needed — preference stored client-side

#### 🖥️ Frontend
- On toggle: add/remove `.dark-mode` class on `<body>`
- Persist to `localStorage` key `theme`
- On app init (before first render): read `localStorage` and apply class synchronously in `index.html` `<script>` to avoid flash of wrong theme
- All component styles respond to `.dark-mode` parent class (CSS variable overrides)

---

### Missing / To Implement

#### 🔧 Backend
- No backend items for navigation

#### 🖥️ Frontend
- [ ] Smooth sidebar transition (CSS transition on width + content margin-left)
- [ ] Tooltip on collapsed sidebar icons
- [ ] Dark mode: persist to localStorage, apply before render (no flash)
- [ ] Mobile: bottom navigation bar or hamburger drawer
- [ ] Breadcrumb bar at top of each page ("Meta > Campaigns")

---

## 8. Cross-Cutting: Loading States & Error Handling

These rules apply to **every screen**. Inconsistent spinner behaviour is a recurring issue.

---

### Loading State Rules

#### 🔧 Backend
- All endpoints must respond within a reasonable time (< 2s for DB queries, < 5s for platform API calls)
- Long-running operations (sync, bulk actions) should return a `202 Accepted` with a job ID, then client polls `GET /api/jobs/{id}` for status
- Every error response must include: `{ status: number, message: string, code?: string }`

#### 🖥️ Frontend

| Context | Behaviour |
|---|---|
| Initial page load | Skeleton placeholder matching page layout (not full-page spinner) |
| Table data loading | Skeleton rows (4 grey bars) inside the table container |
| Dropdown loading | `[disabled]="true"`, small spinner icon on right of dropdown trigger |
| Chart loading | Grey rectangle matching chart height |
| Metric card loading | Grey circle (icon area) + two grey bars |
| Button action | Spinner inside button, button disabled, label changes ("Publishing…") |
| Spinner disappears | **Immediately when data is available** — use `finalize()` on every observable |

---

### Error State Rules

#### 🔧 Backend
- All 4xx/5xx responses return `{ status, message, code }` — never return plain strings
- 401: handled globally by HTTP interceptor → redirect to login
- 403: show "You don't have permission to do this"
- 404: handled per-component (show "not found" state)
- 5xx: show generic error with Retry

#### 🖥️ Frontend

| Context | Behaviour |
|---|---|
| API call fails | Inline error near the affected component + Retry button |
| Transient operations (sync complete, ad published) | Toast, auto-dismiss 4 seconds |
| Form validation | Inline error below the field |
| Empty state (no data) | Illustration + message + action button |
| Network error | Persistent banner at top: "Connection issue — Retry" |

**Angular implementation pattern:**
```typescript
// In every component that fetches data:
isLoading = false;
error: string | null = null;

loadData() {
  this.isLoading = true;
  this.error = null;
  this.service.getData().pipe(
    finalize(() => this.isLoading = false)
  ).subscribe({
    next: data => this.data = data,
    error: err => this.error = err.message
  });
}
```

**Shared components to build:**
- `<app-loading-state>` — wraps skeleton/spinner, accepts `[isLoading]` and `[error]` inputs
- `<app-empty-state>` — accepts `[icon]`, `[title]`, `[message]`, `[actionLabel]`, `(action)` output
- `<app-error-state>` — accepts `[message]`, `(retry)` output

---

## 9. Master Implementation Checklist

### 🔧 Backend — All Items

#### Authentication
- [ ] `POST /api/auth/forgot-password` — password reset email
- [ ] `POST /api/auth/reset-password` — token validation + password update

#### Sync Accounts
- [ ] `GET /api/ad-account-connections` — return connection status per platform per user
- [ ] `DELETE /api/oauth/meta/disconnect` — token revocation and entity cleanup
- [ ] `PATCH /api/ad-account-connections/{id}/last-synced` — update last sync timestamp

#### Creative Library
- [ ] Add `tags` column to `stored_asset` — Liquibase migration
- [ ] `POST /api/assets/{id}/tags` — set tags on an asset
- [ ] `GET /api/assets?tags=tag1,tag2` — filter by tags
- [ ] `GET /api/assets/{id}/status` — polling endpoint for processing status
- [ ] Platform-specific variant generation (TikTok, Pinterest specs) when platforms added

#### Insights
- [ ] `insight_snapshot` table — Liquibase migration
- [ ] `PATCH /api/users/insights-config` — save metric card configuration
- [ ] `GET /api/users/insights-config` — load metric card configuration
- [ ] `POST /api/insights/compare` — cross-platform normalized comparison

#### Meta Overview
- [ ] `user_config` table — Liquibase migration (stores column config + insights config)
- [ ] `PATCH /api/users/column-config` — save column configuration per entity type
- [ ] `GET /api/users/column-config?entityType=` — load column configuration
- [ ] `PATCH /api/campaigns/bulk-status` — bulk status update

#### Create Ad
- [ ] `GET /api/meta/pages` — fetch Meta pages for connected ad account

#### General
- [ ] Standardize all error responses: `{ status, message, code }`
- [ ] Long-running operations return `202 Accepted` + `GET /api/jobs/{id}` polling

---

### 🖥️ Frontend — All Items

#### Authentication
- [ ] Remember last visited route, redirect after login
- [ ] Session expiry warning modal (2 minutes before JWT expiry)
- [ ] Forgot password page

#### Sync Accounts
- [ ] Fetch and display connection status on page load
- [ ] Sync button disabled when not connected (tooltip on hover)
- [ ] Three visual states for each platform card: not-connected / connected / syncing
- [ ] Sync progress indicator (step 1/3, 2/3, 3/3)
- [ ] Last synced timestamp on card
- [ ] Disconnect confirmation dialog

#### Creative Library
- [ ] Platform filter pill tabs
- [ ] Tag management UI: add/remove tags, tag pills on cards
- [ ] Tag filter bar
- [ ] Drag-and-drop upload
- [ ] Upload progress bar per file
- [ ] Asset status polling (PROCESSING → READY/FAILED without page refresh)
- [ ] Card hover overlay with quick actions (Publish, Edit Tags, Delete)

#### Insights
- [ ] Platform tabs at top of page
- [ ] Disable Sync button when no object selected
- [ ] Load and apply persisted metric card config on init
- [ ] Save metric card changes via PATCH immediately on selection
- [ ] Cross-platform comparison toggle + dual-series chart
- [ ] Skeleton loaders on metric cards and charts
- [ ] Unified date range control (quick selectors + custom date pickers grouped)
- [ ] Custom date range picker component (see UI_COMPONENT_PROMPTS.md)

#### Meta Overview
- [ ] Load persisted column config on page init
- [ ] Save column config via PATCH on Apply
- [ ] Spinner on toggle during status update
- [ ] Bulk action: checkboxes + bulk pause/enable bar

#### Create Ad
- [ ] Add Page selector dropdown (required)
- [ ] Fix spinner on all dropdowns — use `finalize()`, disappear immediately
- [ ] Fix creative list skeleton — remove immediately when list renders
- [ ] Replace campaign/ad set/page dropdowns with custom `SearchableDropdownComponent`
- [ ] Empty state in right panel when no creative selected
- [ ] Mobile: single-panel stepper layout

#### Cross-Cutting
- [ ] Build shared `<app-loading-state>` component
- [ ] Build shared `<app-empty-state>` component
- [ ] Build shared `<app-error-state>` component
- [ ] All screens: consistent skeleton loaders on initial page load
- [ ] All screens: retry buttons on every API error
- [ ] Sidebar: smooth CSS transition (no layout reflow)
- [ ] Sidebar: tooltip on collapsed icons
- [ ] Dark mode: persist to localStorage, apply before render (no flash)
- [ ] Mobile: bottom navigation bar or drawer
- [ ] Breadcrumb bar at top of each page
