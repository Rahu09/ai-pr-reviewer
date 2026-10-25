package io.github.rahu09.prreviewer.review.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.context.DiffLine;
import io.github.rahu09.prreviewer.context.DiffParser;
import io.github.rahu09.prreviewer.context.budget.ContextBudget;
import io.github.rahu09.prreviewer.context.location.AllowedLocation;
import io.github.rahu09.prreviewer.context.location.AnnotatedDiff;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The strongest check on "comments land on the right line": for REAL fixtures generated from git branches, the
 * text of every allowed location, read back from the file contents at head, equals the added line in the diff.
 * Also proves each expected finding's line is an allowed location (so the answer keys are reachable).
 */
class FixtureLineMappingTest {

	private final FixtureLoader loader = new FixtureLoader(new FixtureProperties("../evaluation/datasets"), JsonMapper.builder().build());

	static Stream<String> fixtureIds() {
		return Stream.of("list-orders-unpaginated", "list-orders-paginated", "cancel-order-swallowed", "cancel-order-handled");
	}

	@ParameterizedTest
	@MethodSource("fixtureIds")
	void everyAllowedLocationPointsAtTheSameTextInTheHeadFile(String id) {
		var fixture = loader.load(id);
		var parsed = DiffParser.parse(fixture.snapshot().diff());
		var selection = new ContextBudget(10, 400, 60_000).select(parsed);
		AnnotatedDiff annotated = AnnotatedDiff.of(selection.reviewed());

		assertThat(annotated.locations()).isNotEmpty();
		List<String> addedTexts = selection.reviewed().stream()
				.flatMap(f -> f.hunks().stream()).flatMap(h -> h.lines().stream())
				.filter(DiffLine.Added.class::isInstance).map(DiffLine::text).toList();
		assertThat(addedTexts).hasSameSizeAs(annotated.locations());

		for (int i = 0; i < annotated.locations().size(); i++) {
			AllowedLocation loc = annotated.locations().get(i);
			String headFile = fixture.snapshot().files().get(loc.path());
			assertThat(headFile).as("head file for %s", loc.path()).isNotNull();
			String lineInHead = headFile.lines().toList().get(loc.line() - 1);
			assertThat(lineInHead).as("%s line %d", loc.path(), loc.line()).isEqualTo(addedTexts.get(i));
		}
	}

	@ParameterizedTest
	@MethodSource("fixtureIds")
	void expectedFindingsAreOnAllowedLocations(String id) {
		var fixture = loader.load(id);
		var annotated = AnnotatedDiff.of(new ContextBudget(10, 400, 60_000).select(DiffParser.parse(fixture.snapshot().diff())).reviewed());

		for (var expected : fixture.meta().expectedFindings()) {
			assertThat(annotated.locations())
					.as("expected finding %s:%d", expected.file(), expected.line())
					.anySatisfy(loc -> {
						assertThat(loc.path()).isEqualTo(expected.file());
						assertThat(loc.line()).isEqualTo(expected.line());
					});
		}
	}

	@ParameterizedTest
	@MethodSource("fixtureIds")
	void fixtureMetaAndSnapshotLoad(String id) {
		var fixture = loader.load(id);

		assertThat(fixture.meta().id()).isEqualTo(id);
		assertThat(fixture.meta().kind()).isIn("buggy", "clean");
		assertThat(fixture.meta().kind().equals("clean")).isEqualTo(fixture.meta().expectedFindings().isEmpty());
		assertThat(fixture.snapshot().headSha()).hasSize(40);
	}
}
