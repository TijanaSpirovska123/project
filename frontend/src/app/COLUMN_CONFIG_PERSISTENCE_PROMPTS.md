# Column Config Persistence — Implementation Prompts

> These prompts cover **only** the column customization persistence feature.
> Everything else (status badges, toggle spinner, bulk actions, etc.) is handled
> in the separate frontend agent prompt (FRONTEND_AGENT_PROMPT.md).
> Do not implement anything outside the scope described here.

---

## Context

The Meta overview page (`/meta`) has a Campaigns / Ad Sets / Ads table.
Each table has a **Customize Columns** modal (opened by the `+` button in the table header).
The modal already works — users can check/uncheck columns and reorder them.

**The problem:** when the user logs out and logs back in, the column selection resets
to the default. The chosen columns are only stored in component memory.

**The fix:** persist the column selection to the database so it survives logout/login.

---

## What Already Exists (Do Not Rewrite)

- The Customize Columns modal UI — fully working, do not touch it
- The table column rendering logic — fully working, do not touch it
- The existing `UserEntity` and `UserRepository`
- Spring Boot 3.2 / Java 21 backend with Liquibase for migrations
- Angular 21 frontend with RxJS, Reactive Forms, ngx-toastr

---

## PROMPT 1 — Backend

Paste this entire prompt to your backend AI agent:

---

### Task

Implement two endpoints to persist and retrieve per-user column configuration
for the campaign/ad set/ad tables in the Meta overview page.

This is a focused task. Do not modify anything outside the files listed below.

### What to build

#### 1. Liquibase migration — new `user_config` table

Create a new migration file in `src/main/resources/db.changelog/changes/`
and reference it in `db.changelog-master.xml`.

Table definition:
```sql
CREATE TABLE user_config (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    config_type VARCHAR(50)  NOT NULL,
    config_json TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_config UNIQUE (user_id, config_type)
);
```

`config_type` values used by this feature:
- `COLUMN_CONFIG_CAMPAIGN`
- `COLUMN_CONFIG_AD_SET`
- `COLUMN_CONFIG_AD`

(The same table will be reused later for insights metric card config with a different
`config_type` — design for that reuse now, but only implement the column config part.)

#### 2. `UserConfigEntity`

```java
@Entity
@Table(name = "user_config")
public class UserConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String configType;   // "COLUMN_CONFIG_CAMPAIGN" etc.

    @Column(nullable = false, columnDefinition = "TEXT")
    private String configJson;   // JSON array: ["name","status","budget","impressions",...]

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
```

#### 3. `UserConfigRepository`

```java
public interface UserConfigRepository extends JpaRepository<UserConfigEntity, Long> {
    Optional<UserConfigEntity> findByUserIdAndConfigType(Long userId, String configType);
}
```

#### 4. `UserConfigService`

```java
@Service
@RequiredArgsConstructor
public class UserConfigService {

    private final UserConfigRepository userConfigRepository;
    private final ObjectMapper objectMapper;

    // Default columns per entity type — returned when user has no saved config
    private static final Map<String, List<String>> DEFAULTS = Map.of(
        "COLUMN_CONFIG_CAMPAIGN", List.of(
            "name", "status", "budget", "impressions", "reach", "actions",
            "delivery", "attribution_setting", "ends", "last_significant_edit",
            "bid_strategy", "schedule", "ad_set_name"
        ),
        "COLUMN_CONFIG_AD_SET", List.of(
            "name", "status", "budget", "impressions", "reach",
            "clicks", "ctr", "cpc", "spend"
        ),
        "COLUMN_CONFIG_AD", List.of(
            "name", "status", "impressions", "reach", "clicks", "ctr", "cpc", "spend"
        )
    );

    public List<String> getColumnConfig(Long userId, String entityType) {
        String configType = toConfigType(entityType);
        return userConfigRepository
            .findByUserIdAndConfigType(userId, configType)
            .map(entity -> parseJson(entity.getConfigJson()))
            .orElseGet(() -> DEFAULTS.getOrDefault(configType, List.of()));
    }

    @Transactional
    public void saveColumnConfig(Long userId, String entityType, List<String> columns) {
        String configType = toConfigType(entityType);
        UserConfigEntity entity = userConfigRepository
            .findByUserIdAndConfigType(userId, configType)
            .orElseGet(() -> {
                UserConfigEntity e = new UserConfigEntity();
                e.setUserId(userId);
                e.setConfigType(configType);
                e.setCreatedAt(Instant.now());
                return e;
            });

        entity.setConfigJson(toJson(columns));
        entity.setUpdatedAt(Instant.now());
        userConfigRepository.save(entity);
    }

    private String toConfigType(String entityType) {
        return switch (entityType.toUpperCase()) {
            case "CAMPAIGN" -> "COLUMN_CONFIG_CAMPAIGN";
            case "AD_SET"   -> "COLUMN_CONFIG_AD_SET";
            case "AD"       -> "COLUMN_CONFIG_AD";
            default -> throw new IllegalArgumentException("Unknown entityType: " + entityType);
        };
    }

    private List<String> parseJson(String json) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(List<String> columns) {
        try {
            return objectMapper.writeValueAsString(columns);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize column config", e);
        }
    }
}
```

