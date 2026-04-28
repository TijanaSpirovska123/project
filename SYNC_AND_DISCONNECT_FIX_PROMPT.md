# Sync & Disconnect Fix — Agent Prompt

## Read First

Before making any changes, read ALL of these completely:
- `AbstractPlatformService.java` — the full implementation
- `PlatformService.java` — the interface
- `PlatformStrategy.java` — the strategy interface
- The sync controller/endpoint
- The sync request DTO
- `CampaignService.java`, `AdSetService.java`, `AdService.java`
- `CampaignRepository.java`, `AdSetRepository.java`, `AdRepository.java`
- `BasePlatformEntity.java` — check what fields exist (userId, platform, adAccountId)
- The OAuth disconnect endpoint and service
- `AdAccountConnectionRepository.java`
- `AdAccountConnectionEntity.java`
- The frontend SyncAccountsComponent HTML and TypeScript
- The frontend sync service

Produce a report of:
1. What fields `BasePlatformEntity` has — specifically userId, platform, adAccountId
2. What the sync endpoint currently does with adAccountId
3. What delete methods already exist on campaign/adset/ad repositories
4. What the disconnect endpoint currently does
5. What generic repository methods `JpaRepository` provides

---

## Behavior to implement

### Sync
- Uses only the specific adAccountId selected by the user
- Upserts data — existing data from OTHER ad accounts untouched
- `syncFromPlatform()` already does upsert — do not change it
- Only fix is to pass the correct adAccountId from frontend → backend

### Disconnect specific ad account
- Deletes campaigns + ad sets + ads for that specific adAccountId
- Other ad accounts' data untouched
- ad_account_connection row marked inactive

### Disconnect Meta entirely
- Deletes ALL campaigns + ad sets + ads for user + META
- All ad_account_connection rows deactivated
- oauth_accounts row deactivated

---

## PART 1 — Extend AbstractPlatformService

Add two new methods to `AbstractPlatformService` so the delete
logic is available to ALL platform services automatically.
Any future platform (TikTok, Pinterest, etc.) gets this for free.

### Step 1 — Add abstract hook to get the repository

`AbstractPlatformService` already holds `JpaRepository<E, Long> repository`.
The delete queries need to filter by `userId`, `platform`, and `adAccountId`.

Add a new abstract method that subclasses must implement to
provide the delete queries. This keeps the generic service clean
while letting each concrete service define how to delete:

```java
// Add to AbstractPlatformService as abstract methods

/**
 * Delete all entities for a specific user + platform + adAccountId.
 * Called when disconnecting a specific ad account.
 */
protected abstract void deleteAllByUserAndPlatformAndAdAccount(
    Long userId, String platform, String adAccountId);

/**
 * Delete all entities for a specific user + platform (all ad accounts).
 * Called when disconnecting an entire platform connection.
 */
protected abstract void deleteAllByUserAndPlatform(
    Long userId, String platform);
```

### Step 2 — Add public wipe methods to AbstractPlatformService

Add two public methods that call the abstract hooks:

```java
/**
 * Wipes all synced data for a specific ad account.
 * Use when the user disconnects a single ad account.
 */
@Transactional
public void wipeAdAccount(UserEntity user, Provider platform,
                           String adAccountId) {
    deleteAllByUserAndPlatformAndAdAccount(
        user.getId(), platform.name(), adAccountId);
}

/**
 * Wipes all synced data for an entire platform connection.
 * Use when the user disconnects Meta/TikTok/etc entirely.
 */
@Transactional
public void wipePlatform(UserEntity user, Provider platform) {
    deleteAllByUserAndPlatform(user.getId(), platform.name());
}
```

### Step 3 — Implement the abstract methods in each service

**In CampaignService:**

First add delete methods to `CampaignRepository`:

```java
// CampaignRepository
@Modifying
@Transactional
@Query("DELETE FROM CampaignEntity c " +
       "WHERE c.user.id = :userId " +
       "AND c.platform = :platform " +
       "AND c.adAccountId = :adAccountId")
void deleteAllByUserIdAndPlatformAndAdAccountId(
    @Param("userId") Long userId,
    @Param("platform") String platform,
    @Param("adAccountId") String adAccountId);

@Modifying
@Transactional
@Query("DELETE FROM CampaignEntity c " +
       "WHERE c.user.id = :userId " +
       "AND c.platform = :platform")
void deleteAllByUserIdAndPlatform(
    @Param("userId") Long userId,
    @Param("platform") String platform);
```

Then implement in CampaignService:

```java
@Override
protected void deleteAllByUserAndPlatformAndAdAccount(
        Long userId, String platform, String adAccountId) {
    campaignRepository.deleteAllByUserIdAndPlatformAndAdAccountId(
        userId, platform, adAccountId);
}

@Override
protected void deleteAllByUserAndPlatform(
        Long userId, String platform) {
    campaignRepository.deleteAllByUserIdAndPlatform(userId, platform);
}
```

