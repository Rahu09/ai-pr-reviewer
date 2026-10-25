package io.github.rahu09.prreviewer.review;

/** The pipeline step that broke a run, recorded so "why did this review fail?" has an answer. */
public enum FailedStage {
	FETCH, PARSING, GENERATION, VALIDATION, PERSISTENCE
}
