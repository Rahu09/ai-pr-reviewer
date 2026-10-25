package io.github.rahu09.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rahu09.prreviewer.review.ReviewLimits;
import io.github.rahu09.prreviewer.review.ReviewSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitHubSnapshotProviderTest {

	private static final String DIFF = """
			diff --git a/src/A.java b/src/A.java
			--- a/src/A.java
			+++ b/src/A.java
			@@ -1,2 +1,3 @@
			 class A {
			+  int x;
			 }
			diff --git a/src/Gone.java b/src/Gone.java
			deleted file mode 100644
			--- a/src/Gone.java
			+++ /dev/null
			@@ -1,2 +0,0 @@
			-class Gone {
			-}
			diff --git a/README.md b/README.md
			--- a/README.md
			+++ b/README.md
			@@ -1 +1 @@
			-a
			+b
			""";

	private final GitHubClient github = mock(GitHubClient.class);

	private GitHubSnapshotProvider provider(int maxJavaFiles, int maxFileBytes) {
		return new GitHubSnapshotProvider(github, new ReviewLimits(maxJavaFiles, 400, 5, 50, 60_000),
				new GitHubProperties("https://api.github.com", "t", maxFileBytes));
	}

	private void prWithDiff() {
		when(github.getPullRequest("o", "r", 7)).thenReturn(new PullRequest(7, "open", false, "t", null,
				new PullRequest.Ref("headsha", null), new PullRequest.Ref("basesha", null)));
		when(github.getDiff("o", "r", "basesha", "headsha")).thenReturn(DIFF);
	}

	@Test
	void snapshotCarriesTheShasAndOnlyFetchesChangedJavaFilesAtTheHeadSha() {
		prWithDiff();
		when(github.getFileContent("o", "r", "src/A.java", "headsha")).thenReturn(Optional.of("class A {}"));

		ReviewSnapshot snapshot = provider(10, 1000).fetch("o", "r", 7);

		assertThat(snapshot.repo()).isEqualTo("o/r");
		assertThat(snapshot.prNumber()).isEqualTo(7);
		assertThat(snapshot.baseSha()).isEqualTo("basesha");
		assertThat(snapshot.headSha()).isEqualTo("headsha");
		assertThat(snapshot.diff()).isEqualTo(DIFF);
		assertThat(snapshot.files()).containsOnlyKeys("src/A.java");
		// deleted and non-Java files are never requested
		verify(github, never()).getFileContent(any(), any(), eq("src/Gone.java"), any());
		verify(github, never()).getFileContent(any(), any(), eq("README.md"), any());
	}

	@Test
	void oversizedFilesAreLeftOut() {
		prWithDiff();
		when(github.getFileContent("o", "r", "src/A.java", "headsha")).thenReturn(Optional.of("x".repeat(50)));

		assertThat(provider(10, 10).fetch("o", "r", 7).files()).isEmpty();
	}
}