**In AdSetService — same pattern:**

```java
// AdSetRepository
@Modifying
@Transactional
@Query("DELETE FROM AdSetEntity a " +
       "WHERE a.user.id = :userId " +
       "AND a.platform = :platform " +
       "AND a.adAccountId = :adAccountId")
void deleteAllByUserIdAndPlatformAndAdAccountId(
    @Param("userId") Long userId,
    @Param("platform") String platform,
    @Param("adAccountId") String adAccountId);

@Modifying
@Transactional
@Query("DELETE FROM AdSetEntity a " +
       "WHERE a.user.id = :userId " +
       "AND a.platform = :platform")
void deleteAllByUserIdAndPlatform(
    @Param("userId") Long userId,
    @Param("platform") String platform);
```

```java
// AdSetService
@Override
protected void deleteAllByUserAndPlatformAndAdAccount(
        Long userId, String platform, String adAccountId) {
    adSetRepository.deleteAllByUserIdAndPlatformAndAdAccountId(
        userId, platform, adAccountId);
}

@Override
protected void deleteAllByUserAndPlatform(Long userId, String platform) {
    adSetRepository.deleteAllByUserIdAndPlatform(userId, platform);
}
```

**In AdService — same pattern:**

```java
// AdRepository
@Modifying
@Transactional
@Query("DELETE FROM AdEntity a " +
       "WHERE a.user.id = :userId " +
       "AND a.platform = :platform " +
       "AND a.adAccountId = :adAccountId")
void deleteAllByUserIdAndPlatformAndAdAccountId(
    @Param("userId") Long userId,
    @Param("platform") String platform,
    @Param("adAccountId") String adAccountId);

@Modifying
@Transactional
@Query("DELETE FROM AdEntity a " +
       "WHERE a.user.id = :userId " +
       "AND a.platform = :platform")
void deleteAllByUserIdAndPlatform(
    @Param("userId") Long userId,
    @Param("platform") String platform);
```

```java
// AdService
@Override
protected void deleteAllByUserAndPlatformAndAdAccount(
        Long userId, String platform, String adAccountId) {
    adRepository.deleteAllByUserIdAndPlatformAndAdAccountId(
        userId, platform, adAccountId);
}

@Override
protected void deleteAllByUserAndPlatform(Long userId, String platform) {
    adRepository.deleteAllByUserIdAndPlatform(userId, platform);
}
```

**Important:** Read entity field names from existing entity classes
before writing JPQL queries. The field names in JPQL must match
the Java field names exactly (e.g. `c.user.id` only if the entity
has a `user` relationship, otherwise use `c.userId`).

---

## PART 2 — Fix sync to use specific adAccountId

### Backend — Sync controller

Find the sync endpoint. Update it to:
1. Use only the adAccountId from the request
2. Validate the account belongs to the user

```java
@PostMapping("/sync")
@Transactional
public ResponseEntity<?> sync(Authentication auth,
                               @RequestBody SyncRequest req) {
    Long userId = extractUserId(auth);
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));

    Provider provider = Provider.from(req.getProvider());
    String adAccountId = req.getAdAccountId();

    // Security: verify this adAccountId belongs to this user
    boolean owned = adAccountConnectionRepository
        .existsByUserIdAndProviderAndAdAccountId(
            userId, req.getProvider(), adAccountId);
    if (!owned) {
        return ResponseEntity.status(403)
            .body("Ad account does not belong to this user");
    }

    // Sync only for the selected ad account — upsert, no wipe
    campaignService.syncFromPlatform(user, provider, adAccountId);
    adSetService.syncFromPlatform(user, provider, adAccountId);
    adService.syncFromPlatform(user, provider, adAccountId);

    return ResponseEntity.ok().build();
}
```

If SyncRequest DTO does not have `adAccountId` — add it:

```java
public class SyncRequest {
    private String provider;
    private String adAccountId; // add if missing
}
```

### Frontend — Verify adAccountId is sent

In the existing sync service, confirm `adAccountId` is in the body:

```typescript
syncData(provider: string, adAccountId: string): Observable<any> {
  return this.http.post(`${this.apiUrl}/sync`, {
    provider,
    adAccountId
  });
}
```

---

## PART 3 — Disconnect specific ad account

### Backend — New endpoint

```java
@DeleteMapping("/oauth/connections/{adAccountId}")
@Transactional
public ResponseEntity<?> disconnectAdAccount(
        @PathVariable String adAccountId,
        Authentication auth) {

    Long userId = extractUserId(auth);
    UserEntity user = userRepository.findById(userId)
        .orElseThrow();

    // Security check
    boolean owned = adAccountConnectionRepository
        .existsByUserIdAndProviderAndAdAccountId(
            userId, "META", adAccountId);
    if (!owned) {
        return ResponseEntity.status(403).build();
    }

    // Delete synced data using AbstractPlatformService.wipeAdAccount()
    campaignService.wipeAdAccount(user, Provider.META, adAccountId);
    adSetService.wipeAdAccount(user, Provider.META, adAccountId);
    adService.wipeAdAccount(user, Provider.META, adAccountId);

    // Deactivate the ad account connection row
    adAccountConnectionRepository
        .findByUserIdAndProviderAndAdAccountId(userId, "META", adAccountId)
        .ifPresent(conn -> {
            conn.setActive(false);
            adAccountConnectionRepository.save(conn);
        });

    return ResponseEntity.ok().build();
}
```

