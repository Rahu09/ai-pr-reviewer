# ADR-0014: Event-driven review processing with Apache Kafka and a transactional outbox

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning (built in Chunks 12–13)
- **Supersedes:** [ADR-0009](0009-durable-jobs-in-postgres.md) (PostgreSQL job queue). **Amends** [ADR-0003](0003-modular-monolith-spring-boot-postgres.md): Kafka is added to the infrastructure, but there is still one application.

## Context
- The owner asked for Kafka. ADR-0009 had chosen a Postgres job table, arguing that a broker is unnecessary at this volume. **That argument about volume is still true.**
- The new deciding factor is **learning and job-market value**. Kafka with Spring Kafka is one of the most frequently requested skills in Java backend roles. Event-driven design (topics, partitions, keys and ordering, consumer groups, at-least-once delivery, idempotent consumers, dead-letter topics, the outbox pattern) is a standard interview topic.
- **The honest interview line:** "At this scale a database queue would be enough. I chose Kafka deliberately to learn event-driven design, and built it the way I would for production: outbox, idempotent consumer, DLT."

## Decision

**Topics**
- `pr-review.requested.v1` has 3 partitions. Its **key is `owner/repo#prNumber`**, so every event for one PR lands on the same partition, in order.
- The value is the JSON form of the `ReviewRequested` record: `schemaVersion`, `eventId`, `requestId`, `deliveryId`, `repo`, `prNumber`, `headSha`, `configVersion`, `requestedAt`.
- Failures go to the dead-letter topic `pr-review.requested.v1.DLT`.
- Topics are declared as `NewTopic` beans and created at startup. Replication factor is 1, because there is a single broker.

**Producer side: transactional outbox (no dual writes)**
- The webhook handler writes `webhook_deliveries`, `review_requests` and `outbox_events` in **one database transaction**. In the same transaction it marks older `QUEUED` requests for that PR as `SUPERSEDED`. It **never calls Kafka directly**.
- `OutboxRelay` (`@Scheduled`, about every 1 s) claims unpublished rows with `SELECT … FOR UPDATE SKIP LOCKED` and orders them by id. For each row it sends to Kafka and **waits for the broker's ack** (`acks=all`, idempotent producer), then sets `published_at`.
- If Kafka is down, rows simply wait and nothing is lost.

**Consumer side: idempotent consumer**
- `@KafkaListener` with consumer group `pr-reviewer`, concurrency no higher than the partition count, and `max.poll.records=1`. The offset is committed only **after** processing (at-least-once).
- The listener looks up the `review_requests` row by `requestId`:
  - `SUCCEEDED`, `SUPERSEDED` or `SKIPPED`: acknowledge and do nothing.
  - Otherwise: mark it `RUNNING`, increment `attempts`, and run the orchestrator with publishing on.
- Publishing keeps the hidden-marker check from Chunk 7, so a **redelivered message never posts twice**.
- Errors use a sealed `ReviewException` hierarchy:
  - `RetryableReviewException` (5xx, 429/529, timeouts) is retried by Spring Kafka's `DefaultErrorHandler` with exponential backoff, up to 3 attempts.
  - `PermanentReviewException` (401/403/404, invalid data) is not retried.
  - Once retries are exhausted or the error is permanent, `DeadLetterPublishingRecoverer` sends the message to the DLT. A DLT listener marks the request `FAILED` with the reason.
- `max.poll.interval.ms` is set above the worst-case review time, and every external call has a timeout.

**Unchanged**
- The dry-run API stays synchronous and does not use Kafka.

**Infrastructure**
- Apache Kafka 4.x runs in **KRaft mode** (no ZooKeeper) as a single broker in Docker Compose.
- Tests use the Testcontainers Kafka module.

## Why not Kafka transactions / "exactly-once"?
Kafka's exactly-once semantics cover *read from Kafka → process → write to Kafka*. This pipeline's side effects happen in **PostgreSQL and the GitHub API**, which Kafka transactions cannot include. The real guarantee is **at-least-once delivery, idempotent processing and duplicate-safe publishing**. That guarantee is stated plainly in the README.

## Alternatives considered
- **Keep the Postgres job queue (ADR-0009):** simpler and enough for the volume, but it doesn't meet the goal of learning Kafka.
- **Publish to Kafka straight from the webhook handler:** the classic **dual-write problem**, because the database commit and the Kafka send cannot be atomic. GitHub does **not** automatically redeliver failed webhooks, so a lost event means a missed review.
- **Debezium CDC for the outbox:** needs Kafka Connect, which is more infrastructure. A polling relay is enough here, so CDC is a stretch item.
- **RabbitMQ:** a good fit for task queues, but Kafka is more in demand and teaches partitions, ordering and offsets.
- **Non-blocking retries (`@RetryableTopic`):** these don't block the partition while waiting, but they break per-PR ordering. Blocking retries are fine at this volume, so this is a stretch item.
- **One topic per pipeline stage** (fetch → rank → generate → publish): more topics, harder tracing and no benefit.

## Consequences
- **Good:**
  - Durable, decoupled intake.
  - The webhook stays fast and correct even when Kafka, Claude or Jev is down.
  - Crash recovery comes from offset redelivery.
  - Strong interview material.
- **Bad / costs:**
  - One more infrastructure component locally and in production (about 1 GB RAM).
  - More failure modes to test.
  - A single broker gives no high availability. That is acceptable for a portfolio project; real production would use 3 brokers, RF 3 and `min.insync.replicas=2`, or managed Kafka.
  - The `review_jobs` table and stuck-job recovery logic from ADR-0009 are removed, because Kafka redelivery replaces them.

## Revisit if
Running Kafka in production is too heavy or costly. Then return to the ADR-0009 design with a new ADR. The outbox and idempotency work carries over.
