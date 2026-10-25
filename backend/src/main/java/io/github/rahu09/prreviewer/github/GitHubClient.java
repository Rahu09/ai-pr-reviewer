package io.github.rahu09.prreviewer.github;

import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Read-only GitHub REST calls (Chunk 6). Every method maps HTTP failures to {@link GitHubException} with a message
 * that says what to check, instead of leaking {@code RestClient} exceptions upwards.
 */
@Component
public class GitHubClient {

	private static final String RAW = "application/vnd.github.raw+json";
	private static final String DIFF = "application/vnd.github.diff";

	private final RestClient http;
	private final GitHubTokenProvider tokens;

	public GitHubClient(RestClient.Builder builder, GitHubProperties properties, GitHubTokenProvider tokens) {
		this.http = builder.baseUrl(properties.apiUrl())
				.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
				.defaultHeader(HttpHeaders.USER_AGENT, "ai-pr-reviewer")
				.build();
		this.tokens = tokens;
	}

	public PullRequest getPullRequest(String owner, String repo, int number) {
		return call("pull request " + owner + "/" + repo + "#" + number, () -> http.get()
				.uri(u -> u.pathSegment("repos", owner, repo, "pulls", String.valueOf(number)).build())
				.headers(this::auth)
				.accept(MediaType.parseMediaType("application/vnd.github+json"))
				.retrieve()
				.body(PullRequest.class));
	}

	/**
	 * The diff between two commits (three-dot, like a PR's "Files changed"). Pinned to SHAs, so it cannot change
	 * under us if someone pushes meanwhile; that is why the snapshot uses this instead of {@code /pulls/{n}} + diff.
	 */
	public String getDiff(String owner, String repo, String baseSha, String headSha) {
		return call("diff " + baseSha + "..." + headSha, () -> http.get()
				.uri(u -> u.pathSegment("repos", owner, repo, "compare", baseSha + "..." + headSha).build())
				.headers(this::auth)
				.accept(MediaType.parseMediaType(DIFF))
				.retrieve()
				.body(String.class));
	}

	/** File contents at an exact commit, as raw text. Empty when the file does not exist at that commit. */
	public Optional<String> getFileContent(String owner, String repo, String path, String ref) {
		try {
			String body = call("file " + path + "@" + ref, () -> http.get()
					.uri(u -> u.pathSegment("repos", owner, repo, "contents").pathSegment(path.split("/")).queryParam("ref", ref).build())
					.headers(this::auth)
					.accept(MediaType.parseMediaType(RAW))
					.retrieve()
					.body(String.class));
			return Optional.ofNullable(body);
		}
		catch (GitHubException e) {
			if (e.status() == 404) {
				return Optional.empty();
			}
			throw e;
		}
	}

	private void auth(HttpHeaders headers) {
		headers.setBearerAuth(tokens.token());
	}

	private static <T> T call(String what, java.util.function.Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			boolean rateLimited = (status == 403 || status == 429) && "0".equals(e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("X-RateLimit-Remaining"));
			String hint = switch (status) {
				case 401 -> "token rejected (expired or wrong)";
				case 403, 429 -> rateLimited ? "rate limit exceeded" : "no permission (check the token's repository access and Contents / Pull requests read)";
				case 404 -> "not found, or the token cannot see it";
				case 422 -> "GitHub refused the request";
				default -> status >= 500 ? "GitHub is having trouble" : "unexpected response";
			};
			throw new GitHubException("GitHub " + status + " for " + what + ": " + hint, status, e);
		}
	}
}
