package io.github.rahu09.prreviewer.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one public entry point besides the health check (PLAN §6 rule 12). Order matters: verify the signature over the
 * raw bytes FIRST, and only then look at the headers or parse anything, so an unauthenticated caller reaches no logic.
 */
@RestController
@RequestMapping("/webhooks/github")
public class GitHubWebhookController {

	private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);
	private static final int MAX_DELIVERY_ID_CHARS = 100;

	public record WebhookResponse(WebhookOutcome outcome) {
	}

	private final SignatureVerifier signatures;
	private final WebhookService service;
	private final JsonMapper json;

	public GitHubWebhookController(SignatureVerifier signatures, WebhookService service, JsonMapper json) {
		this.signatures = signatures;
		this.service = service;
		this.json = json;
	}

	@PostMapping
	public ResponseEntity<?> receive(
			@RequestBody byte[] rawBody,
			@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestHeader(value = "X-GitHub-Event", required = false) String event,
			@RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId) {
		if (!signatures.isValid(rawBody, signature)) {
			log.warn("Rejected a webhook call with a missing or invalid signature");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid signature"));
		}
		if (event == null || event.isBlank() || deliveryId == null || deliveryId.isBlank() || deliveryId.length() > MAX_DELIVERY_ID_CHARS) {
			return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Missing or invalid X-GitHub-Event / X-GitHub-Delivery"));
		}
		WebhookPayload payload;
		try {
			payload = WebhookPayload.parse(event, rawBody, json);
		}
		catch (WebhookPayload.MalformedException e) {
			return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
		}
		return ResponseEntity.ok(new WebhookResponse(service.handle(deliveryId, event, payload)));
	}
}
