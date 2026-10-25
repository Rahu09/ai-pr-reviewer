package io.github.rahu09.prreviewer.context;

import java.util.List;
import java.util.Objects;

/** One {@code @@ -oldStart,oldCount +newStart,newCount @@ section} block and its lines. */
public record Hunk(int oldStart, int oldCount, int newStart, int newCount, String section, List<DiffLine> lines) {

	public Hunk {
		if (oldStart < 0 || oldCount < 0 || newStart < 0 || newCount < 0) {
			throw new IllegalArgumentException("hunk numbers must be >= 0");
		}
		Objects.requireNonNull(section, "section");
		lines = List.copyOf(lines);
	}

	/** The header as git prints it, for the prompt. */
	public String header() {
		return "@@ -%d,%d +%d,%d @@%s".formatted(oldStart, oldCount, newStart, newCount, section.isEmpty() ? "" : " " + section);
	}
}
