package io.github.rahu09.prreviewer.context;

import java.util.Objects;

/** One line inside a hunk. Sealed so every {@code switch} over lines must handle all three kinds. */
public sealed interface DiffLine {

	String text();

	/** A line that exists only in the new file. {@code newLine} is its 1-based number there. */
	record Added(int newLine, String text) implements DiffLine {
		public Added {
			Objects.requireNonNull(text, "text");
			if (newLine < 1) {
				throw new IllegalArgumentException("newLine must be >= 1, was " + newLine);
			}
		}
	}

	/** A line that exists only in the old file. */
	record Removed(int oldLine, String text) implements DiffLine {
		public Removed {
			Objects.requireNonNull(text, "text");
			if (oldLine < 1) {
				throw new IllegalArgumentException("oldLine must be >= 1, was " + oldLine);
			}
		}
	}

	/** An unchanged line shown for context, present in both files. */
	record Context(int oldLine, int newLine, String text) implements DiffLine {
		public Context {
			Objects.requireNonNull(text, "text");
			if (oldLine < 1 || newLine < 1) {
				throw new IllegalArgumentException("line numbers must be >= 1");
			}
		}
	}
}
