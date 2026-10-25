package io.github.rahu09.prreviewer.webhook;

import static io.github.rahu09.prreviewer.webhook.WebhookTestData.SECRET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/** The HTTP edge: the signature gate comes first, before any header check, parsing or business logic. */
class GitHubWebhookControllerTest {

	private final WebhookService service = mock(WebhookService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
			new GitHubWebhookController(new SignatureVerifier(WebhookTestData.properties(SECRET)), service, JsonMapper.builder().build())).build();

	private static MockHttpServletRequestBuilder signed(String event, String delivery, String json) {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		return post("/webhooks/github").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-Hub-Signature-256", WebhookTestData.sign(SECRET, body))
				.header("X-GitHub-Event", event)
				.header("X-GitHub-Delivery", delivery);
	}

	@Test
	void aBadSignatureGets401AndNothingElseHappens() throws Exception {
		byte[] body = WebhookTestData.trustedOpened().getBytes(StandardCharsets.UTF_8);

		mvc.perform(post("/webhooks/github").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-Hub-Signature-256", WebhookTestData.sign("not-the-secret", body))
				.header("X-GitHub-Event", "pull_request").header("X-GitHub-Delivery", "d1"))
				.andExpect(status().isUnauthorized());

		verify(service, never()).handle(any(), any(), any());
	}

	@Test
	void aMissingSignatureAndAMissingEventHeaderOnAnUnsignedCallAreBoth401() throws Exception {
		mvc.perform(post("/webhooks/github").contentType(MediaType.APPLICATION_JSON).content(WebhookTestData.trustedOpened())
				.header("X-GitHub-Event", "pull_request").header("X-GitHub-Delivery", "d1"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/webhooks/github").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());

		verify(service, never()).handle(any(), any(), any());
	}

	@Test
	void aValidlySignedPullRequestIsHandledAndTheOutcomeReturned() throws Exception {
		when(service.handle(any(), any(), any())).thenReturn(WebhookOutcome.ACCEPTED);

		mvc.perform(signed("pull_request", "d1", WebhookTestData.trustedOpened()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("ACCEPTED"));

		verify(service).handle(org.mockito.ArgumentMatchers.eq("d1"), org.mockito.ArgumentMatchers.eq("pull_request"),
				org.mockito.ArgumentMatchers.argThat(p -> p instanceof WebhookPayload.PullRequestEvent e
						&& e.action().equals("opened") && e.pullRequest().head().sha().equals("headsha1")));
	}

	@Test
	void pingIsAnswered200() throws Exception {
		when(service.handle(any(), any(), any())).thenReturn(WebhookOutcome.PING);

		mvc.perform(signed("ping", "d2", "{\"zen\":\"Keep it logically awesome.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("PING"));
	}

	@Test
	void signedButBrokenOrIncompleteBodiesAndMissingHeadersAre400() throws Exception {
		mvc.perform(signed("pull_request", "d3", "{not json")).andExpect(status().isBadRequest());
		mvc.perform(signed("pull_request", "d4", "{\"action\":\"opened\"}")).andExpect(status().isBadRequest());   // no pull_request
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		mvc.perform(post("/webhooks/github").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-Hub-Signature-256", WebhookTestData.sign(SECRET, body)))
				.andExpect(status().isBadRequest());

		verify(service, never()).handle(any(), any(), any());
	}
}
