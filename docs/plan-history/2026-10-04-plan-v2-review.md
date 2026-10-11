# Plan review: v1 (2 Oct 2026) → v2 (4 Oct 2026)

- **Input:** [2026-10-02-plan-v1-original.md](2026-10-02-plan-v1-original.md), the architecture and learning plan from an earlier chat.
- **Output:** [PLAN.md](../../PLAN.md) v2 and ADR-0001 to 0012.
- **Goal of this review:** keep what is good, cut what is too heavy for a solo learning/portfolio project, fill the gaps, and turn the result into an executable chunk-by-chunk plan with recorded decisions.

**Overall verdict on v1:** the architecture was sound and careful, and most of it was kept. Its weaknesses were that it was one large document with no decision history, it left key choices open (the model provider), and parts of the reliability design were more production-grade than this project needs.

---

## Kept (v1 was right)

| Idea from v1 | Where it lives now |
|---|---|
| Narrow scope: Java PRs on one demo repo, advisory `COMMENT` reviews, honest "skipped" reporting | ADR-0002, PLAN §2 |
| Modular monolith with Spring Boot + PostgreSQL/pgvector, Python offline only | ADR-0003 |
| Three-way split of responsibility: Java (deterministic), Jev (narrow judgments), LLM (findings) | PLAN §3.1 |
| RAG over guidelines only, not the whole codebase; versioned index; no re-embedding | ADR-0006 |
| Jev with one job: 0–3 relevance rubric; similarity fallback; raw decisions stored | ADR-0007, PLAN §7 |
| The model picks allowed location IDs; Java maps and validates; suppressed findings kept with reasons | ADR-0008 |
| Freezing a snapshot to the head SHA; the review tied to `commit_id`; pre-publish head check | PLAN §6, Chunks 6–7 |
| Webhook signature verified before parsing; delivery-ID dedupe | Chunk 11 |
| Evaluation variants A/B/C (D as stretch), equal budgets, manual adjudication, split by family | ADR-0011, PLAN §8 |
| PR text treated as untrusted (prompt injection) | ADR-0012 |
| Building the manual engine first, then automation | PLAN §9 order |
| "Learn → build one thing → test one failure → explain it aloud" per milestone | PLAN §10 working agreement |
| Interview framing: the decision process is the story | PLAN §12 |

## Changed (simplified or made concrete)

| v1 | v2 | Why |
|---|---|---|
| "Choose providers when implementing the first model call" | **Claude** for generation, **local ONNX MiniLM** for embeddings, swappable through Spring AI (ADR-0005) | An open choice blocks setting up accounts. Anthropic has no embeddings API, and local embeddings are free and deterministic. |
| "Spring JDBC or JPA" | **JdbcClient + plain SQL** (ADR-0004) | pgvector and `SKIP LOCKED` need SQL anyway, and the SQL itself is interview material. |
| "Compatible Spring Boot/Spring AI" | **Spring Boot 4.x with Spring AI 2.0.x**, checked 2026-10-04 | Concrete and verified. Exact versions are pinned in Chunk 1. |
| 10 milestones | **14 smaller chunks (0–13)**, each with learn-first, build, done-when, failure test and interview check | The owner asked for understandable chunks, and smaller steps make it easier to keep up. |
| Order: … Jev → webhooks → publication → evaluation → deploy | Order: … **manual publish (v0.1)** → RAG → Jev → **evaluation (v0.2)** → webhooks → jobs (v0.3) → deploy | Gets a demoable PR review early and finishes the AI core plus measured results before the plumbing. If time runs out, the strongest story is already done. |
| 8 conceptual tables (installations, deliveries, jobs, runs, chunks, findings, publications, model_decisions) | **5 tables**: runs, findings, guideline_chunks, webhook_deliveries, jobs. Publication info and Jev decisions become columns or jsonb on runs | Less schema to build and explain, with no loss of traceability. |
| Job states QUEUED/RUNNING/SUCCEEDED/RETRY_WAIT/FAILED/SUPERSEDED/SKIPPED with expiring leases | QUEUED/RUNNING/SUCCEEDED/FAILED/SUPERSEDED; retry = QUEUED + `next_attempt_at`; stuck = old `locked_at` (ADR-0009) | Same guarantees with fewer states. |
| A "publication intent" record plus reconciliation | A **hidden marker** in the review body, checked before any (re)post | Simpler, and it covers the lost-response case. |
| Dataset of about 30 dev + 15 held-out | **About 20 dev + 10 held-out** | Manual labelling time for one person. It can grow if time allows. |
| Metrics included p50/p95 latency and calibration analysis | Average latency, with a small-sample caveat. Calibration analysis moved to stretch | p95 over 30 cases is noise. |
| A 15-row learning map in its own section | Concepts listed per chunk under **"Learn first"** | You learn each concept at the moment you use it. |

