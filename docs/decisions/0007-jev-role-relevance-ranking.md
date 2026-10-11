# ADR-0007: Jev has one role — guideline relevance ranking, with similarity fallback

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning (built in Chunk 9)
- **Supersedes:** —

## Context
Vector similarity finds *similar* text, which is not always *useful* text. Jev (TypeSafe) is a typed decision model. It answers narrow questions (Score, Choice, Noul) with probabilities and a confidence, and it does not generate text or code. The project should use Jev where it adds measurable value, and nowhere it could quietly cause harm.

API facts, checked 2026-10-04 at docs.typesafe.ai/api (re-check before Chunk 9):
- `POST https://api.typesafe.ai/v1/systemone`, `Authorization: Bearer <key>`, JSON body with `state`, `model` (e.g. `jev-latest`) and `questions`.
- A Score question has `type: "score"`, `instructions`, and `criteria` (2–10 level descriptions).
- A Score answer has `score`, `legend`, `probabilities` and `confidence`. The response also includes the actual `model` version (e.g. `jev-1.13.0`) and `usage`.
- `429`/`529` mean retry with backoff.

## Decision
- Jev's **only** v1 job is to **score each retrieved guideline passage's relevance** to the code change on a 0–3 rubric:
  0 = unrelated · 1 = same topic, no usable rule · 2 = applicable but incomplete · 3 = explicit rule that directly applies.
- Java applies the policy: keep scores ≥ 2, sort by expected score (similarity breaks ties), keep at most 3, and allow an empty result.
- `JevRanker` implements the same `ContextRanker` interface as `SimilarityRanker`. On timeout or errors (after up to 2 retries for 429/529), it **falls back to similarity ranking** and the run records the fallback with its reason.
- The raw Jev answers and actual model version are stored on the run.
- Start with one request per candidate; trying the batched form is an experiment.

**Explicitly not Jev's job in v1:** judging whether code is correct, scoring severity, skipping files, declaring code "safe", or deciding what gets published.

## Alternatives considered
- **No reranker:** this is variant B in the evaluation, the baseline Jev must beat.
- **Claude as the reranker:** costlier, its output is untyped text, there are no per-level probabilities, and it mixes the generator's and the judge's roles.
- **A local cross-encoder reranker:** a reasonable alternative, but it doesn't teach the owner's chosen tool, Jev.
- **Jev as a publication gate:** a gate that hides findings needs evidence that it doesn't hide valid ones. Citation-support screening is kept as a stretch item (variant D).

## Consequences
- **Good:** one bounded, measurable role (B versus C), and the system tolerates Jev outages. It also gives a clear interview story about the right tool for a narrow job.
- **Bad / costs:** extra latency and tokens per review (up to 8 calls). These are measured and reported in Chunk 10. A relevance probability is **not** the probability that a finding is correct, and the docs and README must say so.

## Revisit if
Chunk 10 shows C is not better than B on held-out data, or that the extra cost is not worth it. Record the result honestly and decide in a new ADR whether Jev stays in the default config.
