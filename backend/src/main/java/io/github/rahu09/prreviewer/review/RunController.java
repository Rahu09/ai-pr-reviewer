package io.github.rahu09.prreviewer.review;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read side of the audit trail. Internal like the dry-run API: never exposed publicly (PLAN §6 rule 12). */
@RestController
@RequestMapping("/api/runs")
public class RunController {

	private final ReviewRunRepository runs;

	public RunController(ReviewRunRepository runs) {
		this.runs = runs;
	}

	@GetMapping("/{id}")
	public RunDetails get(@PathVariable UUID id) {
		ReviewRun run = runs.findRun(id).orElseThrow(() -> new RunNotFoundException(id));
		return new RunDetails(run, runs.findFindings(id));
	}
}
