# Create Ad Workflow — Responsive Design Prompt

## Task

Fix the responsive scaling of the Create Ad Workflow page.
CSS and template changes only. Do not touch any TypeScript, form logic,
API calls, validation, or component data bindings.

---

## What Is Wrong (Images 1 & 2 — Big Screen, Broken)

On a large monitor (≥ 1280px) the layout renders as a small narrow card
that barely fills the left third of the screen, with the rest being empty
teal background. When the "Select Ad Creative" panel opens (image 2),
both panels are also too narrow and small — they do not use the
available screen width at all.

---

## What Is Correct (Image 3 — Large Screen, Target)

On a large screen the layout should look like image 3:

### Left panel "Create Ad"
- Takes roughly 50% of the available viewport width
- White card with generous padding
- Large, readable form fields — inputs are tall and full-width within the panel
- Labels are uppercase, slightly larger
- "AD NAME" and "PLATFORM" are side by side in a two-column row
- "AD SET" and "STATUS" are side by side in a two-column row
- "CAMPAIGN" is full width
- "FACEBOOK PAGE" is full width
- "AD CREATIVE" is full width with "Choose Creative" and "Create Ad Creative" side by side
- "Publish Ad" and "Cancel" buttons are full-width at the bottom of the panel

### Right panel "Select Ad Creative" (when open)
- Takes roughly 50% of the available viewport width
- Sits directly to the right of the left panel, same height
- Creative image grid: 4 columns on large screen
- Each creative card shows: thumbnail image, creative name (truncated), type badge, status badge, ID text
- Scrollable vertically within the panel
- "Select Existing Creative" and "Create from Asset Library" tabs at the top

---

## Breakpoint Strategy

Use **mobile-first** — small screen is the baseline, scale up with `min-width` queries.

```
sm:  < 768px    — single column, stacked panels
md:  768–1023px — single column, wider form fields
lg:  1024–1279px — two panels appear side by side for the first time
xl:  1280–1535px — full two-panel layout (matches image 3)
2xl: ≥ 1536px   — same as xl with larger spacing
```

---

## Layout Changes

### Page / container wrapper

The outer wrapper that holds the two panels must use flexbox or CSS grid
and respond to viewport width:

```scss
.create-ad-workflow-container {
  display: flex;
  flex-direction: column;  // sm/md: stacked
  gap: 16px;
  padding: 16px;
  min-height: 100vh;
  box-sizing: border-box;

  @media (min-width: 1024px) {
    flex-direction: row;   // lg+: side by side
    align-items: flex-start;
    padding: 24px;
    gap: 20px;
  }

  @media (min-width: 1280px) {
    padding: 32px;
    gap: 24px;
  }
}
```

---

### Left panel "Create Ad"

```scss
.create-ad-panel {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #E5E7EB;
  width: 100%;          // sm/md: full width
  padding: 20px 16px;
  box-sizing: border-box;

  @media (min-width: 1024px) {
    width: 50%;           // lg: half the screen
    flex-shrink: 0;
    padding: 24px 20px;
  }

  @media (min-width: 1280px) {
    padding: 32px 28px;
    border-radius: 16px;
  }
}
```

Panel title "Create Ad":
```scss
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 20px;

  @media (min-width: 1280px) {
    font-size: 20px;
    margin-bottom: 28px;
  }
}
```

---

### Form field labels

```scss
.field-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #6B7280;
  margin-bottom: 4px;

  @media (min-width: 1280px) {
    font-size: 12px;
    letter-spacing: 0.07em;
    margin-bottom: 6px;
  }
}
```

---

### Form inputs and dropdowns

```scss
.form-input,
.form-select,
select,
input[type="text"] {
  height: 36px;
  font-size: 13px;
  padding: 0 12px;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  width: 100%;
  box-sizing: border-box;

  @media (min-width: 1280px) {
    height: 44px;
    font-size: 15px;
    padding: 0 16px;
    border-radius: 10px;
  }
}
```

---

### Form row layout

**"AD NAME" + "PLATFORM" row** — always side by side:
```scss
.row-ad-name-platform {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;

  @media (min-width: 1280px) {
    gap: 20px;
    margin-bottom: 20px;
  }
}
```

**"CAMPAIGN" row** — always full width:
```scss
.row-campaign {
  margin-bottom: 16px;

  @media (min-width: 1280px) {
    margin-bottom: 20px;
  }
}
```

**"FACEBOOK PAGE" row** — always full width:
```scss
.row-facebook-page {
  margin-bottom: 16px;

  @media (min-width: 1280px) {
    margin-bottom: 20px;
  }
}
```

**"AD SET" + "STATUS" row** — always side by side:
```scss
.row-adset-status {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;

  @media (min-width: 1280px) {
    gap: 20px;
    margin-bottom: 20px;
  }
}
```