#### 5. DTOs

```java
// Request — sent by frontend when user clicks Apply
public record ColumnConfigRequest(
    String entityType,          // "CAMPAIGN", "AD_SET", or "AD"
    List<String> columns        // ordered list of column IDs
) {}

// Response — sent to frontend on GET
public record ColumnConfigResponse(
    String entityType,
    List<String> columns,
    boolean isDefault           // true if no saved config exists yet for this user
) {}
```

#### 6. `UserConfigController`

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserConfigController {

    private final UserConfigService userConfigService;

    /**
     * Load column config for a given entity type.
     * Returns the user's saved config, or the default if none exists.
     * Called by the frontend on page load.
     */
    @GetMapping("/column-config")
    public ResponseEntity<ColumnConfigResponse> getColumnConfig(
            @RequestParam String entityType,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        List<String> columns = userConfigService.getColumnConfig(userId, entityType);
        boolean isDefault = /* check if a saved record exists */
            !userConfigRepository.existsByUserIdAndConfigType(
                userId, toConfigType(entityType));

        return ResponseEntity.ok(
            new ColumnConfigResponse(entityType, columns, isDefault));
    }

    /**
     * Save column config for a given entity type.
     * Called when user clicks Apply in the Customize Columns modal.
     * Upserts — safe to call multiple times.
     */
    @PatchMapping("/column-config")
    public ResponseEntity<Void> saveColumnConfig(
            @RequestBody @Valid ColumnConfigRequest request,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        userConfigService.saveColumnConfig(
            userId, request.entityType(), request.columns());

        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(Authentication authentication) {
        // adapt to your existing JWT/UserDetails pattern
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return Long.parseLong(userDetails.getUsername());
    }
}
```

Also add `existsByUserIdAndConfigType` to `UserConfigRepository`:
```java
boolean existsByUserIdAndConfigType(Long userId, String configType);
```

#### 7. Validation

Add to `ColumnConfigRequest`:
- `entityType` must be one of `CAMPAIGN`, `AD_SET`, `AD` — validate with a custom validator or `@Pattern`
- `columns` must not be null or empty — validate with `@NotEmpty`
- Return `400` with `{ status: 400, message: "Invalid entityType", code: "VALIDATION_ERROR" }` on bad input

#### 8. Security

Both endpoints are protected by your existing JWT filter — no additional security config needed.
They sit under `/api/users/` which is already in the protected path pattern.

#### 9. What NOT to implement

- Do not implement insights metric card config (`PATCH /api/users/insights-config`) — that is a separate task
- Do not implement bulk status update — separate task
- Do not touch any existing controller, service, or entity

---

## PROMPT 2 — Frontend

Paste this entire prompt to your frontend AI agent:

---

### Task

Wire the existing Customize Columns modal to the new backend persistence endpoints.
The modal UI already works — you are only adding save/load behaviour around it.

Do not rewrite or restructure the modal. Do not change how columns render in the table.
Only add the two integration points described below.

### Context

- Angular 21 app, RxJS 7.8, ngx-toastr for notifications
- The Meta overview page (`/meta`) has `MetaComponent` with Campaigns / Ad Sets / Ads tabs
- Each tab has a table. The `+` button opens `CustomizeColumnsComponent` (or a modal/dialog)
- Currently: when user clicks Apply, the selected columns update in memory only
- Goal: on Apply → also save to backend. On page load → load from backend first

### Backend endpoints available

```
GET  /api/users/column-config?entityType=CAMPAIGN
     Response: { entityType: "CAMPAIGN", columns: ["name","status","budget",...], isDefault: boolean }

PATCH /api/users/column-config
     Request body: { entityType: "CAMPAIGN", columns: ["name","status","budget",...] }
     Response: 204 No Content
```

Valid `entityType` values: `"CAMPAIGN"`, `"AD_SET"`, `"AD"`

### Step 1 — Add a typed model

```typescript
// In your models folder (e.g. models/user-config.model.ts)

export interface ColumnConfigResponse {
  entityType: string;
  columns: string[];
  isDefault: boolean;
}

export interface ColumnConfigRequest {
  entityType: string;
  columns: string[];
}
```

### Step 2 — Add service methods

Add to your existing `UserService` (or create `UserConfigService` if it doesn't exist):

```typescript
// services/user-config.service.ts (or add to existing user service)

@Injectable({ providedIn: 'root' })
export class UserConfigService extends CoreService {

  getColumnConfig(entityType: 'CAMPAIGN' | 'AD_SET' | 'AD'): Observable<ColumnConfigResponse> {
    return this.getByPath<ColumnConfigResponse>(
      `/api/users/column-config?entityType=${entityType}`
    );
  }

  saveColumnConfig(entityType: 'CAMPAIGN' | 'AD_SET' | 'AD', columns: string[]): Observable<void> {
    return this.patch<void>('/api/users/column-config', { entityType, columns });
  }
}
```

### Step 3 — Load on page init

In `MetaComponent` (or wherever the table columns are initialised), load the saved config
for the active tab when the component initialises:

```typescript
// MetaComponent

isLoadingColumns = false;

ngOnInit(): void {
  this.loadColumnConfig('CAMPAIGN');  // load for the default active tab
}

onTabChange(tab: 'CAMPAIGN' | 'AD_SET' | 'AD'): void {
  this.loadColumnConfig(tab);
}

private loadColumnConfig(entityType: 'CAMPAIGN' | 'AD_SET' | 'AD'): void {
  this.isLoadingColumns = true;
  this.userConfigService.getColumnConfig(entityType).pipe(
    finalize(() => this.isLoadingColumns = false)
  ).subscribe({
    next: config => {
      this.applyColumnConfig(config.columns);
      // applyColumnConfig = your existing method that sets which columns are visible
    },
    error: () => {
      // silently fall back to defaults — do not show error toast for this
      // the table will render with whatever defaults are already hardcoded
    }
  });
}
```

**Important:** show a skeleton or spinner over the table header row while `isLoadingColumns` is true,
so the user doesn't see the columns snap from default to saved. Use the `<app-loading-state>`
shared component if it has been built, otherwise a simple `*ngIf`.

### Step 4 — Save on Apply

Find the place in the code where the Customize Columns modal's Apply button is handled.
It currently updates the column list in memory. After that update, also call the save endpoint:

```typescript
onColumnsApplied(selectedColumns: string[]): void {
  // existing logic — keep exactly as-is
  this.applyColumnConfig(selectedColumns);

  // NEW: persist to backend
  const entityType = this.activeTab;  // 'CAMPAIGN' | 'AD_SET' | 'AD'
  this.userConfigService.saveColumnConfig(entityType, selectedColumns).subscribe({
    next: () => {
      // silent success — no toast needed, this is background save
    },
    error: () => {
      this.toastr.warning(
        'Column preferences could not be saved — they will reset on next login.',
        '',
        { timeOut: 5000 }
      );
    }
  });
}
```

Do not disable the Apply button or show a spinner while saving — the save is fire-and-forget
from the user's perspective. The table updates immediately regardless of whether the save succeeds.

### Step 5 — Tab switching

When the user switches between Campaigns / Ad Sets / Ads tabs, load the saved config
for the newly active tab:

```typescript
onTabChange(newTab: 'CAMPAIGN' | 'AD_SET' | 'AD'): void {
  this.activeTab = newTab;
  this.loadColumnConfig(newTab);   // from Step 3
}
```

This means each tab independently remembers its own column selection.

### Step 6 — Behaviour summary to verify

After implementation, manually verify this sequence works:

1. User opens `/meta`, Campaigns tab loads → columns come from backend (or defaults on first visit)
2. User clicks `+` → Customize Columns modal opens
3. User checks/unchecks columns, clicks Apply
4. Table updates immediately (existing behaviour)
5. `PATCH /api/users/column-config` is called silently in the background
6. User clicks Log Out
7. User logs back in, navigates to `/meta`
8. Table loads → `GET /api/users/column-config?entityType=CAMPAIGN` returns the saved columns
9. Table renders with the previously chosen columns ✓
10. User switches to Ad Sets tab → `GET /api/users/column-config?entityType=AD_SET` is called
11. If the user never customised Ad Sets columns, backend returns `isDefault: true` and the hardcoded defaults are used

### What NOT to implement

- Do not implement insights metric card config persistence — separate task
- Do not implement any other part of `FRONTEND_AGENT_PROMPT.md` — this is column config only
- Do not rewrite the Customize Columns modal UI
- Do not rewrite the table column rendering logic
- Do not add bulk actions
