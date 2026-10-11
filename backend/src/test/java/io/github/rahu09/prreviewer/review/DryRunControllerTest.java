package io.github.rahu09.prreviewer.review;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DryRunControllerTest {

	private MockMvc mvc(ReviewGenerator generator) {
		return MockMvcBuilders.standaloneSetup(new DryRunController(generator))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void returnsFindingsAndUsage() throws Exception {
		ReviewGenerator generator = diff -> new GenerationResult(
				new CandidateFindings(List.of(new CandidateFinding("A.java", 3, "GUIDELINE", "t", "e", "f", "x"))),
				"stub", "stub-model", "review-v1", 10, 5);

		mvc(generator).perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON)
				.content("{\"diff\":\"+int a;\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("stub"))
				.andExpect(jsonPath("$.inputTokens").value(10))
				.andExpect(jsonPath("$.findings[0].category").value("GUIDELINE"));
	}

	@Test
	void blankDiffIsRejected() throws Exception {
		ReviewGenerator mustNotRun = diff -> {
			throw new AssertionError("must not be called");
		};

		mvc(mustNotRun)
				.perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON).content("{\"diff\":\"  \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void providerFailureBecomesA502ProblemWithoutStackTrace() throws Exception {
		ReviewGenerator failing = diff -> {
			throw new ReviewGenerationException("Model returned malformed output after 2 attempts", null);
		};

		mvc(failing).perform(post("/api/dry-run/diff").contentType(MediaType.APPLICATION_JSON).content("{\"diff\":\"+int a;\"}"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.detail").value("Model returned malformed output after 2 attempts"));
	}
}
