package io.github.rahu09.prreviewer.webhook;

import static io.github.rahu09.prreviewer.webhook.WebhookTestData.SECRET;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

	private final SignatureVerifier verifier = new SignatureVerifier(WebhookTestData.properties(SECRET));
	private final byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

	@Test
	void matchesTheTestVectorFromGitHubsDocumentation() {
		var docs = new SignatureVerifier(new WebhookProperties("It's a Secret to Everybody", java.util.List.of(), java.util.List.of()));

		assertThat(docs.isValid("Hello, World!".getBytes(StandardCharsets.UTF_8),
				"sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17")).isTrue();
	}

	@Test
	void acceptsASignatureMadeWithTheSecretOverTheExactBytes() {
		assertThat(verifier.isValid(body, WebhookTestData.sign(SECRET, body))).isTrue();
	}

	@Test
	void rejectsABodyChangedByEvenOneByte() {
		String signature = WebhookTestData.sign(SECRET, body);
		byte[] tampered = "{\"action\":\"openeD\"}".getBytes(StandardCharsets.UTF_8);

		assertThat(verifier.isValid(tampered, signature)).isFalse();
	}

	@Test
	void rejectsWrongSecretMissingMalformedAndNonHexSignatures() {
		assertThat(verifier.isValid(body, WebhookTestData.sign("another-secret", body))).isFalse();
		assertThat(verifier.isValid(body, null)).isFalse();
		assertThat(verifier.isValid(body, "")).isFalse();
		assertThat(verifier.isValid(body, "sha1=" + "ab".repeat(20))).isFalse();
		assertThat(verifier.isValid(body, "sha256=not-hex")).isFalse();
		assertThat(verifier.isValid(body, "sha256=")).isFalse();
		assertThat(verifier.isValid(body, "sha256=abcd")).isFalse();   // right shape, wrong length
	}

	@Test
	void anUnsetSecretRejectsEverythingEvenASignatureMadeWithTheEmptySecret() {
		var unset = new SignatureVerifier(WebhookTestData.properties(""));

		// Even the signature an attacker could compute for an empty secret (HMAC rejects an empty key, so use the raw digest of "").
		String forged = "sha256=" + java.util.HexFormat.of().formatHex(new byte[32]);

		assertThat(unset.isValid(body, forged)).isFalse();
		assertThat(unset.isValid(body, WebhookTestData.sign(SECRET, body))).isFalse();
	}

	@Test
	void theSecretNeverAppearsInToString() {
		assertThat(WebhookTestData.properties(SECRET).toString()).doesNotContain(SECRET).contains("<hidden>");
	}
}
