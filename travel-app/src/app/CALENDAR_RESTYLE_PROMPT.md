# Calendar Restyling Prompt

## Task

Restyle the existing date picker / calendar component **CSS only**.
Do not change any logic, date handling, Angular component code, or TypeScript.
Only touch styles — either the component's SCSS/CSS file, or a global override if the
calendar is a PrimeNG / Angular Material component that requires global ::ng-deep overrides.

---

## Current vs Target — Exact Differences

### Current (what exists now — change these)

| Property | Current value |
|---|---|
| Month/year header | "February 2026 ▼" — sentence-style, mixed case, large |
| Month navigation buttons | Up ↑ and Down ↓ arrow icons |
| Weekday header row | Full abbreviations: Su Mo Tu We Th Fr Sa — same size as day numbers |
| Day numbers | Normal weight, no visual separation from weekday row |
| Selected day | Square/rectangle box outline around the number |
| Today indicator | No visible indicator |
| "Clear" and "Today" links | Teal text links at bottom |
| Overall background | White with visible border |
| Font weight throughout | Uniform — nothing stands out |

### Target (what it must look like after — copy this exactly)

| Property | Target value |
|---|---|
| Month/year header | ALL CAPS short month + full year: "MAR 2026 ▼" — smaller, lighter weight |
| Month navigation buttons | Left `‹` and Right `›` chevrons — positioned top-right of the header |
| Weekday header row | Single letters only: S M T W T F S — uppercase, bold, slightly smaller than day numbers, visually separated from the day grid by spacing (no border) |
| Month label row | "MAR" appears again as a bold label row just above the first row of day numbers — same style as weekday letters but full short month name |
| Day numbers | Larger, regular weight, generous row height (~40px per row), clean and airy |
| Selected day / today | Teal ring (circle outline, `border: 2px solid #10B981`, border-radius 50%) around the number — no fill |
| Background | Light grey (`#F5F5F5` or similar) — not white |
| No footer links | Remove or hide "Clear" and "Today" text links at the bottom |
| Overall feel | Open, airy, generous spacing — no borders between cells |

---

## Detailed CSS Specification

Apply these rules. Use `::ng-deep` if the component is PrimeNG `p-calendar`
or Angular Material `mat-datepicker`. If it is a custom component, apply directly.

