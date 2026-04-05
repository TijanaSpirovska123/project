# Frontend Implementation Prompt — Marketing Campaign Manager

> **For:** Claude Code (or any AI coding agent)
> **Stack:** Angular 21 SSR, Angular Material 21, PrimeNG 21, Bootstrap 5.3, RxJS 7.8, ngx-toastr
> **Backend:** Already fully implemented. Do not generate any backend code.

---

## How to Use This Prompt

Work through the phases **in order**. Do not start Phase 2 until Phase 1 is complete and confirmed.
At the end of each phase, list what was changed and ask for confirmation before proceeding.

**After all phases are done**, stop and wait. The OAuth linking flow will be provided as a
separate document (`META_OAUTH_LINKING_SPEC.md`) and implemented as a follow-up session.

---

## Project Context

This is a SaaS marketing platform where users connect their ad accounts (Meta, TikTok, etc.)
and manage campaigns, ad sets, ads, and creatives from one unified interface.

The application already exists and is running. You are **improving and extending** it —
not building from scratch. Preserve all existing functionality. Do not delete or rewrite
working code unless a specific item below requires it.

### Existing tech stack
- Angular 21 with SSR (Express)
- Angular Material 21, PrimeNG 21, Bootstrap 5.3, Font Awesome
- Reactive Forms with FormBuilder
- RxJS 7.8 (observables, async pipe)
- ngx-toastr for notifications
- Karma + Jasmine for tests

### Existing routes (all protected by authGuard except /login, /sign-up, /home, /oauth-success)
- `/meta` — MetaComponent (campaigns, ad sets, ads overview)
- `/create-ad-workflow` — CreateAdWorkflowComponent
- `/sync-accounts` — SyncAccountsComponent
- `/creative-library` — CreativeLibraryComponent
- `/insights` — InsightsComponent

### Design system (apply consistently to everything you touch)
```
Primary teal:        #10B981  (buttons, active states, icons)
Teal dark:           #059669  (hover, pressed)
Teal light:          #D1FAE5  (selected rows, tinted backgrounds)
Teal xlight:         #ECFDF5  (subtle tints, sidebar active bg)

Text primary:        #111827
Text secondary:      #6B7280
Text tertiary:       #9CA3AF  (placeholders, disabled)

Background page:     #F9FAFB
Background card:     #FFFFFF
Background subtle:   #F3F4F6  (row hover, input bg)

Border default:      #E5E7EB
Border focus:        2px solid #10B981

Status active:       bg #D1FAE5  text #065F46
Status paused:       bg #FEF3C7  text #92400E
Status failed:       bg #FEE2E2  text #991B1B
Status archived:     bg #F3F4F6  text #374151
Status processing:   bg #DBEAFE  text #1E40AF
Status ready:        bg #D1FAE5  text #065F46

Border radius:  4px badges/pills | 8px inputs/buttons | 12px cards/dropdowns/modals | 16px panels
Box shadow:     0 4px 12px rgba(0,0,0,0.10) for floating panels
Font sizes:     24px h1 | 20px h2 | 16px h3 | 14px body | 12px labels (uppercase, letter-spacing 0.05em)
```

---

## ❌ Do NOT Implement (Back-End-Dependent, Deferred)

Skip these entirely. The backend endpoints do not exist yet.
Do not create placeholder UI, stub calls, or TODO comments for these items —
just leave the relevant existing UI as-is.

