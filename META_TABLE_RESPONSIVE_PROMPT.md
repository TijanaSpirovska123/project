# Meta Overview Table — Responsive Design Prompt

## Task

Fix the responsive scaling of the Meta overview table component (Campaigns / Ad Sets / Ads).
CSS and template changes only. Do not change any data-fetching logic, sorting, pagination logic,
or column customization modal behaviour.

---

## What Is Wrong (Big Screen — Image 1)

On a large monitor (≥ 1400px) the table looks cramped and small:

| Element | Problem |
|---|---|
| Tab labels (Campaigns, Ad Sets, Ads) | Small font, thin weight — do not scale up |
| Table header row | Small font, too compact, column headers cramped |
| Table row height | Too short — rows feel dense |
| Status badges (PAUSED / ACTIVE) | Small — hard to read at a glance |
| On/Off toggle | Tiny relative to the row height |
| Search bar | Narrow and small on a wide screen |
| Export button | Small, not proportional |
| Pagination | Small numbers, cramped |
| "Show X per page" input | Very small, hard to interact with |
| Column sort icons | Too small |
| Overall spacing | Everything hugs the top-left — no sense of scale |

---

## What Is Correct (Small Screen — Image 2)

The small-screen version already looks good and must be preserved exactly:
- Proper card/panel with white background and rounded corners
- Good row height (~52px)
- Readable status badges with rounded pill shape
- Clear toggle size
- Proper search bar with icon
- Clean pagination with page number buttons
- "Show X per page" with proper input

**The small-screen layout is the design target. The large screen must scale up
from this baseline — bigger elements, more spacing, not shrink down.**

---

## Breakpoints

```
sm:  < 768px      — keep exactly as image 2 (do not touch)
md:  768–1023px   — same as sm, slight spacing increase
lg:  1024–1279px  — intermediate step up
xl:  1280–1535px  — scale up noticeably
2xl: ≥ 1536px     — full large-screen scale
```

---

## Exact Changes Required Per Element

### 1. Outer card / panel

```scss
// sm/md (keep as-is from image 2):
border-radius: 12px;
padding: 16px;

// lg:
padding: 20px 24px;

// xl+:
padding: 24px 32px;
border-radius: 16px;
```

---

### 2. Tab row (Campaigns / Ad Sets / Ads)

```scss
// sm/md (keep as-is):
font-size: 14px;
font-weight: 500;
padding: 8px 12px;

// lg:
font-size: 15px;
padding: 9px 14px;

// xl+:
font-size: 16px;
font-weight: 600;
padding: 10px 18px;
```

Active tab underline/border should scale proportionally — increase from `2px` to `3px` at xl+.

---

### 3. Search bar

```scss
// sm/md (keep as-is):
height: 36px;
font-size: 13px;
padding: 0 12px;
min-width: 200px;

// lg:
height: 40px;
font-size: 14px;
min-width: 260px;

// xl+:
height: 44px;
font-size: 15px;
min-width: 320px;
border-radius: 10px;
```

---

### 4. Export button

```scss
// sm/md (keep as-is):
height: 36px;
font-size: 13px;
padding: 0 14px;

// lg:
height: 40px;
font-size: 14px;
padding: 0 18px;

// xl+:
height: 44px;
font-size: 15px;
padding: 0 22px;
border-radius: 10px;
```

---

### 5. Table header row

```scss
// sm/md (keep as-is):
height: 40px;
font-size: 12px;
font-weight: 500;
letter-spacing: 0.03em;
padding: 0 12px;

// lg:
height: 44px;
font-size: 13px;
padding: 0 14px;

// xl+:
height: 48px;
font-size: 14px;
font-weight: 600;
padding: 0 16px;
letter-spacing: 0.04em;
```

Sort icons: scale from `12px` to `14px` at xl+.

---

### 6. Table data rows

```scss
// sm/md (keep as-is):
height: 52px;
font-size: 13px;
padding: 0 12px;

// lg:
height: 56px;
font-size: 14px;
padding: 0 14px;

// xl+:
height: 64px;
font-size: 15px;
padding: 0 16px;
```

Row divider: keep `1px solid #F3F4F6` at all sizes.

---

### 7. Campaign name cell (first column)

```scss
// sm/md (keep as-is):
font-size: 13px;
font-weight: 400;
min-width: 140px;

// lg:
font-size: 14px;
min-width: 180px;

// xl+:
font-size: 15px;
font-weight: 500;
min-width: 220px;
```

---

### 8. On/Off toggle

```scss
// sm/md (keep as-is — this size is correct):
width: 40px;
height: 22px;

// lg:
width: 44px;
height: 24px;

// xl+:
width: 48px;
height: 26px;
```

