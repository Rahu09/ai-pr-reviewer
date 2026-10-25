package io.github.rahu09.prreviewer.context;

import java.util.List;
import java.util.Objects;

/**
 * The changes to one file. {@code path} is the path in the new version (for a deleted file, the old path);
 * {@code oldPath} equals {@code path} unless the file was renamed. Binary files, pure renames and mode-only
 * changes have no hunks.
 */
public record FileDiff(String path, String oldPath, ChangeType changeType, boolean binary, List<Hunk> hunks) {

	public FileDiff {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(oldPath, "oldPath");
		Objects.requireNonNull(changeType, "changeType");
		hunks = List.copyOf(hunks);
	}

	public boolean isJava() {
		return path.endsWith(".java");
	}

	/** Added plus removed lines: the size of the change, not of the file. */
	public int changedLines() {
		return (int) hunks.stream()
				.flatMap(h -> h.lines().stream())
				.filter(l -> !(l instanceof DiffLine.Context))
				.count();
	}
}
