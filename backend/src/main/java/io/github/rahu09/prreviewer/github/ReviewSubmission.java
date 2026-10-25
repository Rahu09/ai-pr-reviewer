package io.github.rahu09.prreviewer.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Body of GitHub's "create a review" call. {@code event} is always {@code COMMENT}: the bot is advisory and never
 * approves or requests changes. {@code commitId} pins the review to the exact commit that was reviewed.
 */
public record ReviewSubmission(
		@JsonProperty("commit_id") String commitId,
		String event,
		String body,
		List<InlineComment> comments) {

	public ReviewSubmission {
		Objects.requireNonNull(commitId, "commitId");
		Objects.requireNonNull(body, "body");
		comments = List.copyOf(comments);
	}

	public static ReviewSubmission comment(String commitId, String body, List<InlineComment> comments) {
		return new ReviewSubmission(commitId, "COMMENT", body, comments);
	}

	/** One inline comment; {@code line} is a line of the file at the reviewed commit, {@code side} RIGHT for added lines. */
	public record InlineComment(String path, int line, String side, String body) {
	}
}
