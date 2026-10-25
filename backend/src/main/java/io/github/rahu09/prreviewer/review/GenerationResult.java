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

	/** For reviews where there was nothing to send, so no model was called. */
	public static GenerationResult notCalled() {
		return new GenerationResult(new CandidateFindings(null), "none", "none", "none", 0, 0);
	}
}
