package io.github.rahu09.prreviewer.review;

/**
 * One finding as the model proposed it. LLM-facing, so deliberately lenient: every text field is a String
 * and a missing value becomes "" (parse leniently, validate strictly: the validator in Chunk 4 maps
 * {@code category} to an enum and suppresses bad values with a reason). {@code line} is the model's guess
 * until Chunk 4 replaces it with allowed location IDs.
 */
public record CandidateFinding(
		String filePath,
		int line,
		String category,
		String title,
		String explanation,
		String suggestedFix,
		String suggestedTest) {

	public CandidateFinding {
		filePath = orEmpty(filePath);
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
