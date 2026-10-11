# Chunk 01 — Spring Boot skeleton + Postgres + CI

- **Dates built:** 2026-10-11  · **Date studied:** —
- **Status:** Built
- **Tag:** chunk-01

## Goal
A Boot 4 / Java 25 app with Postgres (pgvector), Flyway, a test profile that needs no keys, and CI.

## What I built
- `backend/` generated at start.spring.io (Boot 4.0.8, Java 25, web, validation, jdbc, postgresql, flyway, actuator, testcontainers); package `io.github.rahu09.prreviewer`.
- `application.yml`: `.env` import, datasource on `localhost:5433`, virtual threads, health-only actuator exposure, `reviewer.limits.*`.
- `ReviewLimits` — `@ConfigurationProperties` record with defaults and validation; `@ConfigurationPropertiesScan` on the application class.
- `V1__init.sql` — `CREATE EXTENSION IF NOT EXISTS vector`.
- `compose.yaml` — `pgvector/pgvector:pg17`, host port 5433, password from `.env`.
- `.github/workflows/ci.yml` — Temurin 25, `./mvnw -B verify` in `backend/`; `mvnw` has the exec bit in the git index.
- Empty packages per PLAN §3.4 (`.gitkeep`), `prompts/` folder.
- `PrreviewerApplicationTests`: context loads, Flyway ran, `pg_extension` contains `vector`, limits bind.

## Decisions made
- ADR-0004 addendum with pinned versions (no new ADR). Boot 4.0.8 chosen as the latest 4.0 GA; Spring AI 2.0.1 is added in Chunk 3.
- Actuator exposes only `health` (CLAUDE.md: only webhook + health are public).
- Test container uses `pgvector/pgvector:pg17` as a compatible substitute for `postgres`.

## Problems hit and how they were solved
- Generated test config used `postgres:latest` (no pgvector) → switched to the pgvector image.
- Shell `JAVA_HOME` was JDK 18 → per-session `JAVA_HOME` to JDK 25.

## How it was verified
- `./mvnw -B verify`: BUILD SUCCESS, 2 tests green (≈71 s, mostly the first image pull).
- `docker compose up -d` + `spring-boot:run`: `/actuator/health` → `{"status":"UP"}`.
- Failure test (Docker stopped): the app fails at startup in `flywayInitializer` with "Connection to localhost:5433 refused"; Flyway is the first bean to touch the DB, so the message is clear.
- [x] health UP · [x] test green locally · [x] green in GitHub Actions (CI #1, 51 s) · [x] versions in ADR-0004 addendum

## Deviations from the plan
- None.

## Next
- Owner: push and check the Actions tab is green; create `.env` with `POSTGRES_PASSWORD` (and later keys).
- Chunk 2-lite (demo repo).
