# project (backend) — Authentication & API Flow

## Table of Contents

1. [Overview](#overview)
2. [User Registration & Password Handling](#user-registration--password-handling)
3. [Login Flow](#login-flow)
4. [JWT — Generation, Structure, Validation](#jwt--generation-structure-validation)
5. [JWT Filter — Every Incoming Request](#jwt-filter--every-incoming-request)
6. [Security Configuration — Public vs Protected Endpoints](#security-configuration--public-vs-protected-endpoints)
7. [Meta OAuth Flow](#meta-oauth-flow)
8. [How the Meta Access Token Is Processed](#how-the-meta-access-token-is-processed)
9. [Database — What Is Stored](#database--what-is-stored)
10. [How Authenticated API Calls Are Processed](#how-authenticated-api-calls-are-processed)
11. [Redis Caching](#redis-caching)
12. [Password Reset Flow](#password-reset-flow)
13. [Key Files](#key-files)

---

## Overview

The backend is a **Spring Boot 3.2 / Java 21** application backed by **PostgreSQL** (schema managed by Liquibase) and **Redis** (raw platform data cache). Authentication is **stateless JWT**. The Meta integration uses a standard **3-legged OAuth 2.0** flow where the backend handles all token exchange server-side; the long-lived Meta access token is stored in the database and never exposed to the browser.

```
Client ──POST /api/auth/login──► AuthController
                                      │
                              AuthenticationService
                              ├── BCrypt password check
                              ├── JwtService.generateToken()
                              └── Return JWT + userId + actId

Client ──Bearer JWT──► JwtAuthenticationFilter
                              │
                       JwtService.validateToken()
                              │
                       Extract userId, username, role
                       Set SecurityContext
                              │
                       Protected Controller
                       └── extractUserId(auth) → scope all DB queries to that user
```

---

## User Registration & Password Handling

**Endpoint:** `POST /api/auth/register`

- Accepts `RegisterRequestDto` (fullName, username, email, password)
- Password is hashed with **BCrypt** before being stored — the raw password is never saved
- A `UserEntity` row is inserted with `role = USER`
- No email verification step exists; registration is immediate

**USERS table columns written on registration:**

| Column | Value |
|---|---|
| `FULL_NAME` | As provided |
| `USERNAME` | As provided (must be unique) |
| `EMAIL` | As provided (must be unique) |
| `PASSWORD` | BCrypt hash of the raw password |
| `ROLE` | `USER` |

---

## Login Flow

**Endpoint:** `POST /api/auth/login`
**Class:** `AuthenticationService.login()`

```
1. Find UserEntity by email
2. BCryptPasswordEncoder.matches(rawPassword, storedHash)
   └── Mismatch → throw 401
3. JwtService.generateToken(user) → JWT string
4. Query AdAccountConnectionEntity for active META connections belonging to this user
5. Return LoginResponseDto:
   {
     token:   "<jwt>",
     userId:  42,
     role:    "USER",
     actId:   "act_1234567890"   // first active account, or null
   }
```

The `actId` is included so the frontend can immediately use the correct ad account without an extra round-trip.

---

## JWT — Generation, Structure, Validation

**Class:** `JwtService.java`

### Generation

```java
Jwts.builder()
    .subject(user.getUsername())
    .claim("role",   user.getRole())
    .claim("userId", user.getId())
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + expiration))  // +1 hour
    .signWith(getSigningKey(), Jwts.SIG.HS512)
    .compact();
```

| JWT Claim | Value |
|---|---|
| `sub` | Username |
| `role` | `USER` or `ADMIN` |
| `userId` | Database primary key |
| `iat` | Issued-at timestamp |
| `exp` | Expiry timestamp (issued-at + 1 hour) |

### Signing key

- Algorithm: **HS512** (HMAC-SHA-512)
- Secret: Base64-decoded value of env var `JWT_SECRET` (minimum 64 bytes required)

### Validation

```java
Jwts.parser()
    .verifyWith(getSigningKey())
    .build()
    .parseSignedClaims(token);  // throws if signature invalid or token expired
```

Returns `false` (invalid) on `JwtException` or `IllegalArgumentException`. Returns `true` if the token parses cleanly and is not expired.

---

## JWT Filter — Every Incoming Request

**Class:** `JwtAuthenticationFilter extends OncePerRequestFilter`

Runs **once per HTTP request**, before `UsernamePasswordAuthenticationFilter`.

```
Incoming request
  ↓
Read Authorization header
  ├── Missing or does not start with "Bearer " → skip (pass through)
  └── Present
        ↓
      Extract token (remove "Bearer " prefix)
        ↓
      JwtService.validateToken(token)
        ├── Invalid / expired → return HTTP 401, stop chain
        └── Valid
              ↓
            Extract: userId, username, role
            Create UserPrincipal(userId, username)
            Create UsernamePasswordAuthenticationToken(principal, null, authorities)
            SecurityContextHolder.setAuthentication(...)
              ↓
            Continue filter chain → reach controller
```

### Endpoints that bypass the filter

The filter's `shouldNotFilter()` skips:

```
/api/auth/**
/oauth2/**
/login/oauth2/**
/v3/api-docs**
/swagger-ui**
```

---

## Security Configuration — Public vs Protected Endpoints

**Class:** `SecurityConfig.java`

| Endpoint pattern | Access |
|---|---|
| `POST /api/auth/register` | Public |
| `POST /api/auth/login` | Public |
| `GET  /api/oauth/meta/callback` | Public (state-validated internally) |
| `POST /api/user/request-password-reset` | Public |
| `POST /api/user/reset-password` | Public |
| `/v3/api-docs/**`, `/swagger-ui/**` | Public |
| `/oauth2/**`, `/login/oauth2/**` | Public (Spring Security OAuth2) |
| Everything else | Requires valid JWT |

**CORS** allows: `http://localhost:5000`, `http://localhost:8080`

**Session management:** `STATELESS` — no `HttpSession` is created or used for JWT-authenticated requests.

---

## Meta OAuth Flow

**Classes:** `OAuthConnectController`, `MetaOAuthService`, `TokenExchangeService`, `OAuth2LoginSuccessHandler`

### Step 1 — Initiate connect

**Endpoint:** `POST /api/oauth/meta/connect` *(requires JWT)*

```
1. Generate a random UUID as the OAuth state parameter
2. Insert OAuthConnectRequestEntity:
   {
     state:     "<uuid>",
     userId:    <from JWT>,
     provider:  "META",
     expiresAt: now + 10 minutes,
     consumed:  false
   }
3. Build authorization URL:
   https://www.facebook.com/dialog/oauth
     ?client_id=<META_CLIENT_ID>
     &redirect_uri=http://localhost:8080/api/oauth/meta/callback
     &scope=ads_read,ads_management,pages_read_engagement,pages_show_list
     &state=<uuid>
     &response_type=code
4. Return { authorizationUrl: "..." } to the frontend
```

### Step 2 — User grants permissions

The browser is redirected to the Facebook dialog. The user logs in and approves the requested scopes. Facebook redirects back to the callback URL.

### Step 3 — Handle callback

**Endpoint:** `GET /api/oauth/meta/callback?code=CODE&state=UUID` *(public)*

```
Security validations (all must pass):
  ├── state exists in oauth_connect_requests table
  ├── state.consumed == false   (prevents replay attacks)
  ├── state.expiresAt > now     (prevents stale use)
  └── Mark state.consumed = true immediately (single-use guarantee)

Token exchange:
  1. POST https://graph.facebook.com/oauth/access_token
       ?client_id=...&client_secret=...&code=CODE&redirect_uri=...
     → short-lived user token (valid ~2 hours)

  2. POST https://graph.facebook.com/oauth/access_token
       ?grant_type=fb_exchange_token
       &client_id=...&client_secret=...
       &fb_exchange_token=<short-lived>
     → long-lived user token (valid ~60 days)

Data fetch:
  GET /me?fields=id,name,email&access_token=<long-lived>
  GET /me/adaccounts?fields=id,name,account_id,account_status,
                              currency,timezone_name,business
                    &access_token=<long-lived>

Database writes:
  UPSERT oauth_accounts      (one row per user per provider)
  UPSERT ad_account_connection (one row per ad account found)

Redirect:
  First connect → http://localhost:5000/oauth/token
                    ?token=<new-jwt>&userId=42&actId=act_123
  Reconnect     → frontend success page (user already has a JWT)
```

The `client_secret` is never sent to the browser. All token exchange calls are server-to-server.

---

## How the Meta Access Token Is Processed

### After the callback completes

The long-lived token (≈60 days) is stored in `oauth_accounts.access_token`. It is **never returned to the frontend** except implicitly through the fact that the frontend now has a fresh JWT confirming the connection is active.

### Every time a sync or reporting call needs it

**Class:** `TokenService.getAccessToken(user, provider)`

```java
OAuthAccountEntity oauth = repo.findByUserAndProvider(user, "META");

// Refuse if token expires within 2 days
if (oauth.getTokenExpiry().isBefore(LocalDateTime.now().plusDays(2))) {
    throw new TokenExpiredException("Token expired. Please reconnect Meta.");
}

return oauth.getAccessToken();
```

The token is then injected as a query/header parameter in calls to the Meta Graph API (v23.0) by `MetaStrategy`.

### Expiry handling

There is no automatic token refresh. When the token is within 2 days of expiry:

1. Any sync/reporting operation throws `TokenExpiredException`
2. The controller returns a 4xx response
3. The frontend surfaces a "Reconnect Meta" prompt
4. The OAuth flow restarts from Step 1, which issues a new long-lived token and overwrites the old one in the database

---

## Database — What Is Stored

### USERS

```sql
ID          SERIAL PRIMARY KEY
FULL_NAME   VARCHAR(255)
USERNAME    VARCHAR(255) UNIQUE
EMAIL       VARCHAR(255) UNIQUE
PHONE_NUMBER INTEGER
ADDRESS     VARCHAR(255)
PASSWORD    VARCHAR(255)          -- BCrypt hash, never raw
ROLE        VARCHAR(50)           -- 'USER' | 'ADMIN'
```

### oauth_accounts *(one row per user per provider)*

```sql
id               SERIAL PRIMARY KEY
user_id          BIGINT REFERENCES USERS(ID)
provider         VARCHAR(50)       -- 'META'
access_token     VARCHAR(2048)     -- long-lived Meta token
token_expiry     TIMESTAMP
external_user_id VARCHAR(100)      -- Meta user ID returned by /me
granted_scopes   VARCHAR(2048)     -- comma-separated scope list
created_at       TIMESTAMP
updated_at       TIMESTAMP
UNIQUE(user_id, provider)
```

### oauth_connect_requests *(one row per OAuth initiation attempt)*

```sql
state          VARCHAR(128) PRIMARY KEY  -- random UUID
user_id        BIGINT REFERENCES USERS(ID)
provider       VARCHAR(50)
expires_at     TIMESTAMP                 -- creation + 10 minutes
consumed       BOOLEAN DEFAULT FALSE     -- set true when callback fires
completed      BOOLEAN DEFAULT FALSE     -- set true on success
failure_reason VARCHAR(500)
created_at     TIMESTAMP
INDEX ON (user_id), INDEX ON (expires_at)
```

### ad_account_connection *(one row per ad account per user)*

```sql
id               SERIAL PRIMARY KEY
user_id          BIGINT REFERENCES USERS(ID)
provider         VARCHAR(50)
ad_account_id    VARCHAR(64)      -- e.g. 'act_1234567890'
ad_account_name  VARCHAR(255)
account_id       VARCHAR(64)
account_status   INTEGER
currency         VARCHAR(10)
timezone_name    VARCHAR(64)
business_id      VARCHAR(64)
active           BOOLEAN DEFAULT FALSE
last_synced      TIMESTAMP
created_at       TIMESTAMP
updated_at       TIMESTAMP
UNIQUE(user_id, provider, ad_account_id)
INDEX ON (user_id), INDEX ON (provider), INDEX ON (active)
```

### campaign

```sql
id             BIGINT PRIMARY KEY
user_id        BIGINT REFERENCES USERS(ID)
platform       VARCHAR(50)         -- 'META'
ad_account_id  VARCHAR(64)
external_id    VARCHAR(255)        -- Meta campaign ID
name           VARCHAR(255)
status         VARCHAR(50)
raw_data       JSONB               -- full Meta API response
created_at     TIMESTAMP
updated_at     TIMESTAMP
UNIQUE(user_id, platform, ad_account_id, external_id)
```

### adset

```sql
id                  BIGINT PRIMARY KEY
user_id             BIGINT REFERENCES USERS(ID)
campaign_id         BIGINT REFERENCES campaign(id)
platform            VARCHAR(50)
ad_account_id       VARCHAR(64)
external_id         VARCHAR(255)
name                VARCHAR(255)
status              VARCHAR(50)
daily_budget        NUMERIC
lifetime_budget     NUMERIC
optimization_goal   VARCHAR(100)
billing_event       VARCHAR(100)
targeting           JSONB
raw_data            JSONB
UNIQUE(user_id, platform, ad_account_id, external_id)
```

### ad

```sql
id             BIGINT PRIMARY KEY
user_id        BIGINT REFERENCES USERS(ID)
adset_id       BIGINT REFERENCES adset(id)
external_id    VARCHAR(255)
platform       VARCHAR(50)
ad_account_id  VARCHAR(64)
name           VARCHAR(255)
status         VARCHAR(50)
creative_id    BIGINT
raw_data       JSONB
```

### password_reset_tokens

```sql
token       VARCHAR(255) PRIMARY KEY
user_id     BIGINT REFERENCES USERS(ID)
expires_at  TIMESTAMP
```

### user_config

```sql
id              BIGINT PRIMARY KEY
user_id         BIGINT REFERENCES USERS(ID) UNIQUE
theme           VARCHAR(50)
column_config   JSONB
insights_config JSONB
```

---

## How Authenticated API Calls Are Processed

Once the JWT filter has validated the token and placed a `UserPrincipal` in the `SecurityContextHolder`, every protected controller follows the same pattern.

### BaseController helper

```java
protected Long extractUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated())
        throw new RuntimeException("Unauthorized");
    if (auth.getPrincipal() instanceof UserPrincipal p)
        return p.userId();
    throw new RuntimeException("Invalid principal");
}
```

### Example: fetching campaigns

```
GET /api/campaigns/META
Authorization: Bearer <jwt>
                │
   ┌────────────▼──────────────────┐
   │  JwtAuthenticationFilter       │
   │  Validates JWT                 │
   │  Extracts userId=42            │
   │  Sets SecurityContext          │
   └────────────┬──────────────────┘
                │
   ┌────────────▼──────────────────┐
   │  CampaignController            │
   │  Long userId = extractUserId() │
   └────────────┬──────────────────┘
                │
   ┌────────────▼──────────────────┐
   │  CampaignService               │
   │  SELECT * FROM campaign        │
   │  WHERE user_id = 42            │
   │    AND platform = 'META'       │
   └────────────┬──────────────────┘
                │
        BaseResponse<List<CampaignDto>>
        { data: [...], success: true }
```

**Every service query is scoped to the `userId` extracted from the JWT.** A user can never retrieve another user's data by manipulating request parameters.

### Standard response envelope

All endpoints return:

```json
{
  "data": { },
  "success": true,
  "error": null
}
```

On error:

```json
{
  "data": null,
  "success": false,
  "error": "Human-readable message"
}
```

---

## Redis Caching

Raw data fetched from Meta Graph API (campaigns, ad sets, ads, insights) is cached in Redis with a TTL. This prevents redundant calls to the Meta API on repeated requests within the same window.

Cache keys are namespaced by `userId` and `adAccountId` so that one user's cache never bleeds into another user's.

Configuration:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

---

## Password Reset Flow

1. `POST /api/user/request-password-reset` — accepts email
   - Generates a random token, stores in `password_reset_tokens` with `expires_at = now + 1 hour`
   - Sends reset link by email (via Spring Mail / Gmail SMTP)

2. `POST /api/user/reset-password` — accepts token + new password
   - Validates token exists and is not expired
   - BCrypt-hashes the new password and updates `USERS.PASSWORD`
   - Deletes the used token row

---

## Key Files

### Authentication

| File | Purpose |
|---|---|
| `auth/JwtService.java` | Token generation, signing, validation |
| `auth/JwtAuthenticationFilter.java` | Per-request JWT validation middleware |
| `auth/AuthenticationService.java` | Login logic (password check + token issue) |
| `auth/AuthController.java` | `/api/auth/login`, `/api/auth/register` |
| `configuration/SecurityConfig.java` | Public vs protected endpoint rules, CORS, session policy |

### Meta OAuth

| File | Purpose |
|---|---|
| `oauth/api/OAuthConnectController.java` | `/api/oauth/meta/connect`, `/api/oauth/meta/callback` |
| `oauth/service/MetaOAuthService.java` | Orchestrates the full OAuth callback: validate → exchange → save → fetch accounts |
| `oauth/service/TokenExchangeService.java` | Short-lived → long-lived token exchange via Graph API |
| `oauth/service/TokenService.java` | Retrieve and validate stored token before use |
| `oauth/handler/OAuth2LoginSuccessHandler.java` | Spring Security OAuth2 success handler |

### Entities & Repositories

| File | Purpose |
|---|---|
| `user/entity/UserEntity.java` | `USERS` table |
| `oauth/entity/OAuthAccountEntity.java` | `oauth_accounts` table |
| `oauth/entity/OAuthConnectRequestEntity.java` | `oauth_connect_requests` table |
| `auth/AdAccountConnectionEntity.java` | `ad_account_connection` table |
| `campaign/entity/CampaignEntity.java` | `campaign` table |

### Database Migrations (Liquibase)

| Migration | Creates |
|---|---|
| `01.create_user_table.xml` | `USERS` |
| `03.create_oauth_accounts_table.xml` | `oauth_accounts` |
| `14.create_oauth_connect_requests.xml` | `oauth_connect_requests` |
| `15.create_ad_account_connection.xml` | `ad_account_connection` |

### Configuration

```yaml
# application.yml highlights
jwt:
  secret: ${JWT_SECRET}       # 64+ byte Base64 key
  expiration: 3600000         # 1 hour in ms

meta:
  client-id:     ${META_CLIENT_ID}
  client-secret: ${META_CLIENT_SECRET}
  redirect-uri:  http://localhost:8080/api/oauth/meta/callback
  scopes:        ads_read,ads_management,pages_read_engagement,pages_show_list
  graph-api-version: v23.0
  graph-api-base-url: https://graph.facebook.com
```
