package io.github.rahu09.prreviewer.review.model;

import java.util.Arrays;
import java.util.Optional;

public enum Category {
	CORRECTNESS, GUIDELINE, TEST_SUGGESTION;

	/** Case- and separator-tolerant, because the value comes from a model. Unknown values are the caller's to reject. */
	public static Optional<Category> parse(String raw) {
		String normalized = raw == null ? "" : raw.strip().toUpperCase().replace('-', '_').replace(' ', '_');
		return Arrays.stream(values()).filter(c -> c.name().equals(normalized)).findFirst();
	}
}
