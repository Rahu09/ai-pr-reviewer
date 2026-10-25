package io.github.rahu09.prreviewer.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Proves a delivery came from GitHub: it signs the exact request bytes with the shared secret (HMAC-SHA256) and sends
 * the result in {@code X-Hub-Signature-256}. We recompute it over the RAW bytes (re-serialised JSON would differ) and
 * compare in constant time, so response timing reveals nothing about the right signature.
 */
@Component
public class SignatureVerifier {

	private static final String PREFIX = "sha256=";
	private static final String ALGORITHM = "HmacSHA256";

	private final WebhookProperties properties;

	public SignatureVerifier(WebhookProperties properties) {
		this.properties = properties;
	}

	/** False for a missing, malformed or wrong signature, and always false while no secret is configured. */
	public boolean isValid(byte[] rawBody, String signatureHeader) {
		if (properties.secret().isBlank() || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
			return false;
		}
		byte[] given;
		try {
			given = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
		}
		catch (IllegalArgumentException e) {
			return false;
		}
		return MessageDigest.isEqual(hmac(rawBody), given);
	}

	private byte[] hmac(byte[] data) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
			return mac.doFinal(data);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 is always available", e);
		}
	}
}
