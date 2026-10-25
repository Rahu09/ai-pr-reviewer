package io.github.rahu09.prreviewer.context.location;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.context.DiffParser;
import io.github.rahu09.prreviewer.context.Side;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotatedDiffTest {

	private static final String DIFF = """
			diff --git a/src/A.java b/src/A.java
			--- a/src/A.java
			+++ b/src/A.java
			@@ -10,3 +10,4 @@ class A {
			 keep
			-old
			+new one
			+new two
			 tail
			diff --git a/src/B.java b/src/B.java
			new file mode 100644
			--- /dev/null
			+++ b/src/B.java
			@@ -0,0 +1,2 @@
			+first
			+second
			""";

	@Test
	void everyAddedLineGetsAUniqueIdAcrossFilesAndMapsToItsNewFileLine() {
		AnnotatedDiff annotated = AnnotatedDiff.of(DiffParser.parse(DIFF).files());

		assertThat(annotated.locations()).containsExactly(
				new AllowedLocation("L1", "src/A.java", 11, Side.RIGHT),
				new AllowedLocation("L2", "src/A.java", 12, Side.RIGHT),
				new AllowedLocation("L3", "src/B.java", 1, Side.RIGHT),
				new AllowedLocation("L4", "src/B.java", 2, Side.RIGHT));
	}

	@Test
	void textShowsIdsOnAddedLinesOnly() {
		String text = AnnotatedDiff.of(DiffParser.parse(DIFF).files()).text();

		assertThat(text).contains("File: src/A.java (MODIFIED)", "File: src/B.java (ADDED)")
				.contains("@@ -10,3 +10,4 @@ class A {")
				.contains("[L1]+new one", "[L2]+new two", "[L3]+first", "[L4]+second")
				.contains("\n-old\n", "\n keep\n", "\n tail\n");
		assertThat(text).doesNotContain("[L5]").doesNotContain("]-old").doesNotContain("] keep");
	}

	@Test
	void renamedFileShowsItsOldName() {
		var files = DiffParser.parse("diff --git a/Old.java b/New.java\nsimilarity index 90%\nrename from Old.java\nrename to New.java\n--- a/Old.java\n+++ b/New.java\n@@ -1 +1 @@\n-a\n+b\n").files();

		assertThat(AnnotatedDiff.of(files).text()).contains("File: New.java (RENAMED from Old.java)");
		assertThat(AnnotatedDiff.of(files).locations()).containsExactly(new AllowedLocation("L1", "New.java", 1, Side.RIGHT));
	}

	@Test
	void noFilesMeansNoLocations() {
		AnnotatedDiff annotated = AnnotatedDiff.of(List.of());

		assertThat(annotated.text()).isEmpty();
		assertThat(annotated.locations()).isEmpty();
	}
}
