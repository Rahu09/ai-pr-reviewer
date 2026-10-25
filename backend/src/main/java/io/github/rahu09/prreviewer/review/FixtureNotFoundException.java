package io.github.rahu09.prreviewer.review;

public class FixtureNotFoundException extends RuntimeException {

	public FixtureNotFoundException(String id) {
		super("Fixture '%s' not found".formatted(id));
	}
}
