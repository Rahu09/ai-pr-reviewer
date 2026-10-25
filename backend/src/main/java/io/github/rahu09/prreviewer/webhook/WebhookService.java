package io.github.rahu09.prreviewer.webhook;

import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.ACCEPTED;
import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.DUPLICATE;
import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.IGNORED_DRAFT;
import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.IGNORED_EVENT;
import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.IGNORED_REPO;
import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.IGNORED_UNTRUSTED;
import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.PING;
import static io.github.rahu09.prreviewer.webhook.WebhookOutcome.SKIPPED_DAILY_CAP;

import io.github.rahu09.prreviewer.github.PullRequest;
import io.github.rahu09.prreviewer.review.ReviewLimits;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.run.ReviewRunRepository;
import io.github.rahu09.prreviewer.webhook.WebhookPayload.Ping;
import io.github.rahu09.prreviewer.webhook.WebhookPayload.PullRequestEvent;
import io.github.rahu09.prreviewer.webhook.WebhookPayload.Unsupported;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides what a verified delivery means: store it once, and start a review only if it is in scope and trusted
 * (ADR-0019). The review start is an event delivered AFTER this transaction commits, so a review can never begin
 * before the delivery row exists (pitfalls §6).
 */
@Service
public class WebhookService {

	private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

	private final WebhookProperties properties;
	private final ReviewLimits limits;
	private final ReviewRunRepository runs;
	private final WebhookDeliveryRepository deliveries;
	private final ApplicationEventPublisher events;
	private final Clock clock = Clock.systemUTC();

	public WebhookService(WebhookProperties properties, ReviewLimits limits, ReviewRunRepository runs,
			WebhookDeliveryRepository deliveries, ApplicationEventPublisher events) {
		this.properties = properties;
		this.limits = limits;
		this.runs = runs;
		this.deliveries = deliveries;
		this.events = events;
	}

	@Transactional
	public WebhookOutcome handle(String deliveryId, String eventName, WebhookPayload payload) {
		Instant now = clock.instant();
		return switch (payload) {
			case Ping _ -> PING;
			case Unsupported _ -> store(new Delivery(deliveryId, eventName, null, null, null, null), IGNORED_EVENT, now);
			case PullRequestEvent pr -> handlePullRequest(deliveryId, eventName, pr, now);
		};
	}

	private WebhookOutcome handlePullRequest(String deliveryId, String eventName, PullRequestEvent event, Instant now) {
		String repo = event.repository().fullName();
		PullRequest pr = event.pullRequest();
		WebhookOutcome outcome = classify(event, now);
		var delivery = new Delivery(deliveryId, eventName, event.action(), repo, pr.number(), pr.head().sha());
		WebhookOutcome stored = store(delivery, outcome, now);
		if (stored == ACCEPTED) {
			String[] parts = repo.split("/", 2);
			events.publishEvent(new ReviewStartRequested(parts[0], parts[1], pr.number(), pr.head().sha(), deliveryId));
		}
		return stored;
	}

	/** Cheapest checks first; the database is only asked about the cap for a PR that is otherwise going to be reviewed. */
	private WebhookOutcome classify(PullRequestEvent event, Instant now) {
		PullRequest pr = event.pullRequest();
		if (!properties.isAllowedRepo(event.repository().fullName())) {
			return IGNORED_REPO;
		}
		if (!inScope(event.action())) {
			return IGNORED_EVENT;
		}
		if (pr.draft()) {
			return IGNORED_DRAFT;
		}
		if (!sameRepo(pr, event.repository()) || pr.user() == null || !properties.isAllowedAuthor(pr.user().login())) {
			return IGNORED_UNTRUSTED;
		}
		if (runs.countStartedSince(RunSource.PR, now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)) >= limits.maxReviewsPerDay()) {
			return SKIPPED_DAILY_CAP;
		}
		return ACCEPTED;
	}

	/** New commits or a (re)opened, ready PR need a review; everything else (labels, edits, closes...) does not. */
	private static boolean inScope(String action) {
		return switch (action) {
			case "opened", "reopened", "synchronize", "ready_for_review" -> true;
			default -> false;
		};
	}

	/** A fork's head repo differs from the base repo; a deleted fork has no head repo at all, so it is untrusted too. */
	private static boolean sameRepo(PullRequest pr, PullRequest.Repo base) {
		return pr.head().repo() != null && pr.head().repo().fullName() != null && pr.head().repo().fullName().equalsIgnoreCase(base.fullName());
	}

	private WebhookOutcome store(Delivery d, WebhookOutcome outcome, Instant now) {
		boolean isNew = deliveries.recordIfNew(new WebhookDeliveryRepository.Delivery(d.id(), d.event(), d.action(), d.repo(), d.prNumber(), d.headSha(), outcome, now));
		if (!isNew) {
			log.info("Delivery {} was already handled: ignoring the redelivery", d.id());
			return DUPLICATE;
		}
		log.info("Delivery {} ({} {}): {}", d.id(), d.event(), d.action(), outcome);
		return outcome;
	}

	private record Delivery(String id, String event, String action, String repo, Integer prNumber, String headSha) {
	}
}
