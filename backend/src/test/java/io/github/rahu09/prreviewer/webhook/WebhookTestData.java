package io.github.rahu09.prreviewer.webhook;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Builds webhook bodies and signs them the way GitHub does, so tests send realistic deliveries. */
final class WebhookTestData {

	static final String SECRET = "test-webhook-secret";
	static final String REPO = "Rahu09/order-service-demo";

	private WebhookTestData() {
	}

	static String pullRequestEvent(String action, boolean draft, String author, String headRepo, String baseRepo) {
		return """
				{"action":"%s","number":1,"unknown_field":{"x":1},
				 "pull_request":{"number":1,"state":"open","draft":%s,"title":"Add endpoint","user":{"login":"%s","id":5},
				   "head":{"sha":"headsha1","ref":"feature/x","repo":%s},
				   "base":{"sha":"basesha1","ref":"main","repo":{"full_name":"%s"}}},
				 "repository":{"full_name":"%s"},
				 "installation":{"id":99}}
				""".formatted(action, draft, author, headRepo == null ? "null" : "{\"full_name\":\"" + headRepo + "\"}", baseRepo, baseRepo);
	}

	static String trustedOpened() {
		return pullRequestEvent("opened", false, "Rahu09", REPO, REPO);
	}

	static String sign(String secret, byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	static WebhookProperties properties(String secret) {
		return new WebhookProperties(secret, java.util.List.of(REPO), java.util.List.of("Rahu09"));
	}
}
