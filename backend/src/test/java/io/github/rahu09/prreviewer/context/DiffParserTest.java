package io.github.rahu09.prreviewer.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Most cases come from ONE real {@code git diff} (src/test/resources/diffs/mixed-changes.diff) generated from a
 * scratch repo: multi-hunk, added, deleted, renamed, renamed+edited, binary, mode change, "no newline at end of
 * file" and a removed line that itself starts with "--".
 */
class DiffParserTest {

	private static ParsedDiff mixed;

	@BeforeAll
	static void parseRealGitDiff() throws IOException {
		try (InputStream in = DiffParserTest.class.getResourceAsStream("/diffs/mixed-changes.diff")) {
			mixed = DiffParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	private static FileDiff file(String path) {
		return mixed.files().stream().filter(f -> f.path().equals(path)).findFirst()
				.orElseThrow(() -> new AssertionError("no file " + path + " in " + mixed.files().stream().map(FileDiff::path).toList()));
	}

	@Test
	void findsEveryFileOfTheDiff() {
		assertThat(mixed.files()).extracting(FileDiff::path).containsExactlyInAnyOrder(
				"logo.png", "notes.sql", "run.sh", "src/Fresh.java", "src/Moved.java", "src/MovedEdited.java",
				"src/Multi.java", "src/NoNewline.java", "src/NoNewlineNew.java", "src/ToDelete.java");
	}

	@Test
	void addedFileHasOnlyAddedLinesNumberedFromOne() {
		FileDiff fresh = file("src/Fresh.java");

		assertThat(fresh.changeType()).isEqualTo(ChangeType.ADDED);
		assertThat(fresh.oldPath()).isEqualTo("src/Fresh.java");
		assertThat(fresh.hunks()).hasSize(1);
		Hunk hunk = fresh.hunks().getFirst();
		assertThat(hunk.oldStart()).isZero();
		assertThat(hunk.oldCount()).isZero();
		assertThat(hunk.lines()).hasSize(5).allMatch(DiffLine.Added.class::isInstance);
		assertThat(hunk.lines().getFirst()).isEqualTo(new DiffLine.Added(1, "package x;"));
		assertThat(hunk.lines().getLast()).isEqualTo(new DiffLine.Added(5, "}"));
	}

	@Test
	void modifiedFileWithTwoHunksKeepsCorrectNewLineNumbers() {
		FileDiff multi = file("src/Multi.java");

		assertThat(multi.changeType()).isEqualTo(ChangeType.MODIFIED);
		assertThat(multi.hunks()).hasSize(2);
		Hunk first = multi.hunks().get(0);
		Hunk second = multi.hunks().get(1);
		assertThat(first.header()).isEqualTo("@@ -1,5 +1,5 @@");
		assertThat(first.lines()).contains(new DiffLine.Removed(2, "line2"), new DiffLine.Added(2, "line2 changed"));
		assertThat(second.header()).isEqualTo("@@ -15,6 +15,6 @@ line14");
		assertThat(second.lines()).contains(new DiffLine.Removed(18, "line18"), new DiffLine.Added(18, "line18 changed"));
		assertThat(second.lines().getFirst()).isEqualTo(new DiffLine.Context(15, 15, "line15"));
		assertThat(multi.changedLines()).isEqualTo(4);
	}

	@Test
	void deletedFileUsesItsOldPathAndHasOnlyRemovedLines() {
		FileDiff deleted = file("src/ToDelete.java");

		assertThat(deleted.changeType()).isEqualTo(ChangeType.DELETED);
		assertThat(deleted.hunks().getFirst().lines()).containsExactly(
				new DiffLine.Removed(1, "class Old {"), new DiffLine.Removed(2, "  int a;"), new DiffLine.Removed(3, "}"));
	}

	@Test
	void pureRenameHasBothPathsAndNoHunks() {
		FileDiff moved = file("src/Moved.java");

		assertThat(moved.changeType()).isEqualTo(ChangeType.RENAMED);
		assertThat(moved.oldPath()).isEqualTo("src/Renamed.java");
		assertThat(moved.hunks()).isEmpty();
	}

	@Test
	void renameWithEditsKeepsHunksOnTheNewPath() {
		FileDiff moved = file("src/MovedEdited.java");

		assertThat(moved.changeType()).isEqualTo(ChangeType.RENAMED);
		assertThat(moved.oldPath()).isEqualTo("src/RenamedEdited.java");
		assertThat(moved.hunks()).hasSize(1);
		assertThat(moved.hunks().getFirst().lines()).contains(new DiffLine.Added(8, "  int g; int h;"));
	}

	@Test
	void binaryFileIsMarkedAndHasNoHunks() {
		FileDiff logo = file("logo.png");

		assertThat(logo.binary()).isTrue();
		assertThat(logo.hunks()).isEmpty();
	}

	@Test
	void modeOnlyChangeIsAModifiedFileWithoutHunks() {
		FileDiff script = file("run.sh");

		assertThat(script.changeType()).isEqualTo(ChangeType.MODIFIED);
		assertThat(script.hunks()).isEmpty();
		assertThat(script.binary()).isFalse();
	}

	@Test
	void noNewlineAtEndOfFileMarkerIsNotALine() {
		FileDiff file = file("src/NoNewline.java");

		assertThat(file.hunks().getFirst().lines()).containsExactly(
				new DiffLine.Removed(1, "class Same {}"),
				new DiffLine.Added(1, "class Same {}"),
				new DiffLine.Added(2, "// added"));
		assertThat(file("src/NoNewlineNew.java").hunks().getFirst().lines()).containsExactly(new DiffLine.Added(1, "class Z {}"));
	}

	@Test
	void removedLineThatStartsWithTwoDashesIsStillARemovedLine() {
		// The diff line is "--- removed sql comment", which looks like a file header. Hunk counts decide.
		FileDiff notes = file("notes.sql");

		assertThat(notes.path()).isEqualTo("notes.sql");
		assertThat(notes.hunks().getFirst().lines()).containsExactly(
				new DiffLine.Context(1, 1, "# title"),
				new DiffLine.Removed(2, "-- removed sql comment"),
				new DiffLine.Context(3, 2, "keep"));
	}

	@Test
	void hunkHeaderWithoutCountsMeansOneLine() {
		var diff = DiffParser.parse("""
				diff --git a/A.java b/A.java
				--- a/A.java
				+++ b/A.java
				@@ -3 +3 @@
				-old
				+new
				""");

		Hunk hunk = diff.files().getFirst().hunks().getFirst();
		assertThat(hunk.oldCount()).isEqualTo(1);
		assertThat(hunk.newCount()).isEqualTo(1);
		assertThat(hunk.lines()).containsExactly(new DiffLine.Removed(3, "old"), new DiffLine.Added(3, "new"));
	}

	@Test
	void emptyContextLineWithTheSpaceStrippedIsStillContext() {
		var diff = DiffParser.parse("diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,3 +1,3 @@\n a\n\n-b\n+c\n");

		assertThat(diff.files().getFirst().hunks().getFirst().lines()).containsExactly(
				new DiffLine.Context(1, 1, "a"), new DiffLine.Context(2, 2, ""), new DiffLine.Removed(3, "b"), new DiffLine.Added(3, "c"));
	}

	@Test
	void pathsWithSpacesAreReadFromTheHeader() {
		var diff = DiffParser.parse("diff --git a/my dir/A B.java b/my dir/A B.java\n--- a/my dir/A B.java\n+++ b/my dir/A B.java\n@@ -1 +1 @@\n-x\n+y\n");

		assertThat(diff.files().getFirst().path()).isEqualTo("my dir/A B.java");
	}

	@Test
	void emptyOrBlankInputGivesNoFiles() {
		assertThat(DiffParser.parse("").files()).isEmpty();
		assertThat(DiffParser.parse("\n\n").files()).isEmpty();
	}

	@Test
	void truncatedHunkIsRejectedNotGuessed() {
		String truncated = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,3 +1,3 @@\n a\n-b\n";

		assertThatThrownBy(() -> DiffParser.parse(truncated))
				.isInstanceOf(DiffParseException.class)
				.hasMessageContaining("Truncated hunk");
	}

	@Test
	void hunkCutOffByTheNextFileIsRejected() {
		String cut = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,3 +1,3 @@\n a\n"
				+ "diff --git a/B.java b/B.java\n--- a/B.java\n+++ b/B.java\n@@ -1 +1 @@\n-x\n+y\n";

		assertThatThrownBy(() -> DiffParser.parse(cut)).isInstanceOf(DiffParseException.class).hasMessageContaining("Truncated hunk");
	}

	@Test
	void hunkLongerThanItsHeaderIsRejected() {
		String extraAdded = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1 +1 @@\n-a\n+b\n+c\n";
		String extraContext = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,1 +1,1 @@\n a\n b\n";

		assertThatThrownBy(() -> DiffParser.parse(extraAdded)).isInstanceOf(DiffParseException.class).hasMessageContaining("more lines");
		assertThatThrownBy(() -> DiffParser.parse(extraContext)).isInstanceOf(DiffParseException.class).hasMessageContaining("more lines");
	}

	@Test
	void garbageInsideAHunkIsRejected() {
		String garbage = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,2 +1,2 @@\n a\nthis is not diff syntax\n";

		assertThatThrownBy(() -> DiffParser.parse(garbage)).isInstanceOf(DiffParseException.class).hasMessageContaining("Unexpected line");
	}

	@Test
	void changedLinesCountsAddedAndRemovedOnly() {
		assertThat(file("src/Fresh.java").changedLines()).isEqualTo(5);
		assertThat(file("src/ToDelete.java").changedLines()).isEqualTo(3);
		assertThat(file("run.sh").changedLines()).isZero();
	}

	@Test
	void everyAddedLineGetsItsNewFileLineNumberAcrossHunks() {
		List<Integer> numbers = file("src/Multi.java").hunks().stream()
				.flatMap(h -> h.lines().stream())
				.filter(DiffLine.Added.class::isInstance)
				.map(l -> ((DiffLine.Added) l).newLine())
				.toList();

		assertThat(numbers).containsExactly(2, 18);
	}
}
