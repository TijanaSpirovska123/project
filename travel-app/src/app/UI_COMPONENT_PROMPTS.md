# UI Component Implementation Prompts

## Context

These prompts are for building custom Angular components that match the existing design system:
- Primary teal: `#10B981`
- Border radius: `8px` on inputs, `12px` on dropdowns/panels
- Font: Inter / system-ui, 14px body
- Input border: `1px solid #E5E7EB`, focus border: `2px solid #10B981`
- White background cards, light grey page background `#F9FAFB`
- Labels: 12px uppercase, `#6B7280`, letter-spacing 0.05em
- Box shadow on floating panels: `0 4px 12px rgba(0,0,0,0.10)`

---

## Prompt 1 — Custom Searchable Dropdown Component

### Problem
The current campaign dropdown uses a native `<select>` or a basic PrimeNG/Angular Material select. When open it renders:
- A separate floating list with no visual connection to the trigger input
- Dark grey selected-item highlight (does not match brand)
- No search/filter capability
- No icons or status badges per item
- Scrollbar styling does not match the app
- The open panel does not align with the input trigger width

### Prompt to give to an AI coding assistant

```
I am building an Angular 21 application with a custom design system. I need a reusable
SearchableDropdownComponent that replaces the native select and PrimeNG p-dropdown.

DESIGN REQUIREMENTS — must match exactly:
- Trigger input: white background, 1px solid #E5E7EB border, border-radius 8px,
  height 40px, 14px text #111827, padding 0 12px
- On focus/open: border becomes 2px solid #10B981 (teal), no outline
- Chevron icon on the right of the trigger: #9CA3AF, rotates 180deg when open (CSS transition 200ms)
- Dropdown panel: white background, border-radius 12px, border 1px solid #E5E7EB,
  box-shadow 0 4px 12px rgba(0,0,0,0.10), appears directly below the trigger,
  same width as trigger, z-index 1000, margin-top 4px
- Search input inside panel: full width, border-bottom 1px solid #F3F4F6 only (no other borders),
  padding 8px 12px, 14px placeholder #9CA3AF, no border-radius, background #FAFAFA
  sticky at top of panel
- Option item: height 36px, padding 0 12px, 14px text #111827, display flex align-center
  Hover: background #F9FAFB
  Selected: background #ECFDF5, text #065F46, checkmark icon (#10B981) on the right
  First item highlighted by default on open (keyboard navigation ready)
- Max panel height: 240px, overflow-y auto
- Custom scrollbar: width 4px, thumb #D1D5DB, track transparent, border-radius 2px
- Loading state: show a small teal spinner (20px) centred in the panel instead of the list,
  disable the trigger input
- Empty state: centred 14px text #9CA3AF "No results found" when search yields nothing
- Disabled state: trigger background #F9FAFB, text #9CA3AF, cursor not-allowed, no hover effect

COMPONENT API:
  @Input() options: { value: any; label: string; badge?: string; disabled?: boolean }[]
  @Input() placeholder: string = 'Select...'
  @Input() loading: boolean = false
  @Input() disabled: boolean = false
  @Input() searchable: boolean = true
  @Output() selectionChange: EventEmitter<any>

BEHAVIOUR:
- Click outside closes the panel (use @HostListener document click)
- Escape key closes the panel
- ArrowDown / ArrowUp navigate options, Enter selects focused option
- Search filters options client-side (case-insensitive substring match on label)
- Search input is cleared when panel closes
- When loading=true, show spinner and ignore option clicks

OUTPUT: A single Angular standalone component file (TypeScript + inline template + inline styles).
Use only CSS — no external libraries. Use Angular signals for state where appropriate.
```

---

## Prompt 2 — Custom Date Range Picker Component

### Problem
The current date range picker uses a PrimeNG or Angular Material calendar that renders:
- An unstyled floating panel that does not match the card/panel design
- Up/down arrow navigation for months instead of left/right chevrons
- No visual range highlight between start and end dates
- "Clear" and "Today" links in teal that look inconsistent with the button style
- The FROM/TO inputs at the top are styled correctly but the calendar panel underneath breaks the visual continuity
- Month/year header uses a dropdown arrow (▼) that looks dated

### Prompt to give to an AI coding assistant