**"AD CREATIVE" row** — "Choose Creative" + "Create Ad Creative" side by side:
```scss
.row-ad-creative {
  margin-bottom: 20px;
}

.creative-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;

  @media (min-width: 1280px) {
    gap: 12px;
  }
}
```

Creative buttons height:
```scss
.btn-choose-creative,
.btn-create-creative {
  height: 40px;
  font-size: 13px;

  @media (min-width: 1280px) {
    height: 48px;
    font-size: 15px;
    border-radius: 10px;
  }
}
```

---

### "Publish Ad" and "Cancel" buttons

```scss
.btn-publish-ad {
  width: 100%;
  height: 44px;
  font-size: 14px;
  border-radius: 8px;
  margin-bottom: 8px;

  @media (min-width: 1280px) {
    height: 52px;
    font-size: 16px;
    border-radius: 12px;
    margin-bottom: 12px;
  }
}

.btn-cancel {
  width: 100%;
  height: 36px;
  font-size: 13px;
  background: transparent;
  border: none;
  color: #6B7280;
  cursor: pointer;

  @media (min-width: 1280px) {
    height: 44px;
    font-size: 15px;
  }
}
```

---

### Right panel "Select Ad Creative"

This panel only appears when the user opens the creative selector.
On sm/md it stacks below the left panel. On lg+ it sits to the right.

```scss
.select-creative-panel {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #E5E7EB;
  width: 100%;
  padding: 20px 16px;
  box-sizing: border-box;

  @media (min-width: 1024px) {
    width: 50%;
    flex-shrink: 0;
    padding: 20px;
    // Match height of left panel
    align-self: stretch;
  }

  @media (min-width: 1280px) {
    padding: 24px;
    border-radius: 16px;
  }
}
```

Panel title "Select Ad Creative":
```scss
.creative-panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 16px;

  @media (min-width: 1280px) {
    font-size: 20px;
    margin-bottom: 20px;
  }
}
```

---

### Tabs ("Select Existing Creative" / "Create from Asset Library")

```scss
.creative-tab {
  font-size: 13px;
  font-weight: 500;
  padding: 8px 12px;
  border-bottom: 2px solid transparent;
  color: #6B7280;
  cursor: pointer;

  &.active {
    color: #10B981;
    border-bottom-color: #10B981;
  }

  @media (min-width: 1280px) {
    font-size: 15px;
    padding: 10px 16px;
  }
}
```

---

### Creative image grid (inside "Select Existing Creative" tab)

```scss
.creative-grid {
  display: grid;
  gap: 12px;
  overflow-y: auto;

  // sm/md: 2 columns
  grid-template-columns: repeat(2, 1fr);

  // lg: 3 columns
  @media (min-width: 1024px) {
    grid-template-columns: repeat(3, 1fr);
    gap: 14px;
  }

  // xl+: 4 columns (matches image 3)
  @media (min-width: 1280px) {
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
  }
}
```

---

### Creative card (each item in the grid)

```scss
.creative-card {
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 150ms ease;

  &:hover {
    border-color: #10B981;
  }

  &.selected {
    border: 2px solid #10B981;
  }

  @media (min-width: 1280px) {
    border-radius: 10px;
  }
}

.creative-card-thumbnail {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  display: block;
}

.creative-card-info {
  padding: 6px 8px;

  @media (min-width: 1280px) {
    padding: 8px 10px;
  }
}

.creative-card-name {
  font-size: 11px;
  font-weight: 500;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;

  @media (min-width: 1280px) {
    font-size: 12px;
  }
}

.creative-card-badges {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.creative-card-id {
  font-size: 10px;
  color: #9CA3AF;

  @media (min-width: 1280px) {
    font-size: 11px;
  }
}
```

Type and status badges on creative cards:
```scss
.badge-type {
  font-size: 10px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 3px;
  background: #F3F4F6;
  color: #374151;

  @media (min-width: 1280px) {
    font-size: 11px;
    padding: 2px 8px;
  }
}

.badge-active {
  font-size: 10px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 3px;
  background: #D1FAE5;
  color: #065F46;

  @media (min-width: 1280px) {
    font-size: 11px;
    padding: 2px 8px;
  }
}
```

---

### Refresh and Close icons (top-right of creative panel)

```scss
.panel-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.panel-icon-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid #E5E7EB;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #6B7280;

  &:hover {
    background: #F3F4F6;
  }

  @media (min-width: 1280px) {
    width: 34px;
    height: 34px;
    font-size: 16px;
    border-radius: 8px;
  }
}
```

---

## What NOT to Change

- Do not change any TypeScript component logic, form controls, or reactive form bindings
- Do not change validation logic or error message handling
- Do not change any API service calls
- Do not change the tab switching logic between "Select Existing Creative" and "Create from Asset Library"
- Do not change what data is shown on creative cards — only how they are sized and laid out
- Do not change the creative grid scroll behaviour — only the column count and gap
- Do not touch the sidebar
- Do not change the teal background of the page — it is correct at all sizes
