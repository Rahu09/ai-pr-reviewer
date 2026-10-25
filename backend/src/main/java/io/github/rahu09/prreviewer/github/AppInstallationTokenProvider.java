package io.github.rahu09.prreviewer.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Authenticates as the GitHub App (ADR-0010): signs a short-lived RS256 JWT with the App's private key (JDK only, no JWT
 * library), exchanges it for an installation access token and caches that token until shortly before it expires.
 */
public class AppInstallationTokenProvider implements GitHubTokenProvider {

	private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

	/** Installation access token; unknown fields are ignored. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record InstallationToken(String token, @JsonProperty("expires_at") Instant expiresAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Installation(long id) {
	}

	private final GitHubAppProperties app;
	private final RestClient http;
	private final Clock clock;

	private PrivateKey privateKey;
	private Long installationId;
	private InstallationToken cached;

	public AppInstallationTokenProvider(GitHubAppProperties app, GitHubProperties github, RestClient.Builder builder, Clock clock) {
		this.app = app;
		this.http = builder.baseUrl(github.apiUrl())
				.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
				.defaultHeader(HttpHeaders.USER_AGENT, "ai-pr-reviewer")
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.build();
		this.clock = clock;
		if (!app.id().matches("\\d{1,20}")) {
			throw new IllegalArgumentException("GITHUB_APP_ID must be the numeric App ID");
		}
	}

	// synchronized: one refresh at a time. The HTTP call inside is fine on virtual threads (no pinning since Java 24).
	@Override
	public synchronized String token() {
		Instant now = clock.instant();
		if (cached != null && now.isBefore(cached.expiresAt().minus(REFRESH_MARGIN))) {
			return cached.token();
		}
		String jwt = jwt(loadKey(), app.id(), now);
		long installation = installationId(jwt);
		InstallationToken fresh = call("installation token", () -> http.post()
				.uri(u -> u.pathSegment("app", "installations", String.valueOf(installation), "access_tokens").build())
				.headers(h -> h.setBearerAuth(jwt))
				.retrieve()
				.body(InstallationToken.class));
		if (fresh == null || fresh.token() == null || fresh.expiresAt() == null) {
			throw new GitHubException("GitHub returned no installation token", 0, null);
		}
		cached = fresh;
		return fresh.token();
	}

	/** The App JWT: RS256, issued 60 s in the past (clock drift), valid 9 minutes (GitHub allows at most 10). */
	static String jwt(PrivateKey key, String appId, Instant now) {
		String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payload = base64Url("{\"iat\":%d,\"exp\":%d,\"iss\":\"%s\"}"
				.formatted(now.getEpochSecond() - 60, now.getEpochSecond() + 540, appId).getBytes(StandardCharsets.UTF_8));
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(key);
			signature.update((header + "." + payload).getBytes(StandardCharsets.UTF_8));
			return header + "." + payload + "." + base64Url(signature.sign());
		}
		catch (GeneralSecurityException e) {
			throw new GitHubException("Could not sign the GitHub App JWT: " + e.getMessage(), 0, e);
		}
	}

	private static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private long installationId(String jwt) {
		if (installationId != null) {
			return installationId;
		}
		if (!app.installationId().isBlank()) {
			installationId = Long.parseLong(app.installationId().strip());
			return installationId;
		}
		Installation[] all = call("app installations", () -> http.get()
				.uri(u -> u.pathSegment("app", "installations").build())
				.headers(h -> h.setBearerAuth(jwt))
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(Installation[].class));
		if (all == null || all.length != 1) {
			throw new GitHubException("Expected the App to be installed exactly once but found " + (all == null ? 0 : all.length)
					+ "; set GITHUB_APP_INSTALLATION_ID", 0, null);
		}
		installationId = all[0].id();
		return installationId;
	}

	private PrivateKey loadKey() {
		if (privateKey != null) {
			return privateKey;
		}
		if (app.privateKeyPath().isBlank()) {
			throw new GitHubException("GITHUB_APP_PRIVATE_KEY_PATH is not set", 0, null);
		}
		try {
			String pem = Files.readString(Path.of(app.privateKeyPath()));
			if (pem.contains("BEGIN RSA PRIVATE KEY")) {
				throw new GitHubException("The App key is PKCS#1; convert it to PKCS#8 with 'openssl pkcs8 -topk8 -nocrypt' (pitfalls §6)", 0, null);
			}
			String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
			privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
			return privateKey;
		}
		catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
			throw new GitHubException("Cannot read the GitHub App private key at " + app.privateKeyPath() + ": " + e.getClass().getSimpleName(), 0, e);
		}
	}

	private static <T> T call(String what, java.util.function.Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			String hint = status == 401 ? "JWT rejected (wrong App ID, wrong key, or clock drift)" : status == 404 ? "App or installation not found" : "unexpected response";
			throw new GitHubException("GitHub " + status + " for " + what + ": " + hint, status, e);
		}
	}
}
