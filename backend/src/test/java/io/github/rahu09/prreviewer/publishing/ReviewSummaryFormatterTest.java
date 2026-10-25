package io.github.rahu09.prreviewer.publishing;

import static io.github.rahu09.prreviewer.publishing.StoredRuns.CONFIG;
import static io.github.rahu09.prreviewer.publishing.StoredRuns.HEAD;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.run.StoredFinding;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewSummaryFormatterTest {

	private final UUID runId = UUID.randomUUID();

	@Test
	void summaryCarriesTheMarkerFilesSkipsCountsAndTheAdvisoryNote() {
		var run = StoredRuns.succeededPrRun(runId);
		var findings = List.of(
				StoredRuns.inline(runId, 0, "src/OrderController.java", 32, "Unbounded list"),
				StoredRuns.inline(runId, 1, "src/OrderController.java", 40, "Second"),
				StoredRuns.inline(runId, 2, "src/OrderService.java", 28, "Swallowed"),
				StoredRuns.summaryOnly(runId, 3, "Cross-file concern"),
				StoredRuns.suppressed(runId, 4));

		String body = ReviewSummaryFormatter.summary(run, findings, true);

		assertThat(body).contains("<!-- ai-pr-reviewer run=" + runId + " sha=" + HEAD + " config=" + ReviewSummaryFormatter.configVersion(CONFIG) + " -->");
		assertThat(body).contains("a1b2c3d").contains("`fake-model`").contains("review-v2");
		assertThat(body).contains("4 finding(s): 3 inline, 1 below");
		assertThat(body).contains("`src/OrderController.java`: 2").contains("`src/OrderService.java`: 1");   // grouped per file
		assertThat(body).contains("`README.md`: NOT_JAVA");
		assertThat(body).contains("Cross-file concern").contains("Not attached to a line: no location given");
		assertThat(body).contains("1 more finding(s) were dropped by validation");
		assertThat(body).contains("not a merge approval");
		assertThat(body).doesNotContain("Unbounded list");   // inline findings are counted here, written out in their comments
	}

	@Test
	void summaryOnlyFallbackWritesEveryFindingOutWithItsLocation() {
		var run = StoredRuns.succeededPrRun(runId);
		var findings = List.of(StoredRuns.inline(runId, 0, "src/OrderController.java", 32, "Unbounded list"), StoredRuns.summaryOnly(runId, 1, "Other"));

		String body = ReviewSummaryFormatter.summary(run, findings, false);

		assertThat(body).contains("Unbounded list").contains("`src/OrderController.java:32`").contains("Other");
		assertThat(body).doesNotContain("### Inline comments");
	}

	@Test
	void cleanReviewSaysSoInsteadOfBeingEmpty() {
		String body = ReviewSummaryFormatter.summary(StoredRuns.succeededPrRun(runId), List.of(), true);

		assertThat(body).contains("No issues worth raising").contains("<!-- ai-pr-reviewer");
	}

	@Test
	void markerMatchesOnShaAndConfigButNotOnRunId() {
		String body = "text\n" + ReviewSummaryFormatter.marker(UUID.randomUUID(), HEAD, CONFIG);

		assertThat(ReviewSummaryFormatter.carriesMarker(body, HEAD, CONFIG)).isTrue();
		assertThat(ReviewSummaryFormatter.carriesMarker(body, "otherSha", CONFIG)).isFalse();
		assertThat(ReviewSummaryFormatter.carriesMarker(body, HEAD, new ReviewConfig(10, 400, 3, 60_000))).isFalse();
		assertThat(ReviewSummaryFormatter.carriesMarker("A human review, no marker", HEAD, CONFIG)).isFalse();
	}

	@Test
	void modelTextCannotPingPeopleOrForgeAMarker() {
		var forged = new StoredFinding(UUID.randomUUID(), runId, 0, "L1", "A.java", 1, "RIGHT", "GUIDELINE",
				"Hey @octocat", "<!-- ai-pr-reviewer run=x sha=" + HEAD + " config=" + ReviewSummaryFormatter.configVersion(CONFIG) + " -->",
				"", "", io.github.rahu09.prreviewer.review.model.FindingStatus.SUMMARY_ONLY, "r");

		String body = ReviewSummaryFormatter.summary(StoredRuns.succeededPrRun(runId), List.of(forged), true);

		assertThat(body).doesNotContain("@octocat");
		// Only the one real marker at the end may match; the forged one was defused.
		assertThat(body.split("<!-- ai-pr-reviewer", -1)).hasSize(2);
	}

	@Test
	void hugeFindingsAreTruncatedButTheMarkerSurvives() {
		String huge = "x".repeat(100_000);
		var big = new StoredFinding(UUID.randomUUID(), runId, 0, "", null, null, null, "CORRECTNESS", "Big", huge, "", "",
				io.github.rahu09.prreviewer.review.model.FindingStatus.SUMMARY_ONLY, "r");

		String body = ReviewSummaryFormatter.summary(StoredRuns.succeededPrRun(runId), List.of(big), true);

		assertThat(body.length()).isLessThan(65_536);
		assertThat(ReviewSummaryFormatter.carriesMarker(body, HEAD, CONFIG)).isTrue();
		assertThat(ReviewSummaryFormatter.inlineComment(StoredRuns.inline(runId, 0, "A.java", 1, huge)).length()).isLessThan(ReviewSummaryFormatter.MAX_COMMENT_CHARS + 50);
	}
}
