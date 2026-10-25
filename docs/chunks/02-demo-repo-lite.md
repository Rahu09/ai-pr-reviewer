# Chunk 02 — Demo repo, guidelines, first fixtures (lite, Sprint 1)

- **Dates built:** 2026-10-12  · **Date studied:** —
- **Status:** Built
- **Tag:** chunk-02-lite

## Goal
A small demo repo with testable guidelines, 4 fixtures generated from real branches (2 buggy + 2 clean twins), and 2 open PRs for Chunks 6–7. The full Chunk 2 (15–25 rules, 6 fixtures) comes in Sprint 2.

## What I built
Demo repo `E:\production\order-service-demo` (Spring Boot 4.0.8, Java 25, in-memory storage, 12 main classes, 3 test classes):
- `Order`, `OrderItem` (records), `OrderStatus`, `CreateOrderRequest` (validated record), `OrderRepository` + `InMemoryOrderRepository`, `OrderService`, `OrderController`, `ApiExceptionHandler` (`ProblemDetail`), `OrderNotFoundException`, `OrderStateException`.
- `docs/engineering-guidelines.md`: 10 rules with stable IDs (API-01..04, ERR-01..03, ARC-01..02, TST-01).
- Branches (all build and pass their tests):
  - `feature/list-orders-unpaginated` — **buggy**: `GET /orders` returns every order (violates API-03).
  - `clean/list-orders-paginated` — clean twin: `page`/`size`, size capped at 100, `PageResponse`.
  - `feature/cancel-order-swallowed` — **buggy**: `OrderService.cancel` catches `OrderStateException` and returns the unchanged order, so a shipped order "cancels" with 200 (violates ERR-02).
  - `clean/cancel-order-handled` — clean twin: the exception propagates, `ApiExceptionHandler` maps it to 409.

In this repo:
- `evaluation/make_fixtures.py` generates fixture folders from the demo repo's branches (`git diff --no-color main...<branch>`, `git show <branch>:<path>`).
- `evaluation/datasets/dev/<id>/` (`fixture.json`, `change.diff`, `files/…`) for the 4 fixtures, and `evaluation/datasets/guidelines/engineering-guidelines.md` (frozen copy).

## Expected findings (one sentence each)
- `list-orders-unpaginated`: `OrderController.list` (line 32) returns all orders unpaginated → API-03.
- `cancel-order-swallowed`: `OrderService.cancel` (line 28, the `catch`) swallows `OrderStateException`, hiding that a shipped order cannot be cancelled → ERR-02.
- Both clean twins: zero expected findings.

## Decisions made
- No new ADR. Small choices: in-memory repository (no DB needed for a demo); Boot 4.0.8 like the main project; the clean twins carry more tests than the buggy branches (a realistic "done right" PR).
- The buggy branches include a success test but no failure test, so a reviewer might add a TST-01 comment. That is legitimate and is noted so we judge it fairly in adjudication.

## Problems hit and how they were solved
- A large shell heredoc with Java text blocks failed to parse → wrote files with the editor tool instead.
- Pagination limits: validating `@Min/@Max` on request params did not look reliable under standalone MockMvc, so the service clamps `size` to 1..100 explicitly and a test covers it.

## How it was verified
- `./mvnw verify` green on `main` and on all 4 branches (JDK 25).
- Fixture line numbers re-checked with `sed -n <line>p` against the head files.
- [x] pushed (owner) · [x] 2 PRs open: order-service-demo#1 (list-orders-unpaginated), #2 (cancel-order-swallowed)

## Deviations from the plan
- None. (Demo repo has 12 classes rather than "about 8"; still small.)

## Next
- Chunk 3 done. The 2 open PRs are used by Chunks 6-7.
