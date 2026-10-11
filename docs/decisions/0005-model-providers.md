# ADR-0005: Model providers — Claude for generation, local ONNX embeddings

- **Status:** Accepted — generation provider (Claude) superseded by [ADR-0018](0018-llm-provider-gemini-or-openai-by-config.md) (Gemini or OpenAI by config); the local ONNX embeddings decision still stands
- **Date:** 2026-10-04
- **Chunk:** planning (provider access confirmed in Chunk 0; first call in Chunk 3)
- **Supersedes:** v1 plan's "choose provider later"

## Context
Two different model jobs are needed:
1. **Generation:** read a code change plus evidence and write findings. This needs strong code understanding and reliable structured (JSON) output.
2. **Embeddings:** turn guideline chunks and queries into vectors for similarity search. The corpus is tiny (roughly 20–100 chunks).

The owner was open to Claude or OpenAI. Anthropic does not offer a first-party embeddings API. Cost matters because this is a self-funded project.

As of 2026-10-04, Spring AI provides an Anthropic chat starter, OpenAI chat/embedding starters, and `spring-ai-starter-model-transformers` (local ONNX, default model `sentence-transformers/all-MiniLM-L6-v2`, **384 dimensions**, ~80 MB, downloaded and cached on first use).

## Decision
- **Generation: Anthropic Claude**, a Sonnet-class model, through Spring AI's `ChatModel`/`ChatClient`. The model ID is pinned in `application.yml`, and the actual model ID is recorded on every run. Check the current model list when starting Chunk 3.
- **Embeddings: local ONNX `all-MiniLM-L6-v2`** through the Spring AI Transformers starter. It needs no API key, costs nothing, works offline and gives deterministic results in tests.
- Business code depends only on our own `ReviewGenerator` interface and Spring AI's `EmbeddingModel`, never on vendor SDK classes.
- The embedding model is part of the guideline **index version** (ADR-0006), so switching models forces a clean re-index rather than mixing vectors.

## Alternatives considered
- **OpenAI for both chat and embeddings:** one vendor and one key, and a perfectly good choice if OpenAI credits are already available. Switching is a config and dependency change thanks to Spring AI.
- **OpenAI `text-embedding-3-small` with Claude chat:** probably better retrieval quality, but it means a second paid account and network calls for every embedding.
- **Voyage AI** (Anthropic's recommended embedding partner): another account, with less direct Spring AI support.
- **Choosing the provider later** (the v1 plan): it left a key decision open, and making it now lets the accounts and keys be set up in Chunk 0.

## Consequences
- **Good:** only one paid LLM key (plus Jev); free and fast re-indexing; a clean comparison story ("generation and embeddings are separate, swappable concerns").
- **Bad / costs:** MiniLM is weaker than hosted embedding models, but this is measured through retrieval recall@k in Chunk 10, and Jev reranking (variant C) partly compensates. The first embedding call downloads the model, and the ONNX runtime is a native library that could cause trouble on Windows. If it does, run in Docker or switch embeddings (new ADR).

## Revisit if
- Retrieval recall@k on the dev set is poor (for example below 0.7). Then try OpenAI embeddings as an experiment and record a new ADR.
- No Anthropic API access, or the budget doesn't fit. Then use OpenAI for generation (new ADR).
- Stretch: compare Claude and an OpenAI model on the same eval set; this is a config change.
