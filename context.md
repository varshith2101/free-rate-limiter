# Rate Limiter Project Context (Deployment-Focused)

This document is a curated handoff for another LLM to answer deployment/infrastructure questions about this repo.

## 1) Project Overview

- Project: Self-hosted rate limiting platform with dashboard + backend API + Redis + nginx gateway.
- Primary stack:
  - Backend: Java 21, Spring Boot 4.0.1, Maven
  - Frontend dashboard: React 18 + Vite 5 (built and served by nginx)
  - Cache/state: Redis 7
  - DB: PostgreSQL in prod profile (external DB URL expected)
  - Edge/router: nginx gateway as single public entrypoint
- Repo modules:
  - `ratelimiter/` = Spring Boot backend service
  - `dashboard/` = React SPA
  - `nginx-gateway/` = reverse proxy (public :80)
  - `test-app/` = sample app that integrates with ratelimiter API

## 2) Runtime Architecture

- Public traffic enters `gateway` container on port `80`.
- nginx gateway routes:
  - `/api/` -> `backend:8081/api/`
  - `/actuator/` -> `backend:8081/actuator/`
  - `/` -> `dashboard:80`
- Backend depends on Redis and external PostgreSQL.
- Dashboard calls API base `/api/v1` (relative), so it works behind same origin gateway.

## 3) Docker Compose Topology

From `docker-compose.yml`:

- `redis`
  - image: `redis:7-alpine`
  - volume: `redis_data:/data`
  - healthcheck: `redis-cli ping`
- `backend`
  - build: `./ratelimiter/Dockerfile`
  - env from `.env`
  - enforced profile: `SPRING_PROFILES_ACTIVE=prod`
  - redis host forced to service name `redis`
  - exposes no host port directly (only internal)
  - healthcheck: `curl http://localhost:8081/actuator/health`
- `dashboard`
  - build: `./dashboard/Dockerfile`
  - build arg + env: `VITE_API_URL=/api/v1`
  - no host port directly
- `gateway`
  - image: `nginx:1.27-alpine`
  - host mapping: `80:80`

Only gateway is publicly exposed by compose.

## 4) Build/Run Details

### Backend image

- Multi-stage Dockerfile:
  - Build: `maven:3.9-eclipse-temurin-21`, `mvn clean package -DskipTests`
  - Runtime: `eclipse-temurin:21-jre-alpine`
  - Exposes `8081`

### Dashboard image

- Multi-stage Dockerfile:
  - Build: `node:18-alpine`, `npm ci`, `npm run build`
  - Runtime: `nginx:alpine`, serves static SPA on `80`
  - SPA fallback enabled in dashboard nginx config (`try_files ... /index.html`)

### Startup helper

- Script: `./launch-services.sh up` wraps compose build/up + health checks.
- Also supports `down`, `status`, `logs`, etc.

## 5) Environment Variables

Canonical template is `.env.example`.

Required/important vars:

- DB:
  - `DATABASE_URL` (supports `postgresql://...`; backend normalizes to `jdbc:...`)
  - `DATABASE_USERNAME`
  - `DATABASE_PASSWORD`
- JWT/Auth:
  - `JWT_SECRET`
  - `AUTH_MODE` (`standard` or `solo`)
  - `ADMIN_EMAIL`, `ADMIN_PASSWORD` (needed for `solo` mode)
- Redis:
  - `SPRING_DATA_REDIS_HOST` (compose uses `redis`)
  - `SPRING_DATA_REDIS_PORT` (default `6379`)
- Mail/OTP:
  - `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`
  - plus SMTP TLS/SSL timeout flags
- Frontend:
  - `VITE_API_URL` (defaults to `/api/v1` in compose)
  - `VITE_AUTH_MODE`

Security note:

- Current local `.env` in this repo contains real populated credentials/secrets (DB/JWT/SMTP).
- Treat those as compromised for external sharing; rotate and replace with placeholders before sharing logs/files.

## 6) Backend Configuration Behavior

From `application.yml` + `application-prod.yml` + `DataSourceConfig`:

