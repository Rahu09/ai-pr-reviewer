package io.github.rahu09.prreviewer.review.run;

import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.ReviewOutcome;
import io.github.rahu09.prreviewer.review.model.FailedStage;
import io.github.rahu09.prreviewer.review.model.RunSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of review runs and their findings (plain SQL, no JPA: ADR-0004). */
public interface ReviewRunRepository {

	/** What is known before the pipeline starts. */
	record RunStart(RunSource source, String repo, int prNumber, String baseSha, String headSha, ReviewConfig config, Instant startedAt) {
	}

	/** Inserts a {@code RUNNING} row in its own transaction, so even a crash mid-review leaves a trace. */
	UUID start(RunStart start);

	/** Records the exact commits once a PR snapshot has been fetched. */
	void attachSnapshot(UUID runId, String baseSha, String headSha);

	/** Marks the run {@code SUCCEEDED} and stores every finding, in ONE transaction: no run without its findings. */
	void complete(UUID runId, ReviewOutcome outcome, long generationMs, long latencyMs, Instant finishedAt);

	void fail(UUID runId, FailedStage stage, String errorMessage, long latencyMs, Instant finishedAt);

	/** Remembers the GitHub review that carries this run's findings (Chunk 7). */
	void recordPublished(UUID runId, long githubReviewId, boolean summaryOnly, Instant publishedAt);

	/** The PR moved on (closed, or a new head commit) before anything was posted. */
	void markSuperseded(UUID runId);

	Optional<ReviewRun> findRun(UUID id);

	List<StoredFinding> findFindings(UUID runId);
}
