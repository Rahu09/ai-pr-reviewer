# Modern Java style guide (Java 25 LTS)

This is a living guide. The decision behind it is [ADR-0013](decisions/0013-java-25-modern-java-style.md). Every example here is a piece this project will actually need.

**Golden rule:** modern features should make the code *clearer*. If a stream or pattern makes something harder to read, use the plain version and write a one-line comment explaining why.

**Never:** preview features (`--enable-preview`), Lombok (records replace it), JPA entities, `.parallel()` streams, or mutable DTOs with setters.

---

## 1. Records for all data

Use records for domain objects, API DTOs, GitHub/Jev/LLM payloads, Kafka events and configuration. Validate in the **compact constructor**, and make collections immutable with `List.copyOf` (it also rejects `null` elements).

```java
public record AllowedLocation(String id, String path, int line, Side side) {
    public AllowedLocation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
        if (line < 1) throw new IllegalArgumentException("line must be >= 1, was " + line);
    }
}

public record FileDiff(String path, String oldPath, ChangeType changeType, List<Hunk> hunks) {
    public FileDiff {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(changeType, "changeType");
        hunks = List.copyOf(hunks);               // immutable copy
    }
    public boolean isJava() { return path.endsWith(".java"); }
}

// Kafka event (Chunk 12): a record is the message schema
public record ReviewRequested(
        int schemaVersion, UUID eventId, UUID requestId, String deliveryId,
        String repo, int prNumber, String headSha, String configVersion, Instant requestedAt) {
    public String partitionKey() { return repo + "#" + prNumber; }
}
```

Use **enums** for simple closed constants with no per-case data: `Category`, `Side`, `ChangeType`, `RequestStatus`.

## 2. Sealed types + exhaustive `switch` for closed outcomes

When something can be "one of N shapes, each with different data", use a **sealed interface with record implementations**. A `switch` over it needs **no `default`**, so if a case is added later, every unhandled `switch` becomes a compile error. That is exactly what you want.

```java
public sealed interface DiffLine {
    record Added(int newLine, String text) implements DiffLine {}
    record Removed(int oldLine, String text) implements DiffLine {}
    record Context(int oldLine, int newLine, String text) implements DiffLine {}
}

public sealed interface ValidationResult {
    record Publishable(CandidateFinding finding, AllowedLocation location) implements ValidationResult {}
    record SummaryOnly(CandidateFinding finding, String reason) implements ValidationResult {}
    record Suppressed(CandidateFinding finding, String reason) implements ValidationResult {}
}

// Record patterns + unnamed variables (_) for parts we don't need
// (assumes: import <your.package>.publishing.ValidationResult.*;)
String describe(ValidationResult result) {
    return switch (result) {
        case Publishable(_, var loc)   -> "inline at %s:%d".formatted(loc.path(), loc.line());
        case SummaryOnly(_, var why)   -> "summary only: " + why;
        case Suppressed(_, var why)    -> "suppressed: " + why;
    };
}
```

Where the project uses them:

| Sealed type | Cases | Chunk |
|---|---|---|
| `DiffLine` | Added, Removed, Context | 4 |
| `ValidationResult` | Publishable, SummaryOnly, Suppressed | 4 |
| `PublishOutcome` | Posted, AlreadyPosted, Superseded | 7 |
| `RankingOutcome` | Ranked(passages, decisions), FellBack(passages, reason) | 9 |
| `ReviewException` | RetryableReviewException, PermanentReviewException | 13 |

Sealed exceptions decide Kafka retry behaviour (Chunk 13):

```java
public sealed abstract class ReviewException extends RuntimeException
        permits RetryableReviewException, PermanentReviewException {
    protected ReviewException(String message, Throwable cause) { super(message, cause); }
}

static ReviewException classify(RestClientResponseException e) {
    int status = e.getStatusCode().value();
    return switch (status) {
        case 429, 500, 502, 503, 504, 529 -> new RetryableReviewException("upstream " + status, e);
        default                           -> new PermanentReviewException("upstream " + status, e);
    };
}
```

## 3. Streams for collection transformations

**Default to streams** when turning one collection into another (filter, map, group, sort, top-k, dedupe). End with `Stream.toList()`, which returns an unmodifiable list.

```java
// Group publishable findings by file (Chunk 7: summary formatting)
Map<String, List<Publishable>> byFile = results.stream()
        .filter(Publishable.class::isInstance)
        .map(Publishable.class::cast)
        .collect(Collectors.groupingBy(p -> p.location().path()));

// Top-k ranking policy (Chunks 8–9)
List<ScoredPassage> kept = scored.stream()
        .filter(p -> p.level() >= 2)
        .sorted(Comparator.comparingDouble(ScoredPassage::expectedScore).reversed()
                .thenComparing(Comparator.comparingDouble(ScoredPassage::similarity).reversed()))
        .limit(3)
        .toList();

// Dedupe: same location + category → keep the first (Chunk 4)
List<CandidateFinding> unique = List.copyOf(findings.stream()
        .collect(Collectors.toMap(
                f -> f.locationId() + "|" + f.category(),
                Function.identity(),
                (first, _) -> first,
                LinkedHashMap::new))
        .values());
```