## Removed or deferred (not needed for v1)

| v1 item | Status | Why |
|---|---|---|
| Email notifications after publication | Removed (stretch backlog) | It isn't part of the core story and adds a failure mode. |
| Multi-installation tenancy (`installations/repositories` ownership model) | Removed. One installation on one demo repo, but retrieval is still **scoped by repo** | No real tenants. The repo scoping keeps the important safety property. |
| Separate `publications` and `model_decisions` tables | Merged into `review_runs` | See above. |
| Finding fingerprints across commits | Stretch | It's useful but not core, and needs evaluation data to design well. |
| Citation-support screening (variant D) | Stretch | Prove that C beats B first. |
| Syntax-aware extraction, related-test retrieval, hybrid retrieval, history page, model routing | Stretch | Only if the evaluation shows a need. |
| A broker or separate worker deployment "when justified" | Kept as a "revisit if" note in ADR-0003/0009 | This is not planned work. |

## Added (missing from v1)

| Addition | Why |
|---|---|
| **Decision log system:** ADRs (immutable, superseded rather than edited), chunk journals, CHANGELOG and plan history (ADR-0001) | The owner explicitly wants the history of changes and the reasons behind them. |
| **`CLAUDE.md` working agreement** and a **kickoff prompt** | So any new chat or coding assistant executes the plan the same way. |
| **★ "you write first" items** (DiffParser, FindingValidator, signature check, job-claim SQL, score.py) | So the owner can truthfully say "I wrote this" about the parts interviews probe. |
| **PAT-first, GitHub App later** behind `GitHubTokenProvider` (ADR-0010) | Avoids front-loading App/JWT complexity before the engine works. |
| **Manual publish endpoint** (Chunk 7) | A demoable bot before any webhook plumbing exists. |
| **Testcontainers** for real-Postgres tests | pgvector and `SKIP LOCKED` behaviour can't be tested on H2. |
| **smee.io** for local webhooks | v1 did not say how webhooks reach a laptop. |
| **Rule IDs in guideline headings** | Makes retrieval measurable (recall@k) and citations checkable. |
| **Versioned prompt files** (never edit a used version) | Evaluation results stay reproducible. |
| **Cost guardrails** (spend limits, per-review caps) | It's a self-funded project. |
| **Explicit ADR-0012** for prompt injection and "no model tools" | v1 mentioned it in one paragraph. It is a key security decision, so it gets its own record. |
| **Resume milestones and a bullet template** (claims only from real numbers) | Ties the project directly to the job-search goal. |
| **Accounts and keys checklist**, including an early check of Jev access and pricing | Avoids discovering a blocker in Chunk 9. |

## Facts verified during this review (2026-10-04)
- TypeSafe Jev HTTP API: `POST https://api.typesafe.ai/v1/systemone`, Bearer auth, fields `state` / `model` / `questions`; Score answers return `score`, `probabilities` and `confidence`, the response reports the actual model version, and 429/529 mean retry with backoff. Pricing and access tiers were **not** stated on the API page, so check them in Chunk 0.
- Spring AI 2.0.x (2.0.1 current) supports Spring Boot 4.0.x and 4.1.x.
- `spring-ai-starter-model-transformers` uses `sentence-transformers/all-MiniLM-L6-v2` by default (384 dimensions, ~80 MB, cached on first use).
