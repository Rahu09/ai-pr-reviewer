package io.github.rahu09.prreviewer.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * GitHub App credentials (Chunk 11). When {@code id} is set the App replaces the PAT (ADR-0010).
 * {@code privateKeyPath} points to the PKCS#8 key file outside the repo (pitfalls §6); {@code installationId} is optional
 * because the App is installed on one repo only, so it can be looked up.
 */
@ConfigurationProperties("reviewer.github-app")
public record GitHubAppProperties(
		@DefaultValue("") String id,
		@DefaultValue("") String privateKeyPath,
		@DefaultValue("") String installationId) {

	public boolean configured() {
		return !id.isBlank();
	}
}
