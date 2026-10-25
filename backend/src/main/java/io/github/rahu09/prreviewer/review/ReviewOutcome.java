package io.github.rahu09.prreviewer.review;

import io.github.rahu09.prreviewer.context.SkippedFile;
import io.github.rahu09.prreviewer.publishing.ValidationResult;
import java.util.List;
import java.util.UUID;

/** Everything one review produced, ready to be persisted (Chunk 5), reported (dry-run) or published (Chunk 7). */
public record ReviewOutcome(
		UUID runId,
		ReviewConfig config,
		List<String> filesReviewed,
		List<SkippedFile> filesSkipped,
		int allowedLocations,
		GenerationResult generation,
		List<ValidationResult> results) {

	public ReviewOutcome {
		filesReviewed = List.copyOf(filesReviewed);
		filesSkipped = List.copyOf(filesSkipped);
		results = List.copyOf(results);
	}
}
