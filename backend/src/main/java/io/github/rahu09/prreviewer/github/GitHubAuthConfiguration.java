package io.github.rahu09.prreviewer.github;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Picks how we authenticate to GitHub: the App when {@code GITHUB_APP_ID} is set, otherwise the PAT (ADR-0010). */
@Configuration
class GitHubAuthConfiguration {

	private static final Logger log = LoggerFactory.getLogger(GitHubAuthConfiguration.class);

	@Bean
	GitHubTokenProvider gitHubTokenProvider(GitHubAppProperties app, GitHubProperties github, RestClient.Builder builder) {
		if (app.configured()) {
			log.info("GitHub auth: App {} (installation token)", app.id());
			return new AppInstallationTokenProvider(app, github, builder, Clock.systemUTC());
		}
		log.info("GitHub auth: personal access token");
		return new PatTokenProvider(github);
	}
}
