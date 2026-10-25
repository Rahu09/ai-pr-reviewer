package io.github.rahu09.prreviewer.publishing;

/**
 * What publishing a run did. Sealed, so a {@code switch} must handle every case: a result type instead of booleans
 * and exceptions for outcomes that are normal, not errors.
 */
public sealed interface PublishOutcome {

	/** A new review was created. {@code summaryOnly} is true when GitHub refused an inline location and we re-posted without inline comments. */
	record Posted(long reviewId, int inlineComments, boolean summaryOnly) implements PublishOutcome {
	}

	/** A review for this commit and config already exists (a repeat call, or a lost response): nothing new was posted. */
	record AlreadyPosted(long reviewId) implements PublishOutcome {
	}

	/** The PR closed or got a new head commit, so these findings no longer describe it: nothing was posted. */
	record Superseded(String reason) implements PublishOutcome {
	}
}