```
I am building an Angular 21 application with a custom design system. I need a reusable
DateRangePickerComponent that replaces the current PrimeNG/Angular Material calendar.

DESIGN REQUIREMENTS — must match exactly:

TRIGGER (the FROM → TO row):
- Two date inputs side by side connected by a → arrow
- Each input: white background, 1px solid #E5E7EB, border-radius 8px, height 40px,
  padding 0 12px, 14px text #111827
- Label above each: 12px uppercase #6B7280 letter-spacing 0.05em ("FROM", "TO")
- Calendar icon inside the input on the right: #9CA3AF, clicking opens the panel
- When a date is selected, the input shows the date in DD/MM/YYYY format
- Active input (the one being edited): 2px solid #10B981 border

CALENDAR PANEL:
- White background, border-radius 12px, border 1px solid #E5E7EB,
  box-shadow 0 4px 16px rgba(0,0,0,0.10)
- Width: 280px, appears below the FROM input, z-index 1000, margin-top 4px
- Single month view (not two months side by side)

PANEL HEADER:
- Month + Year as plain text (e.g. "February 2026"), font-weight 600, 15px, #111827
- Left chevron (‹) and right chevron (›) on either side to navigate months
  Chevron buttons: 28px × 28px, border-radius 6px, hover background #F3F4F6
  No up/down arrows — only left/right navigation

DAY GRID:
- Weekday row: Su Mo Tu We Th Fr Sa — 12px, #9CA3AF, font-weight 500
- Day cells: 32px × 32px, centered, 14px #374151
- Today: ring 1px solid #10B981, no fill
- Hover (not selected): background #F3F4F6, border-radius 6px
- Selected start date: background #10B981, text white, border-radius 6px left half
- Selected end date: background #10B981, text white, border-radius 6px right half
- Range between start and end: background #ECFDF5 (teal xlight), no border-radius
  text #065F46
- Days outside current month: text #D1D5DB, not selectable
- Disabled days (future beyond today if restricting to past): text #E5E7EB, cursor not-allowed

PANEL FOOTER:
- Two text buttons: "Clear" (left) and "Today" (right)
- Style: 13px, font-weight 500, teal #10B981, no border, background transparent
- Hover: underline
- "Clear" resets both dates, "Today" sets both FROM and TO to today

QUICK SELECTORS (shown above the trigger row as pill buttons):
- [Last 7 days] [Last 14 days] [Last 30 days] [Last 90 days]
- Pill style: border 1px solid #E5E7EB, border-radius 20px, padding 4px 12px,
  13px text #374151, background white
- Active: background #10B981, text white, border-color #10B981
- Selecting a quick option sets FROM and TO automatically and highlights the pill

BEHAVIOUR:
- Clicking FROM input opens panel in "selecting start" mode
- Clicking TO input opens panel in "selecting end" mode
- After selecting start date, panel stays open for end date selection
- End date cannot be before start date (earlier dates are disabled after start is picked)
- Clicking outside closes the panel
- Escape closes the panel
- Max range validation: if range exceeds 365 days, show inline error "Maximum range is 365 days"

COMPONENT API:
  @Input() maxRangeDays: number = 365
  @Output() rangeChange: EventEmitter<{ from: Date | null; to: Date | null }>
  @Output() quickSelectChange: EventEmitter<'7d' | '14d' | '30d' | '90d'>

OUTPUT: A single Angular standalone component file (TypeScript + inline template + inline styles).
Use only CSS — no external libraries, no date-fns or moment.js dependency
(use native Date object). Use Angular signals for state.
```

---

## General Notes for Both Components

Both components must:
1. Work as Angular standalone components (`standalone: true`)
2. Implement `ControlValueAccessor` for use inside Angular Reactive Forms (`formControlName`)
3. Have ARIA attributes: `role="combobox"` on the dropdown trigger, `role="listbox"` on the panel, `role="option"` on items, `aria-expanded`, `aria-selected`, `aria-activedescendant`
4. Use `OnPush` change detection
5. Not depend on any CSS framework — all styles inline or in the component's `styles` array
6. Work correctly in both light and dark mode (CSS variables or conditional classes)
7. Support keyboard navigation fully (Tab, Escape, Arrow keys, Enter)
