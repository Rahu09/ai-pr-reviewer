package io.github.rahu09.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** No network: MockRestServiceServer plays GitHub. */
class GitHubClientTest {

	private static final String PR_JSON = """
			{"number":1,"state":"open","draft":false,"title":"Add endpoint","extra":"ignored",
			 "user":{"login":"Rahu09","id":5},
			 "head":{"sha":"headsha","ref":"feature/x","repo":{"full_name":"Rahu09/order-service-demo"}},
			 "base":{"sha":"basesha","ref":"main","repo":{"full_name":"Rahu09/order-service-demo"}}}
			""";

	private static final GitHubProperties PROPS = new GitHubProperties("https://api.github.com", "test-token", 200_000);

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final GitHubClient client = new GitHubClient(builder, PROPS, new PatTokenProvider(PROPS));

	@Test
	void pullRequestIsParsedAndSentWithBearerToken() {
		server.expect(requestTo("https://api.github.com/repos/Rahu09/order-service-demo/pulls/1"))
				.andExpect(header("Authorization", "Bearer test-token"))
				.andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
				.andRespond(withSuccess(PR_JSON, MediaType.APPLICATION_JSON));

		PullRequest pr = client.getPullRequest("Rahu09", "order-service-demo", 1);

		assertThat(pr.head().sha()).isEqualTo("headsha");
		assertThat(pr.base().sha()).isEqualTo("basesha");
		assertThat(pr.user().login()).isEqualTo("Rahu09");
		assertThat(pr.head().repo().fullName()).isEqualTo("Rahu09/order-service-demo");
		assertThat(pr.isOpen()).isTrue();
		server.verify();
	}

	@Test
	void diffIsRequestedBetweenExactShasWithTheDiffMediaType() {
		server.expect(requestTo("https://api.github.com/repos/o/r/compare/basesha...headsha"))
				.andExpect(header("Accept", "application/vnd.github.diff"))
				.andRespond(withSuccess("diff --git a/A.java b/A.java\n", MediaType.valueOf("application/vnd.github.diff")));

		assertThat(client.getDiff("o", "r", "basesha", "headsha")).startsWith("diff --git");
		server.verify();
	}

	@Test
	void fileContentIsRawTextAtTheGivenCommitAndNestedPathsKeepTheirSlashes() {
		server.expect(requestTo("https://api.github.com/repos/o/r/contents/src/main/A.java?ref=headsha"))
				.andExpect(header("Accept", "application/vnd.github.raw+json"))
				.andRespond(withSuccess("class A {}", MediaType.TEXT_PLAIN));

		assertThat(client.getFileContent("o", "r", "src/main/A.java", "headsha")).contains("class A {}");
		server.verify();
	}

	@Test
	void missingFileIsEmptyNotAnError() {
		server.expect(requestTo("https://api.github.com/repos/o/r/contents/Gone.java?ref=headsha")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(client.getFileContent("o", "r", "Gone.java", "headsha")).isEmpty();
	}

	@Test
	void missingPullRequestIsAGitHubExceptionWithStatus404() {
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> client.getPullRequest("o", "r", 99))
				.isInstanceOfSatisfying(GitHubException.class, e -> {
					assertThat(e.status()).isEqualTo(404);
					assertThat(e.getMessage()).contains("not found");
				});
	}

	@Test
	void forbiddenWithRateLimitExhaustedIsReportedAsRateLimit() {
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/1"))
				.andRespond(withStatus(HttpStatus.FORBIDDEN).header("X-RateLimit-Remaining", "0"));

		assertThatThrownBy(() -> client.getPullRequest("o", "r", 1))
				.isInstanceOfSatisfying(GitHubException.class, e -> assertThat(e.getMessage()).contains("rate limit"));
	}

	@Test
	void forbiddenWithoutRateLimitMeansMissingPermission() {
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/1")).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> client.getPullRequest("o", "r", 1))
				.isInstanceOfSatisfying(GitHubException.class, e -> assertThat(e.getMessage()).contains("permission"));
	}

	@Test
	void createReviewPostsACommentReviewPinnedToTheCommitWithSnakeCaseFieldsAndReturnsItsId() {
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/7/reviews"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-token"))
				.andExpect(jsonPath("$.commit_id").value("headsha"))
				.andExpect(jsonPath("$.event").value("COMMENT"))
				.andExpect(jsonPath("$.body").value("summary"))
				.andExpect(jsonPath("$.comments[0].path").value("src/A.java"))
				.andExpect(jsonPath("$.comments[0].line").value(12))
				.andExpect(jsonPath("$.comments[0].side").value("RIGHT"))
				.andExpect(jsonPath("$.comments[0].body").value("inline text"))
				.andRespond(withSuccess("{\"id\":4321,\"body\":\"summary\",\"state\":\"COMMENTED\"}", MediaType.APPLICATION_JSON));

		long id = client.createReview("o", "r", 7, ReviewSubmission.comment("headsha", "summary",
				List.of(new ReviewSubmission.InlineComment("src/A.java", 12, "RIGHT", "inline text"))));

		assertThat(id).isEqualTo(4321L);
		server.verify();
	}

	@Test
	void rejectedInlineLocationIsAGitHubExceptionWithStatus422() {
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/7/reviews")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

		assertThatThrownBy(() -> client.createReview("o", "r", 7, ReviewSubmission.comment("headsha", "s", List.of())))
				.isInstanceOfSatisfying(GitHubException.class, e -> assertThat(e.status()).isEqualTo(422));
	}

	@Test
	void listReviewsFollowsPagesUntilAShortOneAndToleratesNullBodies() {
		String fullPage = "[" + String.join(",", java.util.Collections.nCopies(100, "{\"id\":1,\"body\":\"b\",\"state\":\"COMMENTED\"}")) + "]";
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/7/reviews?per_page=100&page=1"))
				.andRespond(withSuccess(fullPage, MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/o/r/pulls/7/reviews?per_page=100&page=2"))
				.andRespond(withSuccess("[{\"id\":2,\"body\":null}]", MediaType.APPLICATION_JSON));

		List<PullRequestReview> reviews = client.listReviews("o", "r", 7);

		assertThat(reviews).hasSize(101);
		assertThat(reviews.getLast()).isEqualTo(new PullRequestReview(2, ""));
		server.verify();
	}

	@Test
	void unsetPatFailsBeforeAnyRequestAndNeverPrintsTheToken() {
		var empty = new GitHubProperties("https://api.github.com", "", 1);
		var unconfigured = new GitHubClient(RestClient.builder(), empty, new PatTokenProvider(empty));

		assertThatThrownBy(() -> unconfigured.getPullRequest("o", "r", 1))
				.isInstanceOf(GitHubException.class).hasMessageContaining("GITHUB_PAT");
		assertThat(new GitHubProperties("u", "secret-value", 1).toString()).doesNotContain("secret-value");
	}
}
