package io.github.rahu09.prreviewer.review.run;

import io.github.rahu09.prreviewer.review.model.FindingStatus;
import java.util.UUID;

/** One row of {@code review_findings}. Suppressed findings are stored too, with the reason they were rejected. */
public record StoredFinding(
		UUID id,
		UUID runId,
		int position,
		String locationId,
		String filePath,
		Integer line,
		String side,
		String category,
		String title,
		String explanation,
		String suggestedFix,
		String suggestedTest,
		FindingStatus status,
		String reason) {
}
