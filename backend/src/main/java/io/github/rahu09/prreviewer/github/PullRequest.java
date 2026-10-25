package io.github.rahu09.prreviewer.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The parts of GitHub's pull request payload we use. Unknown fields are ignored on purpose. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequest(int number, String state, boolean draft, String title, User user, Ref head, Ref base) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record User(String login) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Ref(String sha, Repo repo) {
	}

	/** {@code repo} is null when the fork behind a PR was deleted. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Repo(@JsonProperty("full_name") String fullName) {
	}

	public boolean isOpen() {
		return "open".equals(state);
	}
}
