package io.github.rahu09.prreviewer.review.model;

import java.util.List;
import java.util.Objects;

/** The JSON contract the model must return. A missing or null list means "no findings". */
public record CandidateFindings(List<CandidateFinding> findings) {

	public CandidateFindings {
		findings = findings == null ? List.of() : findings.stream().filter(Objects::nonNull).toList();
	}
}
