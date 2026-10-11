# Chunk 00 — Setup and decision log

- **Dates built:** 2026-10-11 → 2026-10-11  · **Date studied:** —
- **Status:** Built
- **Tag:** chunk-00

## Goal
Tools installed, repo created at `E:\production\ai-pr-reviewer` and the documentation system in place before any code is written.

## What I built
- `.gitignore`, `.gitattributes` (LF everywhere, CRLF for `*.cmd`/`*.bat`), `.env.example` (names only, forward-slash note), stub `README.md`.
- Local git repo (branch `main`) with the planning docs and ADRs 0001–0019 as the first commit.
- `docs/chunks/00-setup.md` (this journal).

## Decisions made
- No new ADR. Small choice: the JDK in use is **OpenJDK 25.0.2** (IntelliJ-downloaded, `C:\Users\RAHUL\.jdks\openjdk-25.0.2`), not Eclipse Temurin. Same Java 25 LTS; CI will still use Temurin 25.

## Problems hit and how they were solved
- Shell reported Java 18: Machine-level `JAVA_HOME` points at `openjdk-18.0.2.1` → set a **user-level** `JAVA_HOME` to the JDK 25 folder (user variables override machine ones), or set it per session.
- `docker info` could not reach the daemon → Docker Desktop was not running.
- `py -3.12` not found: only Python 3.13 is installed. Not needed until Chunk 10 (Sprint 2); recorded here so we decide then (3.13 is likely fine for two small scripts).

## How it was verified
- `git status` / `git log` show the first commit; `.env` and `.idea/` are ignored (`git check-ignore`).
- [ ] `java -version` shows 25 in a fresh shell (owner: set user `JAVA_HOME`; until then commands use a per-session `JAVA_HOME`. JDK 25 verified by path)
- [x] `docker run hello-world` works (Docker 29.6.2)
- [x] repo pushed to GitHub (`origin/main` at the first journal commit, ADRs 0001–0019)

## Deviations from the plan
- OpenJDK 25 instead of Temurin 25 (see above); Python 3.13 instead of 3.12 (decide in Chunk 10).

## Next
- Owner: set the user-level `JAVA_HOME` (still open).
- Chunk 1 done locally; see its journal.
