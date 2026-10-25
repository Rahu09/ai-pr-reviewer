package io.github.rahu09.prreviewer.review;

import java.util.Map;
import java.util.Objects;

/**
 * The fixed input of one review: which commit, its diff and the contents of the changed files at head.
 * Built from a fixture folder now and from GitHub in Chunk 6, so a review never changes under our feet.
 */
public record ReviewSnapshot(String repo, int prNumber, String baseSha, String headSha, String diff, Map<String, String> files) {

	public ReviewSnapshot {
		Objects.requireNonNull(repo, "repo");
		Objects.requireNonNull(baseSha, "baseSha");
		Objects.requireNonNull(headSha, "headSha");
		Objects.requireNonNull(diff, "diff");
		files = Map.copyOf(files);
	}

	/** A snapshot with only a diff, for pasted input. */
	public static ReviewSnapshot ofDiff(String diff) {
		return new ReviewSnapshot("pasted-diff", 0, "unknown", "unknown", diff, Map.of());
	}
}
