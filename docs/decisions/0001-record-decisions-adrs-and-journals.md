# ADR-0001: Record decisions with ADRs, chunk journals and a changelog

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning
- **Supersedes:** —

## Context
This is a learning project built for interviews and a job search. The owner needs to:
- understand every part of the system,
- remember *why* each choice was made weeks later, and
- answer "why did you do it this way?" in interviews.

Plan v1 was one large document. Changes to it would overwrite the old reasoning and lose the history.

## Decision
Use four complementary records, all in the repo:
1. **ADRs** (`docs/decisions/`): one file per decision covering *why*. Accepted ADRs are immutable; a change is made by writing a new ADR that supersedes the old one.
2. **Chunk journals** (`docs/chunks/`): one per chunk covering *what was built, what was learned, what broke and how it was verified*.
3. **CHANGELOG.md:** a dated list of additions and changes per chunk.
4. **Plan history** (`docs/plan-history/`): dated notes whenever the plan itself changes.

Every chunk ends by updating these (see PLAN.md §10).

## Alternatives considered
- **A single living plan document:** simple, but it loses history every time it is edited (the v1 problem).
- **Git commit messages only:** they keep history, but are hard to browse and too short to hold the reasoning.
- **Notion or a wiki:** these live away from the code, are not versioned with it, and are invisible to reviewers of the repo.

## Consequences
- **Good:** a written, dated reason for every decision; strong interview material; a new chat or person can pick up the project quickly.
- **Bad / costs:** about 15–20 minutes of writing per chunk, and some discipline needed not to skip it.

## Revisit if
The documentation overhead is clearly slowing progress more than it helps. In that case, merge journals into the changelog but keep the ADRs.
