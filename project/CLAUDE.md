# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./mvnw clean install

# Run application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName
```

## Architecture Overview

Spring Boot 3.2 / Java 21 marketing platform integrating with the Meta/Facebook Graph API to manage the Campaign → AdSet → Ad hierarchy.

### Layer Structure (per domain)

Each domain (`campaign`, `adset`, `ad`, etc.) follows this structure:
- `api/` — REST controller (extends `BaseController`)
- `service/` — business logic (extends `AbstractPlatformService`)
- `strategy/` — platform-specific API calls (e.g., `MetaCampaignStrategy`)
- `entity/` — JPA entity (extends `BasePlatformEntity`)
- `dto/` — request/response DTOs
- `mapper/` — MapStruct mapper (Entity ↔ DTO)
- `repository/` — Spring Data JPA repository

### Key Abstractions

**`AbstractPlatformService<E, D, S>`** (`infrastructure/service/platformserviceimpl/`)
Template method base for all platform services. Implements common logic for `createOnPlatform`, `updateOnPlatform`, `deleteOnPlatform`, `listFromPlatform`, and `syncFromPlatform`. Subclasses override hooks: `newEntity()`, `applyDtoToNewEntity()`, `findExisting()`, `cacheRawData()`.

**Strategy Pattern** (`*/strategy/MetaXxxStrategy`)
Encapsulates Meta Graph API calls per entity type. New platforms would add a new strategy implementation without changing service logic.

**`PlatformRawDataCache`** (`infrastructure/cache/`)
Redis cache for raw platform data. Key pattern: `rawdata:{type}:{platform}:{adAccountId}:{externalId}`. TTLs are configured per entity type in `application.yml` under `app.cache.*`.

**`BasePlatformEntity`** (`infrastructure/entity/`)
Mapped superclass with common fields (`externalId`, `platform`, `adAccountId`, `status`, timestamps).

### Database Migrations

Liquibase manages schema. Master changelog: `src/main/resources/db.changelog/db.changelog-master.xml`. New migration files go in `src/main/resources/db.changelog/changes/` and must be referenced in the master file.

### Key Configuration (application.yml)

All sensitive values are injected via environment variables:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — PostgreSQL
- `REDIS_HOST`, `REDIS_PORT` — Redis (defaults: localhost:6379)
- `FB_ACCESS_TOKEN`, `FB_AD_ACCOUNT_ID` — Meta API credentials
- `FACEBOOK_MARKETING_API_VERSION` — defaults to `v23.0`
- `JWT_SECRET`

### Local Development

`docker-compose.yml` starts MinIO (S3-compatible storage). No full local stack is defined — PostgreSQL and Redis must be provided separately (or via environment variables pointing to external instances).

### Annotation Processing

MapStruct and Lombok are both used. Both must be present in the compiler annotation processor path (configured in `pom.xml` via `maven-compiler-plugin`). When adding new mappers, follow the existing pattern of `@Mapper(componentModel = "spring")`.