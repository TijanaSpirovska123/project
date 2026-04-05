# High-Level Architecture — Multi-Platform Vision

## Overview

This document describes the target architecture for the Marketing Campaign Manager platform as it scales to a full SaaS product supporting multiple ad platforms. The design is built around a **cross-platform unified view** — one place to manage, report on, and optimize campaigns across Meta, Google, TikTok, LinkedIn, Pinterest, and Reddit Ads.

The current implementation (Meta only) is already architected correctly for this expansion. Adding new platforms is purely additive — no existing code changes required.

---

## Core Design Principle: Platform Extensibility

The entire backend is built around two abstractions that make multi-platform support clean:

```
AbstractPlatformService       — common CRUD + sync logic (implemented once)
        │
        └── MetaCampaignStrategy     — Meta-specific API calls
        └── TikTokCampaignStrategy   — TikTok-specific API calls  (future)
        └── GoogleCampaignStrategy   — Google-specific API calls  (future)
        └── LinkedInCampaignStrategy — LinkedIn-specific API calls (future)
        └── PinterestCampaignStrategy
        └── RedditCampaignStrategy
```

Adding a new platform = implementing the strategy interface + OAuth handler. Zero changes to service, controller, or database layer.

---

## System Layers

### 1. Users (SaaS)

Each user has their own isolated account:

- Registers and logs in with email + password (JWT-based)
- Connects one or more ad platform accounts via OAuth
- Manages campaigns, ad sets, and ads across all connected platforms
- Data is fully isolated per user (all queries scoped by `userId`)

### 2. Angular Frontend

The frontend is the unified control surface for all platforms.

#### Existing modules

| Module | Description |
|---|---|
| Auth pages | Login, sign-up, OAuth success handler |
| Auth guard | Protects all routes requiring login |
| Meta dashboard | Campaigns, ad sets, ads — overview and status management |
| Create ad workflow | Multi-step ad creation (name → platform → campaign → creative → publish) |
| Creative library | Asset browser with variant preview |
| Analytics | Charts and performance data |
| Insights | Platform insights view |
| Sync accounts | OAuth connection per platform |

#### New modules (roadmap)

| Module | Description |
|---|---|
| Unified dashboard | All platforms in one view — campaigns, ad sets, ads with platform badge |
| Unified reports | Normalized metrics (impressions, clicks, spend, ROAS) across all platforms |
| Alerts center | Notification inbox for triggered automated rules |
| Budget optimizer | Cross-platform spend vs. ROAS visualization + recommendations |

### 3. Spring Boot Backend

#### Existing services

| Service | Description |
|---|---|
| `AbstractPlatformService` | Template for CRUD + sync per entity type across all platforms |
| Platform strategies | One impl per platform per entity (campaign, ad set, ad, creative) |
| OAuth token manager | Stores and refreshes long-lived tokens per user per platform |
| JWT auth service | User registration, login, token validation |
| Asset service | Upload, crop to variants, store in MinIO/S3 |
| `PlatformRawDataCache` | Redis cache for raw API responses, TTL per entity |
| Liquibase | Schema version control and migrations |
| MapStruct | Entity ↔ DTO mapping |

#### New services (roadmap)

| Service | Description |
|---|---|
| Insights aggregator | Normalizes metric names across platforms into a unified schema |
| Automated rules engine | Scheduler that evaluates user-defined rules against live metrics |
| Budget optimizer engine | Reads cross-platform ROAS, surfaces reallocation recommendations |
| Notifier service | Sends email / push / webhook on rule triggers or budget alerts |

### 4. Infrastructure

| Component | Current | Future |
|---|---|---|
| PostgreSQL | Primary data store | Same — extended schema for new platforms and modules |
| Redis | Raw platform data cache | Same — extended with insights and rules result caching |
| MinIO / S3 | Asset storage | Migrate to managed S3 in production |
| Job scheduler | Not yet implemented | Spring `@Scheduled` or Quartz for rules engine + budget jobs |
| Email / push | Not yet implemented | SendGrid / Firebase for alert notifications |

### 5. Ad Platforms

Each platform requires:

1. An OAuth 2.0 handler that exchanges and stores a long-lived token
2. A strategy implementation for each entity type (campaign, ad set, ad)
3. A metric normalization mapping (platform field names → internal schema)

| Platform | Status | API |
|---|---|---|
| Meta Ads | Implemented | Graph API v23.0 |
| Google Ads | Planned | Google Ads API |
| TikTok Ads | Planned | TikTok Marketing API |
| LinkedIn Ads | Planned | LinkedIn Marketing API |
| Pinterest Ads | Planned | Pinterest Ads API |
| Reddit Ads | Planned | Reddit Ads API |

---

## New Module Detail

### Insights Aggregator

The backbone of the unified view. Every platform uses different field names for the same concepts:

| Metric | Meta | Google | TikTok | LinkedIn | Internal |
|---|---|---|---|---|---|
| Spend | `spend` | `cost_micros` | `total_cost` | `costInLocalCurrency` | `spend` |
| Impressions | `impressions` | `impressions` | `impression` | `impressions` | `impressions` |
| Clicks | `clicks` | `clicks` | `click_cnt` | `clicks` | `clicks` |
| Conversions | `actions` | `conversions` | `conversion` | `conversions` | `conversions` |

The aggregator normalizes all platform responses into a single internal `PlatformInsightDto` schema before storing or serving data. This is a prerequisite for unified reporting and the budget optimizer.

