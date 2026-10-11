# Automatic Java AI PR Reviewer — architecture and learning plan

Prepared for Rahul Tiwari on 2 October 2026.

## 1. Goal and product boundary

Build a GitHub App that reviews small Java/Spring Boot pull requests automatically. It retrieves repository engineering guidelines, uses Jev to rank relevant passages, uses a generative LLM to produce findings, validates those findings in Java, and posts a summary plus inline comments. Python scripts evaluate the same Java review pipeline.

The goal is a demonstrable Java + applied GenAI portfolio project that you can explain in an interview. A reliable narrow workflow is the first deliverable. Broader language support, autonomous repairs, and a public SaaS business require additional work.

Initial supported scope:

- A demo repository you own, containing a small Spring Boot order-management API.
- Java source changes with an explicitly bounded review size.
- Repository guidelines and architecture notes from the trusted base revision.
- Automatic triggers for opened, synchronize, and ready_for_review pull-request actions. Ignore drafts until ready.
- Explanations, suggested tests, a summary, and a small configurable number of inline findings.
- Advisory COMMENT reviews; developers decide whether to apply suggestions and merge.
- Explicit reporting of skipped files and limited context.
- A manual/dry-run endpoint for learning and evaluation, using the same review engine.

Treat the tool as an assistant. “No findings in reviewed files” does not mean “safe to merge.”

## 2. Main architectural decision

Use a modular monolith: one Spring Boot deployment with clearly separated packages. Its webhook handler and background worker live in the same application. PostgreSQL stores ordinary application records, durable jobs, and pgvector guideline embeddings.

Python is an offline development/evaluation toolkit, not a runtime microservice. The Java application calls Jev over HTTP. No Python service is required for Jev.

Keep three responsibilities distinct:

| Layer | Responsibility |
| --- | --- |
| Java application | Known rules, authentication, job state, exact comparisons, context budgets, validation, publication, retries |
| Jev decision model | Narrow semantic judgments, beginning with passage relevance |
| Generative LLM | Interpret changed code, propose candidate findings, write explanations and suggested fixes |

Jev is a typed decision model, not a text-generation or code-generation model. The model's output format does not establish correctness. A probability is not authorization, and calibration must be checked on your own use case.

## 3. Runtime and data flows

~~~mermaid
flowchart TD
    GH["GitHub PR event"] --> WH["Webhook receiver"]
    WH --> JOB["Persisted review job"]
    JOB --> WK["Background review worker"]
    WK --> CTX["Diff and code context"]
    CTX --> RET["Retrieve guideline candidates"]
    IDX["Guideline indexing"] --> VS["PostgreSQL and pgvector"]
    VS --> RET
    RET --> JEV["Jev ranks passages"]
    JEV --> GEN["LLM proposes findings"]
    GEN --> VAL["Java validates findings"]
    VAL --> PUB["Publish commit-specific review"]
    PUB --> GH
    PY["Python evaluation"] --> ENGINE["Dry-run review entry point"]
    ENGINE --> CTX
~~~

The diagram shows responsibilities. These boxes are modules inside one application, not separate deployed services.

There are two different flows:

1. Knowledge indexing: load base-revision guidelines, chunk, embed, persist a versioned index.
2. Review processing: freeze a PR snapshot, retrieve from that index, rank, generate, validate, publish.

Do not re-embed unchanged guidelines on every PR. Reuse an index identified by repository, guideline content/version, embedding model, and chunking configuration.

## 4. Suggested stack

| Component | Initial choice | Reason |
| --- | --- | --- |
| Java runtime | Java 21, compatible stable Spring Boot/Spring AI combination | Keep Java central and use supported AI integrations |
| Build | Maven | One standard build tool |
| Backend | Spring Web, Spring validation, Spring JDBC or JPA | Webhooks, HTTP endpoints, persistence |
| AI calls | Spring AI for chat/embeddings; dedicated Jev HTTP client | Separate generation from typed decisions |
| Database | PostgreSQL with pgvector | One database for records and retrieval |
| Migrations | Flyway | Repeatable database setup |
| Packaging | Docker and Docker Compose | Reproducible local application/database setup |
| Python | Virtual environment, HTTP client, JSON and CSV tooling | Evaluation without a second runtime service |
| Interface | GitHub PR reviews; health/status APIs | Avoid frontend work initially |
| Verification | JUnit; integration checks around jobs, diff mapping, and API contracts | Focus tests on actual risks |

