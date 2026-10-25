package io.github.rahu09.prreviewer.github;

import org.springframework.stereotype.Component;

@Component
public class PatTokenProvider implements GitHubTokenProvider {

	private final GitHubProperties properties;

	public PatTokenProvider(GitHubProperties properties) {
		this.properties = properties;
	}

	@Override
	public String token() {
		if (properties.pat().isBlank()) {
			throw new GitHubException("GITHUB_PAT is not set; put a fine-grained read-only token in .env", 0, null);
		}
		return properties.pat();
	}
}
