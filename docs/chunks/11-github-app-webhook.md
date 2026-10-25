# Chunk 11 — GitHub App + webhook receiver ★

- **Dates built:** 2026-10-26  · **Date studied:** —
- **Status:** Built (code and tests done; the live run needs the GitHub App created by the owner, see Next)
- **Tag:** chunk-11

## Goal
A GitHub App (own bot identity, installation tokens) and a public `POST /webhooks/github` that proves each delivery came from GitHub (HMAC over the raw bytes), ignores untrusted or out-of-scope events, deduplicates redeliveries, and starts a review in-process. Opening or updating a PR in the demo repo makes the bot post a review.

## What I built
- **App auth** (`github/`): `AppInstallationTokenProvider` signs an RS256 JWT with the JDK only (`iat` = now − 60 s, `exp` = now + 9 min, `iss` = App ID, URL-safe Base64 without padding), exchanges it for an installation token and caches it until 5 minutes before expiry. `GitHubAuthConfiguration` picks it when `GITHUB_APP_ID` is set, otherwise the PAT, so no review code changed (ADR-0010). `GitHubAppProperties`: `reviewer.github-app.*`.
- **Receiver** (`webhook/`):
  - `GitHubWebhookController` `POST /webhooks/github` verifies the signature FIRST, then checks the headers, then parses. Bad or missing signature → 401, malformed body → 400, otherwise 200 with the outcome.
  - `SignatureVerifier`: HMAC-SHA256 over the raw bytes (`@RequestBody byte[]`) compared with `MessageDigest.isEqual`. An unset secret rejects everything.
  - `WebhookPayload` (sealed: `Ping`, `PullRequestEvent`, `Unsupported`; `parse` is a `switch` on the event name) and `WebhookService` (a `switch` on the payload, then a `switch` on the action).
  - `WebhookService.classify` checks, in order: allowed repo → in-scope action (`opened`, `reopened`, `synchronize`, `ready_for_review`) → not a draft → **same-repo branch and allowlisted author** → **daily cap** (`reviewer.limits.max-reviews-per-day` against today's PR-sourced `review_runs`).
  - Every delivery except `ping` is stored in `webhook_deliveries` with its outcome: `ACCEPTED`, `IGNORED_EVENT`, `IGNORED_DRAFT`, `IGNORED_REPO`, `IGNORED_UNTRUSTED` or `SKIPPED_DAILY_CAP`.
  - `JdbcWebhookDeliveryRepository.recordIfNew`: one `INSERT ... ON CONFLICT (delivery_id) DO NOTHING`. If no row was inserted it was a redelivery → outcome `DUPLICATE`, nothing happens.
  - `InProcessReviewStarter` (**temporary**): an `AFTER_COMMIT` listener runs fetch → review → publish on a virtual thread, so a review can never start before its delivery row exists. A failure is logged and never thrown (the run row already records it).
- `V5__webhook_deliveries.sql`; `ReviewRunRepository.countStartedSince` for the cap.
- Config: `reviewer.webhook.{secret, allowed-repos, allowed-authors}` and `reviewer.github-app.*`; `.env.example` updated (names only).

## Decisions made
- No new ADR: the App (ADR-0010), the trusted-PR filter and cap (ADR-0019) and the in-process trigger (ADR-0017) are all decided. No new dependency.
- Installation ID: not threaded from each payload. The App is installed once, so the provider uses `GITHUB_APP_INSTALLATION_ID` if set, otherwise looks up `GET /app/installations` and requires exactly one. The payload still carries it (the test data does), so the plan's "from the payload" stays possible later.
- Dedupe is the database's job (primary key + `ON CONFLICT`), not an "exists" check first, so two simultaneous redeliveries cannot both start a review.
- A `ping` is answered 200 and not stored (it is not a PR delivery and there is nothing to dedupe).
- Allowlist defaults: author `Rahu09`, repo `Rahu09/order-service-demo` (yml). An EMPTY env value allows nobody, which is the safe direction. Comparisons ignore case.
- The cap reads `review_runs`, and a review's run row appears a moment after the delivery is accepted, so a burst of simultaneous deliveries could overshoot the cap by a few. Fine for a demo; Chunk 13 moves the check next to the request row.

## Problems hit and how they were solved
- `MockRestServiceServer` refuses new expectations after the first request → declare all of them up front (they are consumed in order).
- `Mac.init` throws "Empty key" for an empty secret (the unset-secret test) → the verifier rejects a blank secret before HMAC, and the test uses a literal digest instead of signing with an empty key.

## How it was verified
- 162 tests green (`./mvnw -o verify`, no network, no keys). 34 new:
  - `SignatureVerifierTest` (6): GitHub's documented test vector, valid, one-byte-changed body, wrong secret, missing, malformed and non-hex, wrong-length signatures, unset secret, secret hidden in `toString`.
  - `GitHubWebhookControllerTest` (5): **bad signature → 401 and the service is never called**, missing signature → 401 before any header check, valid PR → outcome returned, ping → 200, broken or incomplete body and missing headers → 400.
  - `WebhookServiceTest` (10): trusted PR accepted and stored, the three in-scope actions, **fork PR, deleted fork and non-allowlisted author → `IGNORED_UNTRUSTED` and no review**, case-insensitive allowlist, draft / other repo / `labeled` / `closed` ignored, **daily cap**, **same delivery id twice → one record, one review**, ping and unknown events.
  - `WebhookEndToEndTest` (3, Spring + Postgres): **a redelivery creates no second row and no second review** (the starter is notified exactly once, after commit), bad signature → 401 and no row, fork PR stored as `IGNORED_UNTRUSTED`.
  - `AppInstallationTokenProviderTest` (7, throwaway RSA key): the JWT's signature verifies with the public key and has the right claims, token cached until 5 min before expiry then refreshed, single installation found automatically, several installations need an explicit ID, 401 → clear message, PKCS#1 key refused with the fix, missing key file, non-numeric App ID.
  - `InProcessReviewStarterTest` (3): fetch happens inside the recorded run, then publish; failures are contained.
- "Done when": [x] bad signature → 401 (test) [x] redelivery creates no duplicate (test, real Postgres) [ ] a real PR event reaches the local app (live, needs the App, see Next).
- Not done from here: nothing was sent to or received from GitHub. Creating the App, the smee channel and the keys are owner steps.

## Deviations from the plan
- **The plan's recovery claim is wrong:** PLAN says a crash mid-review is recovered by "redelivering from GitHub's UI". With the dedupe the same card requires, a redelivery has the same delivery id and is ignored (`DUPLICATE`). A lost review is recovered by pushing a commit or closing and reopening the PR (a new delivery). The class comment says so; PLAN's wording on the Chunk 11 card is left for the owner to confirm. Chunk 12's outbox is the real fix.
- Migration is `V5` (not `V4`), see Chunk 7.
- Installation ID lookup instead of reading the payload (above).

## Known limits
- The in-process review is not durable (a crash loses it). Removed in Chunk 13.
- Two events for the same PR in quick succession start two reviews; the older one finds the head moved at publish time and posts nothing (`Superseded`), after having paid for an LLM call.
- `/api/**` is not authenticated: it must stay unreachable from the internet (Caddy exposes only `/webhooks/github` and health, Chunk 14).

## Next
**You (about 20 minutes). Never paste the secret or the key into chat.**
1. **smee channel:** open <https://smee.io> → *Start a new channel* → copy the channel URL.
2. **Webhook secret:** in Git Bash run `openssl rand -hex 20`, and put the output in `.env` as `GITHUB_WEBHOOK_SECRET=...`.
3. **Create the App:** GitHub → profile photo → *Settings* → *Developer settings* → *GitHub Apps* → *New GitHub App*.
   - Name: anything unique (e.g. `rahu09-pr-reviewer`); Homepage URL: your repo URL.
   - Webhook: *Active*, URL = the smee channel URL, Secret = the same value as in `.env`.
   - Repository permissions: **Pull requests: Read and write**, **Contents: Read-only**, Metadata: Read-only (automatic).
   - Subscribe to events: **Pull request**.
   - *Where can this App be installed:* **Only on this account** → *Create GitHub App*.
4. **App ID and key:** on the App's page copy the numeric **App ID** into `.env` as `GITHUB_APP_ID=...`. Under *Private keys* → *Generate a private key*; move the downloaded `.pem` to `E:\production\secrets\app.pem`, then in Git Bash:
   `openssl pkcs8 -topk8 -nocrypt -in /e/production/secrets/app.pem -out /e/production/secrets/app-pkcs8.pem`
   and set `.env`: `GITHUB_APP_PRIVATE_KEY_PATH=E:/production/secrets/app-pkcs8.pem` (forward slashes, no quotes).
5. **Install it only on the demo repo:** the App's left menu → *Install App* → *Install* next to your account → *Only select repositories* → `order-service-demo` → *Install*.
6. **Run:** terminal 1 `docker compose up -d` then `cd backend && ./mvnw spring-boot:run` (the log must say `GitHub auth: App ...`). Terminal 2 `npx smee-client --url <channel URL> --target http://localhost:8080/webhooks/github`.
7. **Live check:** on a branch of a demo PR push an empty commit (`git commit --allow-empty -m "trigger review"` then push). Expect: smee prints the delivery, the app logs `ACCEPTED` and then a review under the **bot's name** appears on the PR. In the App's *Advanced* → *Recent deliveries* the response should be 200.
8. **Redelivery check:** in *Recent deliveries* click *Redeliver* on that delivery. Expect response `{"outcome":"DUPLICATE"}` and no second review.
9. When it works, **revoke the PAT** (ADR-0010) and empty `GITHUB_PAT` in `.env`.
