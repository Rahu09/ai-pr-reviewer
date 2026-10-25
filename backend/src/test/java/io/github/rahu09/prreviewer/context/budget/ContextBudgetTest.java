package io.github.rahu09.prreviewer.context.budget;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.context.DiffParser;
import io.github.rahu09.prreviewer.context.FileDiff;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ContextBudgetTest {

	private static final ContextBudget GENEROUS = new ContextBudget(10, 400, 60_000);

	/** A new file with {@code added} lines, in real git diff syntax. */
	private static String newFile(String path, int added) {
		String body = IntStream.rangeClosed(1, added).mapToObj(i -> "+int v" + i + " = " + i + ";\n").reduce("", String::concat);
		return "diff --git a/%1$s b/%1$s\nnew file mode 100644\n--- /dev/null\n+++ b/%1$s\n@@ -0,0 +1,%2$d @@\n%3$s".formatted(path, added, body);
	}

	private static ContextBudget.Selection select(ContextBudget budget, String... diffs) {
		return budget.select(DiffParser.parse(String.join("", diffs)));
	}

	@Test
	void reviewsSmallJavaFiles() {
		var selection = select(GENEROUS, newFile("A.java", 3), newFile("B.java", 2));

		assertThat(selection.reviewed()).extracting(FileDiff::path).containsExactly("A.java", "B.java");
		assertThat(selection.skipped()).isEmpty();
	}

	@Test
	void skipsNonJavaBinaryDeletedAndEditlessFilesWithReasons() {
		String diff = newFile("README.md", 2)
				+ "diff --git a/logo.png b/logo.png\nindex 1..2 100644\nBinary files a/logo.png and b/logo.png differ\n"
				+ "diff --git a/Gone.java b/Gone.java\ndeleted file mode 100644\n--- a/Gone.java\n+++ /dev/null\n@@ -1 +0,0 @@\n-class Gone {}\n"
				+ "diff --git a/Old.java b/New.java\nsimilarity index 100%\nrename from Old.java\nrename to New.java\n"
				+ newFile("Real.java", 1);

		var selection = GENEROUS.select(DiffParser.parse(diff));

		assertThat(selection.reviewed()).extracting(FileDiff::path).containsExactly("Real.java");
		assertThat(selection.skipped()).extracting(SkippedFile::path, SkippedFile::reason).containsExactly(
				org.assertj.core.groups.Tuple.tuple("README.md", SkipReason.NOT_JAVA),
				org.assertj.core.groups.Tuple.tuple("logo.png", SkipReason.BINARY),
				org.assertj.core.groups.Tuple.tuple("Gone.java", SkipReason.DELETED),
				org.assertj.core.groups.Tuple.tuple("New.java", SkipReason.NO_CONTENT_CHANGE));
	}

	@Test
	void oversizedFileIsSkippedAndReported() {
		var budget = new ContextBudget(10, 50, 60_000);

		var selection = select(budget, newFile("Huge.java", 51), newFile("Small.java", 5));

		assertThat(selection.reviewed()).extracting(FileDiff::path).containsExactly("Small.java");
		assertThat(selection.skipped()).singleElement().satisfies(s -> {
			assertThat(s.path()).isEqualTo("Huge.java");
			assertThat(s.reason()).isEqualTo(SkipReason.FILE_TOO_LARGE);
			assertThat(s.detail()).contains("51").contains("50");
		});
	}

	@Test
	void fileCountLimitSkipsTheOverflowInOrder() {
		var budget = new ContextBudget(2, 400, 60_000);

		var selection = select(budget, newFile("A.java", 1), newFile("B.java", 1), newFile("C.java", 1));

		assertThat(selection.reviewed()).extracting(FileDiff::path).containsExactly("A.java", "B.java");
		assertThat(selection.skipped()).singleElement().satisfies(s -> {
			assertThat(s.path()).isEqualTo("C.java");
			assertThat(s.reason()).isEqualTo(SkipReason.TOO_MANY_FILES);
		});
	}

	@Test
	void totalChangedLinesLimitSkipsTheFileThatWouldExceedIt() {
		var budget = new ContextBudget(10, 100, 60_000);

		var selection = select(budget, newFile("A.java", 60), newFile("B.java", 60), newFile("C.java", 30));

		assertThat(selection.reviewed()).extracting(FileDiff::path).containsExactly("A.java", "C.java");
		assertThat(selection.skipped()).singleElement().satisfies(s -> assertThat(s.reason()).isEqualTo(SkipReason.CHANGED_LINES_LIMIT));
	}

	@Test
	void promptSizeLimitSkipsTheFileThatWouldOverflowIt() {
		var budget = new ContextBudget(10, 10_000, 600);

		var selection = select(budget, newFile("A.java", 10), newFile("B.java", 40));

		assertThat(selection.reviewed()).extracting(FileDiff::path).containsExactly("A.java");
		assertThat(selection.skipped()).singleElement().satisfies(s -> {
			assertThat(s.path()).isEqualTo("B.java");
			assertThat(s.reason()).isEqualTo(SkipReason.PROMPT_TOO_LARGE);
		});
	}
}