```
Platform API response
        │
        ▼
InsightNormalizerStrategy (one per platform)
        │
        ▼
PlatformInsightDto { platform, entityId, entityType,
                     spend, impressions, clicks,
                     conversions, roas, ctr, cpc, date }
        │
        ▼
insight_snapshot table (PostgreSQL)
        │
        ▼
Unified reports API  →  Frontend unified dashboard
```

### Automated Rules Engine

Allows users to define conditions that trigger actions automatically across any platform.

**Example rules:**
- `IF CPA > 50 AND platform = 'META' THEN pause ad`
- `IF daily_spend > budget * 0.9 THEN send alert`
- `IF ROAS < 1.5 AND impressions > 10000 THEN reduce budget by 20%`

**Architecture:**

```
User defines rule  →  rule stored in rules table
        │
        ▼
Spring Scheduler (runs every N minutes)
        │
        ▼
RulesEvaluatorService
  reads latest normalized insights
  evaluates each rule condition
        │
        ├── condition met → execute action via platform strategy
        │                         (pause, adjust budget, etc.)
        └── alert type   → Notifier service → email / push
```

**Rules data model:**

```
rule
  id, user_id, name, platform (nullable = all platforms),
  entity_type (CAMPAIGN / AD_SET / AD),
  condition_metric, condition_operator, condition_value,
  action_type (PAUSE / ENABLE / ADJUST_BUDGET / ALERT),
  action_value, active, created_at

rule_execution_log
  id, rule_id, entity_id, platform, triggered_at,
  condition_snapshot, action_taken, result
```

### Budget Optimizer

A read-only recommendation engine (first phase) that surfaces cross-platform budget inefficiencies.

```
Normalized insights (all platforms)
        │
        ▼
BudgetOptimizerService
  groups by user + time window
  calculates ROAS per platform per campaign
  identifies over/under-performing allocations
        │
        ▼
BudgetRecommendationDto {
  fromPlatform, toPlatform,
  suggestedShift (amount or %),
  projectedRoasGain,
  reason
}
        │
        ▼
Frontend budget optimizer panel
```

Phase 2 (future): automated budget reallocation with user-defined guardrails (max shift per day, min budget per platform).

---

## Data Flow: Multi-Platform Sync

```
User connects platform (OAuth)
        │
        ▼
OAuthTokenManager stores long-lived token per user per platform
        │
        ▼
User triggers sync (or scheduled background sync)
        │
        ▼
AbstractPlatformService.syncFromPlatform()
        │
        ├── calls PlatformStrategy (platform-specific API call)
        ├── normalizes response to internal entity
        ├── upserts to PostgreSQL (idempotent)
        └── caches raw response in Redis (TTL per entity)
```

---

## Data Flow: Ad Creation (Multi-Platform)

```
User opens create-ad-workflow
        │
        ▼
Select platform → cascading load: campaigns → ad sets
        │
        ▼
Choose or create creative:
  Option A: pick existing synced creative
  Option B: upload from asset library → select variant → fill copy fields
        │
        ▼
Frontend calls POST /api/ads
        │
        ▼
AdService → MetaAdStrategy (or future platform strategy)
  → platform API → creates ad
  → stores result locally
        │
        ▼
User redirected to unified dashboard
```

---

## Adding a New Platform — Checklist

When integrating a new ad platform (e.g. TikTok):

1. **OAuth handler** — add `OAuthProvider.TIKTOK`, implement token exchange and storage
2. **Strategy implementations** — create `TikTokCampaignStrategy`, `TikTokAdSetStrategy`, `TikTokAdStrategy` implementing the existing strategy interfaces
3. **Insight normalizer** — implement `TikTokInsightNormalizer` mapping TikTok field names to internal `PlatformInsightDto`
4. **Frontend OAuth button** — add TikTok connect button in sync-accounts page
5. **Platform enum** — add `TIKTOK` to the platform enum used throughout
6. **No changes needed** to `AbstractPlatformService`, controllers, database schema, or existing strategies

---

## Roadmap Phases

### Phase 1 — Current (Meta only)
- User auth + OAuth for Meta
- Campaign, ad set, ad sync and status management
- Ad creation with asset-based or existing creatives
- Creative library with auto-cropped variants
- Basic analytics + insights

### Phase 2 — Unified view
- Insights aggregator (normalized metrics)
- Unified dashboard (all synced platforms in one view)
- Unified reports page
- Add Google Ads and TikTok Ads strategies

### Phase 3 — Automation
- Automated rules engine (pause / alert / adjust)
- Notification center (email + in-app)
- Budget optimizer (read-only recommendations)
- Add LinkedIn Ads and Pinterest Ads

### Phase 4 — Optimization
- Automated budget reallocation with guardrails
- Reddit Ads integration
- Scheduled background syncs (no manual trigger needed)
- Multi-user / team accounts (role-based access)

---

## Security Considerations

| Concern | Approach |
|---|---|
| User data isolation | All queries scoped by `userId`, enforced at service layer |
| Platform tokens | Stored encrypted, never exposed to frontend |
| JWT | Short-lived, validated on every request |
| OAuth state | UUID state param, 10-minute expiry, single-use |
| Asset access | Presigned MinIO/S3 URLs, scoped per user |
| Rules execution | Sandboxed — only affects entities owned by the rule's user |
