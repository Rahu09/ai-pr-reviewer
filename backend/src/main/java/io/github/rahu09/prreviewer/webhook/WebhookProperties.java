package io.github.rahu09.prreviewer.webhook;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Webhook settings. {@code secret} is the App's webhook secret (never logged). Only repos in {@code allowedRepos} and
 * PR authors in {@code allowedAuthors} are reviewed (ADR-0019); an empty list allows nobody.
 */
@ConfigurationProperties("reviewer.webhook")
public record WebhookProperties(
		@DefaultValue("") String secret,
		@DefaultValue List<String> allowedRepos,
		@DefaultValue List<String> allowedAuthors) {

	public WebhookProperties {
		allowedRepos = allowedRepos.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
		allowedAuthors = allowedAuthors.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
	}

	public boolean isAllowedRepo(String fullName) {
		return fullName != null && allowedRepos.stream().anyMatch(fullName::equalsIgnoreCase);
	}

	public boolean isAllowedAuthor(String login) {
		return login != null && allowedAuthors.stream().anyMatch(login::equalsIgnoreCase);
	}

	@Override
	public String toString() {
		return "WebhookProperties[secret=" + (secret.isBlank() ? "<unset>" : "<hidden>") + ", allowedRepos=" + allowedRepos
				+ ", allowedAuthors=" + allowedAuthors + "]";
	}
}
