package io.github.rahu09.prreviewer.github;

/** A GitHub call failed. {@code status} is the HTTP status, or 0 when there was no response (config, network). */
public class GitHubException extends RuntimeException {

	private final int status;

	public GitHubException(String message, int status, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	public int status() {
		return status;
	}
}
