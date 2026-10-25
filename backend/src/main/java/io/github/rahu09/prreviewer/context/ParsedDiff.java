package io.github.rahu09.prreviewer.context;

import java.util.List;

public record ParsedDiff(List<FileDiff> files) {

	public ParsedDiff {
		files = List.copyOf(files);
	}
}
