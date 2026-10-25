package io.github.rahu09.prreviewer.review;

/** Turns an annotated diff into candidate findings. The only place the engine talks to an LLM (ADR-0016, ADR-0018). */
public interface ReviewGenerator {

	/**
	 * @param annotatedDiff the diff with a location ID on every commentable line (see {@code AnnotatedDiff})
	 * @throws ReviewGenerationException if the provider fails or keeps returning malformed output
	 */
	GenerationResult generate(String annotatedDiff);
}
