package io.github.rahu09.prreviewer.review.fixture;

import java.util.List;

/** The answer key of a fixture ({@code fixture.json}): what the reviewer should and should not find. */
public record FixtureMeta(
		String id,
		String family,
		String kind,
		String sourceBranch,
		String baseBranch,
		String headSha,
		String baseSha,
		List<ExpectedFinding> expectedFindings,
		List<String> expectedRuleIds) {

	public FixtureMeta {
		expectedFindings = expectedFindings == null ? List.of() : List.copyOf(expectedFindings);
		expectedRuleIds = expectedRuleIds == null ? List.of() : List.copyOf(expectedRuleIds);
	}

	public record ExpectedFinding(String file, int line, String category, String ruleId, String why) {
	}
}
