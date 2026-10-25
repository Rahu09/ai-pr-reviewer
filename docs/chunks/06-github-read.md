# Chunk 06 — Review a real PR (GitHub read, dry-run)

- **Dates built:** 2026-10-25  · **Date studied:** —
- **Status:** Built
- **Tag:** chunk-06

## Goal
Fetch a real PR (SHAs, diff, changed Java files at head) with a read-only fine-grained PAT, freeze it into a `ReviewSnapshot`, and review it through the same engine via `POST /api/dry-run/pr`. Nothing is posted.

## What I built
- `github/GitHubProperties` (`reviewer.github.*`, `pat` from `GITHUB_PAT`, `toString` hides the token), `GitHubTokenProvider` + `PatTokenProvider` (fails with a clear message when the PAT is unset).
- `GitHubClient` on `RestClient`: `getPullRequest`, `getDiff` (`application/vnd.github.diff`), `getFileContent` (`application/vnd.github.raw+json`, empty on 404). HTTP failures become `GitHubException(status)` with a hint: 401 bad token, 403 + `X-RateLimit-Remaining: 0` rate limit, 403 permission, 404 not found or no access. Record DTOs `PullRequest` (+ `User`, `Ref`, `Repo`), unknown JSON ignored.
- `GitHubSnapshotProvider.fetch(owner, repo, n)`: PR → diff **between the exact base and head SHAs** (`/compare/base...head`, so a push mid-review cannot change it) → contents of changed, non-deleted Java files at the head SHA, bounded by `maxJavaFiles` and `maxFileBytes`.
- `ReviewOrchestrator.reviewFetched(...)`: the fetch runs inside the recorded run, so a failure is a `FAILED` run with `failed_stage = FETCH`; the SHAs are attached to the run after the fetch (`attachSnapshot`).
- `POST /api/dry-run/pr` `{owner, repo, number, config?}` (owner/repo validated against GitHub-legal characters because they go into a URL). `GitHubException` → 404 for not found, otherwise 502.
- `V3__failed_stage_fetch.sql` widens the `failed_stage` CHECK (V2 is applied, so it is not edited).

## Decisions made
- No new ADR: PAT-first is ADR-0010, `RestClient` and the DTOs are on the card.
- Diff via the compare endpoint with SHAs instead of `/pulls/{n}` + diff media type: immune to a push between the two calls.
- The trusted-PR check (same-repo, allowlisted author, ADR-0019) is not applied to the manual dry run; it lands with the webhook in Chunk 11. `PullRequest` already carries the author and head/base repo for it.

## Problems hit and how they were solved
- A long Bash command mixing heredocs and Java text blocks failed to parse (pitfalls §1) → used the editor tool for those files.

## How it was verified
- 104 tests green (`./mvnw verify`, no network, no keys): `GitHubClientTest` (8, `MockRestServiceServer`: bearer token and API-version headers, accept types, nested path kept, missing file → empty, 404/rate-limit/permission messages, unset PAT, token never printed), `GitHubSnapshotProviderTest` (2: SHAs, only changed non-deleted Java files requested, oversized skipped), orchestrator tests (FETCH failure recorded, SHAs attached), a Postgres test (`failed_stage = FETCH` row, fetched head SHA stored), and `/api/dry-run/pr` controller tests (success, bad input → 400, GitHub 404 → 404).
- Live dry run (read-only PAT, Gemini `gemini-3.8-flash`, prompt `review-v2`), `POST /api/dry-run/pr`:

| PR | Result |
|---|---|
| order-service-demo#1 (buggy, unpaginated list) | `PUBLISHABLE` at `OrderController.java:32`, GUIDELINE: unbounded collection endpoint. Run stored: `SUCCEEDED`, source `PR`, head `a5055d31` |
| order-service-demo#2 (buggy, swallowed exception) | `PUBLISHABLE` at `OrderService.java:28`, CORRECTNESS. Run stored: `SUCCEEDED`, head `ac04b65d` |
| nonexistent PR #999 | HTTP 404 with a clear message; stored run `FAILED`, `failed_stage = FETCH` |

  The stored head SHAs equal the PRs' `headRefOid` from `gh pr list`, and the PRs were not touched (read-only token).
- "Done when": [x] findings with valid locations on a demo PR [x] head SHA stored [x] PR untouched [x] missing PR gives `FAILED` / `FETCH`.

## Deviations from the plan
- `FETCH` stage needed a V3 migration; diff comes from `compare`, not `pulls`.

## Next
- Chunk 7: publish a review (manual trigger).
