# Known pitfalls and fixes

These come from a **pre-build dry run** of the whole plan (2026-10-11), and the file keeps growing as you build.
**Rule (CLAUDE.md):** read the section for your chunk *before* starting it. When you hit a new problem, add it here as **symptom → cause → fix**.

Sections: [0 Versions](#0-version-reality-check-read-first) · [1 Windows](#1-windows-workstation) · [2 Git & CI](#2-git-and-ci) · [3 LLM](#3-llm-chunks-34) · [4 Fixtures](#4-fixtures-chunks-2-4-10) · [5 Persistence](#5-persistence-chunk-5) · [6 GitHub](#6-github-chunks-6-7-11) · [7 RAG](#7-rag-chunk-8) · [8 Evaluation](#8-evaluation-chunk-10) · [9 Kafka](#9-kafka-chunks-1213) · [10 Deployment](#10-deployment-chunk-14)

---

## 0. Version reality check (read first)

This stack is **newer than most AI assistants' training data**. Code written "from memory" will often use Spring Boot 3 or Jackson 2 APIs and fail to compile.

| Library | What changed | Do this |
|---|---|---|
| **Spring Boot 4** | Modular starters: `spring-boot-starter-web` is now `spring-boot-starter-webmvc` (the old name is deprecated). **Flyway needs `spring-boot-starter-flyway`**, because the bare `flyway-core` no longer auto-configures. There are per-technology test starters (`spring-boot-starter-<tech>-test`). **`@MockBean`/`@SpyBean` were removed**, so use `@MockitoBean`/`@MockitoSpyBean`. `@SpringBootTest` no longer provides `TestRestTemplate`, so prefer **`RestTestClient`** (`@AutoConfigureRestTestClient`) | Generate the skeleton at **start.spring.io** (it picks the right starters) rather than writing the pom from memory |
| **Jackson 3** (Boot 4 default) | Packages moved from `com.fasterxml.jackson.*` to **`tools.jackson.*`**. Annotations stay in `com.fasterxml.jackson.annotation` | Inject Boot's mapper (`JsonMapper`); don't create Jackson 2 `ObjectMapper`s |
| **Testcontainers 2** | Artifacts are now `testcontainers-<module>` (e.g. `testcontainers-postgresql`), and some packages moved | Use the versions Boot manages and fix imports from the actual jar. `pgvector/pgvector` works directly as a `PostgreSQLContainer` image |
| **Spring AI 2.0** | Starters are `spring-ai-starter-model-*`; each model type is selected by `spring.ai.model.chat`, `spring.ai.model.embedding`, … | See §3 and ADR-0018 |
| **Spring Kafka 4** | Its JSON (de)serializers are tied to the Jackson version | We avoid them: String payloads plus our own `JsonMapper` (§9) |
| **Java 25** | JNI libraries (the ONNX runtime) print "restricted method" warnings | Add `--enable-native-access=ALL-UNNAMED` (Chunk 8, Dockerfile) |

**When the compiler rejects an API, check the reference docs or the dependency's sources, not memory, and add the finding to this file.**

## 1. Windows workstation

| Symptom | Cause → fix |
|---|---|
| `java -version` or `./mvnw -v` shows an old JDK | An old `JAVA_HOME`/`PATH` → set `JAVA_HOME` to Temurin 25 and put `%JAVA_HOME%\bin` first. In IntelliJ, set Project SDK = 25 |
| `python` opens the Microsoft Store | A Windows app alias → use `py -3.12`, or turn off *App execution aliases* for python |
| `./mvnw` not recognised | Wrong shell → Git Bash: `./mvnw`; PowerShell: `.\mvnw.cmd` |
| "port is already allocated" | Your other projects in `E:\production` (Postgres/Kafka setups) → this project uses host ports **Postgres 5433, Kafka 9094, Kafka UI 8081, app 8080**. Stop other compose stacks |
| App can't find `GEMINI_API_KEY` etc. | **Spring Boot doesn't read `.env` by itself** → in `application.yml`: `spring.config.import: "optional:file:.env[.properties],optional:file:../.env[.properties]"` (this works whether you run from the repo root or `backend/`) |
| Path in `.env` "not found" | `.env` is read as a `.properties` file, where `\` is an escape character → use **forward slashes** (`E:/production/secrets/app-pkcs8.pem`) and no quotes |
| `UnsatisfiedLinkError` from onnxruntime (Chunk 8) | Missing Microsoft Visual C++ runtime → install the latest **VC++ Redistributable (x64)** |
| Testcontainers can't reach Docker or reports an API-version error | Docker Desktop isn't running, or Testcontainers is too old for the Docker engine → start Docker and use Boot's latest managed Testcontainers |
| Containers get killed or tests hang in Sprint 3 | WSL 2 memory too low → give Docker ≥ 6 GB in `%UserProfile%\.wslconfig` |
| `docker compose up` works but the app says password authentication failed after you changed `POSTGRES_PASSWORD` | The Postgres image applies the password only when the data volume is first created → `docker compose down -v` (deletes only local dev data) and start again |
| A long Git Bash heredoc containing Java text blocks fails with `unexpected EOF while looking for matching` | Bash parses the whole command first → create such files with the editor/Write tool, not a shell heredoc |
| `java -version` still shows 18 after setting the user `JAVA_HOME` | The machine `Path` lists `C:\Program Files\Common Files\Oracle\Java\javapath` before user entries → remove it from the system `Path` (admin) or rely on `JAVA_HOME` (`mvnw`, IntelliJ use it) |
| Constant permission prompts in Claude Code | Choose **Always allow** for `./mvnw`, `docker compose`, `git status/diff/add/commit`. **Never** for `git push` |

## 2. Git and CI

| Symptom | Cause → fix |
|---|---|
| CI: `./mvnw: Permission denied` | Windows doesn't keep the executable bit → run once: `git update-index --chmod=+x backend/mvnw`, then commit |
| Scripts fail in Docker/CI with `\r` errors | CRLF line endings → `.gitattributes` from Chunk 0 (`* text=auto eol=lf`) |
| CI fails at startup: "missing API key" | Spring AI tries to configure the LLM → the **test profile** sets `spring.ai.model.chat=none` and provides a stub `ReviewGenerator`. **CI never has LLM keys and never calls a real LLM.** Real-LLM checks are manual dry runs |
| CI downloads the embedding model on every run (Chunk 8) | Only the RAG integration tests should load ONNX (other tests: `spring.ai.model.embedding=none`). Cache the model directory with `actions/cache` |

## 3. LLM (Chunks 3–4)

| Symptom | Cause → fix |
|---|---|
| One odd value (e.g. `"category":"SECURITY"`) makes the whole response fail to parse | The DTO is too strict → **LLM-facing records use `String`** for enum-like fields (`category`, `locationId`). `FindingValidator` maps them to enums and suppresses unknown values with a reason. *Parse leniently, validate strictly* |
| You need both the parsed findings and token usage | `.call().entity(...)` drops the metadata → use **`.call().responseEntity(CandidateFindings.class)`**, which gives the `ChatResponse` (usage) plus the entity |
| `IllegalArgumentException`/odd output from the prompt template | Spring AI templates treat `{…}` as variables → insert diff and evidence as **template variables** (their values aren't parsed). If the template text itself needs literal JSON braces, configure `StTemplateRenderer` with `<` `>` delimiters |
| OpenAI rejects the JSON schema (strict mode) | Strict mode needs every property `required`, with nullable types for optional ones → adjust the record or schema, or fall back to `JSON_OBJECT` and rely on validation |
| HTTP 429 from Gemini (free tier) | Rate limit → retry with backoff (already planned), throttle evaluation runs, and don't loop over fixtures in parallel |
| 502 from the dry run; the log says `404 ... models/gemini-2.5-flash is no longer available to new users` | Google retires model IDs, and new keys can't use old ones → list what your key can use (`GET https://generativelanguage.googleapis.com/v1beta/models` with header `x-goog-api-key`, keep the key out of chat) and pin a current Flash ID in `application.yml` (we use `gemini-3.8-flash`, ADR-0018 addendum) |
| `spring.ai.model.embedding: none` and `spring.ai.model.embedding.text: none` can't both be written as nested YAML | A key can't be both a value and a map → write these as flat dotted keys at the YAML root (see `application.yml`) |
| App fails at startup with the Gemini-only key because another provider's model type wants a key | Each starter auto-configures chat, embedding, image, audio and moderation → set `spring.ai.model.{chat,embedding,embedding.text,image,audio.speech,audio.transcription,moderation}` (verified in the 2.0.1 autoconfigure jars); `ProviderSelectionTest` proves each provider starts alone |
| Spring AI 2.0 API names differ from older blog posts | `responseEntity(Class)` returns `org.springframework.ai.chat.client.ResponseEntity` with `.entity()` and `.response()`; `Usage.getPromptTokens()` is a nullable `Integer`; a test model only needs `ChatModel.call(Prompt)` |
| Gemini reviews are slow or token-heavy | "Thinking" is on → set `spring.ai.google.genai.chat.thinking-budget` low (measure before and after) |

## 4. Fixtures (Chunks 2, 4, 10)

| Symptom | Cause → fix |
|---|---|
| DiffParser fails on a fixture but works on real PRs | The diff was hand-written with wrong hunk counts → **generate fixtures from real branches** in the demo repo: `git diff --no-color main...<branch> > change.diff`, and file contents with `git show <branch>:<path>` |
| Fixture JSON is unreadable or escaping breaks | The diff is embedded in JSON → a **fixture is a folder**: `fixture.json` (id, family, kind, expected findings and rule IDs) + `change.diff` + `files/<path>` (contents at head) |

## 5. Persistence (Chunk 5)

| Symptom | Cause → fix |
|---|---|
| `column "config" is of type jsonb but expression is of type character varying` | JDBC sends text → `CAST(:config AS jsonb)` in the SQL |
| Timestamps shift by hours | Mixed types → use `timestamptz` columns ↔ `Instant` in records |
| `uuid` comparison errors | Use `uuid` columns ↔ `UUID` |

## 6. GitHub (Chunks 6, 7, 11)

| Symptom | Cause → fix |
|---|---|
| `gh` CLI or other tools act strangely after adding the PAT | The env var was named `GITHUB_TOKEN`, which `gh` and Actions also read → name it **`GITHUB_PAT`** |
| File contents arrive as base64 JSON | Default media type → `Accept: application/vnd.github.raw` |
| Diff request refused for a huge PR | The API refuses very large diffs → record "skipped: diff too large" (our limits are far below this) |
| **422** on create-review, and nothing is posted | One inline comment isn't on a diff line, so GitHub rejects the whole review → validation prevents this. As a fallback, **re-post once as summary-only** and record it |
| Reviews appear under your own name | Expected while using the PAT (Chunks 6–7). The GitHub App (Chunk 11) posts as the bot |
| `InvalidKeySpecException` loading the App key | GitHub gives **PKCS#1** (`BEGIN RSA PRIVATE KEY`), but Java needs PKCS#8 → `openssl pkcs8 -topk8 -nocrypt -in app.pem -out app-pkcs8.pem` (`openssl` ships with Git for Windows). Keep keys in `E:\production\secrets\`, never in the repo |
| `401` exchanging the JWT | Clock drift or wrong claims → RS256, `iat` = now − 60 s, `exp` ≤ now + 10 min, `iss` = App ID. The JDK is enough (`Signature` SHA256withRSA + URL-safe Base64 without padding), with no JWT library |
| Don't know the installation ID | It's in every webhook payload: `installation.id` |
| Signature check fails for valid deliveries | The body was re-serialized → verify over the **raw bytes** (`@RequestBody byte[]`) with constant-time `MessageDigest.isEqual` |
| A `ping` delivery right after creating the App | Normal → return 200 |
| `npx` not found | smee needs **Node.js LTS**: `npx smee-client --url <channel> --target http://localhost:8080/webhooks/github` |
| Strangers' fork PRs trigger reviews | The demo repo is public → review **only same-repo branches by allowlisted authors** (ADR-0019) |
| The review starts before the delivery row exists | It started inside the transaction → start the in-process review **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`) |

## 7. RAG (Chunk 8)

| Symptom | Cause → fix |
|---|---|
| `could not determine data type` / `operator does not exist: vector <=> …` | JDBC doesn't know `vector` → pass `"[0.12,0.34,…]"` as text and `CAST(:q AS vector)`. Do the same for inserts |
| Nothing passes the similarity threshold, or everything does | A guessed threshold → MiniLM cosine scores for related text are modest, so **tune on the dev fixtures** (start around 0.3) and record the value |
| First review after startup is very slow | The model (and tokenizer native libraries) download on the first `embed()` → set `spring.ai.embedding.transformer.cache.directory`, and in production mount it as a volume |
| Embedding requests go to Gemini or OpenAI | The provider starters configured their own embedding model → `spring.ai.model.embedding=transformers` |

## 8. Evaluation (Chunk 10)

| Symptom | Cause → fix |
|---|---|
| `Connection refused` from `run_eval.py` | The app isn't running → start it first. The script calls `POST /api/dry-run/fixture/{id}` |
| 429 storms | Free-tier limits → the script **throttles** (one call every few seconds) and retries 429 with backoff |
| Python venv confusion on Windows | `py -3.12 -m venv .venv`, then `.venv\Scripts\activate`, then `pip install -r requirements.txt` |

## 9. Kafka (Chunks 12–13)

| Symptom | Cause → fix |
|---|---|
| The app on your laptop can't connect, or Kafka UI can't connect | **Listener mix-up**, the classic Kafka-in-Docker bug → define two listeners: **EXTERNAL `localhost:9094`** (the app in IntelliJ) and **INTERNAL `kafka:9092`** (Kafka UI, and the app in production), with matching `advertised.listeners` |
| Kafka UI won't start | Port 8080 is taken by the app → map Kafka UI to **8081** |
| `ClassNotFoundException`/"not in trusted packages"/type-header errors | Spring Kafka's JSON (de)serializers → **send the event as a JSON String** (`StringSerializer` + our `JsonMapper`), consume with `StringDeserializer` and map it ourselves. The DLT uses the same String serializers |
| "Kafka down" test can't reconnect after restarting the container | Testcontainers gives a restarted container a **new port** → test the relay with a `KafkaTemplate` that fails first and then succeeds. Check the real thing manually with `docker compose stop kafka` / `start kafka` |
| You can't "kill the app" inside a test | Simulate it: throw after publish but before the ack, so the message is redelivered and the marker prevents a second review. Do one **manual** kill of the real process |

## 10. Deployment (Chunk 14)

| Symptom | Cause → fix |
|---|---|
| The arm64 image build takes forever | Compiling under QEMU emulation → **build the jar in CI natively**, and keep the Dockerfile **runtime-only** (`eclipse-temurin:25-jre`, copy the jar). Then `docker buildx --platform linux/amd64,linux/arm64` only copies files (ADR-0015 addendum) |
| `invalid reference format` pushing to GHCR | Image names must be **lowercase** → `ghcr.io/<lowercase-username>/ai-pr-reviewer` |
| The server can't pull the image | New GHCR packages may be **private** → make the package public, or `docker login ghcr.io` on the server with a read-only token |
| Oracle VM: ports 80/443 unreachable | Oracle's Ubuntu images block ports with **iptables** as well as the **VCN Security List** → open 80/443 in **both**, and persist the iptables rules |
| Caddy can't get a certificate | DNS doesn't point at the VM yet, or port 80 is closed → fix the DNS and firewall first, then start Caddy |
| The app in production can't reach Kafka or Postgres | It's using laptop addresses → in production use `kafka:9092` and `postgres:5432` (service names) |
| Slow first review after every deploy | The model cache isn't persisted → mount the ONNX cache directory as a volume |