- Server port: `8081`
- Active profile default: `dev`, but compose forces `prod`
- Prod DB dialect/driver: PostgreSQL
- Redis required for rate limiting logic
- Actuator exposed endpoints: `health`, `info`, `metrics`, `prometheus`
- Mail health disabled
- `DataSourceConfig` normalizes URL and can extract username/password from URL userinfo

Auth mode behavior:

- `AUTH_MODE=standard`: OTP signup flow enabled
- `AUTH_MODE=solo`: OTP signup disabled, login restricted to `ADMIN_EMAIL`/`ADMIN_PASSWORD`

## 7) API Surface (High-Level)

Main controller prefixes:

- `/api/v1/auth/*` (signup/login/refresh/logout/me/backend-links)
- `/api/v1/manage/*` (tenant endpoints, configs, keys, analytics, nuke tests)
- `/api/v1/ratelimit/*` (check/status/reset)
- `/api/v1/admin/*` (admin operations)
- `/actuator/*` (health/metrics)

Critical runtime endpoint for integrators:

- `POST /api/v1/ratelimit/check`
  - Header: `X-API-Key`
  - Body: `{ "endpoint": "/path", "method": "GET" }`
  - Returns allow/deny and rate-limit headers.

## 8) Security/CORS/Exposure Notes

- Spring Security permits without auth:
  - `/api/v1/auth/send-otp`, `/verify-otp`, `/login`, `/refresh`
  - `/api/v1/ratelimit/**`
  - `/actuator/**`
- Other endpoints require JWT auth.
- `AuthController` has `@CrossOrigin(origins = "*")`.
- Because gateway is same-origin for UI + API in compose, browser CORS is generally avoided in prod path.

## 9) Data/State and Persistence

- Redis is persisted via named volume `redis_data`.
- PostgreSQL is external (not provisioned by compose in this repo).
- JPA ddl auto in prod is `update` (schema auto-mutation at startup).

## 10) Observability/Health

- External health check (via gateway):
  - `GET http://localhost/actuator/health`
- Backend internal health check:
  - `GET http://backend:8081/actuator/health` (from Docker network)
- Prometheus metrics endpoint enabled via actuator.

## 11) Known Deployment Caveats / Mismatches

- `.env` currently has real secrets; do not share as-is.
- `launch-services.sh` local-dev paths reference a `postgres` compose service, but current `docker-compose.yml` defines no `postgres` service (external DB expected).
- Dashboard dev Vite proxy targets `http://localhost:8080` in `dashboard/vite.config.js`, while backend runs on `8081` in current config. This is a likely local-dev mismatch.
- `DataInitializer` seeds demo tenants/API keys/configs when tenant table is empty; this also runs in prod unless code/profile gating is added.
- `RateLimitController` exposes `/api/v1/ratelimit/reset` under permitAll `ratelimit/**` path; comment says it should be protected in production.

## 12) Test App Context (Optional for Deployment Questions)

`test-app/` is a sample consumer app:

- Back-end (`test-app/back`): Express on port `4000`
- Front-end (`test-app/front`): Vite on `5173`, proxies `/api` to `4000`
- Middleware calls rate limiter at:
  - `${RATELIMITER_URL}/api/v1/ratelimit/check`
  - with `RATELIMITER_API_KEY`
- Includes backend ownership verification route:
  - `GET /.well-known/ratelimiter-verify?token=...` returns token text

Useful envs for sample app:

- `RATELIMITER_URL`
- `RATELIMITER_API_KEY`
- `RATELIMITER_BLOCK_ON_UNMATCHED` (optional strict mode)

## 13) Recommended Baseline Deployment Flow

1. Copy `.env.example` -> `.env` and fill required values (DB/JWT/SMTP/auth mode).
2. Ensure external PostgreSQL is reachable from containers.
3. Run `./launch-services.sh up` (or `docker compose up -d --build`).
4. Verify health at `http://localhost/actuator/health`.
5. Access dashboard at `http://localhost`.
6. Create API key, add backend link, verify ownership route, configure endpoint limits.

## 14) Handy Commands

- Start stack: `./launch-services.sh up`
- Stop stack: `./launch-services.sh down`
- Status: `docker compose ps`
- Logs: `docker compose logs -f backend`
- Health: `curl -f http://localhost/actuator/health`

