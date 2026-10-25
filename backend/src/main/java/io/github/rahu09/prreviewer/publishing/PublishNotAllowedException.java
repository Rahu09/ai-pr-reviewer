package io.github.rahu09.prreviewer.publishing;

/** The run cannot be published at all (not a PR run, or it did not succeed). The API answers 409. */
public class PublishNotAllowedException extends RuntimeException {

	public PublishNotAllowedException(String message) {
		super(message);
	}
}
