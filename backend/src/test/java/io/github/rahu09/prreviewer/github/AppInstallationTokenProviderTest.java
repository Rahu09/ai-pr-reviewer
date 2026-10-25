package io.github.rahu09.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** App authentication with a throwaway RSA key and a fake GitHub: no network, nothing real. */
class AppInstallationTokenProviderTest {

	private static final GitHubProperties GITHUB = new GitHubProperties("https://api.github.com", "", 1);
	private static final Instant T0 = Instant.parse("2026-10-26T10:00:00Z");

	/** A clock the test can move. */
	private static final class MovableClock extends Clock {
		Instant now = T0;

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@TempDir
	Path dir;

	private KeyPair keys;
	private Path keyFile;
	private final MovableClock clock = new MovableClock();
	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final List<String> authorizations = new ArrayList<>();

	@BeforeEach
	void generateKey() throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		keys = generator.generateKeyPair();
		keyFile = dir.resolve("app-pkcs8.pem");
		String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		Files.writeString(keyFile, pem);
	}

	private AppInstallationTokenProvider provider(String installationId) {
		return new AppInstallationTokenProvider(new GitHubAppProperties("12345", keyFile.toString(), installationId), GITHUB, builder, clock);
	}

	private void expectTokenExchange(long installation, String token, Instant expiresAt) {
		server.expect(requestTo("https://api.github.com/app/installations/" + installation + "/access_tokens"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(request -> authorizations.add(request.getHeaders().getFirst("Authorization")))
				.andRespond(withSuccess("{\"token\":\"%s\",\"expires_at\":\"%s\",\"permissions\":{}}".formatted(token, expiresAt), MediaType.APPLICATION_JSON));
	}

	@Test
	void exchangesASignedJwtForAnInstallationTokenAndTheJwtVerifiesWithThePublicKey() throws Exception {
		expectTokenExchange(777, "ghs_first", T0.plusSeconds(3600));

		assertThat(provider("777").token()).isEqualTo("ghs_first");

		String jwt = authorizations.getFirst().substring("Bearer ".length());
		String[] parts = jwt.split("\\.");
		assertThat(parts).hasSize(3);
		var json = JsonMapper.builder().build();
		JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
		JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
		assertThat(header.get("alg").asText()).isEqualTo("RS256");
		assertThat(claims.get("iss").asText()).isEqualTo("12345");
		assertThat(claims.get("iat").asLong()).isEqualTo(T0.getEpochSecond() - 60);       // backdated for clock drift
		assertThat(claims.get("exp").asLong()).isEqualTo(T0.getEpochSecond() + 540);      // GitHub's maximum is 10 minutes
		assertThat(jwt).doesNotContain("=");                                              // URL-safe Base64 without padding

		Signature verify = Signature.getInstance("SHA256withRSA");
		verify.initVerify(keys.getPublic());
		verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
		assertThat(verify.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
		server.verify();
	}

	@Test
	void theTokenIsCachedUntilFiveMinutesBeforeItExpiresThenRefreshed() {
		// Expectations must all be set before the first request; they are consumed in order.
		expectTokenExchange(777, "ghs_first", T0.plusSeconds(3600));
		expectTokenExchange(777, "ghs_second", T0.plusSeconds(7200));
		var provider = provider("777");

		assertThat(provider.token()).isEqualTo("ghs_first");
		clock.now = T0.plusSeconds(3600 - 301);
		assertThat(provider.token()).isEqualTo("ghs_first");   // still cached: a second request would consume "ghs_second"

		clock.now = T0.plusSeconds(3600 - 299);
		assertThat(provider.token()).isEqualTo("ghs_second");
		server.verify();
	}

	@Test
	void withoutAConfiguredInstallationIdTheOnlyInstallationIsUsed() {
		server.expect(requestTo("https://api.github.com/app/installations"))
				.andRespond(withSuccess("[{\"id\":4242,\"account\":{\"login\":\"Rahu09\"}}]", MediaType.APPLICATION_JSON));
		expectTokenExchange(4242, "ghs_found", T0.plusSeconds(3600));

		assertThat(provider("").token()).isEqualTo("ghs_found");
		server.verify();
	}

	@Test
	void moreThanOneInstallationNeedsAnExplicitId() {
		server.expect(requestTo("https://api.github.com/app/installations"))
				.andRespond(withSuccess("[{\"id\":1},{\"id\":2}]", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> provider("").token()).isInstanceOf(GitHubException.class).hasMessageContaining("GITHUB_APP_INSTALLATION_ID");
	}

	@Test
	void aRejectedJwtIsAClearErrorAndTheKeyNeverAppearsInIt() {
		server.expect(requestTo("https://api.github.com/app/installations/777/access_tokens")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> provider("777").token())
				.isInstanceOfSatisfying(GitHubException.class, e -> {
					assertThat(e.status()).isEqualTo(401);
					assertThat(e.getMessage()).contains("JWT rejected").doesNotContain("PRIVATE KEY");
				});
	}

	@Test
	void aPkcs1KeyIsRefusedWithTheFixInTheMessage() throws Exception {
		Files.writeString(keyFile, "-----BEGIN RSA PRIVATE KEY-----\nMIIB\n-----END RSA PRIVATE KEY-----\n");

		assertThatThrownBy(() -> provider("777").token()).isInstanceOf(GitHubException.class).hasMessageContaining("PKCS#8");
	}

	@Test
	void missingKeyFileAndNonNumericAppIdAreRejected() {
		var missing = new AppInstallationTokenProvider(new GitHubAppProperties("12345", dir.resolve("nope.pem").toString(), "1"), GITHUB, builder, clock);

		assertThatThrownBy(missing::token).isInstanceOf(GitHubException.class).hasMessageContaining("Cannot read the GitHub App private key");
		assertThatThrownBy(() -> new AppInstallationTokenProvider(new GitHubAppProperties("abc\",\"x", "", ""), GITHUB, builder, clock))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
