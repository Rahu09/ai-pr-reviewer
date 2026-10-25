package io.github.rahu09.prreviewer.review;

/**
 * One finding as the model proposed it (prompt review-v2). LLM-facing, so deliberately lenient: every field is a
 * String and a missing value becomes "" (parse leniently, validate strictly: {@code FindingValidator} maps
 * {@code category} to an enum and {@code locationId} to a real location, or suppresses with a reason).
 * The model returns a location ID such as "L12", never a line number (ADR-0008).
 */
public record CandidateFinding(
		String locationId,
		String category,
		String title,
		String explanation,
		String suggestedFix,
		String suggestedTest) {

	public CandidateFinding {
		locationId = orEmpty(locationId);
		category = orEmpty(category);
		title = orEmpty(title);
		explanation = orEmpty(explanation);
		suggestedFix = orEmpty(suggestedFix);
		suggestedTest = orEmpty(suggestedTest);
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value.strip();
	}
}
