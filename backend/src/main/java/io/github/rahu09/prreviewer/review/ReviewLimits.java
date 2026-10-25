package io.github.rahu09.prreviewer.review;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cost and size guards for one review (PLAN §2, §6 rule 11). */
@ConfigurationProperties("reviewer.limits")
public record ReviewLimits(
		@DefaultValue("10") int maxJavaFiles,
		@DefaultValue("400") int maxChangedLines,
		@DefaultValue("5") int maxInlineFindings,
		@DefaultValue("50") int maxReviewsPerDay,
		@DefaultValue("60000") int maxPromptChars) {

	public ReviewLimits {
		if (maxJavaFiles < 1 || maxChangedLines < 1 || maxInlineFindings < 0 || maxReviewsPerDay < 1 || maxPromptChars < 1) {
			throw new IllegalArgumentException("review limits must be positive");
		}
	}
}
