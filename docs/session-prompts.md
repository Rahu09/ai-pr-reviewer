# Session prompts (copy-paste kickoffs)

The ready-to-paste prompts for every build day and study session of the fast track (PLAN §9.0, [ADR-0017](decisions/0017-fast-track-build-sprints-then-study.md)).
This is a living file: if the plan changes, update the prompts here too.

**The prompts are status-driven.** Each build day starts from the **first chunk in PLAN §9.1 (build order) whose *Built* column isn't ✅**, and works toward that day's target. If a day runs short, the next day simply continues; nothing is skipped.

---

## Session rules

| Rule | Detail |
|---|---|
| **One new session per build day** | Don't run a whole sprint in one session, because long sessions get compacted and lose detail. |
| **One new session per study chunk** | Never mix build and study in one session. |
| **Also start fresh** (at a chunk boundary) when… | the session says it's compacting the conversation, it starts forgetting earlier details or repeating a fixed mistake, or you've come out of a long debugging detour or a long break. |
| **Permission mode** | Build sessions: **Accept edits**. Study sessions: **default**. Never bypass. When prompted, choose **Always allow** for `./mvnw`, `docker compose` and `git status/diff/add/commit`, but **never** for `git push`. |
| **Folder** | Start sessions in `E:\production\ai-pr-reviewer`. On days that touch the demo repo (1 and 3), add `E:\production\order-service-demo` as a second folder. |
| **Pitfalls first** | Each prompt tells Claude to read the relevant [pitfalls.md](pitfalls.md) sections before coding. |
| **Before closing any session** | Make sure the current chunk's journal is up to date: either `Status: Built`, or `Status: In progress` with a clear **Next** step. The prompt at the bottom does this for you. |
| **Secrets** | Never paste keys into a chat. You type them into `.env` yourself. |

---

## Build days

| Day | Sprint | Target (work up to and including) | You get |
|---|---|---|---|
| 1 | 1 | Chunk 5 | The review engine works on fixtures; CI is green |
| 2 | 1 | Chunk 11 + Sprint 1 README | **v0.1:** the bot reviews your demo PRs automatically |
| 3 | 2 | Full Chunk 2, then Chunk 8 | Guideline RAG |
| 4 | 2 | Chunk 10 (lite) | **v0.2:** Jev ranking, first A/B/C numbers |
| 5 | 3 | Hosting checkpoint, then Chunk 12 | Outbox + Kafka publishing |
| 6 | 3 | Chunk 13 | **v0.3:** Kafka consumer, retries, DLT, crash-safe |
| 7 (may spill into 8) | 4 | Chunk 15 | **v1.0:** deployed with CI/CD, polished README, demo, interview kit |

### Day 1: Sprint 1, Day 1 (the engine)
**Before starting (you):**
- Installed: JDK 25 (with `JAVA_HOME` set), Git, Docker Desktop, IntelliJ, Python 3.12, Node.js LTS and the VC++ Redistributable.
- LLM API key ready: **Gemini** (Google AI Studio, free tier) or **OpenAI** (platform.openai.com, with a spend limit). An app subscription is not an API key.
- Jev/TypeSafe access requested.
- Create the **empty folders** `E:\production\order-service-demo` and `E:\production\secrets`, and add the demo folder to the session as a second folder.
```text
I'm building the AI PR Reviewer described in PLAN.md (project at E:\production\ai-pr-reviewer; demo repo folder at E:\production\order-service-demo). Read CLAUDE.md, PLAN.md §9.0, §9.1 and §10, the cards for Chunks 0–5, docs/decisions/README.md, docs/java-style-guide.md and docs/pitfalls.md §0–§5. No chunk journals exist yet. We are in build mode, Sprint 1, Day 1 — I have installed the tools. Work through the chunks in §9.1 build order starting from Chunk 0; today's target is up to and including Chunk 5. Follow §10.1 and stop at each chunk boundary with a short summary. Guide me click-by-click for anything only I can do (creating the GitHub repos, pushing, opening the demo PRs, putting my LLM key (GEMINI_API_KEY or OPENAI_API_KEY) and LLM_PROVIDER in .env — I'll type secret values myself). I'm using <Gemini|OpenAI> (see ADR-0018).
```

### Day 2: Sprint 1, Day 2 (GitHub, automatic reviews)
**Before starting (you):** Node.js installed. You'll create a smee.io channel, a fine-grained PAT and the GitHub App during the session (Claude guides you).
```text
I'm building the AI PR Reviewer described in PLAN.md (project at E:\production\ai-pr-reviewer). Read CLAUDE.md, PLAN.md §9.0, §9.1 and §10, the cards for the chunks we'll touch today, docs/decisions/README.md (especially ADR-0010 and ADR-0019), docs/java-style-guide.md, docs/pitfalls.md §0–§2 and §6, and the newest files in docs/chunks/. We are in build mode, Sprint 1, Day 2. If the newest journal says "In progress", finish that chunk first; then continue from the first chunk in §9.1 whose Built column isn't ✅. Today's target is up to and including Chunk 11 (GitHub App + webhook with the temporary in-process trigger, trusted-PR filter and daily cap). Follow §10.1 and stop at each chunk boundary with a short summary. Guide me click-by-click for the smee.io channel, the fine-grained PAT and the GitHub App (including converting its key to PKCS#8 into E:\production\secrets), and tell me exactly which variable names go in .env — I'll type the secret values myself. Finish the day with the Sprint 1 README (architecture diagram, how to run locally, a screenshot of a real review) and update the Built column in PLAN §9.1.
```