| Feature | Reason to skip |
|---|---|
| Meta OAuth connect / disconnect flow | Handled separately after this session (META_OAUTH_LINKING_SPEC.md) |
| Tag management on creative assets (add/remove tags, tag filter bar, tag pills on cards) | Backend tag endpoints not yet implemented |
| Asset status polling (PROCESSING → READY without page refresh) | Backend polling endpoint not yet ready |
| Cross-platform comparison mode in Insights | Backend compare endpoint not yet implemented |
| Sync progress step indicator (step 1/3, 2/3, 3/3) | Backend does not yet return per-step progress |
| Insights metric card persistence (save/load to DB) | Backend user_config endpoints not yet implemented |
| Column customization persistence in Meta overview | Backend column-config endpoints not yet implemented |
| Bulk status update (pause/enable multiple rows at once) | Backend bulk endpoint not yet implemented |
| Password reset / forgot password page | Backend reset endpoints not yet implemented |
| Session expiry warning modal | Depends on JWT expiry tracking — out of scope for now |
| Drag-and-drop upload in Creative Library | Lower priority — implement only if all other items are done |
| Upload progress bar per file | Lower priority — implement only if all other items are done |
| Breadcrumb bar | Lower priority — implement only if all other items are done |

---

## Phase 1 — Shared Foundation Components

Build these first. Every other phase depends on them.

### 1.1 `AppLoadingStateComponent` (standalone)

```
Selector: app-loading-state
Inputs:
  [isLoading]: boolean
  [error]: string | null
  [retryLabel]: string = 'Retry'
  [loadingTemplate]: TemplateRef (optional — if provided, render it instead of default spinner)
Output:
  (retry): EventEmitter<void>

Behaviour:
  - When isLoading=true and no loadingTemplate provided: show centred teal spinner (32px)
  - When isLoading=true and loadingTemplate provided: render the template (for skeletons)
  - When error is not null: show error message + Retry button (teal outlined)
  - When isLoading=false and error=null: render ng-content (the actual content)
```

### 1.2 `AppEmptyStateComponent` (standalone)

```
Selector: app-empty-state
Inputs:
  [icon]: string  (Font Awesome class, e.g. 'fa-chart-bar')
  [title]: string
  [message]: string
  [actionLabel]: string | null = null
Output:
  (action): EventEmitter<void>

Style:
  Centred column layout, icon 48px teal, title 16px bold #111827,
  message 14px #6B7280, action button teal filled (only shown when actionLabel is set)
```

### 1.3 `AppErrorStateComponent` (standalone)

```
Selector: app-error-state
Inputs:
  [message]: string
Output:
  (retry): EventEmitter<void>

Style:
  Red icon + message text + teal outlined "Retry" button, centred
```

### 1.4 Global loading pattern — apply to every existing component

After building the shared components, audit every component that makes HTTP calls.
Apply this pattern everywhere using `finalize()`:

```typescript
isLoading = false;
error: string | null = null;

loadData(): void {
  this.isLoading = true;
  this.error = null;
  this.service.getData().pipe(
    finalize(() => this.isLoading = false)
  ).subscribe({
    next: data => this.data = data,
    error: err => this.error = err.message ?? 'Something went wrong'
  });
}
```

Wrap the template content with `<app-loading-state>`:
```html
<app-loading-state [isLoading]="isLoading" [error]="error" (retry)="loadData()">
  <!-- actual content here -->
</app-loading-state>
```

### 1.5 Global HTTP error interceptor

Create an Angular `HttpInterceptorFn` that:
- On `401`: clears the JWT from memory and navigates to `/login`
- On `0` (network error): shows a persistent banner at the top of the page:
  "Connection issue — check your internet connection" with a Retry link
- On `500`+: does nothing (handled per-component)
- Does NOT intercept `4xx` (handled per-component)

Register it in `app.config.ts` alongside the existing auth interceptor.

---

## Phase 2 — Sidebar

The sidebar has two bugs: no tooltips on collapsed icons.

### 2.1 Fix layout reflow on expand/collapse

```css
/* The sidebar and the main content area must transition together */
.sidebar {
  width: 64px;   /* collapsed */
  transition: width 200ms cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}
.sidebar.expanded {
  width: 220px;
}
.main-content {
  margin-left: 64px;
  transition: margin-left 200ms cubic-bezier(0.4, 0, 0.2, 1);
}
.main-content.sidebar-expanded {
  margin-left: 220px;
}
```

