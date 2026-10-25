package io.github.rahu09.prreviewer.review;

import java.util.List;

/** What {@code GET /api/runs/{id}} returns: the run and everything it found. */
public record RunDetails(ReviewRun run, List<StoredFinding> findings) {

	public RunDetails {
		findings = List.copyOf(findings);
	}
}
