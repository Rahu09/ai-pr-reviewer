package io.github.rahu09.prreviewer.review;

import io.github.rahu09.prreviewer.context.AnnotatedDiff;
import io.github.rahu09.prreviewer.context.ContextBudget;
import io.github.rahu09.prreviewer.context.DiffParser;
import io.github.rahu09.prreviewer.context.FileDiff;
import io.github.rahu09.prreviewer.publishing.FindingValidator;
import io.github.rahu09.prreviewer.publishing.ValidationResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The engine: snapshot → parse diff → budget → annotate with location IDs → LLM → validate, and every run is
 * recorded (Chunk 5). The dry-run API calls this now; the webhook consumer calls the very same method later,
 * so evaluation measures the real thing.
 */
@Service
public class ReviewOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrator.class);

	private final ReviewGenerator generator;
	private final FindingValidator validator;
	private final ReviewLimits limits;
	private final ReviewRunRepository runs;
	private static final String UNKNOWN_SHA = "unknown";

	private final Clock clock = Clock.systemUTC();

	public ReviewOrchestrator(ReviewGenerator generator, FindingValidator validator, ReviewLimits limits, ReviewRunRepository runs) {
		this.generator = generator;
		this.validator = validator;
		this.limits = limits;
		this.runs = runs;
	}

	/**
	 * Runs one review and records it. If a stage throws, the run is stored as {@code FAILED} with that stage and the
	 * original exception is rethrown, so callers still see the real failure (HTTP 400/502 in the dry-run API).
	 *
	 * @throws io.github.rahu09.prreviewer.context.DiffParseException if the diff is not trustworthy
	 * @throws ReviewGenerationException if the provider fails
	 */
	public ReviewOutcome review(ReviewSnapshot snapshot, RunSource source, ReviewConfig.Override override) {
		return run(source, snapshot.repo(), snapshot.prNumber(), snapshot.baseSha(), snapshot.headSha(), () -> snapshot, override);
	}

	/**
	 * Like {@link #review(ReviewSnapshot, RunSource, ReviewConfig.Override)}, but the snapshot is fetched inside the
	 * recorded run (a PR from GitHub), so a failing fetch is also a {@code FAILED} run, with stage {@code FETCH}.
	 * The SHAs are only known after the fetch and are filled in then.
	 */
	public ReviewOutcome reviewFetched(RunSource source, String repo, int prNumber, Supplier<ReviewSnapshot> fetch, ReviewConfig.Override override) {
		return run(source, repo, prNumber, UNKNOWN_SHA, UNKNOWN_SHA, fetch, override);
	}

	private ReviewOutcome run(RunSource source, String repo, int prNumber, String baseSha, String headSha,
			Supplier<ReviewSnapshot> fetch, ReviewConfig.Override override) {
		ReviewConfig config = override == null ? ReviewConfig.from(limits) : override.applyTo(ReviewConfig.from(limits));
		Instant started = clock.instant();
		UUID runId = runs.start(new ReviewRunRepository.RunStart(source, repo, prNumber, baseSha, headSha, config, started));

		FailedStage stage = FailedStage.FETCH;
		ReviewOutcome outcome;
		long generationMs = 0;
		try {
			ReviewSnapshot snapshot = fetch.get();
			if (UNKNOWN_SHA.equals(headSha)) {
				runs.attachSnapshot(runId, snapshot.baseSha(), snapshot.headSha());
			}
			stage = FailedStage.PARSING;
			var parsed = DiffParser.parse(snapshot.diff());
			ContextBudget.Selection selection = config.budget().select(parsed);
			AnnotatedDiff annotated = AnnotatedDiff.of(selection.reviewed());
			List<String> reviewedPaths = selection.reviewed().stream().map(FileDiff::path).toList();

			if (annotated.locations().isEmpty()) {
				// Nothing the model could comment on: skip the paid call. An empty review is a valid outcome (PLAN §6 rule 4).
				log.info("Nothing to review in {}#{} ({} files skipped)", snapshot.repo(), snapshot.prNumber(), selection.skipped().size());
				outcome = new ReviewOutcome(runId, config, reviewedPaths, selection.skipped(), 0, GenerationResult.notCalled(), List.of());
			}
			else {
				stage = FailedStage.GENERATION;
				Instant generationStart = clock.instant();
				GenerationResult generation = generator.generate(annotated.text());
				generationMs = Duration.between(generationStart, clock.instant()).toMillis();

				stage = FailedStage.VALIDATION;
				List<ValidationResult> results = validator.validate(generation.findings().findings(), annotated.locations(), config.maxInlineFindings());
				outcome = new ReviewOutcome(runId, config, reviewedPaths, selection.skipped(), annotated.locations().size(), generation, results);
			}
		}
		catch (RuntimeException e) {
			recordFailure(runId, stage, e, started);
			throw e;
		}

		try {
			Instant finished = clock.instant();
			runs.complete(runId, outcome, generationMs, Duration.between(started, finished).toMillis(), finished);
		}
		catch (RuntimeException e) {
			recordFailure(runId, FailedStage.PERSISTENCE, e, started);
			throw e;
		}
		return outcome;
	}

	// Best effort: if the database itself is the problem, the original failure must not be hidden by a second one.
	private void recordFailure(UUID runId, FailedStage stage, RuntimeException cause, Instant started) {
		try {
			Instant finished = clock.instant();
			runs.fail(runId, stage, cause.getClass().getSimpleName() + ": " + cause.getMessage(),
					Duration.between(started, finished).toMillis(), finished);
		}
		catch (RuntimeException recordingFailed) {
			log.error("Could not record failure of run {} at {}", runId, stage, recordingFailed);
		}
	}
}
