package io.github.rahu09.prreviewer.review;

import java.util.Objects;

/** What one model call produced, plus what is needed to trace and cost it (stored on the run from Chunk 5). */
public record GenerationResult(
		CandidateFindings findings,
		String provider,
		String model,
		String promptVersion,
		int inputTokens,
		int outputTokens) {

	public GenerationResult {
		Objects.requireNonNull(findings, "findings");
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(promptVersion, "promptVersion");
	}
}
