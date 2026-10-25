package io.github.rahu09.prreviewer.context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a git unified diff into files, hunks and typed lines.
 *
 * <p>A plain loop on purpose (style guide §3): the parser is a state machine that tracks the current file, the
 * current hunk and the old/new line counters, which reads worse as a stream with captured mutable state.
 *
 * <p>The key rule: inside a hunk we trust the line counts from the {@code @@} header, not the line's first
 * characters. That is what lets a removed line whose text starts with {@code --} (so the diff line begins with
 * {@code ---}) or a context line that looks like {@code diff --git} be read correctly.
 */
public final class DiffParser {

	private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@ ?(.*)$");
	private static final String DIFF_GIT = "diff --git ";
	private static final String DEV_NULL = "/dev/null";

	private DiffParser() {
	}

	public static ParsedDiff parse(String diff) {
		List<FileDiff> files = new ArrayList<>();
		FileState file = null;
		HunkState hunk = null;

		for (String line : diff.lines().toList()) {
			if (line.startsWith("\\")) {
				continue; // "\ No newline at end of file": about the previous line, carries no line of its own
			}

			if (hunk != null && !hunk.complete()) {
				if (line.startsWith(DIFF_GIT)) {
					throw new DiffParseException("Truncated hunk in " + file.path + ": expected %d more old and %d more new lines"
							.formatted(hunk.remainingOld, hunk.remainingNew));
				}
				hunk.consume(line, file.path);
				continue;
			}
			if (hunk != null) {
				file.hunks.add(hunk.toHunk());
				hunk = null;
			}

			if (line.startsWith(DIFF_GIT)) {
				if (file != null) {
					files.add(file.toFileDiff());
				}
				file = FileState.fromGitHeader(line.substring(DIFF_GIT.length()));
			}
			else if (file == null) {
				continue; // preamble before the first "diff --git" (email headers, commit message)
			}
			else if (file.binary) {
				continue; // base85 payload of "GIT binary patch" is not text
			}
			else if (line.startsWith("@@ ")) {
				hunk = HunkState.fromHeader(line, file.path);
			}
			else if (file.hunks.isEmpty()) {
				file.readHeaderLine(line);
			}
			else if (isHunkBodyLine(line)) {
				// Between hunks only "@@" or the next "diff --git" may follow. A body-looking line here means
				// the previous header under-counted its lines: the diff is hand-edited or damaged.
				throw new DiffParseException("Hunk of " + file.path + " has more lines than its header says, at: " + line);
			}
		}

		if (hunk != null) {
			if (!hunk.complete()) {
				throw new DiffParseException("Truncated hunk in " + file.path + ": the diff ends before the hunk does");
			}
			file.hunks.add(hunk.toHunk());
		}
		if (file != null) {
			files.add(file.toFileDiff());
		}
		return new ParsedDiff(files);
	}

	private static boolean isHunkBodyLine(String line) {
		// "-- " alone is the signature separator that ends a format-patch email, not a removed line
		return (line.startsWith(" ") || line.startsWith("+") || line.startsWith("-")) && !line.equals("-- ");
	}

	/** Mutable per-file state; only lives inside one parse call. */
	private static final class FileState {
		String path;
		String oldPath;
		ChangeType type = ChangeType.MODIFIED;
		boolean binary;
		final List<Hunk> hunks = new ArrayList<>();

		static FileState fromGitHeader(String rest) {
			String[] paths = splitPaths(rest);
			FileState s = new FileState();
			s.oldPath = stripPrefix(unquote(paths[0]), "a/");
			s.path = stripPrefix(unquote(paths[1]), "b/");
			return s;
		}

		/** "a/P b/P" is ambiguous when P has spaces; for the common same-path case compare both halves. */
		private static String[] splitPaths(String rest) {
			int n = (rest.length() - 5) / 2;
			if (n > 0 && rest.length() == 2 * n + 5 && rest.startsWith("a/") && rest.startsWith(" b/", n + 2)
					&& rest.substring(2, 2 + n).equals(rest.substring(n + 5))) {
				return new String[] { rest.substring(0, 2 + n), rest.substring(n + 3) };
			}
			int split = rest.lastIndexOf(" b/");
			if (split < 0) {
				split = rest.lastIndexOf(" \"b/");
			}
			if (split < 0) {
				throw new DiffParseException("Cannot read file paths from: diff --git " + rest);
			}
			return new String[] { rest.substring(0, split), rest.substring(split + 1) };
		}

