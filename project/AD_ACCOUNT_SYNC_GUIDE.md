# Ad Account Connection & Sync Guide

**Frontend Implementation Guide for Account Syncing**

This document explains how to connect user accounts created in the application with Meta (Facebook) ad accounts and sync campaign data.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [OAuth Connection Flow](#oauth-connection-flow)
3. [Ad Account Selection & Connection](#ad-account-selection--connection)
4. [Data Sync Flow](#data-sync-flow)
5. [Frontend Implementation Steps](#frontend-implementation-steps)
6. [API Endpoints Reference](#api-endpoints-reference)
7. [Database Schema](#database-schema)

---

## Architecture Overview

The application uses a **two-step authentication and connection model**:

1. **OAuth Authentication** - User connects their Meta/Facebook account via OAuth2
2. **Ad Account Selection** - User selects which Meta ad account(s) to work with

### Key Entities

| Entity                      | Purpose                            | Key Fields                                         |
| --------------------------- | ---------------------------------- | -------------------------------------------------- |
| `UserEntity`                | Application user account           | `id`, `email`, `password`                          |
| `OAuthAccountEntity`        | Stores OAuth tokens per provider   | `userId`, `provider`, `accessToken`, `tokenExpiry` |
| `AdAccountConnectionEntity` | Links user to specific ad accounts | `userId`, `provider`, `adAccountId`, `active`      |
| `CampaignEntity`            | Platform campaigns synced locally  | `userId`, `externalId`, `platform`, `adAccountId`  |
| `AdSetEntity`               | Platform ad sets synced locally    | `campaignId`, `externalId`, `platform`             |
| `AdEntity`                  | Platform ads synced locally        | `adSetId`, `externalId`, `platform`                |

---

## OAuth Connection Flow

### Step 1: Initiate OAuth Connection

**Frontend Action:**

```javascript
// When user clicks "Connect Meta Account" button
POST /api/oauth/meta/connect
Headers: {
  Authorization: "Bearer <user_jwt_token>"
}

Response: {
  "authorizationUrl": "/oauth2/authorization/facebook"
}
```

**Backend Process:**

1. Creates an `OAuthConnectRequestEntity` with:
   - Random `state` (UUID)
   - `userId` (from JWT)
   - `provider` = "META"
   - `expiresAt` (10 minutes from now)
2. Sets `oauth_connect_state` cookie with the state value
3. Returns the OAuth authorization URL

### Step 2: Frontend Redirects to OAuth

```javascript
// Redirect user to the authorization URL
window.location.href = response.authorizationUrl;
```

User will be redirected to Meta's OAuth consent screen.

### Step 3: OAuth Callback (Handled by Backend)

After user grants permission, Meta redirects back to:

```
GET /login/oauth2/code/facebook?code=XXX&state=YYY
```

**Backend Process (OAuth2LoginSuccessHandler):**

1. Reads `oauth_connect_state` cookie
2. Validates `state` matches a pending `OAuthConnectRequestEntity`
3. Exchanges short-lived token for long-lived token (60 days)
4. Saves/updates `OAuthAccountEntity` with:
   - `accessToken` (long-lived)
   - `tokenExpiry`
   - `externalUserId` (Meta user ID)
   - `grantedScopes`
5. Cleans up connect request and cookie
6. Redirects to frontend success URL:
   ```
   http://localhost:5000/oauth-success?provider=META&status=connected
   ```

### Step 4: Frontend Handles Success

```javascript
// On /oauth-success page
const urlParams = new URLSearchParams(window.location.search);
const provider = urlParams.get("provider"); // "META"
const status = urlParams.get("status"); // "connected"

if (status === "connected") {
  // Show success message
  // Redirect to ad account selection page
  router.push("/settings/ad-accounts");
} else {
  // Handle errors: "missing_state", "state_expired"
  showError(`Connection failed: ${status}`);
}
```

---

## Ad Account Selection & Connection

### Step 5: Fetch Available Ad Accounts from Meta

**Note:** Currently, the application does NOT have a built-in endpoint to fetch Meta ad accounts. You need to implement this or manually configure ad account IDs.

**Option A: Implement Meta Ad Account Fetching (Recommended)**

Create a new endpoint:

```java
@GetMapping("/api/meta/ad-accounts")
public BaseResponse<List<MetaAdAccountDto>> getMetaAdAccounts(Authentication auth) {
    // 1. Get user's OAuth token from OAuthAccountEntity
    // 2. Call Meta Graph API: GET /me/adaccounts
    // 3. Return list of ad accounts with: id, name, account_status, currency
}
```

**Meta API Call:**

```
GET https://graph.facebook.com/v21.0/me/adaccounts
  ?fields=id,name,account_id,account_status,currency,timezone_name,business
  &access_token=<long_lived_token>
```

**Option B: Manual Configuration**

User manually enters their Meta Ad Account ID (format: `act_1234567890`).

### Step 6: Save Ad Account Connection

**Frontend Action:**

```javascript
// After user selects ad account(s)
POST /api/ad-account-connections
Headers: {
  Authorization: "Bearer <user_jwt_token>"
}
Body: {
  "provider": "META",
  "adAccountId": "act_1234567890",
  "adAccountName": "My Business Account",
  "currency": "USD",
  "timezoneName": "America/Los_Angeles",
  "active": true
}

Response: {
  "success": true,
  "data": {
    "id": 1,
    "userId": 123,
    "provider": "META",
    "adAccountId": "act_1234567890",
    "adAccountName": "My Business Account",
    "active": true,
    "createdAt": "2026-03-12T10:30:00"
  }
}
```

**Note:** This endpoint needs to be implemented. Suggested implementation:

```java
@PostMapping("/api/ad-account-connections")
public BaseResponse<AdAccountConnectionEntity> saveConnection(
    Authentication auth,
    @RequestBody AdAccountConnectionRequest request
) {
    Long userId = extractUserId(auth);

    // Check if connection already exists
    Optional<AdAccountConnectionEntity> existing =
        adAccountConnectionRepository.findByUserIdAndProviderAndAdAccountId(
            userId, request.getProvider(), request.getAdAccountId()
        );

    AdAccountConnectionEntity connection = existing.orElse(new AdAccountConnectionEntity());
    connection.setUserId(userId);
    connection.setProvider(request.getProvider());
    connection.setAdAccountId(request.getAdAccountId());
    connection.setAdAccountName(request.getAdAccountName());
    connection.setCurrency(request.getCurrency());
    connection.setTimezoneName(request.getTimezoneName());
    connection.setActive(request.isActive());

    return ok(adAccountConnectionRepository.save(connection));
}
```

---

## Data Sync Flow

Once OAuth and ad accounts are connected, you can sync campaign data.

### Sync Hierarchy

**IMPORTANT:** Sync in this order due to foreign key relationships:

1. **Campaigns** (no dependencies)
2. **Ad Sets** (requires Campaign IDs)
3. **Ads** (requires Ad Set IDs)

### Step 7: Sync Campaigns

**Frontend Action:**

```javascript
GET /api/campaigns/platform/META/act_1234567890
Headers: {
  Authorization: "Bearer <user_jwt_token>"
}

Response: {
  "success": true,
  "data": [
    {
      "id": 1,                              // Local DB ID
      "externalId": "120210012345678",      // Meta campaign ID
      "name": "Summer Sale Campaign",
      "status": "ACTIVE",
      "platform": "META",
      "adAccountId": "act_1234567890",
      "userId": 123,
      "createdAt": "2026-03-01T10:00:00",
      "updatedAt": "2026-03-12T15:30:00"
    }
  ]
}
```

**Backend Process:**

1. Fetches all campaigns from Meta: `GET /act_{adAccountId}/campaigns?fields=id,name,status`
2. For each campaign:
   - Checks if it exists locally by `externalId`
   - **Creates new** or **updates existing** `CampaignEntity`
   - Saves to database
3. Returns synced campaigns

### Step 8: Sync Ad Sets

**Frontend Action:**

```javascript
GET /api/ad-sets/platform/META/act_1234567890
Headers: {
  Authorization: "Bearer <user_jwt_token>"
}

Response: {
  "success": true,
  "data": [
    {
      "id": 5,                              // Local DB ID
      "externalId": "120210087654321",      // Meta ad set ID
      "name": "Summer Sale - Audience 1",
      "status": "ACTIVE",
      "platform": "META",
      "adAccountId": "act_1234567890",
      "campaignExternalId": "120210012345678",  // Link to campaign
      "campaignId": 1,                           // Local campaign FK
      "userId": 123
    }
  ]
}
```

**Backend Process:**

1. Fetches all ad sets from Meta: `GET /act_{adAccountId}/adsets?fields=id,name,status,campaign_id`
2. For each ad set:
   - Checks if exists by `externalId`
   - **Looks up Campaign** by `campaign_id` (external) → finds local `CampaignEntity`
   - Sets `campaign` FK and `campaignExternalId`
   - Saves to database
3. Returns synced ad sets **with campaign relationships established**

### Step 9: Sync Ads

**Frontend Action:**

```javascript
GET /api/ads/platform/META/act_1234567890
Headers: {
  Authorization: "Bearer <user_jwt_token>"
}

Response: {
  "success": true,
  "data": [
    {
      "id": 10,                             // Local DB ID
      "externalId": "120210099887766",      // Meta ad ID
      "name": "Summer Sale - Creative A",
      "status": "ACTIVE",
      "platform": "META",
      "adAccountId": "act_1234567890",
      "adSetExternalId": "120210087654321",    // Link to ad set
      "adSetId": 5,                             // Local ad set FK
      "adSetName": "Summer Sale - Audience 1",
      "creativeId": "120210012312312",
      "userId": 123
    }
  ]
}
```

**Backend Process:**

1. Fetches all ads from Meta: `GET /act_{adAccountId}/ads?fields=id,name,status,adset_id,creative`
2. For each ad:
   - Checks if exists by `externalId`
   - **Looks up AdSet** by `adset_id` (external) → finds local `AdSetEntity`
   - Sets `adSet` FK, `adSetExternalId`, and `adSetName`
   - Extracts `creativeId` from nested creative object
   - Saves to database
3. Returns synced ads **with ad set relationships established**

---

## Frontend Implementation Steps

### Complete Flow

```javascript
// 1. User Registration/Login (existing flow)
const loginResponse = await fetch("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({ email, password }),
});
const { token } = await loginResponse.json();
localStorage.setItem("authToken", token);

// 2. Connect Meta Account
const connectMeta = async () => {
  const response = await fetch("/api/oauth/meta/connect", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
  const { authorizationUrl } = await response.json();
  window.location.href = authorizationUrl;
};

// 3. Handle OAuth Success (on /oauth-success page)
const handleOAuthSuccess = () => {
  const params = new URLSearchParams(window.location.search);
  if (params.get("status") === "connected") {
    // Show success notification
    showNotification("Meta account connected successfully!");

    // Redirect to ad account selection
    router.push("/settings/ad-accounts");
  }
};

// 4. Fetch and Save Ad Account Connection
const linkAdAccount = async (adAccountData) => {
  // TODO: Implement POST /api/ad-account-connections endpoint
  const response = await fetch("/api/ad-account-connections", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      provider: "META",
      adAccountId: adAccountData.id,
      adAccountName: adAccountData.name,
      currency: adAccountData.currency,
      active: true,
    }),
  });
  return response.json();
};

// 5. Sync Campaign Data
const syncAllData = async (adAccountId) => {
  // Must sync in order: Campaigns → AdSets → Ads

  // Step 1: Sync campaigns
  const campaigns = await fetch(`/api/campaigns/platform/META/${adAccountId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).then((r) => r.json());

  console.log("Synced campaigns:", campaigns.data);

  // Step 2: Sync ad sets (requires campaigns exist)
  const adSets = await fetch(`/api/ad-sets/platform/META/${adAccountId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).then((r) => r.json());

  console.log("Synced ad sets:", adSets.data);

  // Step 3: Sync ads (requires ad sets exist)
  const ads = await fetch(`/api/ads/platform/META/${adAccountId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).then((r) => r.json());

  console.log("Synced ads:", ads.data);

  return { campaigns: campaigns.data, adSets: adSets.data, ads: ads.data };
};

// 6. Display synced data
const displayData = (syncedData) => {
  // Render campaigns, ad sets, ads with proper hierarchy
  syncedData.campaigns.forEach((campaign) => {
    // Find related ad sets
    const relatedAdSets = syncedData.adSets.filter(
      (adSet) => adSet.campaignId === campaign.id,
    );

    relatedAdSets.forEach((adSet) => {
      // Find related ads
      const relatedAds = syncedData.ads.filter((ad) => ad.adSetId === adSet.id);
    });
  });
};
```

---

## API Endpoints Reference

### OAuth & Authentication

| Method | Endpoint                      | Description                   | Required Headers                |
| ------ | ----------------------------- | ----------------------------- | ------------------------------- |
| `POST` | `/api/oauth/meta/connect`     | Initiate OAuth connection     | `Authorization: Bearer <token>` |
| `GET`  | `/login/oauth2/code/facebook` | OAuth callback (auto-handled) | None                            |

### Ad Account Management (TO BE IMPLEMENTED)

| Method | Endpoint                                    | Description                    | Required Headers                |
| ------ | ------------------------------------------- | ------------------------------ | ------------------------------- |
| `GET`  | `/api/meta/ad-accounts`                     | Fetch user's Meta ad accounts  | `Authorization: Bearer <token>` |
| `POST` | `/api/ad-account-connections`               | Save ad account connection     | `Authorization: Bearer <token>` |
| `GET`  | `/api/ad-account-connections`               | List user's connected accounts | `Authorization: Bearer <token>` |
| `PUT`  | `/api/ad-account-connections/{id}/activate` | Set account as active          | `Authorization: Bearer <token>` |

### Data Sync

| Method | Endpoint                                           | Description    | Required Headers                |
| ------ | -------------------------------------------------- | -------------- | ------------------------------- |
| `GET`  | `/api/campaigns/platform/{platform}/{adAccountId}` | Sync campaigns | `Authorization: Bearer <token>` |
| `GET`  | `/api/ad-sets/platform/{platform}/{adAccountId}`   | Sync ad sets   | `Authorization: Bearer <token>` |
| `GET`  | `/api/ads/platform/{platform}/{adAccountId}`       | Sync ads       | `Authorization: Bearer <token>` |

---

## Database Schema

### OAuthAccountEntity Table: `oauth_accounts`

| Column             | Type         | Description            |
| ------------------ | ------------ | ---------------------- |
| `id`               | BIGINT       | Primary key            |
| `user_id`          | BIGINT       | FK to users table      |
| `provider`         | VARCHAR(50)  | "META", "TIKTOK", etc. |
| `external_user_id` | VARCHAR(255) | Meta user ID           |
| `access_token`     | TEXT         | Long-lived OAuth token |
| `token_expiry`     | TIMESTAMP    | Token expiration time  |
| `granted_scopes`   | TEXT         | Comma-separated scopes |
| `created_at`       | TIMESTAMP    | Record creation time   |
| `updated_at`       | TIMESTAMP    | Last update time       |

**Unique Constraint:** `(user_id, provider)`

### AdAccountConnectionEntity Table: `ad_account_connection`

| Column            | Type         | Description                |
| ----------------- | ------------ | -------------------------- |
| `id`              | BIGINT       | Primary key                |
| `user_id`         | BIGINT       | FK to users table          |
| `provider`        | VARCHAR(50)  | "META"                     |
| `ad_account_id`   | VARCHAR(64)  | "act_1234567890"           |
| `ad_account_name` | VARCHAR(255) | Display name               |
| `account_id`      | VARCHAR(64)  | Numeric ID without "act\_" |
| `account_status`  | INT          | Meta account status        |
| `currency`        | VARCHAR(10)  | "USD", "EUR", etc.         |
| `timezone_name`   | VARCHAR(64)  | Account timezone           |
| `business_id`     | VARCHAR(64)  | Meta Business Manager ID   |
| `active`          | BOOLEAN      | Is this the active account |
| `created_at`      | TIMESTAMP    | Record creation time       |
| `updated_at`      | TIMESTAMP    | Last update time           |

**Unique Constraint:** `(user_id, provider, ad_account_id)`

### Campaign Table: `campaign`

| Column          | Type         | Description         |
| --------------- | ------------ | ------------------- |
| `id`            | BIGINT       | Primary key (local) |
| `user_id`       | BIGINT       | FK to users         |
| `external_id`   | VARCHAR(64)  | Meta campaign ID    |
| `name`          | VARCHAR(255) | Campaign name       |
| `status`        | VARCHAR(50)  | "ACTIVE", "PAUSED"  |
| `platform`      | VARCHAR(50)  | "META"              |
| `ad_account_id` | VARCHAR(64)  | "act_xxx"           |

**Unique Constraint:** `(user_id, platform, ad_account_id, external_id)`

### AdSet Table: `ad_set`

| Column                 | Type         | Description           |
| ---------------------- | ------------ | --------------------- |
| `id`                   | BIGINT       | Primary key (local)   |
| `campaign_id`          | BIGINT       | **FK to campaign.id** |
| `campaign_external_id` | VARCHAR(64)  | Meta campaign ID      |
| `external_id`          | VARCHAR(64)  | Meta ad set ID        |
| `name`                 | VARCHAR(255) | Ad set name           |
| `status`               | VARCHAR(50)  | Status                |
| `platform`             | VARCHAR(50)  | "META"                |
| `ad_account_id`        | VARCHAR(64)  | "act_xxx"             |

**Unique Constraint:** `(user_id, platform, ad_account_id, external_id)`

### Ad Table: `ad`

| Column               | Type         | Description         |
| -------------------- | ------------ | ------------------- |
| `id`                 | BIGINT       | Primary key (local) |
| `ad_set_id`          | BIGINT       | **FK to ad_set.id** |
| `ad_set_external_id` | VARCHAR(64)  | Meta ad set ID      |
| `ad_set_name`        | VARCHAR(255) | Denormalized name   |
| `external_id`        | VARCHAR(64)  | Meta ad ID          |
| `creative_id`        | VARCHAR(64)  | Meta creative ID    |
| `name`               | VARCHAR(255) | Ad name             |
| `status`             | VARCHAR(50)  | Status              |
| `platform`           | VARCHAR(50)  | "META"              |
| `ad_account_id`      | VARCHAR(64)  | "act_xxx"           |

**Unique Constraint:** `(user_id, platform, ad_account_id, external_id)`

---

## Important Notes

### Foreign Key Relationships

The sync process automatically establishes FK relationships:

- When syncing **AdSets**: looks up `Campaign` by `campaign_external_id` → sets `campaign_id` FK
- When syncing **Ads**: looks up `AdSet` by `adset_external_id` → sets `ad_set_id` FK

This means:

1. **Always sync campaigns first**
2. Then sync ad sets (they will link to campaigns)
3. Finally sync ads (they will link to ad sets)

### Error Handling

Common errors:

| Error                        | Cause                           | Solution                                                                             |
| ---------------------------- | ------------------------------- | ------------------------------------------------------------------------------------ |
| `Invalid connect state`      | Cookie expired or missing       | Re-initiate OAuth flow                                                               |
| `User not found`             | Invalid JWT token               | Re-authenticate user                                                                 |
| `No adAccountId provided`    | Missing config                  | Set `facebook.login.marketing.ad-account-id` in `application.yml` or pass in request |
| `AdAsset not found for hash` | Creative creation without asset | Upload asset first via `/api/page/asset-creative/assets/upload`                      |

### Testing

1. Create user account: `POST /api/auth/register`
2. Login: `POST /api/auth/login`
3. Connect Meta: `POST /api/oauth/meta/connect` → complete OAuth
4. Link ad account: `POST /api/ad-account-connections`
5. Sync data in order:
   - Campaigns: `GET /api/campaigns/platform/META/act_xxx`
   - Ad Sets: `GET /api/ad-sets/platform/META/act_xxx`
   - Ads: `GET /api/ads/platform/META/act_xxx`

---

## Summary

1. **User registers** in your application
2. **User connects Meta account** via OAuth (gets long-lived token)
3. **User selects ad account(s)** to work with (saved in `ad_account_connection` table)
4. **Frontend syncs data** in order: Campaigns → AdSets → Ads
5. **Data is stored locally** with proper FK relationships maintained
6. **User can now manage campaigns** through your application UI

All sync operations are idempotent - running them multiple times will update existing records, not create duplicates.
