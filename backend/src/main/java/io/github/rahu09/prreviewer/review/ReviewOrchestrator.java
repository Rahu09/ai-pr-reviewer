package io.github.rahu09.prreviewer.review;

import io.github.rahu09.prreviewer.context.AnnotatedDiff;
import io.github.rahu09.prreviewer.context.ContextBudget;
import io.github.rahu09.prreviewer.context.DiffParser;
import io.github.rahu09.prreviewer.context.FileDiff;
import io.github.rahu09.prreviewer.publishing.FindingValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The engine: snapshot → parse diff → budget → annotate with location IDs → LLM → validate. The dry-run API
 * calls this now; the webhook consumer calls the very same method later, so evaluation measures the real thing.
 */
@Service
public class ReviewOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrator.class);

	private final ReviewGenerator generator;
	private final FindingValidator validator;
	private final ReviewLimits limits;

	public ReviewOrchestrator(ReviewGenerator generator, FindingValidator validator, ReviewLimits limits) {
		this.generator = generator;
		this.validator = validator;
		this.limits = limits;
	}

	/** @throws io.github.rahu09.prreviewer.context.DiffParseException if the diff is not trustworthy */
	public ReviewOutcome review(ReviewSnapshot snapshot, ReviewConfig.Override override) {
		ReviewConfig config = override == null ? ReviewConfig.from(limits) : override.applyTo(ReviewConfig.from(limits));

		var parsed = DiffParser.parse(snapshot.diff());
		ContextBudget.Selection selection = config.budget().select(parsed);
		AnnotatedDiff annotated = AnnotatedDiff.of(selection.reviewed());
		List<String> reviewedPaths = selection.reviewed().stream().map(FileDiff::path).toList();

		if (annotated.locations().isEmpty()) {
			// Nothing the model could comment on: skip the paid call. An empty review is a valid outcome (PLAN §6 rule 4).
			log.info("Nothing to review in {}#{} ({} files skipped)", snapshot.repo(), snapshot.prNumber(), selection.skipped().size());
			return new ReviewOutcome(config, reviewedPaths, selection.skipped(), 0,
					GenerationResult.notCalled(), List.of());
		}

		GenerationResult generation = generator.generate(annotated.text());
		var results = validator.validate(generation.findings().findings(), annotated.locations(), config.maxInlineFindings());
		return new ReviewOutcome(config, reviewedPaths, selection.skipped(), annotated.locations().size(), generation, results);
	}
}