### Day 3: Sprint 2, Day 1 (full guidelines + RAG)
**Before starting:** add `E:\production\order-service-demo` to the session as a second folder.
```text
I'm building the AI PR Reviewer described in PLAN.md (project at E:\production\ai-pr-reviewer; demo repo at E:\production\order-service-demo). Read CLAUDE.md, PLAN.md §9.0, §9.1 and §10, the cards for Chunks 2 and 8, docs/decisions/README.md, docs/java-style-guide.md, docs/pitfalls.md §0, §4 and §7, and the newest files in docs/chunks/. We are in build mode, Sprint 2, Day 1 (project Day 3). If the newest journal says "In progress", finish that chunk first, and build any Sprint 1 chunk whose Built column isn't ✅ yet. Then extend Chunk 2 to its full version in the demo repo (all four guideline files, 15–25 rules with stable rule IDs; fixtures generated from real branches) and refresh evaluation/datasets/guidelines/. Then implement Chunk 8 (guideline RAG with local ONNX embeddings and pgvector). Follow §10.1 and stop at each chunk boundary with a short summary.
```

### Day 4: Sprint 2, Day 2 (Jev + first measurements)
**Before starting:** put `TYPESAFE_API_KEY` in `.env` yourself (if you have access). Set aside about an hour to label the findings yourself; honest numbers need your own judgement, not another model's.
```text
I'm building the AI PR Reviewer described in PLAN.md (project at E:\production\ai-pr-reviewer). Read CLAUDE.md, PLAN.md §7, §8, §9.0, §9.1 and §10, the cards for Chunks 9 and 10, docs/decisions/README.md (especially ADR-0007), docs/java-style-guide.md, docs/pitfalls.md §3 and §8, and the newest files in docs/chunks/. We are in build mode, Sprint 2, Day 2 (project Day 4). If the newest journal says "In progress", finish that chunk first; then continue from the first chunk in §9.1 whose Built column isn't ✅. Today's target is up to and including Chunk 10-lite (about 10 fixtures, variants A/B/C, run_eval.py and score.py, a first docs/evaluation.md). Follow §10.1 and stop at each chunk boundary. Prepare adjudication.csv and explain how I should label each finding — I'll do the labelling myself. If I don't have Jev access yet, skip Chunk 9, run 10-lite with variants A and B only, and record that in the journal and CHANGELOG.
```

### Day 5: Sprint 3, Day 1 (hosting checkpoint + outbox + Kafka publishing)
**Before starting:** Docker Desktop has ≥ 6 GB of RAM (Kafka arrives today), and other local Kafka/Postgres stacks are stopped.
```text
I'm building the AI PR Reviewer described in PLAN.md (project at E:\production\ai-pr-reviewer). Read CLAUDE.md, PLAN.md §3.5, §9.0, §9.1 and §10, the card for Chunk 12, docs/decisions/README.md (especially ADR-0014, ADR-0017 and ADR-0019), docs/deployment.md §3–3.1, docs/java-style-guide.md, docs/pitfalls.md §0 and §9, and the newest files in docs/chunks/. We are in build mode, Sprint 3, Day 1 (project Day 5). If the newest journal says "In progress", finish that chunk first; then continue from the first chunk in §9.1 whose Built column isn't ✅. Before any Kafka code, walk me through the Chunk 12 hosting checkpoint: guide me through the Oracle Cloud signup (account only; test A1 capacity, then delete the test VM) and record the outcome; if hosting must be $0 and Oracle failed, stop and help me write the ADR for the fallback instead. Today's target is up to and including Chunk 12 (Kafka in compose.yaml with two listeners, topics, ReviewRequested as a JSON String, V5 migration, transactional outbox, OutboxRelay). Keep the temporary in-process review trigger working today — Chunk 13 removes it. Follow §10.1 and stop at the chunk boundary with a short summary.
```

