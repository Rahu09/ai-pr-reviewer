# ADR-0002: Narrow scope — advisory reviews of Java PRs on one demo repository

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning
- **Supersedes:** —

## Context
"AI PR reviewer" can grow without limit: every language, auto-fixes, approvals, SaaS. The goal is a **reliable, explainable** portfolio project, finished in weeks by one person who is also learning the concepts.

## Decision
- Review only **Java** changes in **one demo repo** that the owner controls (`order-service-demo`).
- Enforce size limits (starting values: ≤ 10 Java files, ≤ 400 changed lines). Anything over the limits is **skipped and reported** in the summary.
- Post **advisory `COMMENT` reviews only**: a summary plus at most 5 inline findings. Never `APPROVE` or `REQUEST_CHANGES`.
- Use three finding categories: `CORRECTNESS`, `GUIDELINE`, `TEST_SUGGESTION`.
- Never run PR code and never auto-apply fixes.
- The summary always states what was reviewed and skipped. "No findings" ≠ "safe to merge".

## Alternatives considered
- **Multi-language support:** each language needs its own context handling and test data. It multiplies the work without adding much interview value.
- **Approving/blocking PRs:** this needs a level of confidence the system cannot honestly claim.
- **Auto-fix commits:** these need code execution, sandboxing and write access, which is far more risk and scope.
- **Reviewing real open-source repos:** there is no control over the PRs, it is noisy for others, and there is no ground truth for evaluation.

## Consequences
- **Good:** achievable, testable and demoable; a controlled environment for evaluation.
- **Bad / costs:** the demo is less general, so the narrow scope has to be explained as a deliberate choice (which is itself a good interview point).

## Revisit if
v1.0 is done and the evaluation is solid. Wider scope is then a stretch item and needs a new ADR.
