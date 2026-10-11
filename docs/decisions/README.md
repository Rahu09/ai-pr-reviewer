# Architecture Decision Records (ADRs)

An ADR records **one decision** together with its context, the alternatives and the consequences. Together the ADRs form the project's decision history.

## Rules
1. One decision per file: `NNNN-short-title.md`. Copy [0000-template.md](0000-template.md).
2. Status is `Proposed` → `Accepted`. Once Accepted, the **Decision text is never edited**.
3. Clarifications (e.g. pinned versions) go in a dated `## Addendum (YYYY-MM-DD)` at the bottom.
4. To change a decision, write a **new** ADR with `Supersedes: ADR-NNNN`, and set the old ADR's status to `Superseded by ADR-MMMM`. Only the status line of the old ADR changes.
5. Add every ADR to the index below in the same commit.

## Index

| ADR | Title | Status | Date | Chunk |
|---|---|---|---|---|
| [0001](0001-record-decisions-adrs-and-journals.md) | Record decisions with ADRs, chunk journals and a changelog | Accepted | 2026-10-04 | planning |
| [0002](0002-narrow-scope-advisory-java-reviews.md) | Narrow scope: advisory reviews of Java PRs on one demo repo | Accepted | 2026-10-04 | planning |
| [0003](0003-modular-monolith-spring-boot-postgres.md) | Modular monolith: one Spring Boot app + PostgreSQL/pgvector | Accepted, amended by 0014 | 2026-10-04 | planning |
| [0004](0004-core-java-stack.md) | Core stack: Spring Boot 4, Maven, JdbcClient, Flyway, Testcontainers | Accepted; Java version superseded by 0013 | 2026-10-04 | planning |
| [0005](0005-model-providers.md) | Model providers: Claude for generation, local ONNX embeddings | Accepted; **generation provider superseded by 0018** (embeddings stand) | 2026-10-04 | planning |
| [0006](0006-rag-over-guidelines-only.md) | RAG over engineering guidelines only, not the whole codebase | Accepted | 2026-10-04 | planning |
| [0007](0007-jev-role-relevance-ranking.md) | Jev has one role: guideline relevance ranking, with similarity fallback | Accepted | 2026-10-04 | planning |
| [0008](0008-allowed-locations-and-java-validation.md) | The model picks allowed diff locations; Java maps and validates | Accepted | 2026-10-04 | planning |
| [0009](0009-durable-jobs-in-postgres.md) | Durable job queue in PostgreSQL, no message broker | **Superseded by 0014** | 2026-10-04 | planning |
| [0010](0010-github-access-pat-then-app.md) | GitHub access: fine-grained PAT first, GitHub App for automation | Accepted | 2026-10-04 | planning |
| [0011](0011-evaluation-python-against-dry-run-api.md) | Evaluation: small Python scripts against the Java dry-run API | Accepted | 2026-10-04 | planning |
| [0012](0012-untrusted-pr-content-no-model-tools.md) | PR content is untrusted; models get no tools | Accepted | 2026-10-04 | planning |
| [0013](0013-java-25-modern-java-style.md) | Java 25 LTS and modern-Java style (records, sealed types, pattern matching, streams) | Accepted | 2026-10-04 | planning |
| [0014](0014-kafka-event-driven-processing-with-outbox.md) | Event-driven processing with Apache Kafka and a transactional outbox | Accepted | 2026-10-04 | planning |
| [0015](0015-deployment-single-vm-compose-caddy-actions.md) | Deploy to one Linux VM with Docker Compose, Caddy and GitHub Actions | Accepted (addenda 2026-10-04, 2026-10-11) | 2026-10-04 | planning |
| [0016](0016-spring-ai-over-langchain4j.md) | Spring AI (not LangChain4j) as the LLM integration library | Accepted | 2026-10-11 | planning |
| [0017](0017-fast-track-build-sprints-then-study.md) | Fast track: build in short sprints first, study alongside | Accepted | 2026-10-11 | planning |
| [0018](0018-llm-provider-gemini-or-openai-by-config.md) | LLM provider: Gemini by default, OpenAI as the alternative, selected by config | Accepted | 2026-10-11 | planning |
| [0019](0019-review-only-trusted-prs-cost-guard-from-sprint-1.md) | Review only trusted PRs (same-repo branches, allowlisted authors); cost guard from Sprint 1 | Accepted | 2026-10-11 | planning |