### Day 6: Sprint 3, Day 2 (Kafka consumer, crash-safe reviews)
```text
I'm building the AI PR Reviewer described in PLAN.md (project at E:\production\ai-pr-reviewer). Read CLAUDE.md, PLAN.md §3.5, §9.0, §9.1 and §10, the card for Chunk 13, docs/decisions/README.md (especially ADR-0014, ADR-0017 and ADR-0019), docs/java-style-guide.md, docs/pitfalls.md §9, and the newest files in docs/chunks/. We are in build mode, Sprint 3, Day 2 (project Day 6). If the newest journal says "In progress", finish that chunk first; then continue from the first chunk in §9.1 whose Built column isn't ✅. Today's target is up to and including Chunk 13 (ReviewRequestConsumer, sealed ReviewException, DefaultErrorHandler with backoff, DLT and DLT listener, daily cap, request status endpoint). Once the consumer works end to end, remove the temporary in-process review trigger from Sprint 1 and say so in the journal and CHANGELOG. Make sure every Chunk 13 "Done when" check passes (simulated crash in a test plus one manual process kill; two quick pushes), then stop with a short summary.
```

### Day 7 (may spill into Day 8): Sprint 4 (deployment + polish)
**Before starting (you):**
- Have the server ready: create the Oracle A1 VM (about 2 OCPUs / 6 GB) or order Contabo Cloud VPS 4, ideally the evening before.
- Have a domain or a DuckDNS subdomain pointing at it.
```text
I'm building the AI PR Reviewer described in PLAN.md (project at E:\production\ai-pr-reviewer). Read CLAUDE.md, PLAN.md §3.6, §9.0, §9.1, §10 and §12, the cards for Chunks 14 and 15, docs/decisions/README.md (especially ADR-0015 and its addenda), docs/deployment.md, docs/pitfalls.md §10, and the newest files in docs/chunks/. We are in build mode, Sprint 4 (project Day 7). If the newest journal says "In progress", finish that chunk first; then continue from the first chunk in §9.1 whose Built column isn't ✅. Implement Chunk 14 following docs/deployment.md: CI builds the jar, runtime-only Dockerfile, multi-arch image to GHCR, deploy/compose.prod.yaml, Caddyfile, backup.sh, deploy.yml, and the server setup. You write the files; guide me step by step for anything on the server, in the hosting provider's console (including Oracle's iptables and Security List if relevant), in DNS, in GitHub Actions secrets and in the GitHub App settings — I'll run server commands and enter secrets myself. Work through the docs/deployment.md §14 checklist until every box is ticked, then do Chunk 15 (final README, docs/demo.md, resume bullets filled with real numbers from docs/evaluation.md). Stop at each chunk boundary with a short summary.
```

---

## Study sessions

Change the chunk number and name each time:

```text
Read PLAN.md §10.2, CLAUDE.md and docs/chunks/04-*.md. We are in study mode for Chunk 4 — Diff parsing, allowed locations, validation. Walk me through the code in request-flow order, tell me where to put breakpoints in IntelliJ, give me the study exercise, then ask me the teach-back questions and record my answers in the journal. Don't change code unless it's part of the exercise.
```

**Suggested order.** Study what your resume claims first. Each study sitting comes after that day's build session.

| Day | Study these chunks | Why |
|---|---|---|
| 2 | **4** ★ diff → line mapping, validation | The core of the project and the most-asked topic |
| 3 | **3** LLM call, structured output · **7** publishing, duplicate protection | Both are in the Sprint 1 resume bullet |
| 4 | **11** ★ webhook signature check, trusted-PR filter · **5** saving runs | Security and traceability questions |
| 5 | **8** RAG | Embeddings and similarity versus relevance |
| 6 | **9** Jev · **10** ★ evaluation | "Did Jev help, and how do you know?" |
| 7 | **12** ★ outbox + Kafka publishing | The dual-write problem |
| After | **13** ★ consumer/DLT · **14** deployment · **15** · then 0, 1, 2, 6 | Finish before interviews that ask about them |

Rule: a chunk counts as **Studied** only once you can answer its interview check without notes. If study falls more than a sprint behind the build, pause building and catch up (ADR-0017).

---

## Handy extra prompts

**Resume in a new session mid-chunk** (after a compaction, a long debugging detour or a break):
```text
Read CLAUDE.md, PLAN.md §9.1 and §10, and the newest files in docs/chunks/. The newest journal says "In progress" — continue from its "Next" step in build mode, following §10.1. Read the matching docs/pitfalls.md section first. Don't restart the chunk.
```

**Stuck on an error:**
```text
We're stuck on this error: <paste the error>. First check docs/pitfalls.md for a known fix. If it's new, find the root cause (check the actual library docs or sources for our versions — Spring Boot 4, Jackson 3, Testcontainers 2, Spring AI 2, Spring Kafka 4 — not memory), fix it, and add it to docs/pitfalls.md as symptom → cause → fix.
```

**Before closing any session:**
```text
Before we stop: update the current chunk's journal (Status: Built, or In progress with a precise Next step), make sure CHANGELOG and PLAN §9.1 are current, list any uncommitted changes, and tell me what I need to do (if anything) before the next session.
```

**When something in the plan looks wrong:**
```text
Something in the plan doesn't fit what we're seeing: <describe it>. Explain the problem, propose the smallest change, and draft the ADR (superseding or addendum, per docs/decisions/README.md) plus the plan-history note. Don't change code until I agree.
```
