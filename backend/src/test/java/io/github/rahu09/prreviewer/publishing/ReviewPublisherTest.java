package io.github.rahu09.prreviewer.publishing;

import static io.github.rahu09.prreviewer.publishing.StoredRuns.CONFIG;
import static io.github.rahu09.prreviewer.publishing.StoredRuns.HEAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rahu09.prreviewer.github.GitHubClient;
import io.github.rahu09.prreviewer.github.GitHubException;
import io.github.rahu09.prreviewer.github.PullRequest;
import io.github.rahu09.prreviewer.github.PullRequestReview;
import io.github.rahu09.prreviewer.github.ReviewSubmission;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.AlreadyPosted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Posted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Superseded;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.model.RunStatus;
import io.github.rahu09.prreviewer.review.run.InMemoryReviewRunRepository;
import io.github.rahu09.prreviewer.review.run.RunNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The publisher's decisions, with GitHub played by a mock: no network, no token. */
class ReviewPublisherTest {

	private final GitHubClient github = mock(GitHubClient.class);
	private final InMemoryReviewRunRepository runs = new InMemoryReviewRunRepository();
	private final ReviewPublisher publisher = new ReviewPublisher(github, runs);
	private final UUID runId = UUID.randomUUID();

	private static PullRequest pr(String state, String headSha) {
		return new PullRequest(1, state, false, "t", new PullRequest.User("Rahu09"),
				new PullRequest.Ref(headSha, new PullRequest.Repo("Rahu09/order-service-demo")),
				new PullRequest.Ref("basesha", new PullRequest.Repo("Rahu09/order-service-demo")));
	}

	private void seedRunWithOneInlineAndOneSummaryFinding() {
		runs.seed(StoredRuns.succeededPrRun(runId), List.of(
				StoredRuns.inline(runId, 0, "src/OrderController.java", 32, "Unbounded list"),
				StoredRuns.summaryOnly(runId, 1, "Cross-file concern"),
				StoredRuns.suppressed(runId, 2)));
	}

	private void githubStateIsOpenAtTheReviewedHead() {
		when(github.getPullRequest("Rahu09", "order-service-demo", 1)).thenReturn(pr("open", HEAD));
		when(github.listReviews("Rahu09", "order-service-demo", 1)).thenReturn(List.of());
	}

	@Test
	void postsOneCommentReviewPinnedToTheReviewedCommitWithInlineCommentsAtTheRightLines() {
		seedRunWithOneInlineAndOneSummaryFinding();
		githubStateIsOpenAtTheReviewedHead();
		when(github.createReview(any(), any(), anyInt(), any())).thenReturn(777L);

		PublishOutcome outcome = publisher.publish(runId);

		assertThat(outcome).isEqualTo(new Posted(777L, 1, false));
		var captor = ArgumentCaptor.forClass(ReviewSubmission.class);
		verify(github).createReview(eq("Rahu09"), eq("order-service-demo"), eq(1), captor.capture());
		ReviewSubmission sent = captor.getValue();
		assertThat(sent.event()).isEqualTo("COMMENT");
		assertThat(sent.commitId()).isEqualTo(HEAD);
		assertThat(sent.comments()).singleElement().satisfies(c -> {
			assertThat(c.path()).isEqualTo("src/OrderController.java");
			assertThat(c.line()).isEqualTo(32);
			assertThat(c.side()).isEqualTo("RIGHT");
			assertThat(c.body()).contains("Unbounded list");
		});
		assertThat(sent.body()).contains("Cross-file concern");
		assertThat(ReviewSummaryFormatter.carriesMarker(sent.body(), HEAD, CONFIG)).isTrue();
		assertThat(runs.published).containsExactly(new InMemoryReviewRunRepository.Published(runId, 777L, false));
	}

	@Test
	void changedHeadPostsNothingAndSupersedesTheRun() {
		seedRunWithOneInlineAndOneSummaryFinding();
		when(github.getPullRequest("Rahu09", "order-service-demo", 1)).thenReturn(pr("open", "someone-pushed-a-new-commit"));

		PublishOutcome outcome = publisher.publish(runId);

		assertThat(outcome).isInstanceOfSatisfying(Superseded.class, s -> assertThat(s.reason()).contains("head moved"));
		verify(github, never()).createReview(any(), any(), anyInt(), any());
		verify(github, never()).listReviews(any(), any(), anyInt());
		assertThat(runs.superseded).containsExactly(runId);
		assertThat(runs.published).isEmpty();
	}

	@Test
	void closedPullRequestPostsNothing() {
		seedRunWithOneInlineAndOneSummaryFinding();
		when(github.getPullRequest("Rahu09", "order-service-demo", 1)).thenReturn(pr("closed", HEAD));

		assertThat(publisher.publish(runId)).isInstanceOfSatisfying(Superseded.class, s -> assertThat(s.reason()).contains("closed"));
		verify(github, never()).createReview(any(), any(), anyInt(), any());
	}

	@Test
	void publishingTwiceCreatesOnlyOneReview() {
		seedRunWithOneInlineAndOneSummaryFinding();
		githubStateIsOpenAtTheReviewedHead();
		when(github.createReview(any(), any(), anyInt(), any())).thenReturn(777L);
		var firstCall = publisher.publish(runId);

		// What the database now says about the run: the review id is stored.
		runs.seed(StoredRuns.run(runId, RunSource.PR, RunStatus.SUCCEEDED, 777L), List.of());
		var secondCall = publisher.publish(runId);

		assertThat(firstCall).isInstanceOf(Posted.class);
		assertThat(secondCall).isEqualTo(new AlreadyPosted(777L));
		verify(github, times(1)).createReview(any(), any(), anyInt(), any());
	}

