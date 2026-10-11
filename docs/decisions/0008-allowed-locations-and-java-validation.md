# ADR-0008: The model picks allowed diff locations; Java maps and validates

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning (built in Chunk 4, extended in Chunk 8)
- **Supersedes:** —

## Context
GitHub inline review comments must point at a line inside the PR diff (`path`, `line`, `side`). LLMs are unreliable at counting lines and at mapping a diff back to file positions. One wrong number means a failed API call, or worse, a comment on the wrong code. LLM output can also be well-formed and still wrong: it may invent evidence, use unknown categories, or repeat itself.

## Decision
- Java parses the unified diff and creates **allowed locations** with short IDs (`L1`, `L2`, …). v1 starts with added lines on the RIGHT side only.
- The prompt shows an **annotated diff** where each commentable line carries its ID. The model returns a `locationId`, never a line number.
- Java maps `locationId` → (`path`, `line`, `side`) for the GitHub API.
- **`FindingValidator`** checks every candidate finding:
  - the location exists in this snapshot,
  - the category is a known enum value and the required text is present,
  - every `evidenceRef` is a member of the evidence set supplied for this run (from Chunk 8),
  - any quoted text actually appears in the referenced evidence (if quoting is used),
  - findings with the same location and category are merged,
  - the per-review inline cap is respected.
- Each finding ends up `PUBLISHABLE`, `SUMMARY_ONLY` (useful but not attachable to a line) or `SUPPRESSED` (with a reason). **All of them are stored**, nothing is deleted.

## Alternatives considered
- **The model returns line numbers:** simpler, but unreliable. Chunk 3 deliberately measures this to justify the decision with real data.
- **Summary-only reviews:** safe, but much less useful, and they skip the most interesting engineering problem.
- **Trusting the output if it parses:** parsing only proves the format, not the content.

## Consequences
- **Good:** comments land on valid lines by construction; hallucinated evidence is caught; there is a full audit trail of what was rejected and why.
- **Bad / costs:** prompts are slightly larger (IDs on lines), and multi-line comments and LEFT-side (deleted line) comments are not supported in v1.

## Revisit if
The evaluation shows many useful findings becoming `SUMMARY_ONLY` because they target deleted or context lines. Then extend the allowed locations (new ADR).

**Important limit:** validation proves structure and references, **not** that the semantic claim is true. Truth is measured by the evaluation (ADR-0011).
