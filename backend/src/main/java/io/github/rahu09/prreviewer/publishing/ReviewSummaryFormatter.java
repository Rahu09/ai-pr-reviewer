package io.github.rahu09.prreviewer.publishing;

import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.model.FindingStatus;
import io.github.rahu09.prreviewer.review.run.ReviewRun;
import io.github.rahu09.prreviewer.review.run.StoredFinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns a stored run into the Markdown GitHub shows: the review summary, the inline comments and the hidden marker
 * that makes publishing idempotent. Pure text, no I/O.
 *
 * <p>Finding text comes from a model that read untrusted PR content, so it is neutralised before it is posted:
 * {@code @name} must not ping anyone and {@code <!--} must not be able to forge a marker.
 */
final class ReviewSummaryFormatter {

	/** GitHub rejects review and comment bodies above 65,536 characters; stay well under. */
	static final int MAX_BODY_CHARS = 60_000;
	static final int MAX_COMMENT_CHARS = 4_000;

	private static final Pattern MARKER = Pattern.compile("<!-- ai-pr-reviewer run=\\S+ sha=(\\S+) config=(\\S+) -->");
	private static final String ADVISORY = "_Advisory only: this is an automated review, not a merge approval. A human decides._";

	private ReviewSummaryFormatter() {
	}

	/** Short, stable id of the settings a review ran with: same SHA and same config means the same review. */
	static String configVersion(ReviewConfig config) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(config.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 4);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

	static String marker(UUID runId, String headSha, ReviewConfig config) {
		return "<!-- ai-pr-reviewer run=%s sha=%s config=%s -->".formatted(runId, headSha, configVersion(config));
	}

	/** True if a review body was written for this commit and these settings (the run id may differ: a retry, another run). */
	static boolean carriesMarker(String body, String headSha, ReviewConfig config) {
		String version = configVersion(config);
		return MARKER.matcher(body).results().anyMatch(m -> m.group(1).equals(headSha) && m.group(2).equals(version));
	}

	/**
	 * The review body. With {@code inline} the publishable findings are posted as inline comments and only counted
	 * here; without it (the 422 fallback) they are written out in full so nothing the model found is lost.
	 */
	static String summary(ReviewRun run, List<StoredFinding> findings, boolean inline) {
		List<StoredFinding> publishable = withStatus(findings, FindingStatus.PUBLISHABLE);
		List<StoredFinding> summaryOnly = withStatus(findings, FindingStatus.SUMMARY_ONLY);
		long suppressed = findings.stream().filter(f -> f.status() == FindingStatus.SUPPRESSED).count();
		int shown = publishable.size() + summaryOnly.size();

		var out = new StringBuilder("## AI review (advisory)\n\n");
		out.append("Reviewed commit `%s` with `%s` (prompt `%s`).\n".formatted(shortSha(run.headSha()), orNone(run.model()), orNone(run.promptVersion())));
		if (shown == 0) {
			out.append("No issues worth raising were found in the reviewed files.\n");
		}
		else if (inline) {
			out.append("%d finding(s): %d inline, %d below.\n".formatted(shown, publishable.size(), summaryOnly.size()));
		}
		else {
			out.append("%d finding(s), listed below because they could not be attached to lines.\n".formatted(shown));
		}
		if (suppressed > 0) {
			out.append("%d more finding(s) were dropped by validation (unknown or invalid location).\n".formatted(suppressed));
		}

		out.append("\n**Files reviewed (%d):** %s\n".formatted(run.filesReviewed().size(), codeList(run.filesReviewed())));
		if (!run.filesSkipped().isEmpty()) {
			out.append("\n**Skipped (%d):**\n".formatted(run.filesSkipped().size()));
			run.filesSkipped().forEach(s -> out.append("- %s: %s%s\n".formatted(code(s.path()), s.reason(), s.detail().isBlank() ? "" : " (" + safe(s.detail()) + ")")));
		}

		if (inline && !publishable.isEmpty()) {
			Map<String, List<StoredFinding>> byFile = publishable.stream()
					.collect(Collectors.groupingBy(StoredFinding::filePath, LinkedHashMap::new, Collectors.toList()));
			out.append("\n### Inline comments\n");
			byFile.forEach((path, list) -> out.append("- %s: %d\n".formatted(code(path), list.size())));
		}

		List<StoredFinding> detailed = inline ? summaryOnly : Stream.concat(publishable.stream(), summaryOnly.stream()).toList();
		if (!detailed.isEmpty()) {
			out.append("\n### Findings\n");
			detailed.forEach(f -> out.append("\n").append(detail(f, true)).append("\n"));
		}

		String content = out.toString();
		String footer = "\n---\n" + ADVISORY + "\n\n" + marker(run.id(), run.headSha(), run.config());
		if (content.length() > MAX_BODY_CHARS) {
			content = content.substring(0, MAX_BODY_CHARS) + "\n\n_(truncated)_\n";
		}
		return content + footer;
	}

	static String inlineComment(StoredFinding f) {
		String text = detail(f, false);
		return text.length() <= MAX_COMMENT_CHARS ? text : text.substring(0, MAX_COMMENT_CHARS) + "\n\n_(truncated)_";
	}

	private static String detail(StoredFinding f, boolean withLocation) {
		var out = new StringBuilder("**[%s] %s**".formatted(f.category(), safe(f.title())));
		if (withLocation && f.filePath() != null) {
			out.append(" (%s)".formatted(code(f.filePath() + ":" + f.line())));
		}
		out.append("\n\n").append(safe(f.explanation()));
		if (!f.suggestedFix().isBlank()) {
			out.append("\n\n**Suggested fix:** ").append(safe(f.suggestedFix()));
		}
		if (!f.suggestedTest().isBlank()) {
			out.append("\n\n**Suggested test:** ").append(safe(f.suggestedTest()));
		}
		if (f.status() == FindingStatus.SUMMARY_ONLY && f.reason() != null) {
			out.append("\n\n_Not attached to a line: ").append(safe(f.reason())).append("_");
		}
		return out.toString();
	}

	/** A zero-width space after {@code @} stops mentions; breaking {@code <!--} stops a forged marker. */
	static String safe(String text) {
		return text.replace("@", "@​").replace("<!--", "&lt;!--");
	}

	private static List<StoredFinding> withStatus(List<StoredFinding> findings, FindingStatus status) {
		return findings.stream().filter(f -> f.status() == status).toList();
	}

	private static String codeList(List<String> paths) {
		return paths.isEmpty() ? "none" : paths.stream().map(ReviewSummaryFormatter::code).collect(Collectors.joining(", "));
	}

	private static String code(String text) {
		return "`" + safe(text) + "`";
	}

	private static String shortSha(String sha) {
		return sha.length() > 7 ? sha.substring(0, 7) : sha;
	}

	private static String orNone(String value) {
		return value == null ? "none" : value;
	}
}