Generate a supported dependency combination through Spring Initializr and the current Spring AI setup guide. Record exact versions in the repository. Start with one chat-model provider and one embedding model. Model API credentials are separate from access to a consumer chat application.

Choose providers when implementing the first live model call, based on access, capabilities, budget, and measured code-review quality. No specific provider or deployment vendor is fixed by this architecture.

## 5. Java modules and folder structure

Use packages by responsibility. Avoid turning every interface into a service deployment.

| Path | Contents |
| --- | --- |
| backend/pom.xml | Java dependency and build configuration |
| backend/src/main/java/.../webhook/ | Controller, signature verification, event parsing |
| backend/src/main/java/.../github/ | App authentication, PR snapshots, source fetching, review publication |
| backend/src/main/java/.../jobs/ | Persistent job repository, worker, retry/lease handling |
| backend/src/main/java/.../review/ | Orchestration, domain records, findings, run state |
| backend/src/main/java/.../context/ | Diff parsing, line maps, surrounding-code extraction, context budgets |
| backend/src/main/java/.../knowledge/ | Guideline ingestion, chunks, embeddings, versioned search |
| backend/src/main/java/.../ai/ | Generative-model adapter, Jev adapter, request/response validation |
| backend/src/main/java/.../publishing/ | Finding validation, deduplication, review formatting, publication reconciliation |
| backend/src/main/resources/prompts/ | Versioned review prompts, Jev rubric configuration, output schema |
| backend/src/main/resources/db/migration/ | Database migrations |
| backend/src/test/ | Relevant unit and integration checks |
| evaluation/datasets/ | Input fixtures, manually checked expected findings, splits |
| evaluation/run_evaluation.py | Call Java dry-run API and save responses |
| evaluation/score_results.py | Score adjudicated results and compare configurations |
| evaluation/report.py | Generate tables/reports |
| evaluation/requirements.txt | Python dependencies |
| docs/architecture.md | Architecture and decisions |
| docs/evaluation.md | Metrics, results, limitations |
| docs/demo.md | Reproducible demo steps |
| compose.yaml | Local containers |
| .env.example | Variable names and placeholders |
| README.md | Product description, setup, examples |

Keep the demo application in a separate GitHub repository. This lets you create realistic PR events without changing the review tool itself.

Useful boundaries, introduced when needed:

- GitHubClient: fetch snapshot/source and publish reviews.
- KnowledgeRetriever: return evidence candidates scoped to an index.
- ContextRanker: rerank candidates. Implement similarity ordering first, Jev ordering second.
- ReviewGenerator: generate candidate findings from a prepared review context.
- FindingValidator: verify structure, evidence references, locations, and duplicates.
- ReviewPublisher: format, publish, and reconcile uncertain outcomes.
- ReviewOrchestrator: connect these steps without taking over their implementation details.

The Python runner invokes the orchestrator through a dry-run endpoint. It must not maintain an independently implemented Python reviewer.

## 6. Knowledge and code context

Start RAG with a small, understandable corpus:

- docs/engineering-guidelines.md
- docs/error-handling.md
- docs/api-conventions.md
- docs/architecture-decisions.md

Use synthetic or original material in your demo. Keep rules concrete: “collection endpoints support pagination” is testable; “write good code” is not.

Each guideline chunk needs:

- Stable chunk ID.
- Repository ID and installation ownership.
- Source path, heading, source revision, and content hash.
- Text and embedding.
- Embedding model identifier and index/chunking version.

Chunk Markdown by heading and paragraph initially. Use token limits to split unusually long sections. Compare a couple of reasonable sizes instead of searching for a supposedly universal optimal chunk size.

Fetch relevant code directly from GitHub for the review snapshot. For the first version, diff context plus surrounding methods is enough. Optional syntax-aware method extraction can follow. Related callers, interfaces, and tests can be fetched later if the initial evaluation reveals specific missing-context failures.

Do not start by embedding the entire repository. Code retrieval needs different chunking and exact-symbol handling and makes versioning harder. Guideline RAG already demonstrates embeddings, vector storage, retrieval, reranking, and grounding.

For each changed method/file:

