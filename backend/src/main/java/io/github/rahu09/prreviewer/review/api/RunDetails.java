package io.github.rahu09.prreviewer.review.api;

import io.github.rahu09.prreviewer.review.run.ReviewRun;
import io.github.rahu09.prreviewer.review.run.StoredFinding;
import java.util.List;

/** What {@code GET /api/runs/{id}} returns: the run and everything it found. */
public record RunDetails(ReviewRun run, List<StoredFinding> findings) {

	public RunDetails {
		findings = List.copyOf(findings);
	}
}
