package io.github.rahu09.prreviewer.github;

/** Supplies the bearer token for GitHub calls: a PAT, or a GitHub App installation token (ADR-0010). */
public interface GitHubTokenProvider {

	/** @throws GitHubException if no token is configured */
	String token();
}
