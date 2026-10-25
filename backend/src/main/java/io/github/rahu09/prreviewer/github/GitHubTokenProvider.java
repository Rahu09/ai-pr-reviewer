package io.github.rahu09.prreviewer.github;

/** Supplies the bearer token for GitHub calls. A PAT now (ADR-0010); a GitHub App installation token in Chunk 11. */
public interface GitHubTokenProvider {

	/** @throws GitHubException if no token is configured */
	String token();
}
