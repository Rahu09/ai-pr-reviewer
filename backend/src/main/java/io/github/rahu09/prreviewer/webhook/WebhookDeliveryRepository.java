package io.github.rahu09.prreviewer.webhook;

import java.time.Instant;

/** Stores each webhook delivery once; GitHub delivers at least once, so the delivery id is the idempotency key. */
public interface WebhookDeliveryRepository {

	record Delivery(String deliveryId, String event, String action, String repo, Integer prNumber, String headSha,
			WebhookOutcome outcome, Instant receivedAt) {
	}

	/** @return true if this delivery id was new and is now stored, false if it was already there (nothing changed) */
	boolean recordIfNew(Delivery delivery);
}
