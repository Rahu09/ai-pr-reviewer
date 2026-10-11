# Chunk journals

One file per chunk: `NN-short-name.md` (e.g. `04-diff-parsing.md`).
A journal records **what was built and what was learned**. The **why** of decisions goes in ADRs (`docs/decisions/`), and the journal links to them.

With the fast track (ADR-0017), a journal is filled in **two passes**:
- **Build mode:** the assistant fills *Goal, What I built, Decisions, Problems, How I verified, Deviations, Next* and sets **Status: Built**.
- **Study mode:** you fill *Concepts I learned, Study exercise, Explain it* and set **Status: Studied**.

Copy the template below.

---

```markdown
# Chunk NN — <Title>

- **Dates built:** YYYY-MM-DD → YYYY-MM-DD   · **Date studied:** YYYY-MM-DD
- **Status:** In progress | Built | Studied   (while In progress, keep "Next" up to date so a new session can resume)
- **Tag:** chunk-NN

## Goal
<Copied from the chunk card in PLAN.md.>

## What I built            <!-- build mode -->
- `ClassName` — one line on what it does.
- `V2__runs_and_findings.sql` — one line.

## Decisions made          <!-- build mode -->
- ADR-NNNN — <title> (new / superseded ADR-MMMM).
- Small choices not worth an ADR: …

## Problems hit and how they were solved   <!-- build mode -->
- Problem → cause → fix.

## How it was verified     <!-- build mode -->
- Tests added: …
- Manual checks: … (commands, sample outputs, screenshots)
- "Done when" checklist: [x] … [x] …

## Deviations from the plan   <!-- build mode -->
- What differed from PLAN.md and why (and whether PLAN.md was updated).

## Concepts I learned (in my own words)   <!-- study mode -->
- **Concept:** 2–3 sentences, as if explaining it to an interviewer.

## Study exercise (what I changed myself)   <!-- study mode -->
- The change, why, and what broke or surprised me while making it.

## Explain it (teach-back)   <!-- study mode -->
1. Q: <the chunk's interview check>  
   A: …
2. Q: …  
   A: …
3. Q: …  
   A: …

## Next
- First step of the next chunk / open questions.
```
