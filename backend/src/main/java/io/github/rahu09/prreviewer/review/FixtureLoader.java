package io.github.rahu09.prreviewer.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Reads a fixture FOLDER: {@code fixture.json} + {@code change.diff} + {@code files/<path>} (pitfalls §4). */
@Component
public class FixtureLoader {

	/** Ids become folder names, so only plain characters are allowed: no "..", no slashes (path traversal). */
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
	private static final List<String> SPLITS = List.of("dev", "heldout");

	public record LoadedFixture(FixtureMeta meta, ReviewSnapshot snapshot) {
	}

	private final Path root;
	private final JsonMapper jsonMapper;

	public FixtureLoader(FixtureProperties properties, JsonMapper jsonMapper) {
		this.root = Path.of(properties.dir());
		this.jsonMapper = jsonMapper;
	}

	public LoadedFixture load(String id) {
		if (!SAFE_ID.matcher(id).matches() || id.contains("..")) {
			throw new FixtureNotFoundException(id);
		}
		Path folder = SPLITS.stream()
				.map(split -> root.resolve(split).resolve(id))
				.filter(p -> Files.isRegularFile(p.resolve("fixture.json")))
				.findFirst()
				.orElseThrow(() -> new FixtureNotFoundException(id));
		try {
			FixtureMeta meta = jsonMapper.readValue(Files.readString(folder.resolve("fixture.json"), StandardCharsets.UTF_8), FixtureMeta.class);
			String diff = Files.readString(folder.resolve("change.diff"), StandardCharsets.UTF_8);
			ReviewSnapshot snapshot = new ReviewSnapshot("order-service-demo", 0, meta.baseSha(), meta.headSha(), diff, readFiles(folder.resolve("files")));
			return new LoadedFixture(meta, snapshot);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Cannot read fixture " + id, e);
		}
	}

	private static Map<String, String> readFiles(Path filesDir) throws IOException {
		Map<String, String> files = new LinkedHashMap<>();
		if (!Files.isDirectory(filesDir)) {
			return files;
		}
		try (Stream<Path> walk = Files.walk(filesDir)) {
			for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
				files.put(filesDir.relativize(p).toString().replace('\\', '/'), Files.readString(p, StandardCharsets.UTF_8));
			}
		}
		return files;
	}
}
