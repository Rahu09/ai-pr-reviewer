package io.github.rahu09.prreviewer.webhook;

import static io.github.rahu09.prreviewer.webhook.WebhookTestData.SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rahu09.prreviewer.StubReviewGeneratorConfiguration;
import io.github.rahu09.prreviewer.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Real Spring wiring and the real schema: signature gate, dedupe on the primary key, review started only after commit. */
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, StubReviewGeneratorConfiguration.class })
@TestPropertySource(properties = "reviewer.webhook.secret=" + SECRET)
@SpringBootTest
class WebhookEndToEndTest {

	@Autowired
	WebApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	InProcessReviewStarter starter;   // the real one would call GitHub and an LLM

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	private static MockHttpServletRequestBuilder delivery(String id, String json, String signature) {
		return post("/webhooks/github").contentType(MediaType.APPLICATION_JSON).content(json.getBytes(StandardCharsets.UTF_8))
				.header("X-Hub-Signature-256", signature)
				.header("X-GitHub-Event", "pull_request")
				.header("X-GitHub-Delivery", id);
	}

	private static MockHttpServletRequestBuilder signedDelivery(String id, String json) {
		return delivery(id, json, WebhookTestData.sign(SECRET, json.getBytes(StandardCharsets.UTF_8)));
	}

	private long rows(String id) {
		return jdbc.sql("SELECT count(*) FROM webhook_deliveries WHERE delivery_id = :id").param("id", id).query(Long.class).single();
	}

	@Test
	void aRedeliveryCreatesNoSecondRecordAndNoSecondReview() throws Exception {
		String id = UUID.randomUUID().toString();
		String json = WebhookTestData.trustedOpened();

		mvc.perform(signedDelivery(id, json)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("ACCEPTED"));
		mvc.perform(signedDelivery(id, json)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));

		assertThat(rows(id)).isEqualTo(1);
		assertThat(jdbc.sql("SELECT outcome FROM webhook_deliveries WHERE delivery_id = :id").param("id", id).query(String.class).single()).isEqualTo("ACCEPTED");
		// The review was requested exactly once, and only after the row was committed (AFTER_COMMIT listener).
		verify(starter, times(1)).onReviewRequested(new ReviewStartRequested("Rahu09", "order-service-demo", 1, "headsha1", id));
	}

	@Test
	void aBadSignatureIs401AndStoresNothing() throws Exception {
		String id = UUID.randomUUID().toString();

		mvc.perform(delivery(id, WebhookTestData.trustedOpened(), "sha256=" + "00".repeat(32))).andExpect(status().isUnauthorized());

		assertThat(rows(id)).isZero();
		verify(starter, never()).onReviewRequested(any());
	}

	@Test
	void aForkPullRequestIsStoredAsUntrustedAndStartsNothing() throws Exception {
		String id = UUID.randomUUID().toString();
		String json = WebhookTestData.pullRequestEvent("opened", false, "Rahu09", "stranger/order-service-demo", WebhookTestData.REPO);

		mvc.perform(signedDelivery(id, json)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("IGNORED_UNTRUSTED"));

		assertThat(jdbc.sql("SELECT outcome FROM webhook_deliveries WHERE delivery_id = :id").param("id", id).query(String.class).single()).isEqualTo("IGNORED_UNTRUSTED");
		verify(starter, never()).onReviewRequested(any());
	}
}
