# ADR-0017: Fast track: build in short sprints first, study alongside

- **Status:** Accepted
- **Date:** 2026-10-11
- **Chunk:** planning (changes how every chunk is executed)
- **Supersedes:** the learning-first execution model of PLAN v3.3 §10, where the assistant explained and waited before each step and the owner wrote the ★ pieces first. That model wasn't an ADR; the [v3.3 snapshot](../plan-history/2026-10-11-plan-v3.3-snapshot.md) preserves it.

## Context
The owner needs the project on their resume **within days**, not weeks. Under the learning-first model, the first demoable version (v0.1) was estimated at about 22–37 sessions (~1–1.5 months). The owner's preferred way to learn is to **get a working system first, then study it by analysing and changing it**.

Two things don't change:
- **Interviewers will probe whatever is on a resume.** Shallow understanding is the main risk of building fast.
- **Accounts, keys and the GitHub App** need the owner's own hands. Some infrastructure can't be rushed safely.

## Decision
- **Two modes** (PLAN §10):
  - **Build mode:** the assistant implements whole chunks, with tests, and stops only at chunk boundaries.
  - **Study mode:** one session per built chunk, with a code walkthrough, tracing in IntelliJ, a small change the owner makes, and teach-back.
- **Four build sprints** (PLAN §9.0):
  1. **Sprint 1 (Days 1–2):** Chunks 0–7 + 11. A working GitHub App that reviews demo-repo PRs automatically.
  2. **Sprint 2:** Chunks 8–10. RAG, Jev and first measurements.
  3. **Sprint 3:** Chunks 12–13. Kafka.
  4. **Sprint 4:** Chunks 14–15. Deployment and polish.
- **Temporary simplification in Sprint 1:** after the webhook stores the delivery, it starts the review **in-process on a virtual thread**. This is not durable, because a crash loses that review (redelivering the webhook from GitHub recovers it). Sprint 3 replaces it with the outbox + Kafka (ADR-0014), which also makes a good before/after interview story.
- **"Lite" versions in Sprint 1:**
  - Chunk 2: one guidelines file (~10 rules), 4 fixtures and 2 PRs.
  - Chunk 10, in Sprint 2: about 10 fixtures.
  - Both grow later.
- **★ pieces:** the owner no longer writes them first. Instead, each ★ chunk's study session includes a **real change to the ★ piece**, made by the owner.
- **Commits:** in build mode the assistant may make **local** commits. The owner pushes.
- **Resume honesty:** list only what is built. Before interviews, pass the interview checks of every chunk behind a resume line. Building with AI assistance is normal; say so if asked. What matters is being able to explain and change the code.

## Alternatives considered
- **Keep learning-first:** the deepest understanding, but too slow for the job-search timeline.
- **Generate all 16 chunks in 2 days:** a large codebase that is barely verified, before accounts and infrastructure are ready, and nobody can explain it. High interview risk.
- **Build fast with no study track:** a resume line that falls apart at the first follow-up question.

## Consequences
- **Good:**
  - A demoable, automatic bot in about 2 days.
  - The full feature set in roughly 7–10 build days.
  - Study is anchored in real, working code.
- **Bad / costs:**
  - Less "learning by writing".
  - The owner must keep the study track going. If study falls far behind the build, the resume claims get ahead of the knowledge.

## Revisit if
- A sprint slips badly: cut scope rather than quality.
- Study falls more than one sprint behind the build: pause building and catch up.
