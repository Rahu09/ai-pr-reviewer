package io.github.rahu09.prreviewer.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the engine on a pasted diff without posting anything. Calls a paid model, so it must never be exposed
 * publicly (PLAN §6 rule 12): only the webhook and health endpoints are public.
 */
@RestController
@RequestMapping("/api/dry-run")
public class DryRunController {

	private final ReviewGenerator generator;

	public DryRunController(ReviewGenerator generator) {
		this.generator = generator;
	}

	public record DiffRequest(@NotBlank @Size(max = 60_000) String diff) {
	}

	public record DryRunResponse(
			String provider,
			String model,
			String promptVersion,
			int inputTokens,
			int outputTokens,
			List<CandidateFinding> findings) {
	}

	@PostMapping("/diff")
	public DryRunResponse dryRunDiff(@Valid @RequestBody DiffRequest request) {
		GenerationResult result = generator.generate(request.diff());
		return new DryRunResponse(result.provider(), result.model(), result.promptVersion(),
				result.inputTokens(), result.outputTokens(), result.findings().findings());
	}
}
