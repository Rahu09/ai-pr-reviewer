package io.github.rahu09.prreviewer.github;

import io.github.rahu09.prreviewer.context.ChangeType;
import io.github.rahu09.prreviewer.context.DiffParser;
import io.github.rahu09.prreviewer.context.FileDiff;
import io.github.rahu09.prreviewer.review.ReviewLimits;
import io.github.rahu09.prreviewer.review.ReviewSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Freezes a pull request into a {@link ReviewSnapshot}: the SHAs, the diff between exactly those SHAs and the
 * contents of the changed Java files at the head SHA. A push after this point cannot change what is reviewed,
 * and the head SHA stored on the run says which commit the comments belong to.
 */
@Component
public class GitHubSnapshotProvider {

	private final GitHubClient github;
	private final ReviewLimits limits;
	private final GitHubProperties properties;

	public GitHubSnapshotProvider(GitHubClient github, ReviewLimits limits, GitHubProperties properties) {
		this.github = github;
		this.limits = limits;
		this.properties = properties;
	}

	public ReviewSnapshot fetch(String owner, String repo, int number) {
		PullRequest pr = github.getPullRequest(owner, repo, number);
		String diff = github.getDiff(owner, repo, pr.base().sha(), pr.head().sha());
		return new ReviewSnapshot(owner + "/" + repo, number, pr.base().sha(), pr.head().sha(), diff,
				changedJavaFiles(owner, repo, pr.head().sha(), diff));
	}

	// Bounded twice: at most maxJavaFiles files (the same limit the review budget applies) and maxFileBytes each.
	private Map<String, String> changedJavaFiles(String owner, String repo, String headSha, String diff) {
		var paths = DiffParser.parse(diff).files().stream()
				.filter(FileDiff::isJava)
				.filter(f -> f.changeType() != ChangeType.DELETED)
				.map(FileDiff::path)
				.limit(limits.maxJavaFiles())
				.toList();
		Map<String, String> files = new LinkedHashMap<>();
		for (String path : paths) { // plain loop: one HTTP call per file, skipping oversized or missing ones
			github.getFileContent(owner, repo, path, headSha)
					.filter(content -> content.length() <= properties.maxFileBytes())
					.ifPresent(content -> files.put(path, content));
		}
		return files;
	}
}