1. Use filenames, identifiers, and a bounded code excerpt as a retrieval query.
2. Retrieve a small candidate set from the correct repository/index.
3. Rank candidates with Jev.
4. Fit selected passages into the prompt budget.

For development, retrieving eight candidates and retaining up to three is a possible experiment, not a guaranteed optimum. Keep an empty-context outcome; never force an irrelevant rule into a review.

## 7. Jev integration design

### First Jev feature: guideline relevance ranking

Ask one Score question per candidate passage against the changed-code context. A proposed rubric:

| Level | Meaning |
| --- | --- |
| 0 | Unrelated to the changed behaviour |
| 1 | Same broad topic, but supplies no useful review rule |
| 2 | Contains an applicable rule, but the excerpt alone is incomplete |
| 3 | Contains a directly applicable and explicit rule for this change |

This is a relevance judgment, not proof that the code violates the rule.

State should include the bounded code change, a concise feature description where necessary, and the candidate passage. Avoid sending the full repository or unrelated conversation. Question instructions should say exactly what is being rated.

Score returns a graded result plus uncertainty information. Rank in Java and handle ambiguity according to a tested policy. Independent questions over one bounded state can be batched, but candidate passages still consume input tokens; measure request size and performance.

Call Jev through a small Java HTTP adapter using the current TypeSafe HTTP API. Keep model names/configuration outside business logic, record the actual model returned, and pin a model version for repeatable comparisons when the provider supports it.

If Jev is unavailable, fall back to the baseline similarity ranking and record the fallback. This fallback works because ranking is an enhancement, not permission to publish.

### Optional later feature: citation support screening

Use a separate narrow judgment: “Does the cited passage support the specific rule claimed in this finding?” A Choice with supported, unsupported, and insufficient_evidence makes the missing-context case explicit.

A citation-support judgment is not a complete bug verifier. Complex control flow, race conditions, numerical correctness, and multi-file dependencies remain separate problems.

Store raw decisions so later policy changes do not require new model calls if the evidence/question is unchanged. For an optional publication screening gate, unavailable or uncertain judgments should be deferred or reported according to an explicit policy, not silently treated as approval.

### Features to postpone

- Jev severity scoring for all possible defects.
- Jev deciding which source files never get reviewed.
- Jev claiming code is safe.
- Many successive model gates with no measured gain.
- Selecting a “correct” source or finding when no relevant candidate exists.

A pre-review skip classifier can save money but may hide real defects. Prove the relevance-ranking benefit before exploring skip routing.

## 8. End-to-end processing and reliability

1. Receive the raw webhook request and verify its signature before trusting the parsed body.
2. Check event/action, installation ownership, repository eligibility, and draft status.
3. Persist the delivery ID and create a job transactionally; acknowledge quickly.
4. Deduplicate redelivery by delivery ID and duplicate work by repository, PR, head SHA, and review configuration.
5. Claim a job with an expiring lease; keep bounded retries and an eventual failed state.
6. Fetch and freeze PR head SHA, base/merge-base context, diff, and trusted guideline revision.
7. Reuse/build the matching guideline index.
8. Build bounded contexts, retrieve, rank, and generate candidate findings.
9. Validate and store findings, including suppressed findings and suppression reasons.
10. Confirm the PR is still open/eligible and its head still matches the snapshot before publication.
11. Publish a review for the explicit reviewed commit.
12. Persist GitHub review/comment identifiers and final run status.

A pre-publication head check reduces stale reviews but cannot prevent a push in the instant between checking and posting. Always attach the review to the reviewed SHA and retain that identity in the summary.

Job states can be QUEUED, RUNNING, SUCCEEDED, RETRY_WAIT, FAILED, SUPERSEDED, or SKIPPED. A stored stage indicates whether failure occurred during fetching, indexing, generation, validation, or publication.

New commits supersede pending older review jobs. Let completed historical reviews remain attached to their original revision. Avoid repeating the same unchanged finding on every commit; fingerprint the finding using its category, relevant code/rule, and source path rather than line number alone.

Posting has an important failure case: GitHub accepts the review, but the response is lost. Record publication intent and a run marker before the call. Before retrying, reconcile existing bot reviews for that commit/marker. Stop for investigation rather than blindly repost if the outcome cannot be determined. Exactly-once external delivery is not established by a database unique constraint alone.

