# Plan change: v2 → v3 (4 Oct 2026)

- **Before:** [2026-10-04-plan-v2-snapshot.md](2026-10-04-plan-v2-snapshot.md)
- **After:** [PLAN.md](../../PLAN.md) v3
- **Trigger:** the owner asked to (1) use the latest Java design, such as records and streams, (2) implement Kafka, (3) have a deployment plan, and (4) keep the project at `E:\production`.

## What changed and why

| # | Change | Why | Record |
|---|---|---|---|
| 1 | **Java 21 → Java 25 LTS**, plus a modern-Java style guide | The owner wants the latest Java design. 25 is the newest LTS and adds Stream Gatherers and unnamed variables, and fixes virtual-thread pinning. The guide ties each feature to the chunk where it's used, so it's learned in context | [ADR-0013](../decisions/0013-java-25-modern-java-style.md), [java-style-guide.md](../java-style-guide.md) |
| 2 | **Postgres job queue → Kafka with a transactional outbox** | The owner asked for Kafka. The volume argument in ADR-0009 still holds, so the deciding reason is **learning and job-market value**, and the ADR says so honestly. It is designed production-style: outbox (no dual writes), PR-keyed partitions, idempotent consumer, backoff and DLT | [ADR-0014](../decisions/0014-kafka-event-driven-processing-with-outbox.md) supersedes ADR-0009 and amends ADR-0003 |
| 3 | **Deployment plan made concrete:** one VM + Docker Compose + Caddy + GitHub Actions + GHCR | v2 deferred the host choice. Kafka makes typical free PaaS tiers impractical, so a single VM running the same Compose model as local is the cheapest and most educational option | [ADR-0015](../decisions/0015-deployment-single-vm-compose-caddy-actions.md), [deployment.md](../deployment.md) |
| 4 | **CI moved to Chunk 1** | Running tests on every push from day one is cheap and is standard practice | ADR-0015 |
| 5 | Data model: `review_jobs` → `review_requests` + `outbox_events` | Kafka is now the queue. Postgres stays the source of truth for request state and pending events | PLAN §5 |
| 6 | Roadmap 0–13 → **0–15** | Kafka is split into two chunks (producer/outbox, then consumer), and deployment is split from README/demo, so each chunk stays small enough to understand | PLAN §9 |
| 7 | New rules: no dual writes; only webhook and health are public; daily review cap | These come from adding Kafka and a public deployment | PLAN §6 |
| 8 | Project location `E:\production\ai-pr-reviewer` | Owner's choice | PLAN header, CLAUDE.md |

## What did NOT change
The scope, the AI design (RAG over guidelines, Jev's single role, allowed locations, validation), the evaluation design, the build order up to Chunk 11, and the documentation system.

## Interview angle
"I first chose a Postgres queue (ADR-0009) because it was enough for the load. I then deliberately switched to Kafka to learn event-driven design (ADR-0014). Because direct publishing has a dual-write problem, I used a transactional outbox. Because Kafka is at-least-once, I made the consumer idempotent and the GitHub publish duplicate-safe."
