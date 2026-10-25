package io.github.rahu09.prreviewer.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rahu09.prreviewer.github.PullRequest;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** What a delivery contains, by event type. Sealed, so every {@code switch} over it must handle all three cases. */
public sealed interface WebhookPayload {

	/** GitHub's "is this URL alive" delivery right after the App is created or its URL changes. */
	record Ping() implements WebhookPayload {
	}

	/** The parts of a {@code pull_request} event we use; unknown fields are ignored. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record PullRequestEvent(String action, @JsonProperty("pull_request") PullRequest pullRequest, PullRequest.Repo repository)
			implements WebhookPayload {

		public PullRequestEvent {
			Objects.requireNonNull(action, "action");
			Objects.requireNonNull(pullRequest, "pull_request");
			Objects.requireNonNull(repository, "repository");
		}
	}

	/** An event type we did not subscribe to or do not handle. */
	record Unsupported(String event) implements WebhookPayload {
	}

	/** The body was not what the event header promised (only reachable with a valid signature). */
	class MalformedException extends RuntimeException {

		MalformedException(String event, Throwable cause) {
			super("Malformed " + event + " payload", cause);
		}
	}

	static WebhookPayload parse(String event, byte[] body, JsonMapper json) {
		return switch (event) {
			case "ping" -> new Ping();
			case "pull_request" -> {
				try {
					yield json.readValue(body, PullRequestEvent.class);
				}
				catch (JacksonException | NullPointerException e) {
					throw new MalformedException(event, e);
				}
			}
			default -> new Unsupported(event);
		};
	}
}
