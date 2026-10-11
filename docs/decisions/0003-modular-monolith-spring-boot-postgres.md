# ADR-0003: Modular monolith — one Spring Boot app + PostgreSQL/pgvector

- **Status:** Accepted — amended by [ADR-0014](0014-kafka-event-driven-processing-with-outbox.md) (Kafka added to infrastructure; still one application)
- **Date:** 2026-10-04
- **Chunk:** planning
- **Supersedes:** —

## Context
The system needs a webhook receiver, a background worker, relational records (runs, findings, jobs) and vector search over guideline chunks. Traffic is tiny (one demo repo). There is one developer, who is learning.

## Decision
- Build **one Spring Boot application** with packages separated by responsibility (`webhook`, `github`, `jobs`, `review`, `context`, `knowledge`, `ai`, `publishing`).
- The webhook receiver and the background worker run **in the same process**.
- Use **one PostgreSQL database** with the **pgvector** extension, for both normal tables and embeddings.
- Run the database locally through Docker Compose (`pgvector/pgvector` image).
- Python is **not** a runtime service; it is only used offline for evaluation (ADR-0011).

## Alternatives considered
- **Microservices** (webhook service, worker service, Python AI service): more deployment, networking and failure modes with no benefit at this scale. It would also be harder to explain end to end.
- **A separate vector database** (Pinecone, Qdrant, Weaviate): another account and another consistency boundary. pgvector is enough for fewer than a thousand chunks and keeps repo scoping in plain SQL.
- **A message broker** (Kafka, RabbitMQ, SQS): see ADR-0009.

## Consequences
- **Good:** one deployable unit, one database, simple local setup, and easy tracing from webhook to comment.
- **Bad / costs:** the worker and the web layer scale together, and a bad deploy affects both. That is acceptable at this scale.

## Revisit if
Real workload, independent scaling or recovery needs appear. Interview answer: "I'd split the worker out first, and only when measurements justify it."
