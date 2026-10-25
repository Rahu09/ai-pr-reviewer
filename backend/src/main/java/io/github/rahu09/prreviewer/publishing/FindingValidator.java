package io.github.rahu09.prreviewer.publishing;

import io.github.rahu09.prreviewer.context.location.AllowedLocation;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Publishable;
import io.github.rahu09.prreviewer.publishing.ValidationResult.SummaryOnly;
import io.github.rahu09.prreviewer.publishing.ValidationResult.Suppressed;
import io.github.rahu09.prreviewer.review.model.CandidateFinding;
import io.github.rahu09.prreviewer.review.model.Category;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Parse leniently, validate strictly (ADR-0008). Every candidate gets exactly one {@link ValidationResult}, in
 * the same order, so nothing the model said disappears.
 *
 * <p>This proves STRUCTURE (a real location, a known category, required text, no duplicates, the inline cap). It
 * does not prove the claim is true: truth is what the evaluation measures (ADR-0011).
 */
@Component
public class FindingValidator {

	public List<ValidationResult> validate(List<CandidateFinding> candidates, List<AllowedLocation> locations, int maxInlineFindings) {
		Map<String, AllowedLocation> byId = locations.stream()
				.collect(Collectors.toMap(AllowedLocation::id, Function.identity()));

		List<ValidationResult> checked = candidates.stream().map(c -> check(c, byId)).toList();

		// Same location + same category = the same finding twice: keep the first, suppress the rest.
		Map<String, Publishable> firstPerKey = checked.stream()
				.filter(Publishable.class::isInstance)
				.map(Publishable.class::cast)
				.collect(Collectors.toMap(
						p -> p.location().id() + "|" + p.category(),
						Function.identity(),
						(first, _) -> first,
						LinkedHashMap::new));

		// Records compare by value, so use identity to tell "the winner" from an equal-looking loser.
		Set<Publishable> winners = Collections.newSetFromMap(new IdentityHashMap<>());
		winners.addAll(firstPerKey.values());
		Set<Publishable> inline = Collections.newSetFromMap(new IdentityHashMap<>());
		firstPerKey.values().stream().limit(maxInlineFindings).forEach(inline::add);

		return checked.stream().<ValidationResult>map(r -> switch (r) {
			case Publishable p when !winners.contains(p) -> new Suppressed(p.finding(),
					"duplicate of an earlier finding at %s with category %s".formatted(p.location().id(), p.category()));
			case Publishable p when !inline.contains(p) -> new SummaryOnly(p.finding(), p.category(),
					"inline comment cap of %d reached".formatted(maxInlineFindings));
			case Publishable p -> p;
			case SummaryOnly s -> s;
			case Suppressed s -> s;
		}).toList();
	}

	private ValidationResult check(CandidateFinding f, Map<String, AllowedLocation> byId) {
		Category category = Category.parse(f.category()).orElse(null);
		if (category == null) {
			return new Suppressed(f, "unknown category '%s'".formatted(f.category()));
		}
		if (f.title().isBlank() || f.explanation().isBlank()) {
			return new Suppressed(f, "missing title or explanation");
		}
		if (f.locationId().isBlank()) {
			return new SummaryOnly(f, category, "no location given: cannot attach to a line");
		}
		AllowedLocation location = byId.get(normalizeId(f.locationId()));
		if (location == null) {
			return new Suppressed(f, "unknown location '%s': not an added line of this change".formatted(f.locationId()));
		}
		return new Publishable(f, category, location);
	}

	/** "[L12]", " l12 " and "L12" all mean the same ID; anything else stays as is and will not be found. */
	private static String normalizeId(String raw) {
		return raw.replace("[", "").replace("]", "").strip().toUpperCase();
	}
}
