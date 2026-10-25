package io.github.rahu09.prreviewer.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rahu09.prreviewer.context.DiffParseException;
import io.github.rahu09.prreviewer.context.SkipReason;
import io.github.rahu09.prreviewer.publishing.FindingValidator;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Publishable;
import io.github.rahu09.prreviewer.publishing.ValidationResult.SummaryOnly;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Suppressed;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The whole engine except the LLM: a fake generator plays the model, everything else is the real code. */
class ReviewOrchestratorTest {

	private static final ReviewLimits LIMITS = new ReviewLimits(10, 400, 5, 50, 60_000);
	private final FixtureLoader fixtures = new FixtureLoader(new FixtureProperties("../evaluation/datasets"), JsonMapper.builder().build());
	private final InMemoryReviewRunRepository runs = new InMemoryReviewRunRepository();
	private final List<String> promptsSeen = new ArrayList<>();

	private ReviewOrchestrator orchestratorAnswering(java.util.function.Function<String, List<CandidateFinding>> model) {
		ReviewGenerator generator = annotated -> {
			promptsSeen.add(annotated);
			return new GenerationResult(new CandidateFindings(model.apply(annotated)), "fake", "fake-model", "review-v2", 100, 10);
		};
		return new ReviewOrchestrator(generator, new FindingValidator(), LIMITS, runs);
	}

	/** Plays a model that spots the line containing {@code needle} and returns the ID the prompt gave it. */
	private static String idOfLineContaining(String annotated, String needle) {
		Matcher m = Pattern.compile("\\[(L\\d+)]\\+.*" + Pattern.quote(needle)).matcher(annotated);
		assertThat(m.find()).as("annotated diff contains an added line with '%s'", needle).isTrue();
		return m.group(1);
	}

	@Test
	void buggyFixtureFindingLandsOnTheExpectedFileAndLine() {
		var fixture = fixtures.load("list-orders-unpaginated");
		var orchestrator = orchestratorAnswering(annotated -> List.of(new CandidateFinding(
				idOfLineContaining(annotated, "List<Order> list()"), "GUIDELINE", "Unbounded list", "Returns all orders", "Paginate", "Test it")));

		ReviewOutcome outcome = orchestrator.review(fixture.snapshot(), RunSource.FIXTURE, null);

		assertThat(outcome.results()).singleElement().isInstanceOfSatisfying(Publishable.class, p -> {
			var expected = fixture.meta().expectedFindings().getFirst();
			assertThat(p.location().path()).isEqualTo(expected.file());
			assertThat(p.location().line()).isEqualTo(expected.line());
		});
		assertThat(outcome.generation().provider()).isEqualTo("fake");
		assertThat(outcome.filesReviewed()).contains("src/main/java/io/github/rahu09/orderservice/order/OrderController.java");
		assertThat(outcome.allowedLocations()).isPositive();
	}

	@Test
	void modelInventingALocationIsSuppressedNotPublished() {
		var fixture = fixtures.load("list-orders-unpaginated");
		var orchestrator = orchestratorAnswering(_ -> List.of(new CandidateFinding("L9999", "GUIDELINE", "t", "e", "", "")));

		var outcome = orchestrator.review(fixture.snapshot(), RunSource.FIXTURE, null);

		assertThat(outcome.results()).singleElement().isInstanceOfSatisfying(Suppressed.class,
				s -> assertThat(s.reason()).contains("L9999"));
	}

	@Test
	void cleanFixtureWithNoFindingsGivesAnEmptyButValidOutcome() {
		var fixture = fixtures.load("list-orders-paginated");

		var outcome = orchestratorAnswering(_ -> List.of()).review(fixture.snapshot(), RunSource.FIXTURE, null);

		assertThat(outcome.results()).isEmpty();
		assertThat(outcome.filesReviewed()).isNotEmpty();
	}

	@Test
	void modelSeesIdsNotLineNumbersAndDiffContent() {
		var fixture = fixtures.load("cancel-order-swallowed");

		orchestratorAnswering(_ -> List.of()).review(fixture.snapshot(), RunSource.FIXTURE, null);

		assertThat(promptsSeen).singleElement().satisfies(prompt -> {
			assertThat(prompt).contains("File: src/main/java/io/github/rahu09/orderservice/order/OrderService.java (MODIFIED)");
			assertThat(prompt).containsPattern("\\[L\\d+]\\+.*catch \\(OrderStateException e\\)");
		});
	}

	@Test
	void nothingReviewableMeansNoModelCallAndSkippedFilesAreReported() {
		String diff = "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n@@ -1 +1 @@\n-a\n+b\n";
		var orchestrator = orchestratorAnswering(_ -> {
			throw new AssertionError("the model must not be called");
		});

		var outcome = orchestrator.review(ReviewSnapshot.ofDiff(diff), RunSource.DIFF, null);

		assertThat(outcome.generation().provider()).isEqualTo("none");
		assertThat(outcome.filesReviewed()).isEmpty();
		assertThat(outcome.filesSkipped()).singleElement().satisfies(s -> assertThat(s.reason()).isEqualTo(SkipReason.NOT_JAVA));
		assertThat(promptsSeen).isEmpty();
	}

