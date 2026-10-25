package io.github.rahu09.prreviewer.publishing;

import io.github.rahu09.prreviewer.context.budget.SkipReason;
import io.github.rahu09.prreviewer.context.budget.SkippedFile;
import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.model.FindingStatus;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.model.RunStatus;
import io.github.rahu09.prreviewer.review.run.ReviewRun;
import io.github.rahu09.prreviewer.review.run.StoredFinding;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Builders for stored runs and findings, so publisher tests read as scenarios. */
final class StoredRuns {

	static final String HEAD = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
	static final ReviewConfig CONFIG = new ReviewConfig(10, 400, 5, 60_000);

	private StoredRuns() {
	}

	static ReviewRun succeededPrRun(UUID id) {
		return run(id, RunSource.PR, RunStatus.SUCCEEDED, null);
	}

	static ReviewRun run(UUID id, RunSource source, RunStatus status, Long githubReviewId) {
		return new ReviewRun(id, null, source, "Rahu09/order-service-demo", 1, "basesha", HEAD, CONFIG, "fake", "fake-model", "review-v2",
				status, null, null, List.of("src/OrderController.java", "src/OrderService.java"),
				List.of(new SkippedFile("README.md", SkipReason.NOT_JAVA, "")), 12, 100, 10, 50L, 80L,
				Instant.parse("2026-10-25T10:00:00Z"), Instant.parse("2026-10-25T10:00:01Z"), githubReviewId, null, false);
	}

	static StoredFinding inline(UUID runId, int position, String path, int line, String title) {
		return new StoredFinding(UUID.randomUUID(), runId, position, "L" + position, path, line, "RIGHT", "GUIDELINE", title,
				"Explanation of " + title, "Fix it", "Test it", FindingStatus.PUBLISHABLE, null);
	}

	static StoredFinding summaryOnly(UUID runId, int position, String title) {
		return new StoredFinding(UUID.randomUUID(), runId, position, "", null, null, null, "CORRECTNESS", title,
				"Explanation of " + title, "", "", FindingStatus.SUMMARY_ONLY, "no location given");
	}

	static StoredFinding suppressed(UUID runId, int position) {
		return new StoredFinding(UUID.randomUUID(), runId, position, "L999", null, null, null, "security", "Invented", "x", "", "",
				FindingStatus.SUPPRESSED, "unknown location L999");
	}
}
