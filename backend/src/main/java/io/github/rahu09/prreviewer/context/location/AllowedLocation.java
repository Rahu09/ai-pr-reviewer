package io.github.rahu09.prreviewer.context.location;

import io.github.rahu09.prreviewer.context.Side;
import java.util.Objects;

/**
 * A line the reviewer is allowed to comment on. The model sees only the {@code id} ("L17"); Java maps it back to
 * GitHub's {@code path} / {@code line} / {@code side}, so the model never computes line numbers (ADR-0008).
 */
public record AllowedLocation(String id, String path, int line, Side side) {

	public AllowedLocation {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(side, "side");
		if (line < 1) {
			throw new IllegalArgumentException("line must be >= 1, was " + line);
		}
	}
}
