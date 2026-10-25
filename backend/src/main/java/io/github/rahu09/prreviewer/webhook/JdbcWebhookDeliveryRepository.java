package io.github.rahu09.prreviewer.webhook;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWebhookDeliveryRepository implements WebhookDeliveryRepository {

	private final JdbcClient jdbc;

	public JdbcWebhookDeliveryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// One atomic statement: of two concurrent redeliveries exactly one inserts a row, so exactly one starts a review.
	@Override
	public boolean recordIfNew(Delivery d) {
		int inserted = jdbc.sql("""
				INSERT INTO webhook_deliveries (delivery_id, event, action, repo, pr_number, head_sha, outcome, received_at)
				VALUES (:id, :event, :action, :repo, :prNumber, :headSha, :outcome, :receivedAt)
				ON CONFLICT (delivery_id) DO NOTHING
				""")
				.param("id", d.deliveryId())
				.param("event", d.event())
				.param("action", d.action())
				.param("repo", d.repo())
				.param("prNumber", d.prNumber())
				.param("headSha", d.headSha())
				.param("outcome", d.outcome().name())
				.param("receivedAt", OffsetDateTime.ofInstant(d.receivedAt(), ZoneOffset.UTC))
				.update();
		return inserted == 1;
	}
}