	@Test
	void lostResponseIsRecoveredByFindingTheMarkerOnGitHubInsteadOfPostingAgain() {
		// First attempt: GitHub accepted the review, our request timed out, nothing was stored (the run has no review id).
		// The retry must find the review by its hidden marker, remember its id, and post nothing.
		seedRunWithOneInlineAndOneSummaryFinding();
		when(github.getPullRequest("Rahu09", "order-service-demo", 1)).thenReturn(pr("open", HEAD));
		String bodyGitHubKept = "## AI review\n\n" + ReviewSummaryFormatter.marker(UUID.randomUUID(), HEAD, CONFIG);
		when(github.listReviews("Rahu09", "order-service-demo", 1)).thenReturn(List.of(
				new PullRequestReview(5, "LGTM from a human"), new PullRequestReview(888, bodyGitHubKept)));

		PublishOutcome outcome = publisher.publish(runId);

		assertThat(outcome).isEqualTo(new AlreadyPosted(888L));
		verify(github, never()).createReview(any(), any(), anyInt(), any());
		assertThat(runs.published).containsExactly(new InMemoryReviewRunRepository.Published(runId, 888L, false));
	}

	@Test
	void aMarkerForAnOlderCommitDoesNotBlockAReviewOfTheNewOne() {
		seedRunWithOneInlineAndOneSummaryFinding();
		when(github.getPullRequest("Rahu09", "order-service-demo", 1)).thenReturn(pr("open", HEAD));
		when(github.listReviews("Rahu09", "order-service-demo", 1)).thenReturn(List.of(
				new PullRequestReview(5, ReviewSummaryFormatter.marker(UUID.randomUUID(), "an-older-commit", CONFIG))));
		when(github.createReview(any(), any(), anyInt(), any())).thenReturn(9L);

		assertThat(publisher.publish(runId)).isInstanceOf(Posted.class);
	}

	@Test
	void rejectedInlineLocationIsRetriedOnceAsSummaryOnlyAndRecorded() {
		seedRunWithOneInlineAndOneSummaryFinding();
		githubStateIsOpenAtTheReviewedHead();
		List<ReviewSubmission> sent = new ArrayList<>();
		when(github.createReview(any(), any(), anyInt(), any())).thenAnswer(invocation -> {
			ReviewSubmission submission = invocation.getArgument(3);
			sent.add(submission);
			if (!submission.comments().isEmpty()) {
				throw new GitHubException("GitHub 422 for review: GitHub refused the request", 422, null);
			}
			return 42L;
		});

		PublishOutcome outcome = publisher.publish(runId);

		assertThat(outcome).isEqualTo(new Posted(42L, 0, true));
		assertThat(sent).hasSize(2);
		assertThat(sent.get(1).comments()).isEmpty();
		assertThat(sent.get(1).body()).contains("Unbounded list").contains("`src/OrderController.java:32`").contains("Cross-file concern");
		assertThat(runs.published).containsExactly(new InMemoryReviewRunRepository.Published(runId, 42L, true));
	}

	@Test
	void otherGitHubFailuresAreNotRetriedAndNothingIsRecorded() {
		seedRunWithOneInlineAndOneSummaryFinding();
		githubStateIsOpenAtTheReviewedHead();
		when(github.createReview(any(), any(), anyInt(), any())).thenThrow(new GitHubException("GitHub 403: no permission", 403, null));

		assertThatThrownBy(() -> publisher.publish(runId)).isInstanceOfSatisfying(GitHubException.class, e -> assertThat(e.status()).isEqualTo(403));

		verify(github, times(1)).createReview(any(), any(), anyInt(), any());
		assertThat(runs.published).isEmpty();
	}

	@Test
	void aReviewWithNoFindingsIsStillPostedAsAnEmptyAdvisoryComment() {
		runs.seed(StoredRuns.succeededPrRun(runId), List.of());
		githubStateIsOpenAtTheReviewedHead();
		when(github.createReview(any(), any(), anyInt(), any())).thenReturn(3L);

		assertThat(publisher.publish(runId)).isEqualTo(new Posted(3L, 0, false));
	}

	@Test
	void onlySucceededPrRunsCanBePublished() {
		UUID fixtureRun = UUID.randomUUID();
		UUID failedRun = UUID.randomUUID();
		runs.seed(StoredRuns.run(fixtureRun, RunSource.FIXTURE, RunStatus.SUCCEEDED, null), List.of());
		runs.seed(StoredRuns.run(failedRun, RunSource.PR, RunStatus.FAILED, null), List.of());

		assertThatThrownBy(() -> publisher.publish(fixtureRun)).isInstanceOf(PublishNotAllowedException.class).hasMessageContaining("FIXTURE");
		assertThatThrownBy(() -> publisher.publish(failedRun)).isInstanceOf(PublishNotAllowedException.class).hasMessageContaining("FAILED");
		assertThatThrownBy(() -> publisher.publish(UUID.randomUUID())).isInstanceOf(RunNotFoundException.class);
		verify(github, never()).getPullRequest(any(), any(), anyInt());
	}
}
