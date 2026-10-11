# Plan change: v3.3 → v4 fast track (11 Oct 2026)

- **Before:** [2026-10-11-plan-v3.3-snapshot.md](2026-10-11-plan-v3.3-snapshot.md)
- **After:** [PLAN.md](../../PLAN.md) v4
- **Decision record:** [ADR-0017](../decisions/0017-fast-track-build-sprints-then-study.md)
- **Trigger:** the owner said the first demoable version in about 1.5 months was too slow. They want the app working in **2 days**, to put it on their resume and then learn it by analysing it.

## What changed and why

| # | Change | Why |
|---|---|---|
| 1 | **Two modes:** build mode (the assistant implements whole chunks and stops at chunk boundaries) and study mode (walkthrough, tracing, an exercise the owner makes, teach-back) | Get a working system quickly, then learn from real code. This is the owner's preferred learning style |
| 2 | **Four sprints** (PLAN §9.0): Sprint 1 (Days 1–2) covers chunks 0–7 + 11, Sprint 2 covers 8–10, Sprint 3 covers 12–13 and Sprint 4 covers 14–15 | A working, *automatic* bot by about Day 2, and the full feature set in roughly 7–10 build days |
| 3 | **Chunk 11 moved into Sprint 1,** with a temporary in-process trigger (a virtual thread) until Kafka arrives in Sprint 3 | "Automatic reviews" is what makes the demo convincing, and Kafka needs more time than Day 2 allows |
| 4 | **Lite versions:** Chunk 2 (one guidelines file, 4 fixtures, 2 PRs) and Chunk 10 (about 10 fixtures) | Fit the timebox. They grow later |
| 5 | **★ pieces:** the owner writes the *study exercise* on them, not the first version | Keeps hands-on ownership of the parts interviews probe without blocking the sprint |
| 6 | **Local commits by the assistant in build mode;** the owner still pushes | Speed, while the owner keeps control of what goes public |
| 7 | **Journal template in two passes** (build sections, then study sections), and the §9 table gains **Built** and **Studied** columns | Makes it visible when study falls behind the build |
| 8 | **Resume honesty rule** (PLAN §12) | Resume claims must stay backed by understanding |

## What did NOT change
The architecture, scope, AI design, Kafka design, deployment plan, all ADRs 0001–0016, the chunk cards' "Done when" checks (fast never means untested) and the documentation system.

## Interview angle
"I time-boxed an MVP to two days by cutting scope, not tests. I used a temporary in-process trigger, recorded it as a known limitation, and later replaced it with a transactional outbox and Kafka. That replacement is a clean before-and-after story."