Toggle thumb should scale proportionally inside the track.
Use `transform: scale()` if using a CSS-only toggle,
or set `[style.transform]` dynamically if using Angular Material `mat-slide-toggle`.

For Angular Material specifically, wrap in a container and scale it:
```html
<div class="toggle-wrapper">
  <mat-slide-toggle ...></mat-slide-toggle>
</div>
```
```scss
.toggle-wrapper {
  display: flex;
  align-items: center;

  @media (min-width: 1280px) {
    transform: scale(1.15);
    transform-origin: left center;
  }
  @media (min-width: 1536px) {
    transform: scale(1.25);
    transform-origin: left center;
  }
}
```

---

### 9. Status badges (PAUSED / ACTIVE)

```scss
// sm/md (keep as-is):
font-size: 11px;
font-weight: 500;
padding: 3px 10px;
border-radius: 4px;

// lg:
font-size: 12px;
padding: 4px 12px;

// xl+:
font-size: 13px;
font-weight: 600;
padding: 5px 14px;
border-radius: 5px;
```

Colors stay the same:
- PAUSED: `background #FEF3C7; color #92400E`
- ACTIVE: `background #D1FAE5; color #065F46`

---

### 10. Pagination row (bottom bar)

```scss
// sm/md (keep as-is):
font-size: 13px;
gap: 6px;

// lg:
font-size: 14px;
gap: 8px;

// xl+:
font-size: 15px;
gap: 10px;
```

Page number buttons:
```scss
// sm/md (keep as-is):
width: 32px;
height: 32px;
font-size: 13px;
border-radius: 6px;

// lg:
width: 36px;
height: 36px;
font-size: 14px;

// xl+:
width: 40px;
height: 40px;
font-size: 15px;
border-radius: 8px;
```

Previous/Next chevron buttons: same sizing as page number buttons.

Active page button: `background #10B981; color white` — keep at all sizes.

---

### 11. "Show X per page" input

```scss
// sm/md (keep as-is):
width: 48px;
height: 32px;
font-size: 13px;
border-radius: 6px;

// lg:
width: 54px;
height: 36px;
font-size: 14px;

// xl+:
width: 60px;
height: 40px;
font-size: 15px;
border-radius: 8px;
```

"per page" label next to the input:
```scss
// xl+:
font-size: 15px;
```

---

### 12. "1 - 10 of 34 items" info text

```scss
// sm/md (keep as-is):
font-size: 13px;
color: #6B7280;

// xl+:
font-size: 15px;
```

---

### 13. + button (add column, top-right of header row)

```scss
// sm/md (keep as-is):
width: 28px;
height: 28px;
font-size: 16px;

// xl+:
width: 34px;
height: 34px;
font-size: 18px;
border-radius: 8px;
```

---

## Implementation Notes

### Use SCSS breakpoint mixins or CSS custom properties

If the project uses Bootstrap 5.3, use its breakpoint mixins:
```scss
@include media-breakpoint-up(xl) { ... }   // ≥ 1280px
@include media-breakpoint-up(xxl) { ... }  // ≥ 1400px
```

If using plain CSS media queries:
```scss
@media (min-width: 1280px) { ... }
@media (min-width: 1536px) { ... }
```

Do NOT use `max-width` queries for this component — the small screen
layout is the baseline, scale up with `min-width` (mobile-first).

---

### Angular Material overrides

If the table uses `mat-table`, header rows, and cells, use `::ng-deep`
(or `:host ::ng-deep` in Angular) to override Material defaults at each breakpoint:

```scss
::ng-deep .mat-mdc-header-row {
  @media (min-width: 1280px) {
    height: 48px;
  }
}

::ng-deep .mat-mdc-row {
  @media (min-width: 1280px) {
    height: 64px;
  }
}

::ng-deep .mat-mdc-cell,
::ng-deep .mat-mdc-header-cell {
  @media (min-width: 1280px) {
    font-size: 14px;
    padding: 0 16px;
  }
}
```

---

### PrimeNG table overrides

If using `p-table`, use the `styleClass` prop and target PrimeNG CSS variables:

```scss
.p-datatable {
  @media (min-width: 1280px) {
    .p-datatable-thead > tr > th {
      padding: 14px 16px;
      font-size: 14px;
    }
    .p-datatable-tbody > tr > td {
      padding: 18px 16px;
      font-size: 15px;
    }
  }
}
```

---

## What NOT to Change

- Do not change any column widths relative to each other — only padding and font sizes
- Do not change the horizontal scroll behaviour — the table already scrolls correctly
- Do not change the teal scrollbar track at the bottom (image 2 bottom) — it is correct
- Do not change the sidebar — it is out of scope
- Do not change the column order or which columns are visible
- Do not modify any TypeScript, component logic, or data binding
- Do not change the Customize Columns modal
