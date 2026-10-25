package io.github.rahu09.prreviewer.review.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rahu09.prreviewer.StubReviewGeneratorConfiguration;
import io.github.rahu09.prreviewer.TestcontainersConfiguration;
import io.github.rahu09.prreviewer.publishing.FindingValidator;
import io.github.rahu09.prreviewer.review.ReviewGenerationException;
import io.github.rahu09.prreviewer.review.ReviewGenerator;
import io.github.rahu09.prreviewer.review.ReviewLimits;
import io.github.rahu09.prreviewer.review.ReviewOrchestrator;
import io.github.rahu09.prreviewer.review.ReviewOutcome;
import io.github.rahu09.prreviewer.review.ReviewSnapshot;
import io.github.rahu09.prreviewer.review.api.ApiExceptionHandler;
import io.github.rahu09.prreviewer.review.api.RunController;
import io.github.rahu09.prreviewer.review.fixture.FixtureLoader;
import io.github.rahu09.prreviewer.review.fixture.FixtureProperties;
import io.github.rahu09.prreviewer.review.model.CandidateFinding;
import io.github.rahu09.prreviewer.review.model.CandidateFindings;
import io.github.rahu09.prreviewer.review.model.FailedStage;
import io.github.rahu09.prreviewer.review.model.FindingStatus;
import io.github.rahu09.prreviewer.review.model.GenerationResult;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.model.RunStatus;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/** The real schema and real SQL (Testcontainers Postgres), with a scripted model instead of an LLM. */
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, StubReviewGeneratorConfiguration.class })
@SpringBootTest
class ReviewRunPersistenceTest {

	@Autowired
	ReviewRunRepository runs;

	@Autowired
	ReviewLimits limits;

	@Autowired
	JdbcClient jdbc;

	private final FixtureLoader fixtures = new FixtureLoader(new FixtureProperties("../evaluation/datasets"), JsonMapper.builder().build());

	private ReviewOrchestrator orchestratorWith(ReviewGenerator generator) {
		return new ReviewOrchestrator(generator, new FindingValidator(), limits, runs);
	}

