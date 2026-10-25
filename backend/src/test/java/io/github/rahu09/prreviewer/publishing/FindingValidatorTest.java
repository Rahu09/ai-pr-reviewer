package io.github.rahu09.prreviewer.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.context.Side;
import io.github.rahu09.prreviewer.context.location.AllowedLocation;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Publishable;
import io.github.rahu09.prreviewer.publishing.ValidationResult.SummaryOnly;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Suppressed;
import io.github.rahu09.prreviewer.review.model.CandidateFinding;
import io.github.rahu09.prreviewer.review.model.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindingValidatorTest {

	private static final List<AllowedLocation> LOCATIONS = List.of(
			new AllowedLocation("L1", "A.java", 10, Side.RIGHT),
			new AllowedLocation("L2", "A.java", 11, Side.RIGHT),
			new AllowedLocation("L3", "B.java", 5, Side.RIGHT));

	private final FindingValidator validator = new FindingValidator();

	private static CandidateFinding finding(String locationId, String category) {
		return new CandidateFinding(locationId, category, "Title", "Explanation", "Fix", "Test");
	}

	@Test
	void validFindingOnARealLocationIsPublishable() {
		var results = validator.validate(List.of(finding("L2", "GUIDELINE")), LOCATIONS, 5);

		assertThat(results).singleElement().isInstanceOfSatisfying(Publishable.class, p -> {
			assertThat(p.location()).isEqualTo(new AllowedLocation("L2", "A.java", 11, Side.RIGHT));
			assertThat(p.category()).isEqualTo(Category.GUIDELINE);
		});
	}

	@Test
	void unknownLocationIsSuppressedWithAReason() {
		var results = validator.validate(List.of(finding("L99", "CORRECTNESS")), LOCATIONS, 5);

		assertThat(results).singleElement().isInstanceOfSatisfying(Suppressed.class,
				s -> assertThat(s.reason()).contains("unknown location 'L99'"));
	}

	@Test
	void aLineNumberIsNotALocationId() {
		var results = validator.validate(List.of(finding("32", "CORRECTNESS")), LOCATIONS, 5);

		assertThat(results).singleElement().isInstanceOf(Suppressed.class);
	}

	@Test
	void idWrittenWithBracketsOrLowercaseStillMapsToTheSameLocation() {
		var results = validator.validate(List.of(finding("[L3]", "GUIDELINE"), finding(" l1 ", "CORRECTNESS")), LOCATIONS, 5);

		assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(Publishable.class));
	}

	@Test
	void unknownCategoryIsSuppressedButKnownOnesAreCaseTolerant() {
		var results = validator.validate(List.of(
				finding("L1", "SECURITY"),
				finding("L2", "test-suggestion"),
				finding("L3", "correctness")), LOCATIONS, 5);

		assertThat(results.get(0)).isInstanceOfSatisfying(Suppressed.class, s -> assertThat(s.reason()).contains("unknown category 'SECURITY'"));
		assertThat(results.get(1)).isInstanceOfSatisfying(Publishable.class, p -> assertThat(p.category()).isEqualTo(Category.TEST_SUGGESTION));
		assertThat(results.get(2)).isInstanceOf(Publishable.class);
	}

	@Test
	void missingTitleOrExplanationIsSuppressed() {
		var noTitle = new CandidateFinding("L1", "GUIDELINE", "", "Explanation", "", "");
		var noExplanation = new CandidateFinding("L1", "GUIDELINE", "Title", "  ", "", "");

		var results = validator.validate(List.of(noTitle, noExplanation), LOCATIONS, 5);

		assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOfSatisfying(Suppressed.class,
				s -> assertThat(s.reason()).contains("missing title or explanation")));
	}

	@Test
	void blankLocationIsSummaryOnlyNotSuppressed() {
		var results = validator.validate(List.of(finding("", "TEST_SUGGESTION")), LOCATIONS, 5);

		assertThat(results).singleElement().isInstanceOfSatisfying(SummaryOnly.class, s -> {
			assertThat(s.category()).isEqualTo(Category.TEST_SUGGESTION);
			assertThat(s.reason()).contains("no location");
		});
	}

	@Test
	void sameLocationAndCategoryIsMergedKeepingTheFirst() {
		var first = new CandidateFinding("L1", "GUIDELINE", "First", "e", "", "");
		var second = new CandidateFinding("L1", "GUIDELINE", "Second", "e", "", "");
		var otherCategory = new CandidateFinding("L1", "CORRECTNESS", "Third", "e", "", "");

		var results = validator.validate(List.of(first, second, otherCategory), LOCATIONS, 5);

		assertThat(results.get(0)).isInstanceOf(Publishable.class);
		assertThat(results.get(1)).isInstanceOfSatisfying(Suppressed.class, s -> assertThat(s.reason()).contains("duplicate"));
		assertThat(results.get(2)).isInstanceOf(Publishable.class);
	}

	@Test
	void identicalDuplicatesAreStillTreatedAsOneWinnerAndOneLoser() {
		var same = finding("L1", "GUIDELINE");

		var results = validator.validate(List.of(same, same), LOCATIONS, 5);

		assertThat(results.get(0)).isInstanceOf(Publishable.class);
		assertThat(results.get(1)).isInstanceOf(Suppressed.class);
	}

	@Test
	void inlineCapMovesTheOverflowToTheSummaryInOrder() {
		var results = validator.validate(List.of(finding("L1", "GUIDELINE"), finding("L2", "GUIDELINE"), finding("L3", "GUIDELINE")), LOCATIONS, 2);

		assertThat(results.get(0)).isInstanceOf(Publishable.class);
		assertThat(results.get(1)).isInstanceOf(Publishable.class);
		assertThat(results.get(2)).isInstanceOfSatisfying(SummaryOnly.class, s -> assertThat(s.reason()).contains("cap of 2"));
	}

	@Test
	void everyCandidateGetsExactlyOneResultInTheSameOrder() {
		var candidates = List.of(finding("L1", "GUIDELINE"), finding("L99", "GUIDELINE"), finding("", "GUIDELINE"), finding("L1", "GUIDELINE"));

		var results = validator.validate(candidates, LOCATIONS, 5);

		assertThat(results).hasSameSizeAs(candidates);
		assertThat(results).extracting(r -> r.finding().locationId()).containsExactly("L1", "L99", "", "L1");
	}

	@Test
	void noCandidatesIsFine() {
		assertThat(validator.validate(List.of(), LOCATIONS, 5)).isEmpty();
	}
}
