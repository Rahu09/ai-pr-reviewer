package io.github.rahu09.prreviewer.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rahu09.prreviewer.github.GitHubException;
import io.github.rahu09.prreviewer.github.GitHubSnapshotProvider;
import io.github.rahu09.prreviewer.publishing.PublishOutcome;
import io.github.rahu09.prreviewer.publishing.ReviewPublisher;
import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.ReviewOrchestrator;
import io.github.rahu09.prreviewer.review.ReviewOutcome;
import io.github.rahu09.prreviewer.review.ReviewSnapshot;
import io.github.rahu09.prreviewer.review.model.GenerationResult;
import io.github.rahu09.prreviewer.review.model.RunSource;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InProcessReviewStarterTest {

	private final ReviewOrchestrator orchestrator = mock(ReviewOrchestrator.class);
	private final GitHubSnapshotProvider snapshots = mock(GitHubSnapshotProvider.class);
	private final ReviewPublisher publisher = mock(ReviewPublisher.class);
	private final InProcessReviewStarter starter = new InProcessReviewStarter(orchestrator, snapshots, publisher);
	private final ReviewStartRequested request = new ReviewStartRequested("Rahu09", "order-service-demo", 7, "headsha", "d1");
	private final UUID runId = UUID.randomUUID();

	private ReviewOutcome outcome() {
		return new ReviewOutcome(runId, new ReviewConfig(10, 400, 5, 60_000), List.of(), List.of(), 0, GenerationResult.notCalled(), List.of());
	}

	@Test
	void fetchesTheSnapshotInsideTheRecordedRunThenPublishesThatRun() {
		when(orchestrator.reviewFetched(eq(RunSource.PR), eq("Rahu09/order-service-demo"), eq(7), any(), any())).thenReturn(outcome());
		when(publisher.publish(runId)).thenReturn(new PublishOutcome.Posted(1L, 0, false));

		starter.run(request);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Supplier<ReviewSnapshot>> fetch = ArgumentCaptor.forClass(Supplier.class);
		verify(orchestrator).reviewFetched(eq(RunSource.PR), eq("Rahu09/order-service-demo"), eq(7), fetch.capture(), eq(null));
		fetch.getValue().get();
		verify(snapshots).fetch("Rahu09", "order-service-demo", 7);
		verify(publisher).publish(runId);
	}

	@Test
	void aFailingReviewIsLoggedNotThrownAndNothingIsPublished() {
		when(orchestrator.reviewFetched(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
				.thenThrow(new GitHubException("GitHub 404 for pull request: not found", 404, null));

		assertThatCode(() -> starter.run(request)).doesNotThrowAnyException();

		verify(publisher, never()).publish(any());
	}

	@Test
	void aFailingPublishIsAlsoContained() {
		when(orchestrator.reviewFetched(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any())).thenReturn(outcome());
		when(publisher.publish(runId)).thenThrow(new GitHubException("GitHub 403: no permission", 403, null));

		assertThatCode(() -> starter.run(request)).doesNotThrowAnyException();
		assertThat(runId).isNotNull();
	}
}
