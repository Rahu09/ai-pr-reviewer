package io.github.rahu09.prreviewer.review;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rahu09.prreviewer.github.GitHubSnapshotProvider;
import io.github.rahu09.prreviewer.publishing.FindingValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class DryRunControllerTest {

	private static final String DIFF = "diff --git a/A.java b/A.java\\n--- a/A.java\\n+++ b/A.java\\n@@ -1,2 +1,3 @@\\n class A {\\n+  int x = 1;\\n }\\n";

	private final FixtureLoader fixtures = new FixtureLoader(new FixtureProperties("../evaluation/datasets"), JsonMapper.builder().build());

	private final GitHubSnapshotProvider github = org.mockito.Mockito.mock(GitHubSnapshotProvider.class);

	private MockMvc mvc(ReviewGenerator generator) {
		var orchestrator = new ReviewOrchestrator(generator, new FindingValidator(), new ReviewLimits(10, 400, 5, 50, 60_000), new InMemoryReviewRunRepository());
		return MockMvcBuilders.standaloneSetup(new DryRunController(orchestrator, fixtures, github))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	private static ReviewGenerator answering(CandidateFinding... findings) {
		return annotated -> new GenerationResult(new CandidateFindings(List.of(findings)), "stub", "stub-model", "review-v2", 10, 5);
	}

	@Test
	void diffEndpointReturnsValidatedFindingsWithPathAndLineMappedFromTheLocationId() throws Exception {
		var finding = new CandidateFinding("L1", "GUIDELINE", "Title", "Explanation", "Fix", "Test");

		mvc(answering(finding)).perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON)
				.content("{\"diff\":\"" + DIFF + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("stub"))
				.andExpect(jsonPath("$.inputTokens").value(10))
				.andExpect(jsonPath("$.allowedLocations").value(1))
				.andExpect(jsonPath("$.findings[0].status").value("PUBLISHABLE"))
				.andExpect(jsonPath("$.findings[0].path").value("A.java"))
				.andExpect(jsonPath("$.findings[0].line").value(2));
	}

	@Test
	void invalidLocationShowsUpAsSuppressedWithAReason() throws Exception {
		var finding = new CandidateFinding("L42", "GUIDELINE", "Title", "Explanation", "", "");

		mvc(answering(finding)).perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON)
				.content("{\"diff\":\"" + DIFF + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.findings[0].status").value("SUPPRESSED"))
				.andExpect(jsonPath("$.findings[0].reason").value(org.hamcrest.Matchers.containsString("L42")));
	}

	@Test
	void fixtureEndpointIncludesTheAnswerKeyAndAcceptsAnEmptyBody() throws Exception {
		mvc(answering()).perform(post("/api/dry-run/fixture/list-orders-unpaginated"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expected.kind").value("buggy"))
				.andExpect(jsonPath("$.expected.expectedFindings[0].ruleId").value("API-03"))
				.andExpect(jsonPath("$.filesReviewed.length()").value(5));
	}

	@Test
	void fixtureEndpointAppliesAConfigOverride() throws Exception {
		mvc(answering()).perform(post("/api/dry-run/fixture/list-orders-unpaginated")
				.contentType(MediaType.APPLICATION_JSON).content("{\"config\":{\"maxJavaFiles\":2}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filesReviewed.length()").value(2))
				.andExpect(jsonPath("$.filesSkipped[0].reason").value("TOO_MANY_FILES"));
	}

	@Test
	void prEndpointFetchesTheSnapshotAndReturnsTheRunId() throws Exception {
		var snapshot = fixtures.load("list-orders-unpaginated").snapshot();
		org.mockito.Mockito.when(github.fetch("Rahu09", "order-service-demo", 1)).thenReturn(snapshot);

		mvc(answering()).perform(post("/api/dry-run/pr").contentType(MediaType.APPLICATION_JSON)
				.content("{\"owner\":\"Rahu09\",\"repo\":\"order-service-demo\",\"number\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.runId").exists())
				.andExpect(jsonPath("$.filesReviewed.length()").value(5));
	}

	@Test
	void prEndpointRejectsBadOwnerAndNumberAndMapsGitHubNotFoundTo404() throws Exception {
		MockMvc mvc = mvc(answering());
		mvc.perform(post("/api/dry-run/pr").contentType(MediaType.APPLICATION_JSON)
				.content("{\"owner\":\"../x\",\"repo\":\"r\",\"number\":1}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/dry-run/pr").contentType(MediaType.APPLICATION_JSON)
				.content("{\"owner\":\"o\",\"repo\":\"r\",\"number\":0}")).andExpect(status().isBadRequest());

		org.mockito.Mockito.when(github.fetch("o", "r", 9))
				.thenThrow(new io.github.rahu09.prreviewer.github.GitHubException("GitHub 404 for pull request o/r#9: not found", 404, null));
		mvc.perform(post("/api/dry-run/pr").contentType(MediaType.APPLICATION_JSON)
				.content("{\"owner\":\"o\",\"repo\":\"r\",\"number\":9}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not found")));
	}

	@Test
	void unknownFixtureIs404AndPathTraversalIsRefused() throws Exception {
		MockMvc mvc = mvc(answering());

		mvc.perform(post("/api/dry-run/fixture/does-not-exist")).andExpect(status().isNotFound());
		mvc.perform(post("/api/dry-run/fixture/..%2Ffixture")).andExpect(status().isNotFound());
	}

	@Test
	void damagedDiffIs400WithAClearMessage() throws Exception {
		String truncated = "diff --git a/A.java b/A.java\\n--- a/A.java\\n+++ b/A.java\\n@@ -1,5 +1,5 @@\\n a\\n";

		mvc(answering()).perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON)
				.content("{\"diff\":\"" + truncated + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Invalid diff")));
	}

	@Test
	void blankDiffIsRejected() throws Exception {
		ReviewGenerator mustNotRun = annotated -> {
			throw new AssertionError("must not be called");
		};

		mvc(mustNotRun)
				.perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON).content("{\"diff\":\"  \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void providerFailureBecomesA502ProblemWithoutStackTrace() throws Exception {
		ReviewGenerator failing = annotated -> {
			throw new ReviewGenerationException("Model returned malformed output after 2 attempts", null);
		};

		mvc(failing).perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON).content("{\"diff\":\"" + DIFF + "\"}"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.detail").value("Model returned malformed output after 2 attempts"));
	}
}