On restart, expired job leases are recoverable. Keep retryable provider/network/rate-limit failures separate from permanent permissions or malformed-data failures.

## 9. Findings and publication policy

A candidate finding should contain:

| Field | Purpose |
| --- | --- |
| findingId | Stable internal identity |
| filePath | Exact affected source path |
| line/side or allowedLocationId | Proposed location within the reviewed diff |
| category | Correctness, guideline, or test-coverage suggestion |
| title | Short description |
| explanation | Mechanism and possible consequence |
| evidenceRefs | Source chunk IDs or code evidence locations |
| suggestedFix | Optional bounded replacement or prose recommendation |
| suggestedTest | Optional test scenario |
| status/suppressionReason | Publication decision and why |
| reviewedHeadSha | Snapshot identity |

Prefer giving the model a list of allowed diff-location IDs and mapping IDs to exact GitHub coordinates in Java. The model should not calculate diff positions from scratch.

Validation checks:

- File/location exists in the reviewed snapshot and is valid for an inline comment.
- Referenced guideline chunks are real members of the supplied evidence set.
- Exact quoted text, if used, occurs in its referenced evidence.
- Generated output follows the schema and allowed enums.
- Duplicate or purely cosmetic findings are handled by your policy.
- Suggested replacements are bounded to the referenced lines.
- Coverage limits and omitted files are reflected in the summary.

These checks catch invalid structure and unsupported references. They do not prove the semantic claim is true. Measure that through evaluation.

When a useful finding cannot be attached to a valid diff location, place it in the summary with a source reference. Report factual constraints plainly: “No findings in the three reviewed Java files; one oversized file was skipped.”

Start with summary plus inline comments. Add GitHub suggestion blocks for small replacements only after coordinate handling works. Do not run submitted code or automatically apply fixes.

## 10. Conceptual data model

This is a planning model, not a finished SQL schema.

| Record | Important contents |
| --- | --- |
| installations/repositories | Installation ID, repository ownership, enablement, settings |
| webhook_deliveries | Delivery ID, event/action, receipt time, processing result |
| review_jobs | PR identity, SHA, configuration version, state, attempts, lease, next retry |
| review_runs | Snapshot, guideline index, models/prompts, timing, usage, coverage, result |
| guideline_chunks/indexes | Text, embedding, source metadata, content/index versions |
| review_findings | Candidate, evidence, validation outcome, suppression reason, fingerprint |
| publications | Intended payload identity, external review ID, publication state |
| model_decisions | Jev rubric, score/probabilities, actual model, fallback information |

Keep credentials out of these records wherever possible; configuration refers to secrets held outside source code. Avoid recording full code in ordinary logs. Store only the bounded artifacts needed for debugging/evaluation and define how to remove them.

Use migrations from the beginning. Exact locking/index/lease mechanics should be implemented and tested when durable jobs are added.

## 11. Python evaluation architecture

Prepare cases while building, then automate experiments.

Each case includes:

- Case ID and family.
- Diff and bounded surrounding code.
- Guideline documents/index version.
- Expected findings checked by you.
- Relevant file/location and reasoning.
- Whether the case is clean, ambiguous, or deliberately outside supported scope.
- Expected supporting guideline IDs for retrieval tests.

Create related positive/negative cases: a missing null check versus a valid early return; an unbounded endpoint versus a paginated endpoint. Include missing context, misleading comments, renamed files, and an updated PR revision.

Split by scenario family so nearly identical variants do not leak between development and held-out evaluation. A reasonable initial dataset is around 30 development cases and 15 held-out cases; this is a small portfolio benchmark, not evidence of general industrial accuracy.

Compare:

| Variant | Pipeline | Question answered |
| --- | --- | --- |
| A | Diff/context → LLM | What does generation alone achieve? |
| B | Diff/context + vector retrieval → LLM | Does repository evidence help? |
| C | Diff/context + retrieval + Jev ranking → LLM | Does Jev improve selection enough to justify its overhead? |
| D, later | C + citation support screening | Does screening reduce invalid citations without hiding valid findings? |

Use equal context budgets and the same generation model/prompt wherever possible. Give B the same number of selected passages as C to isolate the ranking change. Store exactly which candidates each saw.

Evaluation measures:

