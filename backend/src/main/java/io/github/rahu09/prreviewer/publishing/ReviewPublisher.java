package io.github.rahu09.prreviewer.publishing;

import io.github.rahu09.prreviewer.github.GitHubClient;
import io.github.rahu09.prreviewer.github.GitHubException;
import io.github.rahu09.prreviewer.github.PullRequest;
import io.github.rahu09.prreviewer.github.PullRequestReview;
import io.github.rahu09.prreviewer.github.ReviewSubmission;
import io.github.rahu09.prreviewer.github.ReviewSubmission.InlineComment;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.AlreadyPosted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Posted;
import io.github.rahu09.prreviewer.publishing.PublishOutcome.Superseded;
import io.github.rahu09.prreviewer.review.model.FindingStatus;
import io.github.rahu09.prreviewer.review.model.RunSource;
import io.github.rahu09.prreviewer.review.model.RunStatus;
import io.github.rahu09.prreviewer.review.run.ReviewRun;
import io.github.rahu09.prreviewer.review.run.ReviewRunRepository;
import io.github.rahu09.prreviewer.review.run.RunNotFoundException;
import io.github.rahu09.prreviewer.review.run.StoredFinding;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Posts a stored run as ONE GitHub review. The external side effect is made safe to repeat:
 * <ol>
 *   <li>re-fetch the PR; if it closed or its head moved, post nothing ({@code Superseded});</li>
 *   <li>look for our hidden marker (this commit + this config) on the PR's existing reviews; if found, the review
 *       is already there ({@code AlreadyPosted}). This also covers a <em>lost response</em>: GitHub accepted the
 *       review but we never learned its id, so the retry finds it instead of posting a duplicate;</li>
 *   <li>otherwise create it. A 422 (one bad inline location rejects the whole review) is retried once as summary-only.</li>
 * </ol>
 * Not guarded against two simultaneous calls for the same run; the webhook path serialises per PR (Chunks 11-13).
 */
@Service
public class ReviewPublisher {

	private static final Logger log = LoggerFactory.getLogger(ReviewPublisher.class);

	private final GitHubClient github;
	private final ReviewRunRepository runs;
	private final Clock clock = Clock.systemUTC();

	public ReviewPublisher(GitHubClient github, ReviewRunRepository runs) {
		this.github = github;
		this.runs = runs;
	}

	/**
	 * @throws RunNotFoundException if the run does not exist
	 * @throws PublishNotAllowedException if it is not a finished PR run
	 * @throws GitHubException if GitHub refuses for a reason other than the handled 422
	 */
	public PublishOutcome publish(UUID runId) {
		ReviewRun run = runs.findRun(runId).orElseThrow(() -> new RunNotFoundException(runId));
		if (run.source() != RunSource.PR) {
			throw new PublishNotAllowedException("Run " + runId + " reviewed a " + run.source() + ", not a pull request: nothing to post to");
		}
		if (run.githubReviewId() != null) {
			return new AlreadyPosted(run.githubReviewId());
		}
		if (run.status() == RunStatus.SUPERSEDED) {
			return new Superseded("run was already superseded");
		}
		if (run.status() != RunStatus.SUCCEEDED) {
			throw new PublishNotAllowedException("Run " + runId + " is " + run.status() + ", only a SUCCEEDED run can be published");
		}

		String[] repoParts = run.repo().split("/", 2);
		String owner = repoParts[0];
		String repo = repoParts[1];

		PullRequest pr = github.getPullRequest(owner, repo, run.prNumber());
		if (!pr.isOpen()) {
			return supersede(run, "pull request is " + pr.state());
		}
		if (!run.headSha().equals(pr.head().sha())) {
			return supersede(run, "head moved from %s to %s".formatted(run.headSha(), pr.head().sha()));
		}

		var existing = github.listReviews(owner, repo, run.prNumber()).stream()
				.filter(r -> ReviewSummaryFormatter.carriesMarker(r.body(), run.headSha(), run.config()))
				.findFirst();
		if (existing.isPresent()) {
			PullRequestReview review = existing.get();
			log.info("Review {} already on {}#{} for {}: not posting again", review.id(), run.repo(), run.prNumber(), run.headSha());
			runs.recordPublished(runId, review.id(), false, clock.instant());
			return new AlreadyPosted(review.id());
		}

		List<StoredFinding> findings = runs.findFindings(runId);
		List<InlineComment> inline = findings.stream()
				.filter(f -> f.status() == FindingStatus.PUBLISHABLE)
				.map(f -> new InlineComment(f.filePath(), f.line(), f.side(), ReviewSummaryFormatter.inlineComment(f)))
				.toList();

		boolean summaryOnly = false;
		long reviewId;
		try {
			reviewId = github.createReview(owner, repo, run.prNumber(),
					ReviewSubmission.comment(run.headSha(), ReviewSummaryFormatter.summary(run, findings, true), inline));
		}
		catch (GitHubException e) {
			if (e.status() != 422 || inline.isEmpty()) {
				throw e;
			}
			// One inline location GitHub does not accept rejects the whole review. Post once more with every finding in the body.
			log.warn("GitHub refused inline comments on {}#{} ({}); re-posting summary-only", run.repo(), run.prNumber(), e.getMessage());
			summaryOnly = true;
			reviewId = github.createReview(owner, repo, run.prNumber(),
					ReviewSubmission.comment(run.headSha(), ReviewSummaryFormatter.summary(run, findings, false), List.of()));
		}

		runs.recordPublished(runId, reviewId, summaryOnly, clock.instant());
		return new Posted(reviewId, summaryOnly ? 0 : inline.size(), summaryOnly);
	}

	private Superseded supersede(ReviewRun run, String reason) {
		log.info("Not posting run {} on {}#{}: {}", run.id(), run.repo(), run.prNumber(), reason);
		runs.markSuperseded(run.id());
		return new Superseded(reason);
	}
}
