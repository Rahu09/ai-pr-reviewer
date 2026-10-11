# ADR-0010: GitHub access — fine-grained PAT first, GitHub App for automation

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning (PAT in Chunks 6–7, App in Chunk 11)
- **Supersedes:** —

## Context
The reviewer needs to read PRs (metadata, diff, file contents) and post reviews. A **GitHub App** is the right final design: it has its own bot identity, fine-grained installation permissions, short-lived tokens and built-in webhooks. But App authentication (an RS256 JWT exchanged for installation tokens) is extra complexity at the stage where the focus should be the review engine.

## Decision
- Hide authentication behind a `GitHubTokenProvider` interface.
- **Chunks 6–7:** `PatTokenProvider` with a **fine-grained PAT** scoped to the demo repo only. It starts read-only (Contents, Pull requests), and Pull requests write access is added in Chunk 7.
- **Chunk 11:** `AppInstallationTokenProvider` uses a JWT signed with the App's private key, exchanges it for an installation access token, and caches the token until shortly before expiry. Once it works, the PAT is revoked.
- Call the GitHub REST API directly with Spring `RestClient` (`Accept: application/vnd.github.diff` for diffs, the pulls/reviews endpoints for publishing).
- In local development, use **smee.io** to forward webhooks to localhost.

## Alternatives considered
- **GitHub App from day one:** correct, but it front-loads authentication complexity before any review value exists.
- **PAT forever:** comments would appear as the owner's personal account rather than a bot, it wouldn't be how a real product works, and it is a weaker interview story.
- **The `hub4j/github-api` library:** convenient, but it hides the HTTP details (diff media type, review payload shape) that should be understood. It remains a fine option later.
- **ngrok or cloudflared instead of smee:** either works. Smee is what GitHub's docs suggest and needs no account.

## Consequences
- **Good:** fast early progress, a clean upgrade path through the interface, and a nice story ("swapped auth strategy without touching the review code").
- **Bad / costs:** a small amount of throwaway configuration (the PAT). The PAT is a long-lived secret, so it must stay in `.env`, be scoped to one repo, and be revoked after Chunk 11.

## Revisit if
The GitHub App setup is blocked for any reason. Then continue with the PAT and record that in a new ADR.
