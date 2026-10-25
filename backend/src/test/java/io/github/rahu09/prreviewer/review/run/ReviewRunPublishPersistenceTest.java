package io.github.rahu09.prreviewer.review.run;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.StubReviewGeneratorConfiguration;
import io.github.rahu09.prreviewer.TestcontainersConfiguration;
import io.github.rahu09.prreviewer.publishing.FindingValidator;
import io.github.rahu09.prreviewer.review.ReviewLimits;
import io.github.rahu09.prreviewer.review.ReviewOrchestrator;
import io.github.rahu09.prreviewer.review.fixture.FixtureLoader;
import io.github.rahu09.prreviewer.review.fixture.FixtureProperties;
import io.github.rahu09.prreviewer.review.model.CandidateFindings;
import io.github.rahu09.prreviewer.review.model.GenerationResult;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.model.RunStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/** V4 and the publish columns against the real schema (Testcontainers Postgres). */
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, StubReviewGeneratorConfiguration.class })
@SpringBootTest
class ReviewRunPublishPersistenceTest {

	@Autowired
	ReviewRunRepository runs;

	@Autowired
	ReviewLimits limits;

	private final FixtureLoader fixtures = new FixtureLoader(new FixtureProperties("../evaluation/datasets"), JsonMapper.builder().build());

	private UUID succeededRun() {
		var orchestrator = new ReviewOrchestrator(_ -> new GenerationResult(new CandidateFindings(null), "fake", "fake-model", "review-v2", 1, 1),
				new FindingValidator(), limits, runs);
		return orchestrator.review(fixtures.load("list-orders-unpaginated").snapshot(), RunSource.PR, null).runId();
	}

	@Test
	void aNewRunHasNoReviewUntilOneIsRecordedAndTheIdSurvivesTheRoundTrip() {
		UUID id = succeededRun();
		ReviewRun before = runs.findRun(id).orElseThrow();
		assertThat(before.githubReviewId()).isNull();
		assertThat(before.publishedAt()).isNull();
		assertThat(before.publishSummaryOnly()).isFalse();

		// A real GitHub review id is larger than an int, so this also proves the column is a bigint.
		runs.recordPublished(id, 4_100_000_000L, true, Instant.parse("2026-10-25T12:00:00Z"));

		ReviewRun after = runs.findRun(id).orElseThrow();
		assertThat(after.githubReviewId()).isEqualTo(4_100_000_000L);
		assertThat(after.publishedAt()).isEqualTo(Instant.parse("2026-10-25T12:00:00Z"));
		assertThat(after.publishSummaryOnly()).isTrue();
		assertThat(after.status()).isEqualTo(RunStatus.SUCCEEDED);
	}

	@Test
	void supersedingMarksAFinishedUnpublishedRunButNeverAPublishedOne() {
		UUID unpublished = succeededRun();
		UUID published = succeededRun();
		runs.recordPublished(published, 1L, false, Instant.now());

		runs.markSuperseded(unpublished);
		runs.markSuperseded(published);

		assertThat(runs.findRun(unpublished).orElseThrow().status()).isEqualTo(RunStatus.SUPERSEDED);
		assertThat(runs.findRun(published).orElseThrow().status()).isEqualTo(RunStatus.SUCCEEDED);
	}
}
