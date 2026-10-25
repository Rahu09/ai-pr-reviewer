package io.github.rahu09.prreviewer.webhook;

/**
 * "Review this PR now", published once a delivery row is committed. Sprint 1 only: an in-process stand-in for the Kafka
 * message of Chunks 12-13, which will carry the same facts.
 */
public record ReviewStartRequested(String owner, String repo, int prNumber, String headSha, String deliveryId) {
}
