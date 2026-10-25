package io.github.rahu09.prreviewer.review.run;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {

	public RunNotFoundException(UUID id) {
		super("No review run " + id);
	}
}
