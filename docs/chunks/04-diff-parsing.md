# Chunk 04 — Diff parsing, allowed locations, validation ★

- **Dates built:** 2026-10-12  · **Date studied:** —
- **Status:** Built
- **Tag:** chunk-04

## Goal
Guarantee a comment lands on the right line: Java parses the diff, gives the model location IDs instead of line numbers, and validates every finding before anything can be published.

## What I built
Package `context/` (diff → locations):
- `DiffLine` (sealed: `Added` / `Removed` / `Context`), `Hunk`, `FileDiff` (path, oldPath, `ChangeType`, binary, hunks), `ParsedDiff`, `ChangeType`, `Side`.
- ★ `DiffParser`: a plain state-machine loop. Inside a hunk it trusts the header's line counts, not the first character, so a removed line `-- x` (diff line `--- x`) is not mistaken for a file header. Handles added / modified / deleted / renamed (with and without edits) / binary / mode-only changes, multi-hunk files, "no newline at end of file", paths with spaces, hunk headers without counts, and stripped empty context lines. Truncated or over-long hunks throw `DiffParseException` (HTTP 400) instead of guessing.
- `AllowedLocation(id, path, line, side)` and `AnnotatedDiff`: one rendering pass produces the prompt text (`[L3]+added line`) AND the locations, so IDs in the text always exist in the list.
- `ContextBudget` (max Java files, max changed lines, max prompt chars) → `Selection(reviewed, skipped)`; `SkippedFile` + `SkipReason` (BINARY, DELETED, NOT_JAVA, NO_CONTENT_CHANGE, FILE_TOO_LARGE, TOO_MANY_FILES, CHANGED_LINES_LIMIT, PROMPT_TOO_LARGE).

Package `publishing/`:
- ★ `FindingValidator` + sealed `ValidationResult` (`Publishable` / `SummaryOnly` / `Suppressed`). Checks the category (case-tolerant enum), required title and explanation, that the location ID exists (brackets and case tolerated), merges same location + category via `Collectors.toMap` (first wins, the rest are suppressed as duplicates), and applies the inline cap (overflow becomes `SummaryOnly`). One result per candidate, same order; nothing is dropped.

Package `review/`:
- `CandidateFinding` now has `locationId` instead of `filePath`/`line`; `Category` enum; prompts `review-v2.st` and `review-v2-user.st` (v1 files untouched).
- `ReviewSnapshot`, `ReviewConfig` (+ `Override` for per-request changes), `ReviewOrchestrator` (parse → budget → annotate → LLM → validate; skips the paid call when there is nothing reviewable), `ReviewOutcome`, `ReviewReport` (JSON with per-finding status and reason).
- `FixtureLoader` (folder with `fixture.json`, `change.diff`, `files/`; id whitelist against path traversal), `FixtureMeta`, `FixtureProperties` (`reviewer.fixtures.dir`, default `../evaluation/datasets`).
- `POST /api/dry-run/fixture/{id}` (optional `{config: {...}}` body) and `POST /api/dry-run/diff` (now through the orchestrator). `ApiExceptionHandler`: 400 for damaged diffs, 404 for unknown fixtures, 502 for model failures.
- `ReviewLimits` gained `maxPromptChars`.

## Decisions made
- No new ADR (no new dependency, table or service). `CandidateFinding` changed shape because prompt v2 replaces line numbers with location IDs (ADR-0008 as designed).
- A blank `locationId` becomes `SummaryOnly` (a real finding about removed code or missing tests); an unknown ID is `Suppressed`. A line NUMBER given instead of an ID is suppressed too.
- `GenerationResult.notCalled()` records provider `none` when nothing was reviewable.

## Problems hit and how they were solved
- `switch` with guarded record patterns inferred `List<Record & ValidationResult>` → `.<ValidationResult>map(...)`.
- A script edit that inserted a line without bumping the hunk header produced a 400: the strict parser caught a damaged diff, as intended. The injection script now updates the header (pitfalls §4).
- A body-less `POST` from Python's urllib is sent as `application/x-www-form-urlencoded` → 415; the script now sends `{}` as JSON (pitfalls §3).
- A Python edit script turned `\n` escapes into real newlines inside a Java string → fixed with the editor tool.

## How it was verified
- Tests (no keys, CI-like): `DiffParserTest` 20, `AnnotatedDiffTest` 4, `ContextBudgetTest` 6, `FindingValidatorTest` 12, `FixtureLineMappingTest` 12 (parameterised over the 4 real fixtures), `ReviewOrchestratorTest` 7, `DryRunControllerTest` 8, plus the Chunk 1–3 tests. The whole suite is green.
- The parser test input is a real `git diff` generated in a scratch repo (`src/test/resources/diffs/mixed-changes.diff`): multi-hunk, added, deleted, renamed, renamed+edited, binary, mode change, no-newline, and a `-- ` removed line.
- Strongest check: for every real fixture, each allowed location's (path, line), read back from the head file contents, equals the added line text in the diff; and each fixture's expected finding line is an allowed location.
- Real Gemini run (`gemini-3.8-flash`, prompt `review-v2`) via `POST /api/dry-run/fixture/{id}`:

| Fixture | Result |
|---|---|
| list-orders-unpaginated (buggy) | PUBLISHABLE at `OrderController.java:32`, GUIDELINE ✅ (expected API-03, line 32) |
| cancel-order-swallowed (buggy) | PUBLISHABLE at `OrderService.java:28`, CORRECTNESS ✅ (expected ERR-02, line 28) |
| list-orders-paginated (clean) | no findings ✅ |
| cancel-order-handled (clean) | no findings ✅ |

  Lines are now computed by Java from IDs, so they are right by construction. The injection variant (a comment telling the reviewer to ignore its instructions) still produced the real finding, at the shifted line 33.
- "Done when": [x] added/modified/deleted/renamed/binary files [x] multi-hunk [x] no-newline [x] invalid `locationId` suppressed with a reason [x] oversized file skipped and reported.

## Deviations from the plan
- `FileDiff` has an extra `binary` flag (binary files have no hunks and no change type of their own).
- `AllowedLocation` numbering and the prompt rendering live together in `AnnotatedDiff` rather than a separate builder, so they cannot disagree.

## Next
- Chunk 5: `V2` migration (`review_runs`, `review_findings`), `JdbcClient` repositories, record every run (config, SHAs, status, failed stage, tokens, files reviewed/skipped), store ALL findings including suppressed ones with reasons, `GET /api/runs/{id}`.
