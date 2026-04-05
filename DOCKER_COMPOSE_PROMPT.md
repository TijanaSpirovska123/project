# Production Docker Compose — Implementation Prompt

## Context

This is a SaaS marketing platform with two repositories:
- **Backend:** Spring Boot 3.2 / Java 21, built with Maven (`./mvnw clean install`)
- **Frontend:** Angular 21 with SSR (Express), built with `npm run build`

Current local setup: MinIO runs in Docker, PostgreSQL and Redis run separately.
Goal: one `docker-compose.yml` that runs everything together in production on a Linux VPS.

---

## Task

Create the following files. Do not modify any existing application code.

---

### 1. `docker-compose.yml` (root of the project, or a dedicated `/deploy` folder)

```yaml
services:

  postgres:
    image: postgres:16-alpine
    container_name: marketing_postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - internal
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: marketing_redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    networks:
      - internal
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio:latest
    container_name: marketing_minio
    restart: unless-stopped
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - minio_data:/data
    networks:
      - internal
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 10s
      retries: 3

  backend:
    build:
      context: ./backend          # adjust to your backend repo folder name
      dockerfile: Dockerfile
    container_name: marketing_backend
    restart: unless-stopped
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DB_USERNAME: ${POSTGRES_USER}
      DB_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ROOT_USER}
      MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      MINIO_BUCKET: ${MINIO_BUCKET}
      JWT_SECRET: ${JWT_SECRET}
      META_CLIENT_ID: ${META_CLIENT_ID}
      META_CLIENT_SECRET: ${META_CLIENT_SECRET}
      META_REDIRECT_URI: ${META_REDIRECT_URI}
      FACEBOOK_MARKETING_API_VERSION: v23.0
      FRONTEND_SUCCESS_URL: ${FRONTEND_URL}/sync-accounts
      FRONTEND_FAILURE_URL: ${FRONTEND_URL}/sync-accounts
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      minio:
        condition: service_healthy
    networks:
      - internal
      - external

  frontend:
    build:
      context: ./frontend         # adjust to your frontend repo folder name
      dockerfile: Dockerfile
    container_name: marketing_frontend
    restart: unless-stopped
    environment:
      API_URL: http://backend:8080
      NODE_ENV: production
    ports:
      - "4000:4000"
    depends_on:
      - backend
    networks:
      - internal
      - external

volumes:
  postgres_data:
  redis_data:
  minio_data:

networks:
  internal:
    driver: bridge
  external:
    driver: bridge
```

---

### 2. `.env` file (at the same level as `docker-compose.yml`) — template only, never commit real values

```env
# PostgreSQL
POSTGRES_DB=marketing
POSTGRES_USER=marketing_user
POSTGRES_PASSWORD=CHANGE_ME_STRONG_PASSWORD

# Redis
REDIS_PASSWORD=CHANGE_ME_REDIS_PASSWORD

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=CHANGE_ME_MINIO_PASSWORD
MINIO_BUCKET=marketing-assets

# JWT
JWT_SECRET=CHANGE_ME_64_CHAR_RANDOM_STRING

# Meta OAuth
META_CLIENT_ID=your_meta_app_id
META_CLIENT_SECRET=your_meta_app_secret
META_REDIRECT_URI=https://yourdomain.com/oauth/meta/callback

# Frontend public URL
FRONTEND_URL=https://yourdomain.com
```

Add `.env` to `.gitignore` immediately. Never commit it.

---

### 3. Backend `Dockerfile` (inside the backend project root)

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Xmx512m", "-Xms256m", "app.jar"]
```

---

### 4. Frontend `Dockerfile` (inside the frontend project root)

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --silent

COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/dist/marketing/server ./server
COPY --from=build /app/dist/marketing/browser ./browser
COPY --from=build /app/node_modules ./node_modules

EXPOSE 4000
CMD ["node", "server/server.mjs"]
```

**Note:** The Angular SSR output path (`dist/marketing/server`) depends on your
`angular.json` `outputPath` setting. Check `angular.json` and adjust the COPY paths
to match your actual build output folder name.

---

### 5. `.dockerignore` for the backend

```
target/
.git/
.gitignore
*.md
.mvn/wrapper/maven-wrapper.jar
```

### 6. `.dockerignore` for the frontend

```
node_modules/
dist/
.git/
.gitignore
*.md
```

---

## Important notes for the agent

1. **Do not change any Java or Angular source code.** Only create the files listed above.

2. **Backend environment variables:** The existing `application.yml` already reads from
   environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`,
   `REDIS_PORT`, `JWT_SECRET`). The Dockerfile and docker-compose.yml pass these in.
   If `application.yml` uses different variable names, match the docker-compose to those names.

3. **MinIO bucket creation:** MinIO does not auto-create buckets. After first startup,
   the backend needs the bucket to exist. Either:
   - Add a `mc` (MinIO client) init container that creates the bucket on first run, OR
   - Document that the user must run `docker exec marketing_minio mc mb /data/marketing-assets`
     after first `docker compose up`
   Prefer the init container approach:
   ```yaml
   minio-init:
     image: minio/mc:latest
     depends_on:
       minio:
         condition: service_healthy
     entrypoint: >
       /bin/sh -c "
       mc alias set local http://minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD};
       mc mb --ignore-existing local/${MINIO_BUCKET};
       exit 0;
       "
     networks:
       - internal
   ```

4. **Redis password in Spring Boot:** If the existing `application.yml` does not already
   have `spring.data.redis.password: ${REDIS_PASSWORD}`, add only that line to
   `application.yml` (or `application-prod.yml` if using profiles). Do not change anything else.

5. **Health checks:** The `depends_on` conditions use `service_healthy`, which requires
   Docker Compose v2. Verify with `docker compose version` on the VPS — it must be v2.x.

6. **Ports exposed externally:** Only `8080` (backend) and `4000` (frontend) are exposed
   to the host. PostgreSQL (5432), Redis (6379), and MinIO (9000, 9001) are on the
   internal network only — not reachable from outside the VPS. Nginx (set up separately)
   will proxy 80/443 to 4000 and 8080.

7. **Production Spring profile:** The docker-compose sets `SPRING_PROFILES_ACTIVE=prod`.
   If you have an `application-prod.yml`, use it for production-only overrides (logging
   level, etc.). If not, the base `application.yml` is used with the injected env vars.
