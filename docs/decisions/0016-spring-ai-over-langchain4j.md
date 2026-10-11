# ADR-0016: Spring AI (not LangChain4j) as the LLM integration library

- **Status:** Accepted
- **Date:** 2026-10-11
- **Chunk:** planning (applies from Chunk 3)
- **Supersedes:** — (this makes explicit a choice that [ADR-0005](0005-model-providers.md) assumed without comparing alternatives)

## Context
Java has two mature libraries for calling LLMs:
- **Spring AI:** the official Spring project. The 2.0.x line supports Spring Boot 4.
- **LangChain4j:** independent and framework-agnostic. As of 2026-10-11 its docs list Spring Boot 4 starters (suffix `-spring-boot4-starter`, Java 17+). The Spring integration modules are still versioned as **beta** (e.g. `1.22.0-beta32`).

The owner asked why LangChain4j isn't used.

What this project actually needs from such a library is small:
1. A chat call to Claude, with OpenAI swappable.
2. **Structured output** parsed into Java records.
3. Token-usage metadata.
4. A local ONNX **embedding** model.

The project deliberately **hand-writes the RAG pipeline** (chunking, pgvector SQL, ranking policy; ADR-0006/0007) and gives the models **no tools** (ADR-0012). Those are exactly the areas where the two libraries differ most.

## Decision
- Use **Spring AI** for chat, structured output and embeddings.
- **Don't mix both libraries** in the application.
- Business code depends on our own interfaces (`ReviewGenerator`, `ContextRanker`). Spring AI types appear only inside the `ai/` package adapters, so a later switch would stay local to that package.

Why Spring AI fits this project better:
- **Boot-native:** the same auto-configuration, `application.yml` properties, Micrometer observability and release train as the rest of the stack.
- **One less framework to learn.** The project already teaches Spring, Kafka, pgvector and Jev.
- It **covers every need above:** `ChatClient` with `.entity(SomeRecord.class)`, usage metadata in the response, and the Transformers (ONNX) embedding model.

## Alternatives considered
- **LangChain4j:** a genuinely good choice, described fairly here:
  - **AI Services** let you declare an `@AiService` interface with `@SystemMessage` / `@UserMessage` methods that return records. It is very concise.
  - It has richer **RAG building blocks**: document splitters, content retrievers, query transformers, and a `ScoringModel` re-ranking abstraction (Jev could be plugged in as a custom scorer).
  - It has **tools/agents** and **chat memory**.
  - It works outside Spring (Quarkus, Helidon, plain Java), and it appears in Java GenAI job postings too.
  - **Not chosen** because its biggest strengths (the RAG pipeline components, agents/tools and memory) are deliberately unused here, its Spring Boot 4 integration is still beta-versioned, and it would add a second set of conventions next to Spring's.
- **Both libraries:** two abstractions for the same job, which is confusing to build and to explain.
- **The raw Anthropic HTTP API or the official SDK:** maximum control, but structured-output plumbing, retries and switching providers would all fall on us.

## Concept map (for interviews at LangChain4j shops)
| Spring AI | LangChain4j |
|---|---|
| `ChatClient` / `ChatModel` | `ChatModel` / `@AiService` interface |
| `.entity(MyRecord.class)` | AI Service method returning `MyRecord` |
| `EmbeddingModel` | `EmbeddingModel` |
| `VectorStore` (e.g. `PgVectorStore`) | `EmbeddingStore` (e.g. `PgVectorEmbeddingStore`) |
| Advisors (e.g. RAG advisor) | `RetrievalAugmentor`, `ContentRetriever` |
| Tool calling (`@Tool`) | Tools (`@Tool`) |

(We hand-write vector search and use no tools, so only the first three rows matter in this codebase.)

## Consequences
- **Good:** a consistent Spring stack, provider swaps by config, and one library to master.
- **Bad / costs:** we miss LangChain4j's AI Services ergonomics. Be ready to map the concepts (table above) if asked.

## Revisit if
- Agentic tool use or a framework-provided RAG pipeline is ever adopted (each would need its own ADR).
- Spring AI blocks something LangChain4j does natively.

**Stretch idea:** implement a `LangChain4jReviewGenerator` behind the same `ReviewGenerator` interface and run it on the dev fixtures. That gives a first-hand "I've used both" answer.