Do not use `*ngIf` to show/hide the labels — use `opacity` and `max-width` transitions
so the layout does not reflow. Labels fade out instead of popping out.

### 2.2 Active state

```css
.nav-item.active {
  background: #ECFDF5;
  border-left: 3px solid #10B981;
  border-radius: 0 8px 8px 0;
  color: #059669;
}
.nav-item:hover:not(.active) {
  background: #F3F4F6;
}
```

### 2.3 Tooltips on collapsed icons

When sidebar is collapsed, add `matTooltip="Page Name"` with `matTooltipPosition="right"`
to each nav item. Remove the tooltip when sidebar is expanded (bind `[matTooltipDisabled]="expanded"`).

### 2.4 Dark mode — persist and apply without flash

In `index.html`, add this inline `<script>` as the very first child of `<head>` (before any stylesheets):
```html
<script>
  (function() {
    var theme = localStorage.getItem('theme');
    if (theme === 'dark') document.body.classList.add('dark-mode');
  })();
</script>
```

In the sidebar Dark Mode toggle handler, toggle `document.body.classList` and save to `localStorage`.
All components use `.dark-mode` parent CSS class for dark overrides — no `@media prefers-color-scheme`.

### 2.5 Mobile — bottom navigation bar

At viewport width < 768px:
- Hide the left sidebar entirely
- Show a fixed bottom bar (height 56px, white bg, border-top `1px solid #E5E7EB`)
- Five icon tabs: the same 5 nav items as the sidebar
- Active tab: teal icon + 2px teal top border on the tab
- Safe area padding at bottom for notched phones: `padding-bottom: env(safe-area-inset-bottom)`

---

## Phase 3 — Sync Accounts Page

**Important:** Do NOT implement the Connect / Disconnect / Reconnect buttons yet.
Those are covered by the OAuth spec in the follow-up session.
Only implement the connection status display and the Sync Data flow.

### 3.1 Load and display connection status on page init

On `ngOnInit`, call `GET /api/ad-account-connections`.
Expected response shape:
```typescript
interface AdAccountConnectionSummary {
  provider: 'META' | 'TIKTOK' | 'PINTEREST' | 'GOOGLE' | 'LINKEDIN' | 'REDDIT';
  connected: boolean;
  lastSynced: string | null;  // ISO timestamp
}
```

Use this data to drive the visual state of each platform card:

**Not connected state:**
- No badge
- Sync Data button: `[disabled]="true"`, cursor not-allowed,
  `matTooltip="Connect your account first"` on hover
- Connect button: teal outlined (leave its click handler as-is for now)

