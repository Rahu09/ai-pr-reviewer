package io.github.rahu09.prreviewer.webhook;

/** What the receiver did with a delivery. All but {@code PING} and {@code DUPLICATE} are stored in {@code webhook_deliveries}. */
public enum WebhookOutcome {
	/** In scope and trusted: a review was started. */
	ACCEPTED,
	/** An event type or PR action we do not act on. */
	IGNORED_EVENT,
	IGNORED_DRAFT,
	/** Not the configured repo. */
	IGNORED_REPO,
	/** A fork PR or an author outside the allowlist (ADR-0019). */
	IGNORED_UNTRUSTED,
	/** Today's review cap was reached (ADR-0019). */
	SKIPPED_DAILY_CAP,
	/** Answered, not stored. */
	PING,
	/** This delivery id was already handled (GitHub redelivery): nothing was done. */
	DUPLICATE
}
