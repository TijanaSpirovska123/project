# Design Improvements & Responsive Design Guide

## Table of Contents

1. [Design System — Foundations](#1-design-system--foundations)
2. [Component-Level Improvements](#2-component-level-improvements)
3. [Responsive Design Breakpoints & Rules](#3-responsive-design-breakpoints--rules)
4. [Loading & Error States Design](#4-loading--error-states-design)
5. [Accessibility](#5-accessibility)

---

## 1. Design System — Foundations

Your current design is clean and has a strong identity: teal primary, white backgrounds, subtle grey borders, rounded cards. The improvements below build on what you have — they are evolutionary, not a redesign.

### Colour Palette (Formalise What You Have)

```
Primary teal:         #10B981   (buttons, active states, badges, icons)
Primary teal dark:    #059669   (button hover, pressed states)
Primary teal light:   #D1FAE5   (teal tinted backgrounds, selected rows)
Primary teal xlight:  #ECFDF5   (subtle tint for card headers, sidebar active)

Text primary:         #111827   (headings, primary content)
Text secondary:       #6B7280   (labels, descriptions, meta info)
Text tertiary:        #9CA3AF   (placeholders, disabled text)

Background page:      #F9FAFB   (the outer teal you have — consider lightening to off-white)
Background card:      #FFFFFF
Background subtle:    #F3F4F6   (table row hover, input backgrounds)

Border default:       #E5E7EB
Border strong:        #D1D5DB   (input focus, card selected state)

Status active:        #10B981   (green/teal — same as primary)
Status paused:        #F59E0B   (amber)
Status failed:        #EF4444   (red)
Status archived:      #9CA3AF   (grey)
Status processing:    #3B82F6   (blue)

Platform Meta:        #1877F2
Platform TikTok:      #000000
Platform Pinterest:   #E60023
Platform Google:      #4285F4
Platform LinkedIn:    #0A66C2
Platform Reddit:      #FF4500
```

### Typography

```
Font family:     Inter or your current system font — keep it
Heading 1:       24px / weight 600 / line-height 1.3   (page titles)
Heading 2:       20px / weight 600 / line-height 1.3   (section titles)
Heading 3:       16px / weight 600 / line-height 1.4   (card titles, modal titles)
Body:            14px / weight 400 / line-height 1.6
Body small:      12px / weight 400 / line-height 1.5   (meta info, labels)
Label:           12px / weight 500 / uppercase / letter-spacing 0.05em  (column headers)
```

### Spacing Scale (Use Consistently)

```
4px   — tight gaps between related inline elements
8px   — padding inside small components (badges, chips)
12px  — gap between form label and input
16px  — standard card padding, gap between form rows
24px  — card-to-card gap, section padding
32px  — page section separation
48px  — top padding on page content area
```

### Border Radius

```
4px   — small elements: badges, status pills, chips
8px   — inputs, buttons, small cards
12px  — standard cards, modals, dropdowns
16px  — large cards, panels, sidebars
```

### Elevation (Shadows)

```
Level 0: no shadow         — flat elements, table rows
Level 1: 0 1px 3px rgba(0,0,0,0.08)    — cards, dropdowns
Level 2: 0 4px 12px rgba(0,0,0,0.10)   — modals, popovers
Level 3: 0 8px 24px rgba(0,0,0,0.12)   — floating panels
```

---

## 2. Component-Level Improvements

### 2.1 Sidebar

**Current issues:**
- Page shifts slightly on open/close (layout reflow)
- No tooltip on collapsed icons
- Active state could be more distinct
- No visual separation between nav items and bottom actions

**Improvements:**
```
Collapsed width:   64px (icon only + 16px padding each side)
Expanded width:    220px
Transition:        width 200ms cubic-bezier(0.4, 0, 0.2, 1)

Use CSS:
  .sidebar { transition: width 200ms ease; overflow: hidden; }
  .content { margin-left: var(--sidebar-width); transition: margin-left 200ms ease; }

This avoids layout reflow — both sidebar and content animate together.

Active item:
  Background: #ECFDF5 (teal xlight)
  Left border: 3px solid #10B981
  Icon + text: #059669 (teal dark)
  Border radius on the item: 0 8px 8px 0 (rounded on right only, flush against left edge)

Hover item (not active):
  Background: #F3F4F6
  Icon + text: #374151

Tooltip on collapsed:
  <mat-tooltip position="right"> with the page name
  Only shown when sidebar is collapsed

Bottom section:
  Add a 1px #E5E7EB divider above Dark Mode and Log Out
  Dark Mode: show moon icon only when collapsed, "Dark Mode" label when expanded
  Log Out: show exit icon only when collapsed, "Log Out" when expanded
```

### 2.2 Platform Cards (Sync Accounts)

**Three visual states needed:**

```
NOT CONNECTED:
  Border: 1px solid #E5E7EB
  Background: white
  Status indicator: none
  Connect button: teal outlined
  Sync button: disabled (grey, opacity 0.5, cursor: not-allowed)
  Tooltip on disabled Sync: "Connect your account first"

CONNECTED:
  Border: 2px solid #10B981 (teal)
  Background: #ECFDF5 (subtle teal tint)
  Status indicator: green dot + "Connected" text in top-right corner of card
  Last sync time: small grey text below status ("Last synced 2 hours ago")
  Connect button: replaced by "Manage" (outlined teal)
  Sync button: enabled (teal filled)

SYNCING:
  Border: 2px solid #10B981
  Background: #ECFDF5
  Sync button: replaced by animated spinner + "Syncing…" text
  All buttons disabled
  Progress indicator below button: "Syncing campaigns… (1/3)"

COMING SOON:
  Opacity: 0.65 on the entire card
  Lock icon overlay on the platform logo
  "Coming Soon" amber badge (as currently)
  No buttons, or non-interactive ghost buttons
```

### 2.3 Status Badges

Replace plain text status with consistent pill badges across all tables:

```css
.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.badge-active     { background: #D1FAE5; color: #065F46; }
.badge-paused     { background: #FEF3C7; color: #92400E; }
.badge-failed     { background: #FEE2E2; color: #991B1B; }
.badge-archived   { background: #F3F4F6; color: #374151; }
.badge-processing { background: #DBEAFE; color: #1E40AF; }
.badge-ready      { background: #D1FAE5; color: #065F46; }
```

### 2.4 Tables (Meta Overview)

```
Row height:        48px (comfortable touch target on mobile)
Header height:     40px
Header background: #F9FAFB
Header text:       12px uppercase, #6B7280, letter-spacing 0.05em
Row hover:         background #F9FAFB
Row selected:      background #ECFDF5, left border 3px solid #10B981
Divider:           1px solid #F3F4F6 between rows (not #E5E7EB — too heavy)

Sticky first column (campaign name) on horizontal scroll — essential for mobile
Sticky header row on vertical scroll when table is long

On/Off toggle:
  ON state:  teal background (#10B981)
  OFF state: grey background (#D1D5DB)
  Loading:   spinner replaces toggle while API call is in progress
  Size:      32px wide × 18px tall (slightly larger than default for touch)
```

### 2.5 Insights Page

```
Platform tabs:
  Positioned at very top of page, full width
  Active tab: teal background, white text, no bottom border
  Inactive tab: white background, grey text, bottom border teal on hover
  Disconnected tab: grey text, lock icon, not clickable

Date Range group:
  Place below platform tabs, left-aligned
  Quick selectors (7d / 14d / 30d / 90d) as outlined pill buttons
  Active quick selector: teal filled
  "Custom" button opens the From/To date pickers inline
  Layout: [7d] [14d] [30d] [90d] [Custom ▾]  [Feb 18 → Mar 20]

Metric cards:
  3 per row on desktop, 2 per row on tablet, 1 per row on mobile
  Hover state: subtle shadow elevation increase
  Clickable: show dropdown of metric options on click
  Active selection: teal left border on card
  Value: 28px bold
  Label: 12px uppercase grey

Object Selection panel:
  Collapsible — "Editing selection (3 campaigns, 2 ad sets)" summary row
  Expand to show full checkboxes
  This saves significant vertical space on smaller screens
```

### 2.6 Create Ad Form

```
Two-panel layout preserved on desktop (≥1024px)
Left panel: form fields
Right panel: creative browser

Form field improvements:
  - Group related fields: "Ad Details" group (name, platform) and "Placement" group (campaign, ad set, status)
  - Platform field: show platform logo icon inline with the name
  - Required asterisk: use teal colour, not red — less alarming, still visible
  - Page selector: add as the third field in the Ad Details group
  - Dropdown disabled state: grey background, "Loading pages…" placeholder text

Right panel empty state:
  Show an illustration (simple grey icon) + "Select a creative or create one from your asset library"
  Not a blank white box

Creative selection highlight:
  Selected creative: teal border + checkmark overlay on thumbnail

Publish Ad button:
  Full-width at the bottom of the left panel
  Teal filled when enabled
  Grey filled with "Complete all required fields" tooltip when disabled
  Shows spinner inside button while publishing: [⟳ Publishing…]
```

### 2.7 Creative Library Grid

```
Grid layout:
  Desktop (≥1280px): 5 columns
  Desktop (1024–1280px): 4 columns
  Tablet (768–1024px): 3 columns
  Mobile (<768px): 2 columns

Card improvements:
  Hover overlay: semi-transparent dark overlay (rgba(0,0,0,0.45)) with 3 action icons centered:
    [Publish] [Edit Tags] [Delete]
  Overlay uses CSS transition (opacity 0 → 1, 150ms)
  This replaces the need to click into the card to see actions

Variant badge:
  Bottom-right corner, teal background: "4 variants"
  Platform-specific colour when platform filter is active

Tag pills:
  Bottom-left corner of thumbnail
  Max 2 visible, "+3 more" if more exist
  12px, white text on rgba(0,0,0,0.6) background

Status badge:
  Top-left corner: READY (teal) / PROCESSING (blue) / FAILED (red)
  Same pill style as defined in 2.3

Empty state (no assets):
  Centred illustration + "No creatives yet — upload your first image or video"
  Upload button prominently shown

Empty state (filtered, no results):
  "No assets match your filters" + Clear Filters button
```

---

## 3. Responsive Design Breakpoints & Rules

### Breakpoints

```
xs:  <480px    — small phones
sm:  480–767px — large phones
md:  768–1023px — tablets
lg:  1024–1279px — small laptops
xl:  ≥1280px   — desktops and wide screens
```

### Sidebar Behaviour per Breakpoint

```
xl / lg:  sidebar visible, toggle between 64px (collapsed) and 220px (expanded)
md:       sidebar starts collapsed (64px), user can expand to 220px (overlays content as a drawer)
sm / xs:  sidebar replaced by bottom navigation bar with 5 icon tabs
          OR: hamburger icon top-left opens a full-height drawer overlay
```

### Page Layouts per Breakpoint

#### Sync Accounts

```
xl / lg:  4 platform cards in a row (current layout)
md:       2 platform cards per row
sm / xs:  1 platform card per row, full-width
```

#### Meta Overview Table

```
xl / lg:  show all customized columns with horizontal scroll if needed
md:       show 4 columns (Name, Status, On/Off, Budget) + horizontal scroll for rest
          Sticky first column
sm / xs:  show 3 columns (Name, Status, On/Off)
          Tap a row to open a bottom sheet with full row details
          Edit, toggle, and other actions available in the bottom sheet
```

#### Insights

```
xl:       Object Selection (3 columns) + metric cards (3 per row) + full chart
lg:       Object Selection (3 columns, slightly narrower) + metric cards (3 per row)
md:       Object Selection becomes an expandable accordion (collapsed by default)
          Metric cards: 2 per row
sm / xs:  Object Selection: full-width accordion
          Metric cards: 1 per row
          Chart: full-width, reduced height (250px)
          Top Performers table: 3 columns (Name, Spend, Score) with tap-to-expand
```

#### Create Ad Workflow

```
xl / lg:  two-panel side-by-side (current layout, form left, creative right)
md:       two-panel stacked vertically (form on top, creative panel below)
          Creative panel collapsible
sm / xs:  single-panel stepper:
            Step 1: "Ad Details" (name, platform, campaign, ad set, status, page)
            Step 2: "Choose Creative" (creative browser, full screen)
            Step 3: "Review & Publish" (summary of ad with preview)
          Progress dots at the top (● ● ○)
          Next / Back buttons at the bottom
```

#### Creative Library

```
xl:       5-column grid
lg:       4-column grid
md:       3-column grid
sm:       2-column grid
xs:       2-column grid (smaller cards)
          OR: switch to list view for xs with thumbnail on left (60px × 60px), metadata on right
```

### Navigation at Mobile (sm / xs)

```
Bottom navigation bar:
  5 tabs: Dashboard (grid icon) | Create Ad (+) | Insights (chart) | Library (images) | Sync (sync icon)
  Active tab: teal icon + teal dot indicator
  Height: 56px, safe area inset padding at bottom for notched phones
  Always visible (fixed position)

Top bar:
  App logo left
  Page title centre
  User avatar / menu right (opens a popover with Dark Mode + Log Out)
```

### Typography Scaling

```
                  Desktop   Tablet    Mobile
Page title:       24px      20px      18px
Section heading:  20px      18px      16px
Card title:       16px      15px      14px
Body:             14px      14px      14px    (do not go below 14px on mobile)
Label:            12px      12px      12px
Metric value:     28px      24px      20px
```

### Touch Targets

All interactive elements on mobile must have a minimum touch target of 44×44px, even if the visual element is smaller:

```css
.touch-target {
  position: relative;
}
.touch-target::after {
  content: '';
  position: absolute;
  inset: -8px;  /* expand touch area without changing visual size */
}
```

---

## 4. Loading & Error States Design

### Skeleton Loaders

Use skeleton placeholders that match the shape of the content:

```
Table skeleton:     4 rows of grey bars (alternating 60% and 40% width)
Card skeleton:      grey rectangle matching card dimensions
Chart skeleton:     grey rectangle matching chart height
Metric card:        grey circle (icon area) + two grey bars (value + label)
Dropdown loading:   disabled select with a small spinner icon on the right
```

### Spinner Specifications

```
Size:   20px diameter for inline/button spinners
        32px for panel-level spinners (inside a card or panel)
        48px for full-area spinners (should be rare)

Colour: teal (#10B981) on white backgrounds
        white on teal backgrounds (button spinners)

Animation: standard CSS rotation, 600ms linear infinite
```

### Empty States

Each major view needs a specific empty state:

```
Campaigns table (no data):
  Icon: campaign/megaphone outline icon
  Title: "No campaigns synced yet"
  Body: "Connect your Meta account and sync to see your campaigns here."
  Action button: "Go to Sync Accounts" → navigates to /sync-accounts

Creative Library (no assets):
  Icon: image outline icon
  Title: "Your creative library is empty"
  Body: "Upload images or videos to build your creative library."
  Action button: "Upload Creative"

Insights (no objects selected):
  Icon: chart outline icon
  Title: "Select campaigns to see insights"
  Body: "Choose one or more campaigns, ad sets, or ads from the list above, then click Sync."

Insights (no data for selection):
  Icon: chart outline icon with a question mark
  Title: "No data available for this period"
  Body: "Try selecting a wider date range or check that your campaigns ran during this period."
```

### Toast Notifications

```
Position:    top-right, 16px from edge
Width:       340px max
Auto-dismiss: 4 seconds (success/info), manual dismiss for warnings/errors
Stack:       multiple toasts stack vertically with 8px gap

Success:  teal left border, green check icon, #065F46 text
Error:    red left border, × icon, #991B1B text, no auto-dismiss
Warning:  amber left border, ⚠ icon, #92400E text
Info:     blue left border, ℹ icon, #1E40AF text
```

---

## 5. Accessibility

### Keyboard Navigation
- All interactive elements reachable by Tab
- Visible focus ring on all focusable elements (2px teal outline, 2px offset)
- Modal: trap focus inside when open, return focus to trigger on close
- Dropdowns: arrow keys to navigate options, Enter to select, Escape to close

### Colour Contrast
- All text on white backgrounds meets WCAG AA (4.5:1 ratio)
- Primary teal (#10B981) on white: 3.0:1 — use for icons and decorative only, not body text
- Badge text uses dark shade from same colour family (e.g. #065F46 on #D1FAE5) — meets AA

### Screen Readers
- All images have descriptive `alt` attributes
- Icon buttons have `aria-label` (e.g. `aria-label="Publish to Meta"`)
- Status badges use `aria-label` (e.g. `aria-label="Status: Paused"`)
- Tables have proper `<caption>`, `<th scope="col">`, and `<th scope="row">`
- Loading states: `aria-live="polite"` on data containers, `aria-busy="true"` while loading

### Forms
- Every input has an associated `<label>` (not just placeholder text)
- Error messages are linked via `aria-describedby` to their input
- Required fields have `aria-required="true"`
- Disabled buttons have `aria-disabled="true"` and a visible tooltip explaining why
