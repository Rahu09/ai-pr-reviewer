package io.github.rahu09.prreviewer.review.api;

import io.github.rahu09.prreviewer.publishing.PublishOutcome;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.AlreadyPosted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Posted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Superseded;
import io.github.rahu09.prreviewer.publishing.ReviewPublisher;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for posting a stored run to its PR (Chunk 7); the webhook calls the same publisher later.
 * Writes to GitHub, so it is internal like the dry-run API and never exposed publicly (PLAN §6 rule 12).
 */
@RestController
@RequestMapping("/api/reviews")
public class PublishController {

	private final ReviewPublisher publisher;

	public PublishController(ReviewPublisher publisher) {
		this.publisher = publisher;
	}

	/** What the caller sees; {@code outcome} is POSTED, ALREADY_POSTED or SUPERSEDED. */
	public record PublishReport(UUID runId, String outcome, Long reviewId, Integer inlineComments, Boolean summaryOnly, String reason) {

		static PublishReport from(UUID runId, PublishOutcome outcome) {
			return switch (outcome) {
				case Posted(var id, var inline, var summaryOnly) -> new PublishReport(runId, "POSTED", id, inline, summaryOnly, null);
				case AlreadyPosted(var id) -> new PublishReport(runId, "ALREADY_POSTED", id, null, null, null);
				case Superseded(var reason) -> new PublishReport(runId, "SUPERSEDED", null, null, null, reason);
			};
		}
	}

	@PostMapping("/{runId}/publish")
	public PublishReport publish(@PathVariable UUID runId) {
		return PublishReport.from(runId, publisher.publish(runId));
	}
}
