# Chunk 05 — Persist runs and findings

- **Dates built:** 2026-10-25  · **Date studied:** —
- **Status:** Built
- **Tag:** chunk-05

## Goal
Every review is recorded: which commit, which limits, which model and prompt, what was skipped, what it cost, and **all** findings (suppressed ones too, with their reasons). A failed provider call leaves a `FAILED` run with its stage.

## What I built
- `V2__review_runs_and_findings.sql`: `review_runs` (id uuid, request_id, source, repo, pr_number, base/head SHA, `config` jsonb, provider/model/prompt_version, status, failed_stage, error_message, `files_reviewed` / `files_skipped` jsonb, allowed_locations, input/output tokens, generation_ms, latency_ms, started_at / finished_at `timestamptz`) and `review_findings` (FK to the run with `ON DELETE CASCADE`, `position`, the model's `location_id`, the Java-mapped `file_path` / `line` / `side`, category, text fields, `status`, `reason`). CHECK constraints keep status / source / failed_stage values closed.
- `ReviewRunRepository` (interface) + `JdbcReviewRunRepository`: text-block SQL with `JdbcClient`, `CAST(:x AS jsonb)`, `OffsetDateTime` ↔ `timestamptz`, `UUID` ↔ `uuid`, rows mapped to the records `ReviewRun` / `StoredFinding`, `Optional` for the lookup.
  - `start` inserts a `RUNNING` row in its own transaction, so a crash mid-review leaves a trace.
  - `complete` is `@Transactional`: the run update plus all finding inserts commit together or not at all.
  - `fail` records stage + a bounded error message.
- `ReviewOrchestrator.review(snapshot, source, override)` now records each run: stage tracking (`PARSING` → `GENERATION` → `VALIDATION`, plus `PERSISTENCE` if storing the result fails), timings, then rethrows the original exception so the API still returns 400/502. Failure recording is best effort and never hides the original error.
- `ReviewOutcome` and `ReviewReport` carry `runId`; `GenerationResult` already held the provider, model, prompt version and token usage from Spring AI metadata.
- `GET /api/runs/{id}` (`RunController`, `RunDetails`): run + findings, 404 via `RunNotFoundException`.
- Enums `RunStatus`, `RunSource` (`FIXTURE` / `PR` / `DIFF`), `FailedStage`, `FindingStatus`.

## Decisions made
- No new ADR: no new dependency or service, and the tables are the ones in PLAN §5.
- The LLM call is **not** inside a database transaction (no connection held for seconds); only the final run-update + findings write is.
- Findings keep both the model's raw `location_id` and the mapped `file_path`/`line`. Suppressed rows keep the model's raw category string.
- Columns left for the chunks that need them: `guideline_index_version` (8), `ranking_decisions` (9), `github_review_id` (7). `request_id` exists but has no FK until `review_requests` (12).
- `review_findings.reason` instead of the plan's `suppression_reason`, because summary-only findings also have a reason. `evidence_refs` waits for Chunk 8.

## Problems hit and how they were solved
- A Python edit script again turned `\n` escapes inside a Java string into real newlines (compile error) → fixed with the editor tool. Same trap as Chunk 4; the lesson is to edit Java with the editor tool, not scripts.
- Docker Desktop was stopped, so Testcontainers could not run → started Docker first (pitfalls §1).

## How it was verified
- `./mvnw verify` green: 89 tests, including the new `ReviewRunPersistenceTest` (6, real Postgres via Testcontainers) and 3 new orchestrator tests with an in-memory repository double.
  - Success: every field of the run round-trips (SHAs, config, provider/model/prompt, tokens, files, timings); findings keep order, mapped path/line for the publishable one, and the raw location/category plus reason for the two suppressed ones.
  - Files skipped by the budget are stored with their reasons, and a no-model run is stored with provider `none`.
  - Stubbed provider failure → one `FAILED` run, `failed_stage = GENERATION`, message stored, no findings. A damaged diff → `FAILED` / `PARSING`.
  - `complete` for an unknown run id throws and leaves no findings behind (all-or-nothing).
  - `GET /api/runs/{id}` returns run + findings; unknown id → 404.
- Manual real run (Gemini `gemini-3.8-flash`, prompt `review-v2`, Postgres from `compose.yaml`): `POST /api/dry-run/fixture/cancel-order-swallowed` stored a run with a `PUBLISHABLE` finding at `OrderService.java:28`; `GET /api/runs/{id}` returned it with the head SHA, tokens (2059 in / 4 out for an empty review) and timings. Unknown id → 404.
- **Observation for Chunk 10:** `list-orders-unpaginated` returned *no* finding in 2 of 2 runs today, although Chunk 4's run found it at `OrderController.java:32`. The model is not deterministic even at temperature 0.2. One run per fixture is not a measurement, which is exactly why Chunk 10 repeats runs.
- "Done when": [x] every finding traces run → snapshot SHA → location [x] stubbed provider failure gives a `FAILED` run with `failed_stage = GENERATION`.

## Deviations from the plan
- Extra `PERSISTENCE` failed stage and `error_message` column, and `RunSource.DIFF` for pasted diffs (the plan lists `FIXTURE` / `PR`).
- `review_findings.reason` instead of `suppression_reason` (see above).

## Next
- Chunk 6: `GitHubClient` with a fine-grained PAT (`GITHUB_PAT` in `.env`, owner-created), snapshot fetching for a PR (diff, head contents, SHAs), and a dry-run endpoint for a real PR. Runs will use `RunSource.PR`.
