package io.github.rahu09.prreviewer.review;

import io.github.rahu09.prreviewer.context.budget.ContextBudget;

/**
 * The effective settings of one review. Starts from {@link ReviewLimits} (application config) and can be
 * overridden per dry-run request, which is how the evaluation will switch variants later (Chunk 10).
 */
public record ReviewConfig(int maxJavaFiles, int maxChangedLines, int maxInlineFindings, int maxPromptChars) {

	public ReviewConfig {
		if (maxJavaFiles < 1 || maxChangedLines < 1 || maxInlineFindings < 0 || maxPromptChars < 1) {
			throw new IllegalArgumentException("review config values must be positive");
		}
	}

	public static ReviewConfig from(ReviewLimits limits) {
		return new ReviewConfig(limits.maxJavaFiles(), limits.maxChangedLines(), limits.maxInlineFindings(), limits.maxPromptChars());
	}

	public ContextBudget budget() {
		return new ContextBudget(maxJavaFiles, maxChangedLines, maxPromptChars);
	}

	/** What a request body may change. {@code null} means "keep the default". */
	public record Override(Integer maxJavaFiles, Integer maxChangedLines, Integer maxInlineFindings, Integer maxPromptChars) {

		public ReviewConfig applyTo(ReviewConfig base) {
			return new ReviewConfig(
					maxJavaFiles != null ? maxJavaFiles : base.maxJavaFiles(),
					maxChangedLines != null ? maxChangedLines : base.maxChangedLines(),
					maxInlineFindings != null ? maxInlineFindings : base.maxInlineFindings(),
					maxPromptChars != null ? maxPromptChars : base.maxPromptChars());
		}
	}
}
