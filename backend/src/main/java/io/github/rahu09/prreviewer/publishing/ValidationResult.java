package io.github.rahu09.prreviewer.publishing;

import io.github.rahu09.prreviewer.context.AllowedLocation;
import io.github.rahu09.prreviewer.review.CandidateFinding;
import io.github.rahu09.prreviewer.review.Category;

/**
 * What the validator decided about one candidate finding. Sealed, so any {@code switch} must handle all three
 * outcomes and adding a fourth is a compile error everywhere it matters. Nothing is deleted: suppressed findings
 * are stored with their reason (PLAN §6 rule 2).
 */
public sealed interface ValidationResult {

	CandidateFinding finding();

	/** Valid, attached to a real added line: can become an inline comment. */
	record Publishable(CandidateFinding finding, Category category, AllowedLocation location) implements ValidationResult {
	}

	/** Useful but cannot be attached to a line (or the inline cap was reached): goes in the review summary. */
	record SummaryOnly(CandidateFinding finding, Category category, String reason) implements ValidationResult {
	}

	/** Rejected, with the reason kept for debugging and evaluation. */
	record Suppressed(CandidateFinding finding, String reason) implements ValidationResult {
	}
}