	@Test
	void configOverrideChangesTheBudgetAndTheInlineCap() {
		var fixture = fixtures.load("list-orders-paginated");
		var override = new ReviewConfig.Override(1, null, 0, null);
		var orchestrator = orchestratorAnswering(annotated -> List.of(new CandidateFinding(
				idOfLineContaining(annotated, "findPage(int page, int size)"), "GUIDELINE", "t", "e", "", "")));

		var outcome = orchestrator.review(fixture.snapshot(), RunSource.FIXTURE, override);

		assertThat(outcome.config().maxJavaFiles()).isEqualTo(1);
		assertThat(outcome.filesReviewed()).hasSize(1);
		assertThat(outcome.filesSkipped()).anySatisfy(s -> assertThat(s.reason()).isEqualTo(SkipReason.TOO_MANY_FILES));
		assertThat(outcome.results()).singleElement().isInstanceOfSatisfying(SummaryOnly.class,
				s -> assertThat(s.reason()).contains("cap of 0"));
	}

	@Test
	void damagedDiffIsRejectedBeforeAnyModelCall() {
		var orchestrator = orchestratorAnswering(_ -> {
			throw new AssertionError("the model must not be called");
		});
		String truncated = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,5 +1,5 @@\n a\n";

		assertThatThrownBy(() -> orchestrator.review(ReviewSnapshot.ofDiff(truncated), RunSource.DIFF, null)).isInstanceOf(DiffParseException.class);
	}

	@Test
	void successfulRunIsStartedAndCompletedWithItsOutcome() {
		var fixture = fixtures.load("list-orders-paginated");

		var outcome = orchestratorAnswering(_ -> List.of()).review(fixture.snapshot(), RunSource.FIXTURE, null);

		assertThat(runs.started).singleElement().satisfies(start -> {
			assertThat(start.source()).isEqualTo(RunSource.FIXTURE);
			assertThat(start.headSha()).isEqualTo(fixture.snapshot().headSha());
			assertThat(start.config()).isEqualTo(outcome.config());
		});
		assertThat(runs.completed).containsExactly(outcome);
		assertThat(runs.failures).isEmpty();
	}

	@Test
	void providerFailureRecordsAFailedRunAtTheGenerationStageAndStillThrows() {
		var fixture = fixtures.load("list-orders-unpaginated");
		var orchestrator = orchestratorAnswering(_ -> {
			throw new ReviewGenerationException("provider down", null);
		});

		assertThatThrownBy(() -> orchestrator.review(fixture.snapshot(), RunSource.FIXTURE, null))
				.isInstanceOf(ReviewGenerationException.class);

		assertThat(runs.completed).isEmpty();
		assertThat(runs.failures).singleElement().satisfies(f -> {
			assertThat(f.stage()).isEqualTo(FailedStage.GENERATION);
			assertThat(f.message()).contains("provider down");
		});
	}

	@Test
	void failingFetchRecordsAFailedRunAtTheFetchStageAndStillThrows() {
		var orchestrator = orchestratorAnswering(_ -> List.of());

		assertThatThrownBy(() -> orchestrator.reviewFetched(RunSource.PR, "o/r", 5, () -> {
			throw new io.github.rahu09.prreviewer.github.GitHubException("GitHub 404 for pull request o/r#5", 404, null);
		}, null)).isInstanceOf(io.github.rahu09.prreviewer.github.GitHubException.class);

		assertThat(runs.started).singleElement().satisfies(s -> assertThat(s.source()).isEqualTo(RunSource.PR));
		assertThat(runs.failures).singleElement().satisfies(f -> {
			assertThat(f.stage()).isEqualTo(FailedStage.FETCH);
			assertThat(f.message()).contains("404");
		});
		assertThat(runs.completed).isEmpty();
	}

	@Test
	void fetchedSnapshotShasAreAttachedToTheRunAndTheReviewProceeds() {
		var fixture = fixtures.load("list-orders-paginated");

		var outcome = orchestratorAnswering(_ -> List.of()).reviewFetched(RunSource.PR, "o/r", 5, fixture::snapshot, null);

		assertThat(runs.attachedHeadShas).containsExactly(fixture.snapshot().headSha());
		assertThat(runs.completed).containsExactly(outcome);
	}

	@Test
	void damagedDiffRecordsAFailedRunAtTheParsingStage() {
		var orchestrator = orchestratorAnswering(_ -> List.of());
		String truncated = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,5 +1,5 @@\n a\n";

		assertThatThrownBy(() -> orchestrator.review(ReviewSnapshot.ofDiff(truncated), RunSource.DIFF, null)).isInstanceOf(DiffParseException.class);

		assertThat(runs.failures).singleElement().satisfies(f -> assertThat(f.stage()).isEqualTo(FailedStage.PARSING));
	}
}