- Finding precision: valid unique findings divided by reported unique findings.
- Finding recall: known in-scope issues found divided by known in-scope issues.
- Clean-case false-positive rate.
- Evidence/citation correctness.
- Guideline retrieval recall at k and ranking quality where labels support it.
- p50/p95 observed latency and provider-reported token usage; small-sample percentiles are unstable.
- End-to-end cost per review using recorded provider rates/date.
- Percentage of files reviewed or skipped.
- Optional gate coverage: what share of cases is published/deferred, and at what precision.

Manually adjudicate findings initially. Text matching and another model's verdict are not ground truth. Unexpected valid findings can reveal incomplete labels; document label corrections before final scoring rather than silently changing expectations to favour a variant.

Keep Jev's relevance-score confidence separate from “probability that the finding is correct.” If you later use Noul evidence-support judgments, examine calibration against human labels and report the limited sample size.

Cache outputs by case, context, prompt/rubric, and model version for repeatable scoring. A cached run cannot measure fresh-call latency. Run fresh repeats for selected cases to observe instability.

Python owns experiments and metrics; Java owns the reviewer.

## 12. Things to prioritise

1. Evidence and useful comments: clear mechanisms, source support, and clean-PR restraint.
2. Context preparation: changed method, relevant code, correct repository and document version.
3. Diff mapping and snapshot freshness: correct source line on the reviewed commit.
4. Durable background processing: repeat events, crashes, new commits, and uncertain publication.
5. Measured Jev benefit: C versus B, including total tokens and latency.
6. Explainability: trace a finding from webhook to evidence to published comment.

Model choice matters, but context preparation and evaluation should come before constantly switching models.

Keep deterministic tasks in Java: signature checks, line maps, exact comparisons, arithmetic, ownership, retries, and publication limits.

Treat fetched PR text as untrusted data. An instruction in a code comment must not become permission to send email, access another repository, or alter the workflow. Both model adapters get bounded evidence, while Java controls all actions.

## 13. Learning map

| Concept | Where you learn it | Proof of understanding |
| --- | --- | --- |
| LLM inference | First model call | Explain input/output and why answers can be wrong |
| Tokens/context | Context builder | Show how a large diff is split or skipped honestly |
| Structured output | Manual review endpoint | Reject invalid locations and malformed findings |
| Embeddings | Guideline indexing | Find a rule using a paraphrased query |
| Vector similarity | Retrieval | Explain why similar text may still be irrelevant |
| Chunking | Ingestion | Compare paragraph/section sizes on known queries |
| RAG | Review with evidence | Explain that retrieved context does not train the model |
| Reranking | Jev ranker | Compare B and C on the same candidate pool |
| Score/Choice/Noul | Small Jev experiments | Distinguish degree, alternatives, and yes probability |
| Confidence/calibration | Evaluation | Explain why confidence is not proof or permission |
| Hallucinations | Findings/citations | Demonstrate a plausible unsupported finding |
| Prompt injection | Adversarial fixtures | Preserve application behaviour despite injected PR text |
| Precision/recall | Python scoring | Explain the false-positive versus missed-issue trade-off |
| Async/idempotency | Job processing | Repeat an event without duplicate comments |
| Observability | Run history | Identify which pipeline stage failed and its usage |

You do not need fine-tuning, model training, or multi-agent workflows to build the first release.

## 14. Build order and completion checks

| Milestone | Build | Completion check |
| --- | --- | --- |
| 1. Skeleton | Spring app, demo repo, guidelines, initial fixtures | Health endpoint and a PR you can explain |
| 2. Manual baseline | One model, pasted diff, structured findings | Buggy and clean examples both handled |
| 3. Context and persistence | Diff parser, line map, run records | Every finding traces to a snapshot/location |
| 4. Real PR dry run | GitHub App, fetching, bounded code context | Review a PR without publishing |
| 5. RAG | Versioned guidelines, embeddings, search | Relevant evidence appears and absent evidence stays absent |
| 6. Jev | HTTP adapter and Score-based passage ranking | Compare B/C and record raw ranking results |
| 7. Automatic processing | Webhook, database jobs, worker | Event creates review even after a recoverable restart |
| 8. Publication | Summary, inline locations, reconciliation | No duplicates on redelivery and no blind retry after lost response |
| 9. Evaluation | Python runner, labels, held-out comparison | Honest report including failures and trade-offs |
| 10. Deployment/demo | HTTPS, persistent DB, container, README | Open PR → review while local laptop app is stopped |