**Connected state:**
- Green "✓ Connected" badge in top-right of card
- Last synced text below badge: "Last synced 2 hours ago" (use Angular's `DatePipe` or a simple pipe)
- Sync Data button: enabled (teal filled)

**Card structure for all three visual states:**
```html
<div class="platform-card" [class.is-connected]="isConnected('META')">
  <div class="card-header">
    <img src="assets/icons/meta.svg" alt="Meta" />
    <span class="platform-name">Meta</span>
    <span *ngIf="isConnected('META')" class="badge-connected">✓ Connected</span>
  </div>
  <p *ngIf="lastSynced('META')" class="last-synced-text">
    Last synced {{ lastSynced('META') | date:'short' }}
  </p>
  <div class="card-actions">
    <button class="btn btn-outline">Connect</button>
    <button class="btn btn-primary"
            [disabled]="!isConnected('META') || isSyncing"
            [matTooltip]="!isConnected('META') ? 'Connect your account first' : null"
            (click)="syncData('META')">
      <i class="fa fa-sync" [class.fa-spin]="isSyncing"></i>
      {{ isSyncing ? 'Syncing…' : 'Sync Data' }}
    </button>
  </div>
</div>
```

### 3.2 Sync Data flow

When user clicks Sync Data (only enabled when connected):
1. Set `isSyncing = true`, disable button, show spinner icon
2. Call sequentially:
   - `GET /api/campaigns/platform/META/{adAccountId}`
   - `GET /api/ad-sets/platform/META/{adAccountId}`
   - `GET /api/ads/platform/META/{adAccountId}`
3. On full success: `this.toastr.success('Sync complete')`, reload connection status (updates lastSynced)
4. On any failure: `this.toastr.error('Sync failed — try again')`, re-enable button
5. Always: set `isSyncing = false` in `finalize()`

Get `adAccountId` from `AuthStoreService` (already exists in the app).

---

## Phase 4 — Creative Library

### 4.1 Platform filter tabs

Add a row of pill buttons between the existing status filters and the asset grid:

```
[ All ] [ Meta ] [ TikTok ] [ Pinterest ] [ Google Ads ] [ LinkedIn ] [ Reddit ]
```

- Default selected: "All"
- Active pill: `background: #10B981; color: white; border-color: #10B981`
- Inactive pill: `background: white; border: 1px solid #E5E7EB; color: #374151`
- Filter is **client-side** — filter assets based on whether they have variants
  matching the selected platform
  - Meta variants: `META_SQUARE_1080`, `META_PORTRAIT_1080`, `META_LANDSCAPE_1080`, `META_STORY_1080`
  - TikTok (future): `TIKTOK_FEED`, `TIKTOK_SQUARE`
  - "All" shows every asset regardless
- When a platform is selected, the variant count badge on each card
  updates to show count for that platform only (e.g. "3 Meta variants")
- Platform filter state persists while user stays on this page (component state, not URL)

### 4.2 Card hover overlay with quick actions

Each asset card gets a hover overlay:

```css
.asset-card { position: relative; overflow: hidden; }
.asset-card-overlay {
  position: absolute; inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center; gap: 12px;
  opacity: 0;
  transition: opacity 150ms ease;
}
.asset-card:hover .asset-card-overlay { opacity: 1; }
```

Three action icon buttons in the overlay (white icons, transparent background):
- **Publish** (fa-share-alt) — opens the existing Push to Platform modal
- **Edit Tags** — disabled for now (tooltip: "Coming soon") — do not implement tag editing
- **Delete** (fa-trash) — shows confirmation dialog, then calls `DELETE /api/assets/{id}`

### 4.3 Empty states

Add two empty states:

**No assets at all:**
```html
<app-empty-state
  icon="fa-images"
  title="Your creative library is empty"
  message="Upload images or videos to build your creative library."
  actionLabel="Upload Creative"
  (action)="openUploadDialog()">
</app-empty-state>
```

**Filtered — no results:**
```html
<app-empty-state
  icon="fa-filter"
  title="No assets match your filters"
  message="Try a different platform or status filter."
  actionLabel="Clear filters"
  (action)="clearFilters()">
</app-empty-state>
```

---

## Phase 5 — Insights Page

### 5.1 Platform tabs

Add a tab row at the very top of the page (above the Object Selection panel):

```
[ Meta ] [ TikTok ] [ Google Ads ] [ LinkedIn ] [ Pinterest ] [ Reddit ]
```

- Load connected platforms from `GET /api/ad-account-connections` on page init
- Connected platform tabs: fully clickable, teal active state
- Disconnected platform tabs: `opacity: 0.5`, lock icon, `pointer-events: none`,
  `matTooltip="Connect this platform in Sync Accounts"`
- Switching tabs:
  - Clears the object selection checkboxes
  - Reloads campaigns, ad sets, ads for the new platform:
    `GET /api/campaigns?platform={platform}`, etc.
  - Resets date range to 30d
  - Clears metric cards and charts (show empty state)
- Default active tab on page load: first connected platform, or Meta if none connected

### 5.2 Disable Sync button when nothing selected

```typescript
get canSync(): boolean {
  return this.selectedCampaignIds.length > 0
      || this.selectedAdSetIds.length > 0
      || this.selectedAdIds.length > 0;
}
```

```html
<button [disabled]="!canSync || isSyncing"
        [matTooltip]="!canSync ? 'Select at least one campaign, ad set, or ad' : null"
        (click)="syncInsights()">
  Sync
</button>
```

### 5.3 Unified date range control

Consolidate the existing 7d/14d/30d/90d quick selectors and the FROM/TO date pickers
into a single visually grouped control. They should sit on the same row, clearly related:

```
[ Last 7d ] [ Last 14d ] [ Last 30d ] [ Last 90d ]   |   FROM [02/18/2026 📅]  →  TO [03/20/2026 📅]
```

- Quick selectors: pill buttons (outlined, active = teal filled)
- When a quick selector is clicked: it becomes active AND the FROM/TO inputs update automatically
- When FROM/TO is changed manually: no quick selector is active (deselect all pills)
- Position this control below the platform tabs, above the Object Selection panel

**Replace the existing date pickers** with the custom `DateRangePickerComponent`
described in `UI_COMPONENT_PROMPTS.md`. Build that component now if it hasn't been built yet.
Key requirements for the date picker:
- Left/right chevron navigation (not up/down arrows)
- Range highlight between start and end dates (teal xlight: `#ECFDF5`)
- Selected start/end: teal filled cell (`#10B981`, white text)
- Today: teal ring, no fill
- Panel: white, `border-radius: 12px`, `box-shadow: 0 4px 16px rgba(0,0,0,0.10)`

### 5.4 Skeleton loaders on metric cards

While insights data is loading (after Sync is clicked), show skeleton placeholders
on each metric card instead of blank cards:

```html
<!-- in each metric card -->
<ng-container *ngIf="!isLoadingInsights; else metricSkeleton">
  <!-- actual metric value -->
</ng-container>
<ng-template #metricSkeleton>
  <div class="skeleton-circle"></div>
  <div class="skeleton-bar wide"></div>
  <div class="skeleton-bar narrow"></div>
</ng-template>
```

```css
.skeleton-circle { width: 40px; height: 40px; border-radius: 50%; background: #F3F4F6; animation: pulse 1.5s ease-in-out infinite; }
.skeleton-bar { height: 14px; border-radius: 4px; background: #F3F4F6; animation: pulse 1.5s ease-in-out infinite; }
.skeleton-bar.wide { width: 80%; margin-top: 12px; }
.skeleton-bar.narrow { width: 50%; margin-top: 8px; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
```

Also show skeleton for the Performance Over Time chart area:
```html
<div *ngIf="isLoadingInsights" class="skeleton-chart"></div>
```
```css
.skeleton-chart { width: 100%; height: 280px; background: #F3F4F6; border-radius: 8px; animation: pulse 1.5s ease-in-out infinite; }
```

---

## Phase 6 — Meta Overview (Campaigns, Ad Sets, Ads)

### 6.1 Status badges

Replace plain text status values in the table with filled pill badges:

```html
<span class="status-badge" [ngClass]="'badge-' + row.status.toLowerCase()">
  {{ row.status | titlecase }}
</span>
```

```css
.status-badge { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.badge-active    { background: #D1FAE5; color: #065F46; }
.badge-paused    { background: #FEF3C7; color: #92400E; }
.badge-deleted   { background: #FEE2E2; color: #991B1B; }
.badge-archived  { background: #F3F4F6; color: #374151; }
```

### 6.2 On/Off toggle — spinner during update

When user clicks the toggle:
1. Replace the toggle with a small teal spinner (20px) on that row
2. Call `PATCH /api/campaigns/{id}/status` (or ad-sets/ads)
3. Optimistic update: immediately flip the status in the local array
4. On success: restore the toggle with the new state
5. On failure: revert the optimistic update, restore toggle, show toast error

```typescript
togglingIds = new Set<number>();

toggleStatus(row: Campaign): void {
  if (this.togglingIds.has(row.id)) return;
  this.togglingIds.add(row.id);
  const originalStatus = row.status;
  row.status = row.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';  // optimistic

  this.campaignService.updateStatus(row.id, row.status).pipe(
    finalize(() => this.togglingIds.delete(row.id))
  ).subscribe({
    next: () => this.toastr.success('Status updated'),
    error: () => {
      row.status = originalStatus;  // revert
      this.toastr.error('Failed to update status');
    }
  });
}
```

In template:
```html
<ng-container *ngIf="!togglingIds.has(row.id); else toggleSpinner">
  <mat-slide-toggle [checked]="row.status === 'ACTIVE'" (change)="toggleStatus(row)">
  </mat-slide-toggle>
</ng-container>
<ng-template #toggleSpinner>
  <i class="fa fa-spinner fa-spin" style="color: #10B981; font-size: 20px;"></i>
</ng-template>
```

### 6.3 Empty state for table

When the table has no rows after loading:
```html
<app-empty-state
  icon="fa-bullhorn"
  title="No campaigns synced yet"
  message="Connect your Meta account and sync to see your campaigns here."
  actionLabel="Go to Sync Accounts"
  (action)="router.navigate(['/sync-accounts'])">
</app-empty-state>
```

---

## Phase 7 — Create Ad Workflow

### 7.1 Page selector dropdown (new required field)

Add a "Facebook Page" dropdown to the Create Ad form.
Position it in the form between Platform and Campaign.

```typescript
// In CreateAdWorkflowComponent
pages: MetaPage[] = [];
isLoadingPages = false;
pagesError: string | null = null;

loadPages(): void {
  this.isLoadingPages = true;
  this.pagesError = null;
  this.metaService.getPages().pipe(
    finalize(() => this.isLoadingPages = false)
  ).subscribe({
    next: pages => this.pages = pages,
    error: () => this.pagesError = 'Failed to load pages'
  });
}
```

Call `loadPages()` in `ngOnInit()`.

Template:
```html
<div class="form-field">
  <label>FACEBOOK PAGE *</label>
  <select formControlName="pageId" [disabled]="isLoadingPages">
    <option value="" disabled>
      {{ isLoadingPages ? 'Loading pages…' : 'Select a page' }}
    </option>
    <option *ngFor="let page of pages" [value]="page.id">{{ page.name }}</option>
  </select>
  <small *ngIf="pagesError" class="field-error">
    {{ pagesError }} — <a (click)="loadPages()">retry</a>
  </small>
  <small *ngIf="!isLoadingPages && pages.length === 0 && !pagesError" class="field-hint">
    No pages found. Make sure your Meta account has at least one page.
  </small>
</div>
```

Service method (add to your existing Meta service):
```typescript
getPages(): Observable<MetaPage[]> {
  return this.getAll<MetaPage[]>('/api/meta/pages');
}
```

Model:
```typescript
interface MetaPage { id: string; name: string; category?: string; }
```

`pageId` is a required field — `Publish Ad` button must be disabled until it has a value.

### 7.2 Replace campaign and ad set dropdowns with SearchableDropdownComponent

Build the `SearchableDropdownComponent` described in `UI_COMPONENT_PROMPTS.md`.
Then replace the campaign dropdown and the ad set dropdown in the Create Ad form with it.

Key props for campaign dropdown:
```html
<app-searchable-dropdown
  [options]="campaignOptions"
  [loading]="isLoadingCampaigns"
  placeholder="Select a campaign"
  (selectionChange)="onCampaignSelected($event)">
</app-searchable-dropdown>
```

The `SearchableDropdownComponent` must implement `ControlValueAccessor` so it works with `formControlName`.

### 7.3 Fix creative panel loading

Right panel "Select Ad Creative" tab:
- On tab open: immediately show a skeleton (4 rows of grey bars, matching creative item height)
- Use `finalize()` to clear `isLoadingCreatives`
- When list is ready: skeleton disappears, list renders
- If fetch fails: show `<app-error-state>` with Retry

Right panel empty state (nothing selected yet):
```html
<div *ngIf="!selectedCreative" class="creative-empty-state">
  <i class="fa fa-image" style="font-size: 48px; color: #9CA3AF;"></i>
  <p>Select a creative from the list, or create one from your asset library</p>
</div>
```

### 7.4 Publish Ad button states

```html
<button class="btn btn-primary btn-publish"
        [disabled]="!form.valid || isPublishing"
        (click)="publishAd()">
  <i *ngIf="isPublishing" class="fa fa-spinner fa-spin"></i>
  {{ isPublishing ? 'Publishing…' : 'Publish Ad' }}
</button>
```

`form.valid` requires: ad name, platform, campaign, ad set, page, and creative to all have values.

---

## Phase 8 — Responsive Design Pass

After all above phases are done, do a responsive audit of every page.

### Breakpoints to use
```
xs: < 480px
sm: 480–767px
md: 768–1023px
lg: 1024–1279px
xl: ≥ 1280px
```

### Rules per page

**Sync Accounts:**
- xl/lg: 4 cards in a row (existing)
- md: 2 cards per row
- sm/xs: 1 card per row, full-width buttons

**Creative Library grid:**
- xl: 5 columns
- lg: 4 columns
- md: 3 columns
- sm/xs: 2 columns

**Insights Object Selection:**
- md and below: Object Selection collapses to an accordion (collapsed by default),
  expanded by clicking "Edit selection (3 campaigns selected)"
- Metric cards: 2 per row on md, 1 per row on sm/xs
- Chart: full width, height reduced to 220px on sm/xs

**Meta Overview table:**
- md: show Name, Status, On/Off + horizontal scroll for other columns, sticky first column
- sm/xs: show Name, Status only — tap a row to open a bottom sheet with full details

**Create Ad workflow:**
- md: two panels stack vertically (form top, creative panel below)
- sm/xs: single-panel stepper with 3 steps:
  - Step 1: "Ad Details" (name, platform, page, campaign, ad set, status)
  - Step 2: "Choose Creative"
  - Step 3: "Review & Publish"
  - Progress indicator at top: ● ● ○ with step labels
  - Next / Back buttons at the bottom

**Minimum touch targets on mobile:** All buttons and interactive elements must be at least 44×44px.

---

## Phase 9 — Final Polish Pass

### 9.1 Consistent spinner behaviour — audit

Go through every component. Confirm:
- Every `subscribe()` block has `finalize(() => this.isLoading = false)`
- No spinner is left running after data loads or after an error
- No content is shown "empty" without a spinner, skeleton, or error explaining why

### 9.2 Toast rules — enforce consistently

All `ToastrService` calls must follow these rules:
- Success: auto-dismiss 4000ms
- Error: no auto-dismiss (user must close manually)
- Warning: auto-dismiss 6000ms
- Never show raw API error messages to the user — always use friendly strings

### 9.3 Form validation style

All required field labels must show `*` in teal (`color: #10B981`), not browser-default red.
All inline error messages below fields: `font-size: 12px; color: #EF4444; margin-top: 4px`.

---

## What Comes Next (Do NOT Start This Now)

After confirming all 9 phases above are complete, the next session will implement the
Meta OAuth account-linking flow using `META_OAUTH_LINKING_SPEC.md`.

That document covers:
- `POST /oauth/meta/connect` → receive `{ authorizationUrl }` → `window.location.href` redirect
- OAuth callback landing (reading `?status=connected&provider=META` query params on return)
- Connect / Reconnect / Disconnect button behaviour on the Sync Accounts card
- Typed models: `OAuthConnectResponse`, `AdAccountConnectionSummary`
- `OAuthService` methods: `initiateMetaConnect()`, `disconnectMeta()`
- Error reason mapping to user-friendly messages

The Connect button and Disconnect button on the Sync Accounts page are intentionally
left as stubs in this session. Do not implement their click handlers.
