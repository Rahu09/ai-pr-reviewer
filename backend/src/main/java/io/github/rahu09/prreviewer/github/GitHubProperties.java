package io.github.rahu09.prreviewer.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** GitHub access settings. {@code pat} comes from the GITHUB_PAT env var (never GITHUB_TOKEN: pitfalls §6). */
@ConfigurationProperties("reviewer.github")
public record GitHubProperties(
		@DefaultValue("https://api.github.com") String apiUrl,
		@DefaultValue("") String pat,
		@DefaultValue("200000") int maxFileBytes) {

	/** Never print the token, even by accident in a log line or a failed assertion. */
	@Override
	public String toString() {
		return "GitHubProperties[apiUrl=" + apiUrl + ", pat=" + (pat.isBlank() ? "<unset>" : "<hidden>") + ", maxFileBytes=" + maxFileBytes + "]";
	}
}