Build the manual engine first to isolate model/context errors. The first finished release still includes automatic triggering and publication.

For each milestone: learn the few needed concepts, implement one feature, test one relevant failure, then explain the design aloud without reading code.

Do not accept generated code you cannot explain. Ask your coding assistant for a change limited to the current milestone, inspect the diff, run it, and commit before adding another feature.

## 15. Meaningful verification

Prioritise the risks specific to this product:

- Added/deleted/renamed files map to valid comment locations.
- Duplicate delivery and simultaneous new-commit events do not multiply reviews.
- A crash does not lose a persisted job.
- A lost publication response is reconciled rather than blindly retried.
- A stale snapshot remains tied to its original commit.
- Retrieval cannot cross repository boundaries.
- Invalid evidence IDs and unavailable diffs are handled.
- Clean changes do not force invented findings.
- Provider failure yields an explicit failed/degraded result.
- Prompt-injection text does not change application permissions or destinations.

This does not require testing every trivial getter. Choose tests that establish real behaviour.

## 16. Deployment and optional later features

Initial hosted footprint:

- One HTTPS-accessible application container.
- One persistent PostgreSQL instance with pgvector.
- Server-side secrets for GitHub App authentication, webhook verification, and model APIs.
- A background worker in the same process.
- Health checks and bounded per-review usage.

The Python toolkit runs locally or in a scheduled/manual CI job. Evaluations are not required for every runtime webhook.

Add email after review publication is stable. Persist a separate notification task addressed to an explicitly configured verified recipient. Send a short summary and links; avoid attaching source code by default. Email failure must not trigger another GitHub review.

Possible subsequent extensions: Java syntax-aware context extraction, a small review-history page, hybrid exact/semantic retrieval, cited-rule screening, related-test retrieval, developer dismissal feedback, and measured model-routing experiments.

Introduce a broker or separate worker deployment only when actual workload, recovery, or independent scaling needs justify it.

## 17. Interview and resume evidence

Demonstrate real behaviour and describe your contribution accurately:

- Installed GitHub App receives a PR event.
- Worker uses a fixed snapshot and retrieves relevant rules.
- Jev changes passage ranking.
- LLM generates a useful finding with traceable evidence.
- The finding appears at the correct diff location.
- Python results show strengths, false positives, missed issues, and added overhead.

Use measured results only after evaluation. Keep a few example failures in the README. Clearly identify the project as portfolio work if it was not used at your employer.

The strongest story is the engineering decision process: why guideline RAG came first, why Jev had one bounded role, why Java controlled publication, and what your evaluation showed.

## 18. Verified documentation

Consult the current pages when implementing; pin versions in code and record them in evaluation artifacts.

- [TypeSafe System One](https://docs.typesafe.ai/concepts/system-one)
- [TypeSafe HTTP API](https://docs.typesafe.ai/api)
- [TypeSafe Score](https://docs.typesafe.ai/primitives/score)
- [TypeSafe Choice](https://docs.typesafe.ai/primitives/choice)
- [TypeSafe Noul](https://docs.typesafe.ai/primitives/noul)
- [TypeSafe confidence](https://docs.typesafe.ai/confidence)
- [TypeSafe reranking cookbook](https://docs.typesafe.ai/cookbooks/rerank_typesafe)
- [TypeSafe citation screening cookbook](https://docs.typesafe.ai/cookbooks/citation_check)
- [Jev 1.13 documented limitations](https://docs.typesafe.ai/model-jaggedness/jev-1.13) — version-specific; recheck the version you use.
- [Official TypeSafe agent skill](https://github.com/typesafe-ai/skills/blob/main/skills/typesafe-ai/SKILL.md)
- [Spring AI reference](https://docs.spring.io/spring-ai/reference/)
- [Spring AI setup](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [Spring AI pgvector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [GitHub webhook practices](https://docs.github.com/en/webhooks/using-webhooks/best-practices-for-using-webhooks)
- [GitHub PR reviews API](https://docs.github.com/en/rest/pulls/reviews#create-a-review-for-a-pull-request)

This plan's module layout, ranking rubric, dataset sizes, fallbacks, and milestones are design recommendations for this project. They are not promises of model quality or universal framework requirements.

