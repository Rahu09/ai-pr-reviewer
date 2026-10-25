package io.github.rahu09.prreviewer;

import io.github.rahu09.prreviewer.review.ReviewGenerator;
import io.github.rahu09.prreviewer.review.model.CandidateFindings;
import io.github.rahu09.prreviewer.review.model.GenerationResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Replaces the real LLM generator in the Spring context (the test profile turns the real one off). */
@TestConfiguration(proxyBeanMethods = false)
public class StubReviewGeneratorConfiguration {

	@Bean
	ReviewGenerator stubReviewGenerator() {
		return annotatedDiff -> new GenerationResult(new CandidateFindings(null), "stub", "stub-model", "review-v2", 0, 0);
	}
}
