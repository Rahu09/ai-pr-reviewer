# ADR-0011: Evaluation — small Python scripts against the Java dry-run API

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning (fixtures from Chunk 2, full evaluation in Chunk 10)
- **Supersedes:** —

## Context
Without measurement, "RAG helps" and "Jev helps" are just claims. Evaluation is also the strongest differentiator in interviews. It must measure the **real** reviewer, not a copy of it.

## Decision
- **Java owns the reviewer, and Python owns the experiments.** Python never reimplements review logic. It calls `POST /api/dry-run/fixture` with a variant config and saves the responses.
- There are two scripts, `run_eval.py` (runs cases × variants, caches results) and `score.py` (computes metrics from results plus manual labels), with minimal dependencies.
- **Variants:** A (LLM only), B (+ similarity retrieval), C (+ Jev ranking), and D (+ citation screening) as a stretch. All use the same model, prompt and budgets, and the same number of kept passages.
- **Dataset:** about 20 dev and 10 held-out fixtures, **split by scenario family**. Bug cases come with clean twins, and there are prompt-injection, missing-context and renamed-file cases. Fixtures live in `evaluation/datasets/`, with a frozen copy of the guidelines.
- **Ground truth is the owner's manual adjudication** (`adjudication.csv`). Another model's verdict is not ground truth. Label corrections are written down before scoring.
- **Metrics:** finding precision and recall, clean-case false-positive rate, citation correctness, retrieval recall@k, tokens and cost per review, average latency and skipped-file rate.
- Prompts are tuned on dev only, and held-out is run **once**. Results are cached by (case, variant, prompt version, model), with a few fresh repeats to show variance.
- The results are reported honestly in `docs/evaluation.md`, including failures and the small-sample caveat.

## Alternatives considered
- **An evaluation module in Java:** a single language, which is fine. Python was chosen because data wrangling and tables are quicker in it, it is the norm in ML evaluation, and it shows breadth. Both would call the same HTTP API.
- **LLM-as-judge as the primary metric:** cheap, but not ground truth, and it can share the generator's blind spots. It might be added later as a *secondary* signal.
- **A large dataset (100+ cases):** too much manual labelling for one person. Thirty well-built cases with twins teach more.

## Consequences
- **Good:** a real, explainable comparison (B vs C answers "was Jev worth it?") and a black-box evaluation of the actual system.
- **Bad / costs:** manual labelling time, a second language toolchain, and small-sample results that are noisy and must be presented as such.

## Revisit if
Variance between fresh repeats is so large that the A/B/C differences are meaningless. Then add cases or repeats before drawing conclusions.
