# ADR-0013: Java 25 LTS and a modern-Java style (records, sealed types, pattern matching, streams)

- **Status:** Accepted
- **Date:** 2026-10-04
- **Chunk:** planning (applies from Chunk 1)
- **Supersedes:** the "Language: Java 21" line of [ADR-0004](0004-core-java-stack.md). The rest of ADR-0004 still stands.

## Context
The owner asked for the latest Java design: records, streams and similar features. Many Java codebases (and candidates) still write Java 8-style code with mutable POJOs, `instanceof` chains, null checks and getter/setter boilerplate. Showing idiomatic modern Java is a clear interview signal.

- **Java 25** (September 2025) is the newest **LTS** release. Spring Boot 4 / Spring Framework 7 support it.
- Newer non-LTS releases exist but only get about six months of updates.
- Features that are final by Java 25 and useful here:
  - records (16)
  - sealed types (17)
  - pattern matching for `switch` and record patterns (21)
  - virtual threads and sequenced collections (21)
  - unnamed variables `_` (22)
  - **Stream Gatherers** (24)
  - virtual threads no longer pinned by `synchronized` (24)

## Decision
- Target **Java 25 LTS** (`<java.version>25</java.version>`). Use **Eclipse Temurin 25**: the JDK locally and in CI, the JRE in the production image.
- **No preview features.** `--enable-preview` is not allowed, because preview APIs can change and would complicate the build, IDE, CI and Docker setup.
- Follow [docs/java-style-guide.md](../java-style-guide.md). In short:
  - **Records** for all data: domain objects, DTOs, Kafka events and `@ConfigurationProperties`. Use compact constructors for validation and `List.copyOf` for immutability.
  - **Sealed interfaces + records** for closed sets of outcomes (diff lines, validation results, ranking outcomes, retryable versus permanent errors), handled with **exhaustive `switch` and record patterns** and no `default` branch.
  - **Streams** for collection transformations: `filter`/`map`/`groupingBy`/`toMap`/top-k/`toList()`. Use **Gatherers** (`mapConcurrent`, `windowFixed`) where they fit.
  - `Optional` only as a return type, text blocks for SQL, virtual threads switched on, `_` for unused variables, and sequenced-collection methods.
- **Readability beats cleverness.** A plain loop is allowed where it is clearer, for example a stateful parser. Add a one-line comment saying why.

## Alternatives considered
- **Stay on Java 21:** still the most common in production, and records, sealed types, pattern matching and virtual threads are all available. It would lose Stream Gatherers, unnamed variables and the `synchronized` pinning fix, and it weakens the "latest LTS" story. Moving back is cheap if needed.
- **The latest non-LTS release:** short support window and churn for no real gain.
- **Java 25 with preview features** (structured concurrency, primitive patterns): unstable APIs plus extra build flags everywhere.

## Consequences
- **Good:**
  - A concise, immutable domain model.
  - The compiler checks exhaustiveness, so adding a new outcome type forces every `switch` to handle it.
  - Plenty of modern-Java talking points for interviews.
- **Bad / costs:**
  - Some employers run Java 11/17, so be ready to explain the older equivalents.
  - If a dependency lags behind Java 25, fall back to 21 (new ADR).

## Revisit if
A required dependency (Spring AI, the ONNX runtime, Testcontainers or Spring Kafka) fails on Java 25.
