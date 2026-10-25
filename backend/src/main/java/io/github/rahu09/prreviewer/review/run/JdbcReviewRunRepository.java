package io.github.rahu09.prreviewer.review.run;

import io.github.rahu09.prreviewer.context.budget.SkippedFile;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Publishable;
import io.github.rahu09.prreviewer.publishing.ValidationResult.SummaryOnly;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Suppressed;
import io.github.rahu09.prreviewer.publishing.ValidationResult;
import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.ReviewOutcome;
import io.github.rahu09.prreviewer.review.model.FailedStage;
import io.github.rahu09.prreviewer.review.model.FindingStatus;
import io.github.rahu09.prreviewer.review.model.GenerationResult;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.model.RunStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcReviewRunRepository implements ReviewRunRepository {

	private static final int MAX_ERROR_CHARS = 1000;

	private final JdbcClient jdbc;
	private final JsonMapper json;

	public JdbcReviewRunRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public UUID start(RunStart start) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO review_runs (id, source, repo, pr_number, base_sha, head_sha, config, status, started_at)
				VALUES (:id, :source, :repo, :prNumber, :baseSha, :headSha, CAST(:config AS jsonb), 'RUNNING', :startedAt)
				""")
				.param("id", id)
				.param("source", start.source().name())
				.param("repo", start.repo())
				.param("prNumber", start.prNumber())
				.param("baseSha", start.baseSha())
				.param("headSha", start.headSha())
				.param("config", toJson(start.config()))
				.param("startedAt", utc(start.startedAt()))
				.update();
		return id;
	}

	@Override
	public void attachSnapshot(UUID runId, String baseSha, String headSha) {
		jdbc.sql("UPDATE review_runs SET base_sha = :baseSha, head_sha = :headSha WHERE id = :id")
				.param("id", runId)
				.param("baseSha", baseSha)
				.param("headSha", headSha)
				.update();
	}

	@Override
	@Transactional
	public void complete(UUID runId, ReviewOutcome outcome, long generationMs, long latencyMs, Instant finishedAt) {
		GenerationResult g = outcome.generation();
		int updated = jdbc.sql("""
				UPDATE review_runs
				SET status = 'SUCCEEDED', provider = :provider, model = :model, prompt_version = :promptVersion,
				    files_reviewed = CAST(:filesReviewed AS jsonb), files_skipped = CAST(:filesSkipped AS jsonb),
				    allowed_locations = :allowedLocations, input_tokens = :inputTokens, output_tokens = :outputTokens,
				    generation_ms = :generationMs, latency_ms = :latencyMs, finished_at = :finishedAt
				WHERE id = :id
				""")
				.param("id", runId)
				.param("provider", g.provider())
				.param("model", g.model())
				.param("promptVersion", g.promptVersion())
				.param("filesReviewed", toJson(outcome.filesReviewed()))
				.param("filesSkipped", toJson(outcome.filesSkipped()))
				.param("allowedLocations", outcome.allowedLocations())
				.param("inputTokens", g.inputTokens())
				.param("outputTokens", g.outputTokens())
				.param("generationMs", generationMs)
				.param("latencyMs", latencyMs)
				.param("finishedAt", utc(finishedAt))
				.update();
		if (updated != 1) {
			throw new IllegalStateException("review run " + runId + " does not exist");
		}
		List<ValidationResult> results = outcome.results();
		IntStream.range(0, results.size()).forEach(i -> insertFinding(runId, i, results.get(i)));
	}

	@Override
	public void fail(UUID runId, FailedStage stage, String errorMessage, long latencyMs, Instant finishedAt) {
		jdbc.sql("""
				UPDATE review_runs
				SET status = 'FAILED', failed_stage = :stage, error_message = :error, latency_ms = :latencyMs, finished_at = :finishedAt
				WHERE id = :id
				""")
				.param("id", runId)
				.param("stage", stage.name())
				.param("error", truncate(errorMessage))
				.param("latencyMs", latencyMs)
				.param("finishedAt", utc(finishedAt))
				.update();
	}

	@Override
	public void recordPublished(UUID runId, long githubReviewId, boolean summaryOnly, Instant publishedAt) {
		jdbc.sql("""
				UPDATE review_runs
				SET github_review_id = :reviewId, publish_summary_only = :summaryOnly, published_at = :publishedAt
				WHERE id = :id
				""")
				.param("id", runId)
				.param("reviewId", githubReviewId)
				.param("summaryOnly", summaryOnly)
				.param("publishedAt", utc(publishedAt))
				.update();
	}

	@Override
	public void markSuperseded(UUID runId) {
		// Only a finished run can be superseded; a published one is left alone (its review already exists).
		jdbc.sql("UPDATE review_runs SET status = 'SUPERSEDED' WHERE id = :id AND status = 'SUCCEEDED' AND github_review_id IS NULL")
				.param("id", runId)
				.update();
	}

	@Override
	public Optional<ReviewRun> findRun(UUID id) {
		return jdbc.sql("SELECT * FROM review_runs WHERE id = :id")
				.param("id", id)
				.query(this::mapRun)
				.optional();
	}

	@Override
	public List<StoredFinding> findFindings(UUID runId) {
		return jdbc.sql("""
				SELECT id, run_id, position, location_id, file_path, line, side, category, title, explanation,
				       suggested_fix, suggested_test, status, reason
				FROM review_findings
				WHERE run_id = :runId
				ORDER BY position
				""")
				.param("runId", runId)
				.query(this::mapFinding)
				.list();
	}

	// One row per candidate finding, whatever the validator decided: suppressed ones keep their reason (PLAN §6 rule 2).
	private void insertFinding(UUID runId, int position, ValidationResult result) {
		var f = result.finding();
		StoredFinding row = switch (result) {
			case Publishable(_, var category, var loc) -> new StoredFinding(UUID.randomUUID(), runId, position, loc.id(),
					loc.path(), loc.line(), loc.side().name(), category.name(), f.title(), f.explanation(),
					f.suggestedFix(), f.suggestedTest(), FindingStatus.PUBLISHABLE, null);
			case SummaryOnly(_, var category, var reason) -> new StoredFinding(UUID.randomUUID(), runId, position, f.locationId(),
					null, null, null, category.name(), f.title(), f.explanation(),
					f.suggestedFix(), f.suggestedTest(), FindingStatus.SUMMARY_ONLY, reason);
			case Suppressed(_, var reason) -> new StoredFinding(UUID.randomUUID(), runId, position, f.locationId(),
					null, null, null, f.category(), f.title(), f.explanation(),
					f.suggestedFix(), f.suggestedTest(), FindingStatus.SUPPRESSED, reason);
		};
		jdbc.sql("""
				INSERT INTO review_findings (id, run_id, position, location_id, file_path, line, side, category, title,
				                             explanation, suggested_fix, suggested_test, status, reason)
				VALUES (:id, :runId, :position, :locationId, :filePath, :line, :side, :category, :title,
				        :explanation, :suggestedFix, :suggestedTest, :status, :reason)
				""")
				.param("id", row.id())
				.param("runId", row.runId())
				.param("position", row.position())
				.param("locationId", row.locationId())
				.param("filePath", row.filePath())
				.param("line", row.line())
				.param("side", row.side())
				.param("category", row.category())
				.param("title", row.title())
				.param("explanation", row.explanation())
				.param("suggestedFix", row.suggestedFix())
				.param("suggestedTest", row.suggestedTest())
				.param("status", row.status().name())
				.param("reason", row.reason())
				.update();
	}

	private ReviewRun mapRun(ResultSet rs, int rowNum) throws SQLException {
		return new ReviewRun(
				rs.getObject("id", UUID.class),
				rs.getObject("request_id", UUID.class),
				RunSource.valueOf(rs.getString("source")),
				rs.getString("repo"),
				rs.getInt("pr_number"),
				rs.getString("base_sha"),
				rs.getString("head_sha"),
				json.readValue(rs.getString("config"), ReviewConfig.class),
				rs.getString("provider"),
				rs.getString("model"),
				rs.getString("prompt_version"),
				RunStatus.valueOf(rs.getString("status")),
				rs.getString("failed_stage") == null ? null : FailedStage.valueOf(rs.getString("failed_stage")),
				rs.getString("error_message"),
				json.readValue(rs.getString("files_reviewed"), new TypeReference<List<String>>() { }),
				json.readValue(rs.getString("files_skipped"), new TypeReference<List<SkippedFile>>() { }),
				nullableInt(rs, "allowed_locations"),
				nullableInt(rs, "input_tokens"),
				nullableInt(rs, "output_tokens"),
				nullableLong(rs, "generation_ms"),
				nullableLong(rs, "latency_ms"),
				rs.getObject("started_at", OffsetDateTime.class).toInstant(),
				instantOrNull(rs, "finished_at"),
				nullableLong(rs, "github_review_id"),
				instantOrNull(rs, "published_at"),
				rs.getBoolean("publish_summary_only"));
	}

	private StoredFinding mapFinding(ResultSet rs, int rowNum) throws SQLException {
		return new StoredFinding(
				rs.getObject("id", UUID.class),
				rs.getObject("run_id", UUID.class),
				rs.getInt("position"),
				rs.getString("location_id"),
				rs.getString("file_path"),
				nullableInt(rs, "line"),
				rs.getString("side"),
				rs.getString("category"),
				rs.getString("title"),
				rs.getString("explanation"),
				rs.getString("suggested_fix"),
				rs.getString("suggested_test"),
				FindingStatus.valueOf(rs.getString("status")),
				rs.getString("reason"));
	}

	private String toJson(Object value) {
		return json.writeValueAsString(value);
	}

	// timestamptz <-> Instant: bind as OffsetDateTime in UTC, which the driver supports (pitfalls §5).
	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= MAX_ERROR_CHARS ? message : message.substring(0, MAX_ERROR_CHARS);
	}
}
