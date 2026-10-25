package io.github.rahu09.prreviewer.context.budget;

import java.util.Objects;

/** A file left out of the review, with the reason shown in the summary ("what was skipped", PLAN §2). */
public record SkippedFile(String path, SkipReason reason, String detail) {

	public SkippedFile {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(detail, "detail");
	}
}