### Stream Gatherers (Java 24+), for I/O with bounded concurrency

```java
// Score up to 8 passages with Jev: at most 4 calls in flight, on virtual threads, results kept in order (Chunk 9)
List<JevScore> scores = candidates.stream()
        .gather(Gatherers.mapConcurrent(4, jevClient::score))
        .toList();

// Embed guideline chunks in batches of 16 (Chunk 8)
chunks.stream()
        .gather(Gatherers.windowFixed(16))
        .forEach(batch -> repository.saveAll(embedder.embed(batch)));
```

### When **not** to use streams
- **Stateful parsing:** `DiffParser` walks the lines while tracking the current file, the current hunk and the old/new line counters. A `for` loop over `diff.lines().toList()` with a `switch` on the first character is clearer than a stream with mutable captured state.
- **Several early exits or checked exceptions:** a loop reads better.
- **Never `.parallel()`:** this work is I/O-bound. Use virtual threads or `mapConcurrent`.
- **No side effects inside `map`/`filter`.** Side effects go only in a terminal `forEach`.

## 4. `Optional`: only as a return type

```java
Optional<ReviewRequest> findById(UUID id);                    // repository lookup

var request = requests.findById(event.requestId())
        .orElseThrow(() -> new PermanentReviewException("unknown request " + event.requestId(), null));
```

Never use `Optional` for fields, parameters or collection elements. Never call `.get()` without a check; use `orElseThrow`, `map` or `ifPresent`.

## 5. Text blocks for SQL, JSON fixtures and prompt fragments

`JdbcClient` maps rows straight into records (snake_case columns → camelCase components).

```java
List<OutboxEvent> batch = jdbc.sql("""
        SELECT id, topic, msg_key, payload
        FROM outbox_events
        WHERE published_at IS NULL
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """)
        .param("limit", batchSize)
        .query(OutboxEvent.class)
        .list();
```

## 6. Configuration as records

```java
@ConfigurationProperties("reviewer.limits")
public record ReviewLimits(
        @DefaultValue("10")  int maxJavaFiles,
        @DefaultValue("400") int maxChangedLines,
        @DefaultValue("5")   int maxInlineFindings,
        @DefaultValue("50")  int maxReviewsPerDay) {}
```

Enable this with `@ConfigurationPropertiesScan` on the application class. The record is immutable and validated at startup.

## 7. Virtual threads

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Request handling and blocking calls (GitHub, the LLM API, Jev, JDBC) run on cheap virtual threads. Don't pool virtual threads. Since Java 24, `synchronized` no longer pins them, but keep `synchronized` blocks short anyway.

## 8. Small modern touches

| Feature | Use |
|---|---|
| `var` | Locals whose type is obvious from the right-hand side. Not in public signatures |
| Pattern `instanceof` | `if (ex instanceof RestClientResponseException rre && rre.getStatusCode().is5xxServerError())` |
| Unnamed `_` | Unused pattern parts, lambda parameters and catch variables |
| Sequenced collections | `list.getFirst()`, `list.getLast()`, `list.reversed()` |
| `String.formatted` | `"run %s for %s".formatted(runId, sha)` |
| `List.of` / `Map.of` / `copyOf` | Immutable collections everywhere |
| Switch expressions | Always the arrow form (`->`), never fall-through `case:` |
| Lenient at boundaries, strict inside | DTOs that receive **LLM or webhook data** use `String` for enum-like fields, and the validator converts them to enums or sealed types (unknown values are suppressed with a reason). Domain records stay strict |
| Jackson 3 | `tools.jackson.*` packages (annotations stay `com.fasterxml.jackson.annotation`). Inject Boot's `JsonMapper`; don't `new` Jackson 2 mappers (pitfalls §0) |

## 9. Java 25 features we deliberately don't use (yet)

| Feature | Why not |
|---|---|
| Structured concurrency | Still a preview in Java 25 |
| Primitive types in patterns | Preview |
| Scoped values (final in 25) | SLF4J's MDC is enough for log correlation (runId, requestId). Possible stretch item |
| Module import declarations, compact source files | Not useful in a Spring Boot app |

## 10. Feature map by chunk

| Chunk | Modern-Java focus |
|---|---|
| 1 | Java 25 build, `@ConfigurationProperties` records, virtual threads on |
| 3 | Records + enums for findings; Spring AI maps JSON → records |
| 4 | Sealed `DiffLine` / `ValidationResult`, pattern `switch`, streams for dedupe/caps (★ the parser uses a loop on purpose) |
| 5 | Text-block SQL, `JdbcClient` → records, `Optional` lookups |
| 6 | Records for GitHub DTOs, pattern `instanceof` for HTTP errors |
| 7 | Sealed `PublishOutcome`, `groupingBy` for the summary |
| 8 | Streams for top-k, `Gatherers.windowFixed` for embedding batches |
| 9 | `Gatherers.mapConcurrent` for Jev calls, sealed `RankingOutcome` |
| 11 | Records for webhook payloads, `switch` on event/action |
| 12 | Event records, text-block outbox SQL |
| 13 | Sealed exception hierarchy + `switch` classification |
