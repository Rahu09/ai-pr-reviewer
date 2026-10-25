package io.github.rahu09.prreviewer.review;

import io.github.rahu09.prreviewer.context.SkippedFile;
import io.github.rahu09.prreviewer.publishing.ValidationResult;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Publishable;
import io.github.rahu09.prreviewer.publishing.ValidationResult.SummaryOnly;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Suppressed;
import java.util.List;
import java.util.UUID;

/** The JSON a dry run returns (and the Python evaluation will read later): every finding with its status and reason. */
public record ReviewReport(
		UUID runId,
		String provider,
		String model,
		String promptVersion,
		int inputTokens,
		int outputTokens,
		int allowedLocations,
		List<String> filesReviewed,
		List<SkippedFile> filesSkipped,
		List<FindingView> findings,
		FixtureMeta expected) {

	public record FindingView(
			String status,
			String reason,
			String locationId,
			String path,
			Integer line,
			String side,
			String category,
			String title,
			String explanation,
			String suggestedFix,
			String suggestedTest) {
	}

	public static ReviewReport from(ReviewOutcome outcome, FixtureMeta expected) {
		GenerationResult g = outcome.generation();
		return new ReviewReport(outcome.runId(), g.provider(), g.model(), g.promptVersion(), g.inputTokens(), g.outputTokens(),
				outcome.allowedLocations(), outcome.filesReviewed(), outcome.filesSkipped(),
				outcome.results().stream().map(ReviewReport::view).toList(), expected);
	}

	private static FindingView view(ValidationResult result) {
		return switch (result) {
			case Publishable(var f, var category, var loc) -> new FindingView("PUBLISHABLE", null, loc.id(), loc.path(),
					loc.line(), loc.side().name(), category.name(), f.title(), f.explanation(), f.suggestedFix(), f.suggestedTest());
			case SummaryOnly(var f, var category, var reason) -> new FindingView("SUMMARY_ONLY", reason, f.locationId(), null,
					null, null, category.name(), f.title(), f.explanation(), f.suggestedFix(), f.suggestedTest());
			case Suppressed(var f, var reason) -> new FindingView("SUPPRESSED", reason, f.locationId(), null,
					null, null, f.category(), f.title(), f.explanation(), f.suggestedFix(), f.suggestedTest());
		};
	}
}
