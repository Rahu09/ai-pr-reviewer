package io.github.rahu09.prreviewer.review.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rahu09.prreviewer.github.GitHubException;
import io.github.rahu09.prreviewer.publishing.PublishNotAllowedException;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.AlreadyPosted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Posted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Superseded;
import io.github.rahu09.prreviewer.publishing.ReviewPublisher;
import io.github.rahu09.prreviewer.review.run.RunNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublishControllerTest {

	private final ReviewPublisher publisher = mock(ReviewPublisher.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublishController(publisher)).setControllerAdvice(new ApiExceptionHandler()).build();
	private final UUID runId = UUID.randomUUID();

	@Test
	void everyOutcomeHasItsOwnShapeInTheResponse() throws Exception {
		when(publisher.publish(runId)).thenReturn(new Posted(55L, 2, false));
		mvc.perform(post("/api/reviews/" + runId + "/publish"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("POSTED"))
				.andExpect(jsonPath("$.reviewId").value(55))
				.andExpect(jsonPath("$.inlineComments").value(2))
				.andExpect(jsonPath("$.summaryOnly").value(false));

		when(publisher.publish(runId)).thenReturn(new AlreadyPosted(55L));
		mvc.perform(post("/api/reviews/" + runId + "/publish"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("ALREADY_POSTED"))
				.andExpect(jsonPath("$.reviewId").value(55));

		when(publisher.publish(runId)).thenReturn(new Superseded("head moved from a to b"));
		mvc.perform(post("/api/reviews/" + runId + "/publish"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("SUPERSEDED"))
				.andExpect(jsonPath("$.reason").value("head moved from a to b"));
	}

	@Test
	void unknownRunIs404NonPrRunIs409AndGitHubFailureIs502() throws Exception {
		// doThrow, not when(): re-stubbing with when() would call the previous stub.
		doThrow(new RunNotFoundException(runId)).when(publisher).publish(runId);
		mvc.perform(post("/api/reviews/" + runId + "/publish")).andExpect(status().isNotFound());

		doThrow(new PublishNotAllowedException("Run reviewed a FIXTURE, not a pull request")).when(publisher).publish(runId);
		mvc.perform(post("/api/reviews/" + runId + "/publish"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("FIXTURE")));

		doThrow(new GitHubException("GitHub 403: no permission", 403, null)).when(publisher).publish(runId);
		mvc.perform(post("/api/reviews/" + runId + "/publish")).andExpect(status().isBadGateway());
	}

	@Test
	void malformedRunIdIs400() throws Exception {
		mvc.perform(post("/api/reviews/not-a-uuid/publish")).andExpect(status().isBadRequest());
	}
}
