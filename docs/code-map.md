# Code map: where is what

Package root: `backend/src/main/java/io/github/rahu09/prreviewer/`. Tests mirror the same folders. Each top-level package has a `package-info.java` saying what it is for; sub-packages are described here.

## Follow one review through the code

| Step | What happens | Where |
|---|---|---|
| 1. Request | Dry-run HTTP endpoints (`/diff`, `/fixture/{id}`, `/pr`) and `GET /api/runs/{id}` | `review/api/` |
| 2. Input | A fixture folder, or a PR frozen from GitHub (SHAs, diff, changed files at head) | `review/fixture/`, `github/` (`GitHubSnapshotProvider`) |
| 3. Engine | Orchestrates all steps and records the run | `review/ReviewOrchestrator` |
| 4. Diff to locations | Parse the unified diff | `context/` (`DiffParser`, `FileDiff`, `DiffLine`) |
| 5. Limits | Apply file / line / prompt budgets, record skipped files | `context/budget/` |
| 6. Prompt text | Location IDs (`L17`) on every commentable line | `context/location/` (`AnnotatedDiff`, `AllowedLocation`) |
| 7. LLM | Gemini or OpenAI through Spring AI | `ai/` (implements `review/ReviewGenerator`) |
| 8. Validate | Map IDs to real lines, suppress bad findings with a reason | `publishing/` (`FindingValidator`, `ValidationResult`) |
| 9. Save | Run and all findings in Postgres | `review/run/` |

## Packages

| Package | Holds | Depends on |
|---|---|---|
| `review/` | `ReviewOrchestrator`, `ReviewConfig`, `ReviewLimits`, `ReviewSnapshot`, `ReviewOutcome`, `ReviewGenerator` | everything below |
| `review/model/` | Data in flight: `CandidateFinding(s)`, `Category`, `GenerationResult`, status enums | nothing |
| `review/run/` | Stored data: `ReviewRun`, `StoredFinding`, repository | `review/model` |
| `review/api/` | Controllers, `ReviewReport`, `ApiExceptionHandler` | `review` |
| `review/fixture/` | `FixtureLoader`, `FixtureMeta` | `review` |
| `context/` (+ `budget/`, `location/`) | Diff parsing, limits, allowed locations | nothing outside itself |
| `github/` | Token provider, `GitHubClient`, DTOs, snapshot provider | `review`, `context` |
| `publishing/` | `FindingValidator`, `ValidationResult` (Chunk 7 adds the publisher) | `context`, `review/model` |
| `ai/` | `SpringAiReviewGenerator` | `review` |
| `knowledge/`, `messaging/`, `webhook/` | Empty until Chunks 8, 12 and 11 | |

## Folder rules

- New class: put it in the folder that matches its role (data in flight, stored data, HTTP, input). Keep the entry point of a package (like the orchestrator) at its top level.
- Aim for 8 files or fewer per folder. When a folder passes about 12, propose a split before adding more.
- Group by feature first, role second. No project-wide `controllers/` or `repositories/` folders.
- Keep this table current when a chunk adds packages.