		void readHeaderLine(String line) {
			if (line.startsWith("new file mode")) {
				type = ChangeType.ADDED;
			}
			else if (line.startsWith("deleted file mode")) {
				type = ChangeType.DELETED;
			}
			else if (line.startsWith("rename from ")) {
				type = ChangeType.RENAMED;
				oldPath = unquote(line.substring("rename from ".length()));
			}
			else if (line.startsWith("rename to ")) {
				type = ChangeType.RENAMED;
				path = unquote(line.substring("rename to ".length()));
			}
			else if (line.startsWith("Binary files ") || line.startsWith("GIT binary patch")) {
				binary = true;
			}
			else if (line.startsWith("--- ")) {
				String p = unquote(line.substring(4));
				if (!p.equals(DEV_NULL)) {
					oldPath = stripPrefix(p, "a/");
				}
			}
			else if (line.startsWith("+++ ")) {
				String p = unquote(line.substring(4));
				if (!p.equals(DEV_NULL)) {
					path = stripPrefix(p, "b/");
				}
			}
			// index, mode, similarity ... lines carry nothing we need
		}

		FileDiff toFileDiff() {
			return switch (type) {
				case DELETED -> new FileDiff(oldPath, oldPath, type, binary, hunks);
				case ADDED -> new FileDiff(path, path, type, binary, hunks);
				case MODIFIED, RENAMED -> new FileDiff(path, oldPath, type, binary, hunks);
			};
		}
	}

	/** Mutable per-hunk state: counters that say how many old/new lines are still expected. */
	private static final class HunkState {
		final int oldStart, oldCount, newStart, newCount;
		final String section;
		final List<DiffLine> lines = new ArrayList<>();
		int remainingOld, remainingNew, oldLine, newLine;

		private HunkState(int oldStart, int oldCount, int newStart, int newCount, String section) {
			this.oldStart = oldStart;
			this.oldCount = oldCount;
			this.newStart = newStart;
			this.newCount = newCount;
			this.section = section;
			this.remainingOld = oldCount;
			this.remainingNew = newCount;
			this.oldLine = oldStart;
			this.newLine = newStart;
		}

		static HunkState fromHeader(String line, String path) {
			Matcher m = HUNK_HEADER.matcher(line);
			if (!m.matches()) {
				throw new DiffParseException("Bad hunk header in " + path + ": " + line);
			}
			return new HunkState(Integer.parseInt(m.group(1)), count(m.group(2)), Integer.parseInt(m.group(3)),
					count(m.group(4)), m.group(5));
		}

		/** A missing count means 1 ("@@ -3 +3 @@"). */
		private static int count(String group) {
			return group == null ? 1 : Integer.parseInt(group);
		}

		boolean complete() {
			return remainingOld == 0 && remainingNew == 0;
		}

		void consume(String line, String path) {
			char kind = line.isEmpty() ? ' ' : line.charAt(0); // some tools strip the single space of an empty context line
			String text = line.isEmpty() ? "" : line.substring(1);
			switch (kind) {
				case ' ' -> {
					need(remainingOld > 0 && remainingNew > 0, path, line);
					lines.add(new DiffLine.Context(oldLine++, newLine++, text));
					remainingOld--;
					remainingNew--;
				}
				case '+' -> {
					need(remainingNew > 0, path, line);
					lines.add(new DiffLine.Added(newLine++, text));
					remainingNew--;
				}
				case '-' -> {
					need(remainingOld > 0, path, line);
					lines.add(new DiffLine.Removed(oldLine++, text));
					remainingOld--;
				}
				default -> throw new DiffParseException("Unexpected line inside a hunk of " + path + ": " + line);
			}
		}

		private static void need(boolean ok, String path, String line) {
			if (!ok) {
				throw new DiffParseException("Hunk of " + path + " has more lines than its header says, at: " + line);
			}
		}

		Hunk toHunk() {
			return new Hunk(oldStart, oldCount, newStart, newCount, section, lines);
		}
	}

	private static String stripPrefix(String path, String prefix) {
		return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
	}

	/** Git quotes paths with unusual characters: "a/some\"file". Handles the common escapes only. */
	private static String unquote(String path) {
		if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
			return path.substring(1, path.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\t", "\t");
		}
		return path;
	}
}
