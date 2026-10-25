# Chunk 07 — Publish a review (manual trigger)

- **Dates built:** 2026-10-25  · **Date studied:** —
- **Status:** Built (live post and live repeat-publish confirmed on demo PR #1)
- **Tag:** chunk-07

## Goal
Post a stored PR run to GitHub as one `COMMENT` review: validated findings inline at the right lines, everything else in the summary, safe to call twice, and nothing posted if the PR moved on. Trigger: `POST /api/reviews/{runId}/publish`.

## What I built
- `publishing/ReviewPublisher.publish(runId)` returns a sealed `PublishOutcome`:
  - `Superseded(reason)`: the PR is closed or its head SHA differs from the reviewed one. Nothing is posted and the run becomes `SUPERSEDED`.
  - `AlreadyPosted(reviewId)`: the run already stores a review id, or an existing review on the PR carries our marker for this SHA and config.
  - `Posted(reviewId, inlineComments, summaryOnly)` otherwise.
- Order of checks: run is a `SUCCEEDED` PR run (else 409) → run already has a review id → re-fetch the PR (open? same head?) → list the PR's reviews and look for the marker → create the review.
- `publishing/ReviewSummaryFormatter`: summary (commit, model and prompt, files reviewed, skipped files with reasons, counts, per-file inline counts via `groupingBy`, summary-only findings in full, the "advisory, not a merge approval" note) and the hidden marker `<!-- ai-pr-reviewer run=<runId> sha=<headSha> config=<version> -->`. `config` is the first 8 hex characters of SHA-256 over the run's `ReviewConfig`.
- **422 fallback:** if GitHub rejects the review (one bad inline location rejects all), re-post once with `comments = []` and every finding written into the body. The run records `publish_summary_only = true`. Other errors are not retried.
- `github/GitHubClient`: `listReviews` (pages of 100, capped at 10 pages) and `createReview` (the only GitHub write). DTOs `PullRequestReview`, `ReviewSubmission` (+ `InlineComment`, `commit_id` in snake case).
- `V4__publish_review.sql`: `github_review_id bigint`, `published_at`, `publish_summary_only` on `review_runs`. `ReviewRunRepository.recordPublished` and `markSuperseded`.
- `review/api/PublishController` (`POST /api/reviews/{runId}/publish`; `PublishReport` has `outcome` = POSTED / ALREADY_POSTED / SUPERSEDED). `ApiExceptionHandler`: `PublishNotAllowedException` → 409.

## Decisions made
- No new ADR: PAT-first (ADR-0010), publishing by marker, and the sealed `PublishOutcome` are on the card. No new dependency.
- Idempotency lives on GitHub, not only in our database. The stored review id is just a shortcut. This is what answers the interview check: if GitHub accepted the review but our request timed out, nothing is stored, the retry lists the PR's reviews, finds the marker and records that review's id instead of posting again.
- The marker matches on **SHA + config, not run id**: a second run on the same commit with the same settings must not post a second review.
- Finding text is neutralised before posting because the model read untrusted PR content: `@` gets a zero-width space after it (no pings) and `<!--` becomes `&lt;!--` (no forged marker that could block a real review).
- Bodies are cut at 60,000 characters (inline comments at 4,000) before the footer is added, so the marker always survives. GitHub's limit is 65,536.
- A review with zero findings is still posted ("No issues worth raising"): it shows the bot ran, and an empty review is a valid outcome (PLAN §6 rule 4).
- `SUPERSEDED` is also set for a closed PR. A published run is never marked superseded.
- Known limit: two simultaneous calls for the same run could both pass the marker check. Sequential repeats (the done-when) are safe. Chunk 11 triggers one review per delivery, and Chunks 12-13 serialise per PR.

## Problems hit and how they were solved
- Re-stubbing a Mockito mock with `when(mock.m()).thenThrow(...)` calls the previously stubbed method, so a test failed with the old exception → use `doThrow(...).when(mock).m()` for the second and later stubs.

## How it was verified
- 128 tests green (`./mvnw -o verify`, no network, no keys). 24 new:
  - `ReviewPublisherTest` (10, GitHub mocked): happy path (event `COMMENT`, `commit_id` = reviewed head, inline path/line/side), **changed head posts nothing**, closed PR posts nothing, **publishing twice creates one review**, **lost response recovered via the marker**, a marker for an older commit doesn't block, **422 → one summary-only retry and recorded**, other errors not retried or recorded, an empty review is still posted, non-PR and failed runs → 409.
  - `ReviewSummaryFormatterTest` (6): marker content, per-file grouping, skipped files, counts, no `@` pings, no forged marker, truncation keeps the marker.
  - `GitHubClientTest` (+3, `MockRestServiceServer`): create-review wire format and returned id, 422 → `GitHubException(422)`, review paging and null bodies.
  - `PublishControllerTest` (3): every outcome's JSON, 404 / 409 / 502 mapping, bad UUID → 400.
  - `ReviewRunPublishPersistenceTest` (2, Postgres): review id above the int range round-trips, superseding never touches a published run.
- "Done when": [x] twice → one review (test) [x] changed head → nothing posted (test) [x] review at the correct lines on the real demo PR (live, 2026-10-26: screenshot of PR #1 shows the `[GUIDELINE] Unbounded collection endpoint lacks pagination` comment on `OrderController.java` line R32, with suggested fix and test) [x] 422 fallback (test)
- [x] Live repeat publish (2026-10-26): the second call returned `ALREADY_POSTED` and PR #1 still has one review, as reported by the owner. The stored review id on the run was not reported live; it is covered by the Postgres test.
- The live post was run by the owner after giving the PAT Pull requests write; no review was posted from the assistant's side.

## Deviations from the plan
- Migration numbering: this chunk's `V4` is `publish_review`, so Chunk 11's `webhook_deliveries` becomes `V5` and Chunk 12's tables `V6`. PLAN.md Chunk 11 and 12 cards updated.
- `ReviewRun` gained three fields (review id, published-at, summary-only flag).

## Next
- **You (live check, about 5 minutes):** in GitHub → Settings → Developer settings → Fine-grained tokens → your token → set **Pull requests: Read and write** (keep Contents: Read, Metadata: Read) and keep it limited to `order-service-demo`. Then start the app and run:
  1. `POST /api/dry-run/pr` with `{"owner":"Rahu09","repo":"order-service-demo","number":1}`, and copy `runId`.
  2. `POST /api/reviews/<runId>/publish` → expect `POSTED`. Open PR #1: the inline comment should sit on the flagged line.
  3. Call publish again → expect `ALREADY_POSTED` and no second review.
  Reviews appear under your own name while the PAT is used (pitfalls §6).
- Keep the PR #1 screenshot for the README, then go on to **Chunk 11**, GitHub App + webhook receiver.
