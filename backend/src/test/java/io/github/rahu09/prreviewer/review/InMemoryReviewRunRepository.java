package io.github.rahu09.prreviewer.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Unit-test double: remembers what the orchestrator recorded, without a database. */
class InMemoryReviewRunRepository implements ReviewRunRepository {

	record Failure(UUID runId, FailedStage stage, String message) {
	}

	final List<RunStart> started = new ArrayList<>();
	final List<ReviewOutcome> completed = new ArrayList<>();
	final List<Failure> failures = new ArrayList<>();

	@Override
	public UUID start(RunStart start) {
		started.add(start);
		return UUID.randomUUID();
	}

	final List<String> attachedHeadShas = new ArrayList<>();

	@Override
	public void attachSnapshot(UUID runId, String baseSha, String headSha) {
		attachedHeadShas.add(headSha);
	}

	@Override
	public void complete(UUID runId, ReviewOutcome outcome, long generationMs, long latencyMs, Instant finishedAt) {
		completed.add(outcome);
	}

	@Override
	public void fail(UUID runId, FailedStage stage, String errorMessage, long latencyMs, Instant finishedAt) {
		failures.add(new Failure(runId, stage, errorMessage));
	}

	@Override
	public Optional<ReviewRun> findRun(UUID id) {
		return Optional.empty();
	}

	@Override
	public List<StoredFinding> findFindings(UUID runId) {
		return List.of();
	}
}
