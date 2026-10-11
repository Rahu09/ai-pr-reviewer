# ADR-0006: RAG over engineering guidelines only, not the whole codebase

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning (built in Chunk 8)
- **Supersedes:** —

## Context
Reviews should be grounded in the repository's own rules ("collection endpoints must be paginated"), not just the model's general opinions. RAG can supply that evidence. The question is *what* to retrieve.

## Decision
- Retrieve only from **engineering guideline documents** (markdown in the demo repo's `docs/`, read from the **base** branch, which is trusted).
- Code context comes **directly** from the PR snapshot instead: the diff plus the content of the changed files at the head SHA, kept within budget.
- Chunk by heading/rule ID, and split long sections by size. Each chunk stores the repo, source path, rule ID, heading, content hash, source SHA, embedding model and chunker version.
- An index is identified by repo + guideline content hashes + embedding model + chunker version. **An index that already exists is never re-embedded.**
- Retrieval is always filtered by **repo and index version**. It takes the top 8 candidates and keeps at most 3 after ranking. An empty context is allowed.

## Alternatives considered
- **Embedding the whole codebase:** code needs different chunking (symbols, methods), exact-name matching and versioning per commit. That is much more complex, and the guideline RAG already demonstrates embeddings, vector search, reranking and grounding.
- **No RAG (LLM only):** this is kept as **variant A** in the evaluation, as the baseline.
- **Putting all guidelines in every prompt:** it would work for a tiny corpus, but it wastes tokens and doesn't scale. It is also exactly the "does selection matter?" question the evaluation answers.

## Consequences
- **Good:** a small, understandable corpus with labelled rule IDs, which makes retrieval measurable (recall@k).
- **Bad / costs:** findings that need context from other files (callers, interfaces, tests) may be missed. Those cases are recorded in the evaluation.

## Revisit if
The evaluation shows a pattern of missing-context failures that retrieving related tests or callers would fix. That would be a stretch item with a new ADR.
