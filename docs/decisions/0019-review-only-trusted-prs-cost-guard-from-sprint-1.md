# ADR-0019: Review only trusted PRs, with the cost guard active from Sprint 1

- **Status:** Accepted
- **Date:** 2026-10-11
- **Chunk:** planning (built in Chunk 11, kept in Chunk 13)
- **Supersedes:** —, and extends [ADR-0012](0012-untrusted-pr-content-no-model-tools.md)

## Context
The demo repository is **public**, so anyone can fork it and open a pull request. From Chunk 11 onward, every PR event triggers an LLM review. Without a filter:
- **Cost and quota:** strangers could burn the Gemini free-tier quota or OpenAI credit.
- **Abuse surface:** strangers could feed prompt-injection text to the bot. ADR-0012 limits the damage (no tools, Java controls every action), but there's no reason to invite it.
- The daily review cap was only planned for Chunk 13 (Sprint 3), but automatic reviews start in Sprint 1.

## Decision
- **Chunk 11** reviews a PR only if **both** of these hold:
  1. Its head branch is in the **same repository** (`pull_request.head.repo.full_name == repository.full_name`), so fork PRs are ignored.
  2. Its author's login is in **`reviewer.allowed-authors`** (default: the owner's GitHub login).
- Everything else is stored in `webhook_deliveries` with outcome `IGNORED_UNTRUSTED`. No review, no LLM call.
- **The daily cap starts in Chunk 11:** before starting a review, count today's PR-sourced `review_runs`. At or above `reviewer.limits.max-reviews-per-day`, store the outcome `SKIPPED_DAILY_CAP` and stop.
- **Chunk 13** keeps both checks, moved into the Kafka consumer path.
- The README states it plainly: "reviews PRs from allowlisted authors on same-repo branches".

## Alternatives considered
- **Review every PR:** realistic for a product, but risky for a self-funded public demo.
- **Make the demo repo private:** hides the portfolio evidence.
- **Rely on GitHub settings for fork PRs:** those apply to Actions workflows, not to webhooks sent to an App.

## Consequences
- **Good:** bounded cost from Day 2, and strangers can't drive the bot.
- **Bad / costs:** only your own PRs get reviewed in a demo, so to show it to someone, open the PR yourself (or add their login to the allowlist).

## Revisit if
The project ever serves real users, which would need per-installation quotas and a proper abuse policy (new ADR).