```scss
// ─── Panel / container ───────────────────────────────────────────────────────
.p-datepicker,
.mat-datepicker-content,
.calendar-panel {
  background: #F5F5F5;
  border: none;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.10);
  padding: 16px;
  width: 280px;
  font-family: inherit;
}

// ─── Header row (month/year + navigation) ────────────────────────────────────
.p-datepicker-header,
.mat-calendar-header,
.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

// Month + year title: "MAR 2026 ▼"
.p-datepicker-title,
.mat-calendar-period-button,
.calendar-title {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #111827;
}

// Navigation buttons — replace ↑↓ with ‹› if the component allows icon override
// If using PrimeNG, set [showIcon]="false" and use prevIcon/nextIcon props instead
.p-datepicker-prev,
.p-datepicker-next,
.mat-calendar-previous-button,
.mat-calendar-next-button,
.calendar-nav-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #374151;
}
.p-datepicker-prev:hover,
.p-datepicker-next:hover,
.calendar-nav-btn:hover {
  background: #E5E7EB;
}

// ─── Weekday header row: S M T W T F S ───────────────────────────────────────
.p-datepicker-calendar thead th,
.mat-calendar-table-header th,
.calendar-weekdays th {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: #374151;
  text-align: center;
  padding: 0 0 8px 0;
  width: 36px;
}

// Hide the full abbreviation spans if they exist, show single letter only
// For PrimeNG: the default renders full abbreviation — you may need to
// set [showWeekNumbers]="false" and override via firstDayOfWeek or a custom header

// ─── Month label row ("MAR") above day numbers ───────────────────────────────
// This row appears in the target design. If your component does not render it
// automatically, add it as a custom element above the day grid in the template.
.calendar-month-label {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: #374151;
  padding: 0 0 4px 0;
  letter-spacing: 0.04em;
}

// ─── Day cells ────────────────────────────────────────────────────────────────
.p-datepicker-calendar tbody td,
.mat-calendar-body td,
.calendar-day-cell {
  text-align: center;
  padding: 4px 0;
  width: 36px;
  height: 40px;
}

.p-datepicker-calendar tbody td span,
.mat-calendar-body-cell-content,
.calendar-day-number {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  font-size: 14px;
  font-weight: 400;
  color: #111827;
  border-radius: 50%;
  cursor: pointer;
  transition: background 150ms ease;
}

.p-datepicker-calendar tbody td span:hover,
.mat-calendar-body-cell:hover .mat-calendar-body-cell-content,
.calendar-day-number:hover {
  background: #E5E7EB;
}

// ─── Today indicator — teal ring, no fill ─────────────────────────────────────
.p-datepicker-calendar tbody td.p-datepicker-today span,
.mat-calendar-body-today .mat-calendar-body-cell-content,
.calendar-day-number.today {
  border: 2px solid #10B981;
  background: transparent;
  color: #111827;
  font-weight: 500;
}

// ─── Selected day — same teal ring (the range selection uses this too) ─────────
.p-datepicker-calendar tbody td.p-highlight span,
.mat-calendar-body-selected,
.calendar-day-number.selected {
  border: 2px solid #10B981;
  background: transparent;
  color: #111827;
  font-weight: 500;
}

// ─── Days from previous/next month — muted ────────────────────────────────────
.p-datepicker-calendar tbody td.p-datepicker-other-month span,
.mat-calendar-body-label,
.calendar-day-number.other-month {
  color: #D1D5DB;
  cursor: default;
  pointer-events: none;
}

// ─── Remove "Clear" and "Today" footer ────────────────────────────────────────
.p-datepicker-buttonbar,
.mat-datepicker-actions,
.calendar-footer {
  display: none !important;
}

// ─── Remove any internal cell borders/dividers ────────────────────────────────
.p-datepicker-calendar,
.mat-calendar-table {
  border-collapse: separate;
  border-spacing: 0;
}
.p-datepicker-calendar thead,
.mat-calendar-table-header {
  border-bottom: none;
}
```

---

## Navigation Icons

**Current:** ↑ (up arrow) and ↓ (down arrow) for month navigation
**Target:** ‹ (left chevron) and › (right chevron)

If using **PrimeNG `p-calendar`**, set these input properties on the component tag:
```html
<p-calendar
  prevIcon="pi pi-chevron-left"
  nextIcon="pi pi-chevron-right"
  ...>
</p-calendar>
```

If using **Angular Material `mat-datepicker`**, the prev/next buttons use Material icons.
Override the icon content via CSS:
```scss
::ng-deep .mat-calendar-previous-button .mat-icon::before { content: '‹'; font-size: 18px; }
::ng-deep .mat-calendar-next-button .mat-icon::before     { content: '›'; font-size: 18px; }
```

If using a **custom component**, replace the icon elements in the template directly with `‹` and `›` text characters or FontAwesome `fa-chevron-left` / `fa-chevron-right` icons.

---

## Weekday Labels

**Current:** Su Mo Tu We Th Fr Sa (two-letter abbreviations)
**Target:** S M T W T F S (single letters)

If using **PrimeNG**, the weekday labels come from the locale. Override them:
```typescript
// In the component that hosts the calendar, inject PrimeNGConfig or use locale
// Or override in your PrimeNG locale setup:
this.primeNGConfig.setTranslation({
  dayNamesMin: ['S', 'M', 'T', 'W', 'T', 'F', 'S']
});
```

If using **Angular Material**, configure the `MAT_DATE_LOCALE` or a custom `DateAdapter`
that returns single-letter day names from `getDayOfWeekNames('narrow')`.

If using a **custom component**, change the array in the component TypeScript from
`['Su','Mo','Tu','We','Th','Fr','Sa']` to `['S','M','T','W','T','F','S']`.

---

## What NOT to Change

- Do not change date selection logic
- Do not change which dates are selectable or disabled
- Do not change the FROM / TO input fields above the calendar
- Do not change the quick selector pills (7d / 14d / 30d / 90d) if they exist
- Do not change how the calendar opens or closes
- Do not change any TypeScript / component logic
- Do not remove the calendar — only restyle it
