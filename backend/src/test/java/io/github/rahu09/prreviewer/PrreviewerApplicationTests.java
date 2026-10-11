package io.github.rahu09.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.review.ReviewLimits;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, StubReviewGeneratorConfiguration.class })
@SpringBootTest
class PrreviewerApplicationTests {

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ReviewLimits limits;

	@Test
	void flywayRanAndVectorExtensionExists() {
		List<String> extensions = jdbc.sql("SELECT extname FROM pg_extension").query(String.class).list();
		assertThat(extensions).contains("vector");

		Integer applied = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success").query(Integer.class).single();
		assertThat(applied).isGreaterThanOrEqualTo(1);
	}

	@Test
	void reviewLimitsBindFromConfig() {
		assertThat(limits.maxJavaFiles()).isEqualTo(10);
		assertThat(limits.maxChangedLines()).isEqualTo(400);
		assertThat(limits.maxInlineFindings()).isEqualTo(5);
		assertThat(limits.maxReviewsPerDay()).isEqualTo(50);
	}

}
