package io.github.rahu09.prreviewer.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** An existing review on a PR: only the id and body are needed, to find our hidden marker (Chunk 7). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestReview(long id, String body) {

	public PullRequestReview {
		body = body == null ? "" : body;
	}
}
