package io.github.rahu09.prreviewer.review.api;

import io.github.rahu09.prreviewer.github.GitHubSnapshotProvider;
import io.github.rahu09.prreviewer.review.ReviewConfig;
import io.github.rahu09.prreviewer.review.ReviewOrchestrator;
import io.github.rahu09.prreviewer.review.ReviewSnapshot;
import io.github.rahu09.prreviewer.review.fixture.FixtureLoader;
import io.github.rahu09.prreviewer.review.model.RunSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the engine without posting anything. Calls a paid model, so it must never be exposed publicly
 * (PLAN §6 rule 12): only the webhook and health endpoints are public.
 */
@RestController
@RequestMapping("/api/dry-run")
public class DryRunController {

	private final ReviewOrchestrator orchestrator;
	private final FixtureLoader fixtures;
	private final GitHubSnapshotProvider github;

	public DryRunController(ReviewOrchestrator orchestrator, FixtureLoader fixtures, GitHubSnapshotProvider github) {
		this.orchestrator = orchestrator;
		this.fixtures = fixtures;
		this.github = github;
	}

	public record DiffRequest(@NotBlank @Size(max = 200_000) String diff, ReviewConfig.Override config) {
	}

	/** Owner and repo go into a URL path, so only GitHub-legal characters are accepted. */
	public record PrRequest(
			@NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{0,38}") String owner,
			@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,100}") String repo,
			@Min(1) int number,
			ReviewConfig.Override config) {
	}

	public record FixtureRequest(ReviewConfig.Override config) {
	}

	/** Review a pasted unified diff (no file contents, so no fixture answer key). */
	@PostMapping("/diff")
	public ReviewReport dryRunDiff(@Valid @RequestBody DiffRequest request) {
		return ReviewReport.from(orchestrator.review(ReviewSnapshot.ofDiff(request.diff()), RunSource.DIFF, request.config()), null);
	}

	/** Review a fixture folder; the report includes its answer key so results can be compared. The body is optional. */
	@PostMapping("/fixture/{id}")
	public ReviewReport dryRunFixture(@PathVariable String id, @RequestBody(required = false) FixtureRequest request) {
		FixtureLoader.LoadedFixture fixture = fixtures.load(id);
		ReviewConfig.Override override = request == null ? null : request.config();
		return ReviewReport.from(orchestrator.review(fixture.snapshot(), RunSource.FIXTURE, override), fixture.meta());
	}

	/** Review a real pull request, read-only: nothing is posted to GitHub (publishing is Chunk 7). */
	@PostMapping("/pr")
	public ReviewReport dryRunPr(@Valid @RequestBody PrRequest request) {
		var outcome = orchestrator.reviewFetched(RunSource.PR, request.owner() + "/" + request.repo(), request.number(),
				() -> github.fetch(request.owner(), request.repo(), request.number()), request.config());
		return ReviewReport.from(outcome, null);
	}
}