	private static String idOfLineContaining(String annotated, String needle) {
		Matcher m = Pattern.compile("\\[(L\\d+)]\\+.*" + Pattern.quote(needle)).matcher(annotated);
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	@Test
	void everyFindingIsStoredAndTracesBackToTheRunTheSnapshotAndTheLocation() {
		var fixture = fixtures.load("list-orders-unpaginated");
		var orchestrator = orchestratorWith(annotated -> new GenerationResult(new CandidateFindings(List.of(
				new CandidateFinding(idOfLineContaining(annotated, "List<Order> list()"), "GUIDELINE", "Unbounded list", "Returns all orders", "Paginate", "Test it"),
				new CandidateFinding("L9999", "CORRECTNESS", "Invented", "Model made up a location", "", ""),
				new CandidateFinding("", "security", "Odd category", "Unknown category value", "", ""))),
				"fake", "fake-model", "review-v2", 120, 30));

		ReviewOutcome outcome = orchestrator.review(fixture.snapshot(), RunSource.FIXTURE, null);

		ReviewRun run = runs.findRun(outcome.runId()).orElseThrow();
		assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
		assertThat(run.source()).isEqualTo(RunSource.FIXTURE);
		assertThat(run.headSha()).isEqualTo(fixture.snapshot().headSha());
		assertThat(run.baseSha()).isEqualTo(fixture.snapshot().baseSha());
		assertThat(run.config()).isEqualTo(outcome.config());
		assertThat(run.provider()).isEqualTo("fake");
		assertThat(run.model()).isEqualTo("fake-model");
		assertThat(run.promptVersion()).isEqualTo("review-v2");
		assertThat(run.inputTokens()).isEqualTo(120);
		assertThat(run.outputTokens()).isEqualTo(30);
		assertThat(run.filesReviewed()).isEqualTo(outcome.filesReviewed());
		assertThat(run.allowedLocations()).isEqualTo(outcome.allowedLocations());
		assertThat(run.latencyMs()).isNotNegative();
		assertThat(run.finishedAt()).isAfterOrEqualTo(run.startedAt());
		assertThat(run.failedStage()).isNull();

		List<StoredFinding> findings = runs.findFindings(run.id());
		assertThat(findings).hasSize(3);

		var expected = fixture.meta().expectedFindings().getFirst();
		assertThat(findings.get(0).status()).isEqualTo(FindingStatus.PUBLISHABLE);
		assertThat(findings.get(0).filePath()).isEqualTo(expected.file());
		assertThat(findings.get(0).line()).isEqualTo(expected.line());
		assertThat(findings.get(0).side()).isEqualTo("RIGHT");
		assertThat(findings.get(0).runId()).isEqualTo(run.id());

		// Suppressed findings are kept, with the reason and the model's raw values (PLAN §6 rule 2).
		assertThat(findings.get(1).status()).isEqualTo(FindingStatus.SUPPRESSED);
		assertThat(findings.get(1).locationId()).isEqualTo("L9999");
		assertThat(findings.get(1).filePath()).isNull();
		assertThat(findings.get(1).reason()).contains("L9999");
		assertThat(findings.get(2).status()).isEqualTo(FindingStatus.SUPPRESSED);
		assertThat(findings.get(2).category()).isEqualTo("security");
	}

	@Test
	void skippedFilesAreStoredWithTheirReasons() {
		String diff = "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n@@ -1 +1 @@\n-a\n+b\n";

		ReviewOutcome outcome = orchestratorWith(_ -> {
			throw new AssertionError("no model call expected");
		}).review(ReviewSnapshot.ofDiff(diff), RunSource.DIFF, null);

		ReviewRun run = runs.findRun(outcome.runId()).orElseThrow();
		assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
		assertThat(run.provider()).isEqualTo("none");
		assertThat(run.filesSkipped()).singleElement().satisfies(s -> {
			assertThat(s.path()).isEqualTo("README.md");
			assertThat(s.reason().name()).isEqualTo("NOT_JAVA");
		});
		assertThat(runs.findFindings(run.id())).isEmpty();
	}

	@Test
	void failedProviderCallProducesAFailedRunWithStageGeneration() {
		var fixture = fixtures.load("cancel-order-swallowed");
		long before = count();
		var orchestrator = orchestratorWith(_ -> {
			throw new ReviewGenerationException("LLM call failed (stub)", null);
		});

		assertThatThrownBy(() -> orchestrator.review(fixture.snapshot(), RunSource.FIXTURE, null))
				.isInstanceOf(ReviewGenerationException.class);

		assertThat(count()).isEqualTo(before + 1);
		UUID id = jdbc.sql("SELECT id FROM review_runs WHERE status = 'FAILED' ORDER BY started_at DESC LIMIT 1").query(UUID.class).single();
		ReviewRun run = runs.findRun(id).orElseThrow();
		assertThat(run.status()).isEqualTo(RunStatus.FAILED);
		assertThat(run.failedStage()).isEqualTo(FailedStage.GENERATION);
		assertThat(run.errorMessage()).contains("LLM call failed");
		assertThat(run.headSha()).isEqualTo(fixture.snapshot().headSha());
		assertThat(run.finishedAt()).isNotNull();
		assertThat(runs.findFindings(id)).isEmpty();
	}

	@Test
	void missingPullRequestProducesAFailedRunWithStageFetchAndTheFetchedShasAreStored() {
		var orchestrator = orchestratorWith(_ -> new GenerationResult(new CandidateFindings(null), "fake", "fake-model", "review-v2", 1, 1));

		assertThatThrownBy(() -> orchestrator.reviewFetched(RunSource.PR, "Rahu09/order-service-demo", 404, () -> {
			throw new io.github.rahu09.prreviewer.github.GitHubException("GitHub 404 for pull request: not found", 404, null);
		}, null)).isInstanceOf(io.github.rahu09.prreviewer.github.GitHubException.class);

		UUID failedId = jdbc.sql("SELECT id FROM review_runs WHERE failed_stage = 'FETCH' ORDER BY started_at DESC LIMIT 1").query(UUID.class).single();
		ReviewRun failed = runs.findRun(failedId).orElseThrow();
		assertThat(failed.source()).isEqualTo(RunSource.PR);
		assertThat(failed.prNumber()).isEqualTo(404);
		assertThat(failed.status()).isEqualTo(RunStatus.FAILED);

		var fixture = fixtures.load("list-orders-paginated");
		ReviewOutcome ok = orchestrator.reviewFetched(RunSource.PR, "Rahu09/order-service-demo", 3, fixture::snapshot, null);
		assertThat(runs.findRun(ok.runId()).orElseThrow().headSha()).isEqualTo(fixture.snapshot().headSha());
	}

	@Test
	void damagedDiffProducesAFailedRunWithStageParsing() {
		String truncated = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,5 +1,5 @@\n a\n";

		assertThatThrownBy(() -> orchestratorWith(_ -> {
			throw new AssertionError("no model call expected");
		}).review(ReviewSnapshot.ofDiff(truncated), RunSource.DIFF, null)).isInstanceOf(io.github.rahu09.prreviewer.context.DiffParseException.class);

		UUID id = jdbc.sql("SELECT id FROM review_runs WHERE failed_stage = 'PARSING' ORDER BY started_at DESC LIMIT 1").query(UUID.class).single();
		assertThat(runs.findRun(id).orElseThrow().status()).isEqualTo(RunStatus.FAILED);
	}

	@Test
	void findingsAreAllOrNothingWithTheirRun() {
		// A run that cannot be completed (unknown id) must not leave findings behind: complete() is one transaction.
		var fixture = fixtures.load("list-orders-unpaginated");
		ReviewOutcome outcome = orchestratorWith(annotated -> new GenerationResult(new CandidateFindings(List.of(
				new CandidateFinding(idOfLineContaining(annotated, "List<Order> list()"), "GUIDELINE", "t", "e", "", ""))),
				"fake", "fake-model", "review-v2", 1, 1)).review(fixture.snapshot(), RunSource.FIXTURE, null);
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> runs.complete(unknown, outcome, 1, 1, java.time.Instant.now())).isInstanceOf(IllegalStateException.class);

		assertThat(runs.findFindings(unknown)).isEmpty();
	}

	@Test
	void getRunEndpointReturnsTheRunWithItsFindingsAnd404ForUnknownIds() throws Exception {
		var fixture = fixtures.load("list-orders-unpaginated");
		ReviewOutcome outcome = orchestratorWith(annotated -> new GenerationResult(new CandidateFindings(List.of(
				new CandidateFinding(idOfLineContaining(annotated, "List<Order> list()"), "GUIDELINE", "Unbounded list", "e", "", ""))),
				"fake", "fake-model", "review-v2", 7, 3)).review(fixture.snapshot(), RunSource.FIXTURE, null);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new RunController(runs)).setControllerAdvice(new ApiExceptionHandler()).build();

		mvc.perform(get("/api/runs/" + outcome.runId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.run.headSha").value(fixture.snapshot().headSha()))
				.andExpect(jsonPath("$.run.inputTokens").value(7))
				.andExpect(jsonPath("$.findings[0].status").value("PUBLISHABLE"))
				.andExpect(jsonPath("$.findings[0].filePath").value(fixture.meta().expectedFindings().getFirst().file()));

		mvc.perform(get("/api/runs/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	private long count() {
		return jdbc.sql("SELECT count(*) FROM review_runs").query(Long.class).single();
	}
}
