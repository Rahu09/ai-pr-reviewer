package io.github.rahu09.prreviewer.review;

/** Turns a change into candidate findings. The only place the engine talks to an LLM (ADR-0016, ADR-0018). */
public interface ReviewGenerator {

	/** @throws ReviewGenerationException if the provider fails or keeps returning malformed output */
	GenerationResult generate(String diff);
}
