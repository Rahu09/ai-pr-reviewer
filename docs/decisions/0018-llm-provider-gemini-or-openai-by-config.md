# ADR-0018: LLM provider: Gemini by default, OpenAI as the alternative, selected by configuration

- **Status:** Accepted
- **Date:** 2026-10-11
- **Chunk:** planning (applies from Chunk 3)
- **Supersedes:** the generation-provider part of [ADR-0005](0005-model-providers.md) (Claude). ADR-0005's **local ONNX embeddings** decision still stands.

## Context
The owner may use **Gemini or OpenAI keys** instead of Anthropic. The architecture already isolates the provider: business code depends on our own `ReviewGenerator` interface and Spring AI's `ChatClient` (ADR-0016). Embeddings are local (ADR-0005), and Jev is a separate API.

Facts checked on 2026-10-11 in the Spring AI and Google docs:

**Gemini**
- Spring AI 2.0.x has `spring-ai-starter-model-google-genai`, which supports the **Gemini Developer API with a plain API key** (`spring.ai.google.genai.api-key`).
- Its default model is `gemini-2.5-flash`.
- JSON responses come from `spring.ai.google.genai.chat.response-mime-type=application/json`.
- The **Gemini API has a free tier** that includes Flash and Flash-Lite models. On the free tier, **content is used to improve Google's products**; on the paid tier it isn't. Free-tier rate limits aren't published and may change.

**OpenAI**
- `spring-ai-starter-model-openai` uses `spring.ai.openai.api-key` and supports **native Structured Outputs** (`JSON_SCHEMA`, `strict`).
- The OpenAI API has **no free tier**.

**Both**
- `spring.ai.model.chat` (`google-genai`, `openai`, `none`) selects which chat model is auto-configured, so several starters can live in one app.
- **A ChatGPT or Gemini app subscription is not an API key.** API keys and billing are separate: OpenAI keys come from platform.openai.com (prepaid credit), and Gemini keys come from Google AI Studio.

## Decision
- **The provider is configuration, not code.** Chunk 3 includes **both** starters (`google-genai` and `openai`). `spring.ai.model.chat: ${LLM_PROVIDER:google-genai}` picks the active one, and switching needs no code change.
- **Default: Gemini**, using a Flash-class model on the free tier. It means $0 LLM cost during the sprints, the evaluation and the demo. Pin the exact model ID in config.
- **Alternative: OpenAI**, using a current small or mid-size GPT model with native `JSON_SCHEMA` structured output (`strict: true`). Pin its model ID too.
- **One provider-neutral class.** `SpringAiReviewGenerator` (not `ClaudeReviewGenerator`) uses `ChatClient` + `.entity(...)` into records. The Java validation from ADR-0008 absorbs differences in how strictly each provider follows the schema.
- **Every run records the provider and the actual model ID.** In the evaluation, always compare like with like: same provider and model across variants A/B/C.
- **Embeddings stay local:** select them with `spring.ai.model.embedding=transformers`. Disable any other model types the starters auto-configure (image, audio, moderation) via the `spring.ai.model.*` properties, so the app starts with **only one key** present. Confirm the exact property names on the Spring AI docs when adding the starters.
- **Env vars:** `LLM_PROVIDER`, plus `GEMINI_API_KEY` or `OPENAI_API_KEY`. Only the active provider's key is needed.
- **Free-tier data rule:** use Gemini's free tier only with **synthetic demo code** (the demo repo and fixtures). Never send private or employer code through it. Use a paid tier for that.
- **Reading older documents:** where ADR-0007, 0008, 0012, 0014, 0016 or the plan-history files say "Claude", read "the generation LLM". Their decisions are unaffected.

## Alternatives considered
- **Anthropic Claude (ADR-0005):** still possible later by adding the Anthropic starter and a value for `LLM_PROVIDER` (new ADR). It isn't the default because the owner may not have Anthropic keys.
- **Gemini only:** simplest, but keeping OpenAI as a second option costs almost nothing with Spring AI and enables a provider comparison.
- **The OpenAI starter pointed at Gemini's OpenAI-compatible endpoint:** one starter for both, but compatibility layers can lag on features such as structured output. The native, documented starter is safer.
- **LangChain4j:** see ADR-0016. It is unaffected; Spring AI covers both providers.

## Consequences
- **Good:**
  - The LLM cost can be **$0** with Gemini's free tier.
  - A clear interview point: "swap the LLM provider with one environment variable, and the validation layer keeps output safe regardless."
  - A Gemini versus OpenAI comparison becomes a cheap extra experiment.
- **Bad / costs:**
  - Two starters mean models must be selected explicitly (`spring.ai.model.*`). Otherwise there are startup failures or duplicate beans.
  - Free-tier rate limits may slow the evaluation. HTTP 429 is already treated as retryable with backoff (Chunks 9 and 13).
  - Free-tier content is used for product improvement, so it's for synthetic code only.
  - Results differ between providers and models, so reported numbers must name both.

## Revisit if
- Free-tier limits block a sprint or the evaluation (move to Gemini's paid tier or to OpenAI).
- Review quality on the fixtures is poor (try the other provider by config and record the result).
- Anthropic keys become available and you want Claude in the comparison (new ADR).
