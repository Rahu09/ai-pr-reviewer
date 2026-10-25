package io.github.rahu09.prreviewer.review.run;

import io.github.rahu09.prreviewer.context.budget.SkippedFile;
import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.model.FailedStage;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.model.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of {@code review_runs}: enough to reproduce and explain a review (which commit, which limits, which model
 * and prompt, what was skipped, what it cost). Nullable columns are boxed because a failed run has no tokens, etc.
 */
public record ReviewRun(
		UUID id,
		UUID requestId,
		RunSource source,
		String repo,
		int prNumber,
		String baseSha,
		String headSha,
		ReviewConfig config,
		String provider,
		String model,
		String promptVersion,
		RunStatus status,
		FailedStage failedStage,
		String errorMessage,
		List<String> filesReviewed,
		List<SkippedFile> filesSkipped,
		Integer allowedLocations,
		Integer inputTokens,
		Integer outputTokens,
		Long generationMs,
		Long latencyMs,
		Instant startedAt,
		Instant finishedAt) {

	public ReviewRun {
		filesReviewed = List.copyOf(filesReviewed);
		filesSkipped = List.copyOf(filesSkipped);
	}
}