---

## PART 4 — Disconnect Meta entirely

### Backend — Update existing disconnect endpoint

Find the existing disconnect endpoint (likely DELETE /oauth/meta
or POST /oauth/disconnect). After the existing logic that deactivates
the oauth_accounts row, add:

```java
// After existing disconnect logic — add cleanup:
campaignService.wipePlatform(user, Provider.META);
adSetService.wipePlatform(user, Provider.META);
adService.wipePlatform(user, Provider.META);
```

---

## PART 5 — Frontend UI for disconnecting specific ad account

### In SyncAccountsComponent

Add a way for the user to see their connected ad accounts and
disconnect individual ones. The existing sync modal already
shows the list of ad accounts — extend it or add a separate
"Manage Accounts" section.

**Option — Add disconnect button per account in the sync modal:**

In the existing ad account selector modal, add a small disconnect
button next to each account:

```html
<div class="account-item"
     [class.selected]="selectedAccount?.adAccountId === account.adAccountId"
     (click)="selectAccount(account)">
  <div class="account-check">...</div>
  <div class="account-info">
    <div class="account-name">{{ account.adAccountName }}</div>
    <div class="account-meta">{{ account.adAccountId }}</div>
  </div>
  <!-- Disconnect single account button -->
  <button class="account-disconnect-btn"
          (click)="$event.stopPropagation(); disconnectAdAccount(account)"
          [disabled]="disconnectingAccount === account.adAccountId"
          title="Disconnect this ad account">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2" width="14" height="14">
      <path d="M18.36 6.64a9 9 0 1 1-12.73 0"/>
      <line x1="12" y1="2" x2="12" y2="12"/>
    </svg>
  </button>
</div>
```

```typescript
disconnectingAccount: string | null = null;

disconnectAdAccount(account: AdAccountConnection): void {
  if (!confirm(
    `Disconnect "${account.adAccountName}"? ` +
    `This will remove all synced campaigns, ad sets, and ads for this account.`
  )) return;

  this.disconnectingAccount = account.adAccountId;

  this.syncService.disconnectAdAccount(account.adAccountId).subscribe({
    next: () => {
      this.disconnectingAccount = null;
      this.toastr.success(
        `"${account.adAccountName}" disconnected successfully`
      );
      // Remove from local list
      this.adAccounts = this.adAccounts.filter(
        a => a.adAccountId !== account.adAccountId
      );
      // If no accounts left, close modal and reload page state
      if (this.adAccounts.length === 0) {
        this.closeSyncModal();
        this.loadConnectionStatus();
      }
    },
    error: () => {
      this.disconnectingAccount = null;
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Failed to disconnect ad account');
      }
    }
  });
}
```

Add to the sync service:

```typescript
disconnectAdAccount(adAccountId: string): Observable<void> {
  return this.http.delete<void>(
    `${this.apiUrl}/oauth/connections/${adAccountId}`
  );
}
```

**Styling for the disconnect button:**

```scss
.account-disconnect-btn {
  margin-left: auto;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 150ms;
  font-family: inherit;

  &:hover {
    background: var(--red-dim);
    border-color: var(--red);
    color: var(--red);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}
```

---

## Rules

- Read entity field names before writing JPQL — names must match exactly
- The two abstract methods MUST be implemented in CampaignService,
  AdSetService, and AdService — the project will not compile otherwise
- All delete operations must be `@Modifying @Transactional`
- Do not change `syncFromPlatform()` in AbstractPlatformService
- Do not change any existing entity or database schema
- Disconnect specific account: only deletes that account's data
- Disconnect Meta entirely: deletes ALL Meta data for the user
- All new SCSS uses existing CSS variables — no hardcoded colors
- Dark mode works for the new disconnect button
- The confirm dialog before disconnect is required — no accidental deletes

---

## Verification

**Sync correct account:**
- [ ] User selects account B → only account B campaigns synced
- [ ] Account A campaigns still visible in Overview
- [ ] Selecting account A later adds A's campaigns alongside B's

**Disconnect specific ad account:**
- [ ] Disconnect button appears next to each account in the modal
- [ ] Confirm dialog appears before disconnecting
- [ ] After disconnect: that account's campaigns gone from Overview
- [ ] Other accounts' campaigns remain untouched
- [ ] Disconnected account removed from the modal list

**Disconnect Meta entirely:**
- [ ] Click main Disconnect button → all Meta campaigns removed
- [ ] Overview empty after full disconnect
- [ ] Reconnecting Meta and syncing shows fresh data only
