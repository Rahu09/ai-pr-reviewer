package io.github.rahu09.prreviewer.webhook;

/**
 * "Review this PR now", published once a delivery row is committed. Sprint 1 only: an in-process stand-in for the
 * {@code review_requests} row that the Postgres queue of Chunks 12-13 will carry (deleted with the worker, ADR-0021).
 */
public record ReviewStartRequested(String owner, String repo, int prNumber, String headSha, String deliveryId) {
}
