# CLAUDE.md — working agreement for AI coding assistants

This is a **portfolio project for a job search**. The owner (Rahul) builds it fast, then studies it until he can explain and change every part. Optimise for **working, tested code now** (build mode) and **real understanding** (study mode).

Project location: `E:\production\ai-pr-reviewer` (demo repo: `E:\production\order-service-demo`).

## Read first, every session
1. `PLAN.md`: §9.0 (fast-track sprints), §9.1 (chunk table in **build order**, with Built/Studied columns), §10 (working agreement), and **only the cards of today's chunks**. Read other sections when you need them; PLAN.md is long, so keep your context for the work.
2. `docs/decisions/README.md`: the ADR index (why things are the way they are). ADR-0017 defines the two modes.
3. `docs/java-style-guide.md`: how Java code is written here.
4. **`docs/pitfalls.md`**: §0 (version reality check) always, plus the section for today's chunks. **Before coding a chunk, apply its known fixes.**
5. The newest file in `docs/chunks/`. If it says `Status: In progress`, continue from its "Next" step. Don't restart the chunk.

## Version reality (important)
This stack is newer than most training data: **Spring Boot 4, Spring Framework 7, Jackson 3 (`tools.jackson`), Testcontainers 2, Spring AI 2, Spring Kafka 4, Java 25.**
- Don't write these APIs from memory. Use start.spring.io's generated pom, the reference docs, or the dependency sources, and let the compiler arbitrate.
- Known traps: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `@MockitoBean` (`@MockBean` was removed), `RestTestClient`, `testcontainers-<module>` artifacts. Details are in pitfalls §0.
- **Every new problem you solve goes into `docs/pitfalls.md`** as symptom → cause → fix.

## Which mode am I in?
The kickoff message says **build mode** or **study mode**. If it doesn't, ask.

### Build mode (sprints, PLAN §10.1)
- Implement whole chunks in the sprint order from PLAN §9.0. Don't wait for approval after every small step.
- **Stop at each chunk boundary** with a 5-line summary (what was built, how it was verified, anything surprising) and wait for "go".
- **Tests are part of done.** Every "Done when" check and "Failure to test" must pass before moving on. Cut scope, never tests.
- **Never run `git commit`, `git push`, `git reset`, `git revert`, `git rebase` or `git stash`, and never rewrite history.** The owner makes every commit and every push. Leave changes uncommitted in the working tree. When a step is a good commit point (a chunk boundary, or a green logical step), say so and suggest a commit message, and the owner commits. Read-only git (`status`, `diff`, `log`) is fine. If a git operation is half-finished (merge, revert, rebase), do not touch it: report it.
- Write the journal's build sections, any ADRs and the CHANGELOG entry, and tick **Built** in PLAN §9.1.
- Hand the owner anything only they can do: installs, accounts, putting API keys into `.env`, creating the PAT and the GitHub App, pushing. Give exact click-by-click steps.

### Study mode (PLAN §10.2)
- Walk through one built chunk's code in request-flow order, and explain its "Learn first" concepts using this code.
- Suggest how to trace it in IntelliJ (breakpoints, which test or endpoint to run).
- Give **one study exercise**, a small change the owner makes. For ★ chunks it must touch the ★ piece. Then review the owner's change.
- Ask the chunk's interview check plus 2 more questions, and record the answers in the journal.
- **Don't change code** except to review or help with the exercise.

## Always (both modes)
- **No surprises:** a new dependency, table, topic, external service or architectural change needs a short ADR (`docs/decisions/0000-template.md`). Dependencies already named on a chunk card don't need one. In build mode, write it and continue unless it changes the plan's direction, and flag it at the chunk boundary.
- **If the plan is wrong:** say so, and propose a new ADR that supersedes the old one, plus a note in `docs/plan-history/`. Never edit the decision text of an Accepted ADR; only update its status line or append a dated Addendum.
- **Before a session ends mid-chunk:** update the draft journal with progress and the next step.
- **Secrets:** never ask for API keys or tokens in chat. The owner puts them in `.env` themselves. Spring imports `.env` via `spring.config.import`, and paths in it use forward slashes.
- **Tests never call real external APIs.** The LLM, Jev and GitHub are stubbed or WireMocked. The test profile sets `spring.ai.model.chat=none`, and CI has no API keys. Real calls happen only in manual dry runs.
- **Trusted PRs only** (ADR-0019): same-repo branches by allowlisted authors, plus the daily cap.

## Code style: modern Java 25 (see docs/java-style-guide.md)
- **Records** for all data (domain, DTOs, Kafka events, `@ConfigurationProperties`). Validate in compact constructors and use `List.copyOf` for immutability. No Lombok, no setters.
- **Sealed interfaces + records** for closed outcomes, with an exhaustive pattern `switch` and no `default`.
- **Streams** for collection transforms (`toList()`, collectors, comparators) and **Gatherers** (`mapConcurrent`, `windowFixed`) where they fit. Never `.parallel()`. Use a plain loop when it is clearer (stateful parsing), with a one-line comment saying why.
- `Optional` only as a return type. Use text blocks for SQL. Virtual threads are on. Use `_` for unused variables. **No preview features.**
- Use plain SQL via `JdbcClient`, mapping to records (no JPA). Every schema change goes in a new Flyway migration; never edit an applied one.
- Prompts live in versioned files under `resources/prompts/`. Never edit a prompt version that has already been used in an evaluation; add a new version.
- Tests target real risks (diff mapping, validation, idempotency, webhook signatures, Kafka redelivery, retries/DLT, fallbacks), not getters.

## Kafka rules (ADR-0014; from Sprint 3)
- **No dual writes:** never call Kafka from the webhook request path. Write the outbox row in the same DB transaction, and `OutboxRelay` publishes it.
- **Every consumer is idempotent.** Any message can arrive more than once, so check `review_requests` status first, and rely on the publish marker check.
- Classify failures with the sealed `ReviewException` (retryable or permanent). Never swallow exceptions inside the listener.
- Topics and partitions are declared in code (`NewTopic` beans). Changing them needs an ADR.
- Until Sprint 3, the webhook starts reviews in-process on a virtual thread (ADR-0017). This is temporary and not durable.

## Safety rules
- No secrets in code, logs, tests, images or commits. Use env vars, a git-ignored `.env`, or GitHub Actions secrets; `.env.example` holds names only.
- Don't log full source files or full prompts at INFO level.
- PR content is untrusted. The models get no tools and return data only, and Java performs every action.
- The model never computes line numbers. It picks allowed location IDs.
- Only `/webhooks/github` and `/actuator/health` are ever exposed publicly (docs/deployment.md).
