# Changelog

All notable changes to this project, one section per chunk. Newest first.
Sections: **Added** / **Changed** / **Removed** / **Fixed**. Link the chunk journal and any ADRs.

## [Unreleased]

### Added
- **Chunk 2-lite** ([journal](docs/chunks/02-demo-repo-lite.md)): demo repo `order-service-demo` (10 rule-ID'd guidelines, 4 branches), `evaluation/make_fixtures.py`, 4 dev fixtures generated from real branches, frozen guidelines copy.
- **Chunk 1** ([journal](docs/chunks/01-skeleton.md)): Spring Boot 4.0.8 / Java 25 skeleton in `backend/`, `compose.yaml` (pgvector on host port 5433), `application.yml` with `.env` import and virtual threads, `ReviewLimits` configuration record, `V1__init.sql` (pgvector extension), Testcontainers integration test, empty packages per PLAN §3.4, GitHub Actions `ci.yml`. ADR-0004 addendum with pinned versions.
- **Chunk 0** ([journal](docs/chunks/00-setup.md)): `.gitignore`, `.gitattributes`, `.env.example`, stub README, git repo and first commits.

## [Planning v4.3] — 2026-10-11 (pre-build dry run)

### Added
- `docs/pitfalls.md`: about 50 known traps with symptom → cause → fix, grouped by chunk, led by a **version reality check** (Spring Boot 4, Jackson 3, Testcontainers 2, Spring AI 2, Spring Kafka 4, Java 25). It's a living doc, and CLAUDE.md requires reading it before each chunk.
- ADR-0019: review only trusted PRs (same-repo branches, allowlisted authors), with the daily cap active from Sprint 1.
- ADR-0015 addendum: CI builds the jar, the Dockerfile is runtime-only, multi-arch via buildx, lowercase GHCR names and visibility, Oracle iptables.
- Session prompts: status-driven days, a "stuck on an error" prompt, and an "always allow" permission tip.

### Changed
- PLAN chunk cards updated with the dry-run fixes:
  - **Setup and CI:** `.env` import and variable list, host ports, the `mvnw` exec bit, the test profile.
  - **Fixtures and LLM:** fixture folders from real branches, lenient LLM DTOs, `responseEntity`, `POST /api/dry-run/fixture/{id}`.
  - **Data and GitHub:** jsonb/timestamptz/uuid binding, `GITHUB_PAT`, the 422 fallback, PKCS#8 + the JDK JWT, the raw-bytes HMAC, after-commit trigger.
  - **RAG:** the vector CAST.
  - **Kafka:** two listeners, String payloads, realistic Kafka-down and crash tests.
  - **Deployment:** the runtime-only Dockerfile.
- Pre-Day-1 tools: added Node.js LTS, the VC++ Redistributable, `JAVA_HOME`, the secrets folder and optional `gh`.
- CLAUDE.md: a "Version reality" section, read only today's cards, tests never call real APIs, trusted-PR rule.
- The style guide gains the "lenient at boundaries, strict inside" and Jackson 3 rows.
- PLAN v4.2 snapshotted; reasons in `docs/plan-history/2026-10-11-plan-v4.3-dry-run.md`.

## [Planning v4.2] — 2026-10-11

### Added
- ADR-0018: the LLM provider is **Gemini by default** (free tier for development) or **OpenAI**, selected by `spring.ai.model.chat` from the `LLM_PROVIDER` env var. Both Spring AI starters are added in Chunk 3. Includes the free-tier data rule (synthetic demo code only) and the note that an app subscription is not an API key.

### Changed
- ADR-0005: the generation provider (Claude) is superseded by ADR-0018; local ONNX embeddings stand.
- PLAN: every Claude/Anthropic reference is now "the LLM (Gemini/OpenAI)". Chunk 3 uses a provider-neutral `SpringAiReviewGenerator`, with a new done-when ("works with one key; switching needs no code change"). Chunk 8 selects embeddings with `spring.ai.model.embedding: transformers`. Accounts checklist, resume bullets and references updated.
- deployment.md (secrets, cost, external APIs), session-prompts.md (Day 1 checklist and prompt) and java-style-guide.md updated to match.

## [Planning v4.1] — 2026-10-11

### Added
- `docs/session-prompts.md`: copy-paste kickoffs for build Days 1–7, the study-session template and schedule, session rules, and resume / end-of-session / plan-problem prompts. Linked from PLAN §10.

### Fixed
- Sprint 1's in-process review trigger now stays until **Chunk 13** removes it, when the Kafka consumer takes over. Previously "Chunk 12 replaces it" would have left reviews broken between Day 5 and Day 6.
- The Day 1 setup adds `E:\production\order-service-demo` as a second session folder (Chunk 2-lite creates the demo repo there).

## [Planning v4 — fast track] — 2026-10-11

### Added
- ADR-0017: build in short sprints first, study alongside.
- PLAN §9.0 sprint plan: Sprint 1 (Days 1–2) gives a working automatic bot; Sprints 2–4 add RAG/Jev/eval, Kafka and deployment.
- PLAN §10 split into build mode (§10.1), study mode (§10.2) and "always" rules (§10.3), with kickoff prompts for both modes.
- A Sprint 1 resume bullet and an honesty rule (PLAN §12).
- Journal template filled in two passes (build, then study), and Built/Studied columns in the chunk table.

### Changed
- Chunk 11 moved into Sprint 1, with a temporary in-process (virtual-thread) review trigger until Kafka in Sprint 3.
- Lite Chunk 2 (Sprint 1) and lite Chunk 10 (Sprint 2).
- ★ pieces: the owner makes a real change to them in study mode instead of writing the first version.
- In build mode, the assistant may make local commits; the owner pushes.
- CLAUDE.md rewritten for the two modes.
- PLAN v3.3 snapshotted to `docs/plan-history/2026-10-11-plan-v3.3-snapshot.md`, with the reasons in `2026-10-11-plan-v4-review.md`.

## [Planning v3.3] — 2026-10-11 (final pre-build check)

### Fixed
- Chunk 11 now names its migration (`V4` for `webhook_deliveries`). Previously the numbering jumped from V3 to V5.
- The Maven project lives in `backend/`, so CI runs `./mvnw` there (PLAN Chunk 1, deployment.md).
- The dry-run endpoints accept a `ReviewConfig` override, which Chunk 10's evaluation needs to switch variants (Chunk 8).

### Added
- Chunk 0 rewritten as ordered steps saying who does what (you or the assistant), plus `.gitattributes` for line endings (Windows → Linux/Docker safety) and a "never paste keys into chat" rule.
- Chunk 2 session setup (add the demo repo as a second folder).
- Chunk 8 note on the Java 24+ native-access warning for the ONNX runtime (`--enable-native-access=ALL-UNNAMED`).
- Working agreement: a fresh session per chunk, and draft journals (`Status: In progress` + Next) for resuming mid-chunk. CLAUDE.md rules for resuming, git (the owner commits and pushes) and secrets.

## [Planning v3.2] — 2026-10-11

### Added
- ADR-0016: Spring AI (not LangChain4j) as the LLM library, including a concept map for interviews and a stretch idea to compare both.
- ADR-0015 addendum (2026-10-11): Oracle timing, the region warning, the Contabo fallback and a VM sizing tip.
- `docs/deployment.md` §3: checked VPS prices in INR (Contabo, Hostinger).

### Changed
- Oracle Cloud signup moved from Chunk 0 to the **Chunk 12 checkpoint**, and the VM is created in **Chunk 14**, so nothing sits idle and gets reclaimed.

## [Planning v3.1] — 2026-10-04

### Added
- Chunk 0: try to get an Oracle Cloud Always Free A1 VM early, and record the result.
- Chunk 12: a hosting checkpoint (keep Kafka, or fall back to the Postgres queue on free PaaS).
- `docs/deployment.md` §3.1: free-hosting findings checked in Oracle, Render and Neon docs, plus the no-Kafka $0 fallback and the design changes it needs.
- ADR-0015 Addendum recording those findings (the decision is unchanged).

## [Planning v3] — 2026-10-04

### Added
- ADR-0013: Java 25 LTS and a modern-Java style (records, sealed types, pattern matching, streams/Gatherers, virtual threads).
- ADR-0014: Apache Kafka for event-driven review processing, with a transactional outbox, idempotent consumer, retries and a DLT.
- ADR-0015: Deployment to one Linux VM with Docker Compose, Caddy (HTTPS), GitHub Actions CI/CD and GHCR.
- `docs/java-style-guide.md` with project-specific examples, and `docs/deployment.md` (full runbook).
- PLAN §3.5 (Kafka design) and §3.6 (deployment). New chunks 12 (Kafka + outbox), 13 (Kafka consumer), 14 (deployment + CD) and 15 (README/demo).
- CI (GitHub Actions `ci.yml`) moved into Chunk 1. A "Modern Java" line on every chunk card.
- Daily review cap (cost guard), and a rule that only the webhook and health endpoints are public.
- Project location recorded: `E:\production\ai-pr-reviewer`.

### Changed
- Java 21 → Java 25 (ADR-0004's Java line superseded by ADR-0013).
- Postgres job queue → Kafka + outbox (ADR-0009 superseded by ADR-0014; ADR-0003 amended).
- Data model: `review_jobs` replaced by `review_requests` + `outbox_events` (5 → 6 tables).
- Roadmap: 14 chunks → 16 chunks (0–15). v0.3 is now after Chunk 13 and v1.0 after Chunk 15.
- PLAN v2 snapshotted to `docs/plan-history/2026-10-04-plan-v2-snapshot.md`. Reasons in `docs/plan-history/2026-10-04-plan-v3-review.md`.

### Removed
- The `review_jobs` table and stuck-job recovery logic (Kafka offset redelivery replaces them).

## [Planning v2] — 2026-10-04

### Added
- `PLAN.md` v2: 14 chunks (0–13) with goals, "learn first" concepts, build steps, done-when checks and interview checks.
- ADR-0001 to ADR-0012 recording the planning decisions (`docs/decisions/`).
- `CLAUDE.md` working agreement for AI coding assistants.
- Chunk journal template (`docs/chunks/README.md`) and ADR template (`docs/decisions/0000-template.md`).
- Concrete provider choices: Claude for generation, local ONNX embeddings (ADR-0005).
- Plan for PAT-first GitHub access, then a GitHub App (ADR-0010).

### Changed
- Plan v1 → v2: simpler data model (8 → 5 tables), simpler job states, publication idempotency through a hidden review marker, and evaluation before automation in the build order. Details: `docs/plan-history/2026-10-04-plan-v2-review.md`.

### Removed
- Email notifications, the multi-installation tenancy model, and separate `publications` / `model_decisions` tables (all from v1).

## [Planning v1] — 2026-10-02

### Added
- Original architecture and learning plan (archived at `docs/plan-history/2026-10-02-plan-v1-original.md`).
