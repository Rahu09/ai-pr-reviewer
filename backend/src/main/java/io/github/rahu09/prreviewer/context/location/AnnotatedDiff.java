package io.github.rahu09.prreviewer.context.location;

import io.github.rahu09.prreviewer.context.ChangeType;
import io.github.rahu09.prreviewer.context.DiffLine;
import io.github.rahu09.prreviewer.context.FileDiff;
import io.github.rahu09.prreviewer.context.Hunk;
import io.github.rahu09.prreviewer.context.Side;
import java.util.ArrayList;
import java.util.List;

/**
 * The diff as the model sees it, plus the locations it may use. Both come out of ONE rendering pass, so every ID
 * shown in the text is guaranteed to exist in {@code locations} (and vice versa).
 *
 * <p>Each added line is prefixed with its ID: {@code [L3]+return all;}. Context and removed lines have no ID, so the
 * model cannot comment on them (v1 limit, ADR-0008).
 */
public record AnnotatedDiff(String text, List<AllowedLocation> locations) {

	public AnnotatedDiff {
		locations = List.copyOf(locations);
	}

	public static AnnotatedDiff of(List<FileDiff> files) {
		StringBuilder text = new StringBuilder();
		List<AllowedLocation> locations = new ArrayList<>();
		int nextId = 1; // a plain counter shared across files, so IDs are unique per review

		for (FileDiff file : files) {
			text.append("File: ").append(file.path()).append(" (").append(describe(file)).append(")\n");
			for (Hunk hunk : file.hunks()) {
				text.append(hunk.header()).append('\n');
				for (DiffLine line : hunk.lines()) {
					switch (line) {
						case DiffLine.Added(int newLine, String t) -> {
							String id = "L" + nextId++;
							locations.add(new AllowedLocation(id, file.path(), newLine, Side.RIGHT));
							text.append('[').append(id).append("]+").append(t).append('\n');
						}
						case DiffLine.Removed(_, String t) -> text.append('-').append(t).append('\n');
						case DiffLine.Context(_, _, String t) -> text.append(' ').append(t).append('\n');
					}
				}
			}
			text.append('\n');
		}
		return new AnnotatedDiff(text.toString(), locations);
	}

	private static String describe(FileDiff file) {
		return file.changeType() == ChangeType.RENAMED
				? "RENAMED from " + file.oldPath()
				: file.changeType().name();
	}
}
