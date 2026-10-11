# ADR-0004: Core stack — Java 21, Spring Boot 4, Maven, JdbcClient, Flyway, Testcontainers

- **Status:** Accepted — Java version superseded by [ADR-0013](0013-java-25-modern-java-style.md) (Java 25); everything else stands. Exact versions pinned in Chunk 1 via Addendum.
- **Date:** 2026-10-04
- **Chunk:** planning
- **Supersedes:** —

## Context
The project should showcase Java backend skills that employers look for, and it must integrate with Spring AI for model calls. It needs raw SQL features: pgvector operators and `SELECT … FOR UPDATE SKIP LOCKED`.

As of 2026-10-04, **Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x** (checked on the Spring AI getting-started page).

## Decision
| Concern | Choice |
|---|---|
| Language | **Java 21 (LTS)**: records, sealed types, pattern matching, text blocks and virtual threads, and widely used in industry |
| Framework | **Spring Boot 4.x**, in a version supported by the current Spring AI 2.0.x |
| Build | **Maven**, with the wrapper |
| DB access | **Spring `JdbcClient` + plain SQL**, no JPA/Hibernate |
| Migrations | **Flyway**. Applied migrations are never edited |
| HTTP clients | Spring **`RestClient`** |
| Tests | **JUnit 5 + AssertJ**, **Testcontainers** (real Postgres with pgvector), and **WireMock or `MockRestServiceServer`** for external HTTP |

## Alternatives considered
- **JPA/Hibernate:** pgvector queries and `SKIP LOCKED` need native SQL anyway. JPA would hide the SQL that is meant to be explained in interviews, and it brings its own learning curve (lazy loading, dirty checking).
- **Gradle:** a fine tool, but Maven is more common in Java job postings and simpler for a single module.
- **Java 25 (newest LTS):** valid, but 21 matches more production codebases and has every feature needed. Upgrading later is cheap.
- **H2 for tests:** it doesn't support pgvector and behaves differently from Postgres. Testcontainers tests against the real thing.

## Consequences
- **Good:** explicit, explainable SQL; tests that use the real database; a mainstream stack.
- **Bad / costs:** more hand-written mapping code than JPA; Docker is required for tests.

## Revisit if
Spring AI or Spring Boot compatibility changes when generating the project in Chunk 1. Record the actual versions below.

## Addendum (2026-10-11, Chunk 1)
Pinned versions (from start.spring.io's generated pom): Java 25 (OpenJDK 25.0.2 locally, Temurin 25 in CI), Spring Boot **4.0.8** (Spring Framework 7.0.x), Flyway 11.14.1, PostgreSQL JDBC driver 42.7.x, Testcontainers **2.0.5** (`testcontainers-postgresql`, `testcontainers-junit-jupiter`), Jackson 3.1.x (`tools.jackson`). Database image: `pgvector/pgvector:pg17` (compose and tests). The Spring AI BOM is added in Chunk 3; Spring AI **2.0.1** is the latest 2.0.x GA and targets Boot 4.0 (re-confirm when adding it).
Package name: `io.github.rahu09.prreviewer`. Maven coordinates: `io.github.rahu09:prreviewer`.
