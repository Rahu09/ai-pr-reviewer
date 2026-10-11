# ADR-0009: Durable job queue in PostgreSQL, no message broker

- **Status:** Superseded by [ADR-0014](0014-kafka-event-driven-processing-with-outbox.md) (2026-10-04)
- **Date:** 2026-10-04
- **Chunk:** planning (built in Chunk 12)
- **Supersedes:** v1 plan's lease/RETRY_WAIT state machine (simplified)

## Context
GitHub expects a webhook response within seconds, but a review takes tens of seconds and calls flaky external APIs. GitHub may deliver the same event more than once, the app may crash mid-review, and new commits can arrive while a review is running. The work therefore has to happen in the background, survive restarts, and never produce duplicate reviews.

## Decision
- A `review_jobs` table in PostgreSQL is the queue. It has a unique key on (repo, pr_number, head_sha, config_version).
- The webhook handler stores the delivery (delivery ID is the primary key) and creates the job **in one transaction**, then returns 2xx quickly.
- An in-process `@Scheduled` worker **claims** one job atomically with `SELECT … FOR UPDATE SKIP LOCKED` inside an `UPDATE … RETURNING`.
- States: `QUEUED → RUNNING → SUCCEEDED | FAILED | SUPERSEDED`.
- **Retryable** errors (5xx, 429, timeouts) go back to `QUEUED` with `next_attempt_at` set by backoff, up to 3 attempts. **Permanent** errors (401/403/404, invalid data) go straight to `FAILED`.
- **Stuck recovery:** a job left `RUNNING` with an old `locked_at` goes back to `QUEUED` and counts as an attempt.
- A new commit marks older `QUEUED` jobs for the same PR as `SUPERSEDED`. Completed reviews stay attached to their original commit.
- **Publication idempotency:** the review body carries a hidden marker (`run`, `sha`, `config`). Before posting, existing reviews are checked for the marker, so a retry after a lost response does not post twice (ADR-0008 / Chunk 7).

## Alternatives considered
- **`@Async` or an in-memory queue:** jobs are lost on crash or restart, which fails the core requirement.
- **Kafka, RabbitMQ or SQS:** extra infrastructure to run and explain, with no benefit at this volume. A database unique constraint plus a marker check still has to handle the external side effect either way.
- **Libraries (JobRunr, db-scheduler, Quartz):** valid and production-ready, but writing about 150 lines by hand teaches locking, transactions and retries, which are prime interview topics. A library could replace it later (new ADR).

## Consequences
- **Good:** jobs survive crashes, duplicates are harmless, it is easy to inspect with SQL, and there is no extra infrastructure.
- **Bad / costs:** polling adds a little latency (seconds) and some database load, which is fine at this scale. The guarantee is **at-least-once processing with duplicate-safe publishing**, not exactly-once delivery to GitHub, and the docs must say so.

## Revisit if
Several app instances run with high job volume, or polling load becomes measurable.
