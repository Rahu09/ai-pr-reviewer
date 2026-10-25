package io.github.rahu09.prreviewer.context;

/** The text is not a diff we can trust (for example a hunk is shorter than its header says). */
public class DiffParseException extends RuntimeException {

	public DiffParseException(String message) {
		super(message);
	}
}
