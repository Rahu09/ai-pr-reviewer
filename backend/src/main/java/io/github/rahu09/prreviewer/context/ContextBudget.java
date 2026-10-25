package io.github.rahu09.prreviewer.context;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which files of a PR are sent to the model: only Java files with real changes, within three limits
 * (files, changed lines, prompt size). Everything else is skipped AND recorded with a reason, so the summary can
 * say honestly what was not reviewed (PLAN §2).
 */
public record ContextBudget(int maxJavaFiles, int maxChangedLines, int maxPromptChars) {

	public ContextBudget {
		if (maxJavaFiles < 1 || maxChangedLines < 1 || maxPromptChars < 1) {
			throw new IllegalArgumentException("budget limits must be positive");
		}
	}

	public record Selection(List<FileDiff> reviewed, List<SkippedFile> skipped) {
		public Selection {
			reviewed = List.copyOf(reviewed);
			skipped = List.copyOf(skipped);
		}
	}

	public Selection select(ParsedDiff diff) {
		List<FileDiff> reviewed = new ArrayList<>();
		List<SkippedFile> skipped = new ArrayList<>();
		int changedSoFar = 0;
		int charsSoFar = 0;

		// A loop: each decision depends on what earlier files already used up (running totals).
		for (FileDiff file : diff.files()) {
			int changed = file.changedLines();
			if (file.binary()) {
				skipped.add(new SkippedFile(file.path(), SkipReason.BINARY, "binary file"));
			}
			else if (file.changeType() == ChangeType.DELETED) {
				skipped.add(new SkippedFile(file.path(), SkipReason.DELETED, "deleted file: nothing to comment on"));
			}
			else if (!file.isJava()) {
				skipped.add(new SkippedFile(file.path(), SkipReason.NOT_JAVA, "only Java files are reviewed"));
			}
			else if (file.hunks().isEmpty()) {
				skipped.add(new SkippedFile(file.path(), SkipReason.NO_CONTENT_CHANGE, "rename or mode change without edits"));
			}
			else if (changed > maxChangedLines) {
				skipped.add(new SkippedFile(file.path(), SkipReason.FILE_TOO_LARGE,
						"%d changed lines, limit is %d".formatted(changed, maxChangedLines)));
			}
			else if (reviewed.size() >= maxJavaFiles) {
				skipped.add(new SkippedFile(file.path(), SkipReason.TOO_MANY_FILES, "limit is %d Java files".formatted(maxJavaFiles)));
			}
			else if (changedSoFar + changed > maxChangedLines) {
				skipped.add(new SkippedFile(file.path(), SkipReason.CHANGED_LINES_LIMIT,
						"would exceed %d changed lines in total".formatted(maxChangedLines)));
			}
			else {
				int chars = AnnotatedDiff.of(List.of(file)).text().length();
				if (charsSoFar + chars > maxPromptChars) {
					skipped.add(new SkippedFile(file.path(), SkipReason.PROMPT_TOO_LARGE,
							"would exceed %d prompt characters".formatted(maxPromptChars)));
				}
				else {
					reviewed.add(file);
					changedSoFar += changed;
					charsSoFar += chars;
				}
			}
		}
		return new Selection(reviewed, skipped);
	}
}
