package io.github.rahu09.prreviewer.webhook;

import io.github.rahu09.prreviewer.github.GitHubSnapshotProvider;
import io.github.rahu09.prreviewer.publishing.PublishOutcome;
import io.github.rahu09.prreviewer.publishing.ReviewPublisher;
import io.github.rahu09.prreviewer.review.ReviewOrchestrator;
import io.github.rahu09.prreviewer.review.ReviewOutcome;
import io.github.rahu09.prreviewer.review.model.RunSource;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * TEMPORARY (Sprint 1, ADR-0017): runs fetch → review → publish on a virtual thread right after the delivery commits.
 * Not durable: if the app dies mid-review that review is lost. A GitHub "Redeliver" does NOT recover it (same delivery
 * id, so it is a duplicate); push a commit or reopen the PR instead. Chunk 12 adds the Postgres review queue alongside
 * this class and Chunk 13 (the worker) deletes it (ADR-0021).
 */
@Component
public class InProcessReviewStarter {

	private static final Logger log = LoggerFactory.getLogger(InProcessReviewStarter.class);

	private final ReviewOrchestrator orchestrator;
	private final GitHubSnapshotProvider snapshots;
	private final ReviewPublisher publisher;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public InProcessReviewStarter(ReviewOrchestrator orchestrator, GitHubSnapshotProvider snapshots, ReviewPublisher publisher) {
		this.orchestrator = orchestrator;
		this.snapshots = snapshots;
		this.publisher = publisher;
	}

	// fallbackExecution: still runs if someone publishes the event outside a transaction (a plain unit test, say).
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onReviewRequested(ReviewStartRequested request) {
		executor.submit(() -> run(request));
	}

	/** Never throws: nobody is waiting on this thread, so the failure is logged here and the run row already records it. */
	void run(ReviewStartRequested request) {
		String fullName = request.owner() + "/" + request.repo();
		try {
			ReviewOutcome outcome = orchestrator.reviewFetched(RunSource.PR, fullName, request.prNumber(),
					() -> snapshots.fetch(request.owner(), request.repo(), request.prNumber()), null);
			PublishOutcome published = publisher.publish(outcome.runId());
			log.info("Review of {}#{} (delivery {}, run {}): {}", fullName, request.prNumber(), request.deliveryId(), outcome.runId(), published);
		}
		catch (RuntimeException e) {
			log.error("Review of {}#{} (delivery {}) failed: {}", fullName, request.prNumber(), request.deliveryId(), e.toString());
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}
}
