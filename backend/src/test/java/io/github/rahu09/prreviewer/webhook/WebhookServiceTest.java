package io.github.rahu09.prreviewer.webhook;

import static io.github.rahu09.prreviewer.webhook.WebhookTestData.REPO;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.review.ReviewLimits;
import io.github.rahu09.prreviewer.review.run.InMemoryReviewRunRepository;
import io.github.rahu09.prreviewer.webhook.WebhookPayload.Ping;
import io.github.rahu09.prreviewer.webhook.WebhookPayload.Unsupported;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Which deliveries start a review, and which are stored and ignored. Fakes for the database, the real decision logic. */
class WebhookServiceTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Set<String> seenIds = new HashSet<>();
	private final List<WebhookDeliveryRepository.Delivery> stored = new ArrayList<>();
	private final List<Object> published = new ArrayList<>();
	private final InMemoryReviewRunRepository runs = new InMemoryReviewRunRepository();
	private final WebhookDeliveryRepository deliveries = delivery -> {
		if (!seenIds.add(delivery.deliveryId())) {
			return false;
		}
		stored.add(delivery);
		return true;
	};
	private final WebhookService service = new WebhookService(WebhookTestData.properties("s"), new ReviewLimits(10, 400, 5, 3, 60_000), runs,
			deliveries, published::add);
	private int counter = 0;

	private WebhookOutcome send(String event, String json) {
		String id = "delivery-" + (++counter);
		return service.handle(id, event, WebhookPayload.parse(event, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), JSON));
	}

	private WebhookOutcome pr(String action, boolean draft, String author, String headRepo, String baseRepo) {
		return send("pull_request", WebhookTestData.pullRequestEvent(action, draft, author, headRepo, baseRepo));
	}

	@Test
	void aTrustedSameRepoPullRequestIsAcceptedStoredAndStartsOneReview() {
		assertThat(send("pull_request", WebhookTestData.trustedOpened())).isEqualTo(WebhookOutcome.ACCEPTED);

		assertThat(stored).singleElement().satisfies(d -> {
			assertThat(d.outcome()).isEqualTo(WebhookOutcome.ACCEPTED);
			assertThat(d.repo()).isEqualTo(REPO);
			assertThat(d.prNumber()).isEqualTo(1);
			assertThat(d.headSha()).isEqualTo("headsha1");
			assertThat(d.action()).isEqualTo("opened");
		});
		assertThat(published).containsExactly(new ReviewStartRequested("Rahu09", "order-service-demo", 1, "headsha1", "delivery-1"));
	}

	@Test
	void newCommitsReopenAndReadyForReviewAreInScope() {
		assertThat(pr("synchronize", false, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.ACCEPTED);
		assertThat(pr("reopened", false, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.ACCEPTED);
		assertThat(pr("ready_for_review", false, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.ACCEPTED);
		assertThat(published).hasSize(3);
	}

	@Test
	void aForkPullRequestIsStoredAsUntrustedAndNeverReviewed() {
		assertThat(pr("opened", false, "Rahu09", "stranger/order-service-demo", REPO)).isEqualTo(WebhookOutcome.IGNORED_UNTRUSTED);

		assertThat(stored).singleElement().satisfies(d -> assertThat(d.outcome()).isEqualTo(WebhookOutcome.IGNORED_UNTRUSTED));
		assertThat(published).isEmpty();
	}

	@Test
	void aDeletedForkHasNoHeadRepoAndIsUntrusted() {
		assertThat(pr("opened", false, "Rahu09", null, REPO)).isEqualTo(WebhookOutcome.IGNORED_UNTRUSTED);
		assertThat(published).isEmpty();
	}

	@Test
	void anAuthorOutsideTheAllowlistIsUntrustedEvenOnASameRepoBranch() {
		assertThat(pr("opened", false, "someone-else", REPO, REPO)).isEqualTo(WebhookOutcome.IGNORED_UNTRUSTED);
		assertThat(published).isEmpty();
	}

	@Test
	void allowlistAndRepoComparisonsIgnoreCase() {
		assertThat(pr("opened", false, "rahu09", "rahu09/Order-Service-Demo", "RAHU09/order-service-demo")).isEqualTo(WebhookOutcome.ACCEPTED);
	}

	@Test
	void draftsOtherRepositoriesAndUninterestingActionsAreIgnoredWithTheirOwnOutcome() {
		assertThat(pr("opened", true, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.IGNORED_DRAFT);
		assertThat(pr("opened", false, "Rahu09", "Rahu09/other", "Rahu09/other")).isEqualTo(WebhookOutcome.IGNORED_REPO);
		assertThat(pr("labeled", false, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.IGNORED_EVENT);
		assertThat(pr("closed", false, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.IGNORED_EVENT);
		assertThat(stored).hasSize(4);
		assertThat(published).isEmpty();
	}

	@Test
	void theDailyCapStopsReviewsAtTheLimitButOnlyForOtherwiseTrustedPrs() {
		runs.runsToday = 3;   // limit is 3

		assertThat(pr("opened", false, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.SKIPPED_DAILY_CAP);
		assertThat(pr("opened", false, "stranger", REPO, REPO)).isEqualTo(WebhookOutcome.IGNORED_UNTRUSTED);
		assertThat(published).isEmpty();

		runs.runsToday = 2;

		assertThat(pr("opened", false, "Rahu09", REPO, REPO)).isEqualTo(WebhookOutcome.ACCEPTED);
	}

	@Test
	void aRedeliveryOfTheSameDeliveryIdDoesNothing() {
		var payload = WebhookPayload.parse("pull_request", WebhookTestData.trustedOpened().getBytes(java.nio.charset.StandardCharsets.UTF_8), JSON);

		assertThat(service.handle("same-id", "pull_request", payload)).isEqualTo(WebhookOutcome.ACCEPTED);
		assertThat(service.handle("same-id", "pull_request", payload)).isEqualTo(WebhookOutcome.DUPLICATE);

		assertThat(stored).hasSize(1);
		assertThat(published).hasSize(1);   // one review, not two
	}

	@Test
	void pingIsAnsweredWithoutStoringAndUnknownEventsAreStoredAsIgnored() {
		assertThat(service.handle("p1", "ping", new Ping())).isEqualTo(WebhookOutcome.PING);
		assertThat(stored).isEmpty();

		assertThat(service.handle("u1", "issues", new Unsupported("issues"))).isEqualTo(WebhookOutcome.IGNORED_EVENT);
		assertThat(stored).singleElement().satisfies(d -> assertThat(d.event()).isEqualTo("issues"));
		assertThat(published).isEmpty();
	}
}
