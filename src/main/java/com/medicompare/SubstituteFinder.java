package com.medicompare;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds products with the same active ingredients, using ONLY what the
 * dataset says. Each ingredient stays paired with its own strength, so
 * "A 5mg + B 50mg" is never treated as equal to "A 50mg + B 5mg".
 * Anything that cannot be verified is labelled, never silently accepted.
 */
public final class SubstituteFinder {

    public enum MatchLevel {
        CLOSE_MATCH("Close match"),
        DIFFERENT_STRENGTH("Partial - same ingredients, DIFFERENT strength"),
        DIFFERENT_FORM("Partial - same ingredients and strength, "
                + "DIFFERENT dosage form"),
        UNVERIFIED("Unverified - required data missing");

        public final String label;

        MatchLevel(String label) {
            this.label = label;
        }
    }

    public record MatchResult(
            Medicine medicine, MatchLevel level, String note) {
    }

    /**
     * ingredientKey: sorted ingredient names joined with "+".
     * strengths: aligned with that order, joined with "+"; empty if any
     *            strength is unknown.
     * form: empty if unknown.
     */
    private record Spec(String ingredientKey, String strengths, String form) {
    }

    private record Entry(Medicine medicine, Spec spec) {
    }

    private final Map<String, List<Entry>> byIngredients = new HashMap<>();
    private final Map<String, List<Medicine>> byName = new HashMap<>();

    public SubstituteFinder(List<Medicine> all) {
        for (Medicine medicine : all) {
            Spec spec = spec(medicine);
            if (spec != null) {
                byIngredients
                        .computeIfAbsent(spec.ingredientKey(),
                                key -> new ArrayList<>())
                        .add(new Entry(medicine, spec));
            }
            byName.computeIfAbsent(TextUtil.key(medicine.name()),
                    key -> new ArrayList<>()).add(medicine);
        }
    }

    /** Candidates for the selected medicine, best-verified first. */
    public List<MatchResult> find(Medicine selected) {
        Spec wanted = spec(selected);
        if (wanted == null) {
            return List.of();
        }

        List<MatchResult> results = new ArrayList<>();
        for (Entry entry : byIngredients.getOrDefault(
                wanted.ingredientKey(), List.of())) {

            if (entry.medicine().id().equals(selected.id())) {
                continue;
            }
            MatchResult result = compare(
                    selected, wanted, entry.medicine(), entry.spec());
            if (result != null) {
                results.add(result);
            }
        }

        // best-verified first; discontinued products last; then the lowest
        // price per tablet/capsule where it can be worked out (pack sizes
        // differ, so pack prices alone are not comparable)
        results.sort(Comparator
                .comparing((MatchResult r) -> r.level().ordinal())
                .thenComparing(r -> r.medicine().isDiscontinued())
                .thenComparing(r -> PriceNotes.perUnit(r.medicine()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.medicine().price(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.medicine().name(),
                        String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    /** Compares two specific products; null if the ingredients differ. */
    public MatchResult compare(Medicine selected, Medicine candidate) {
        Spec wanted = spec(selected);
        Spec other = spec(candidate);
        if (wanted == null || other == null) {
            return null;
        }
        return compare(selected, wanted, candidate, other);
    }

    public List<Medicine> findByName(String name) {
        return byName.getOrDefault(TextUtil.key(name), List.of());
    }

    // ------------------------------------------------------------------

    private static MatchResult compare(
            Medicine selected, Spec wanted, Medicine candidate, Spec other) {

        if (!wanted.ingredientKey().equals(other.ingredientKey())) {
            return null;
        }

        List<String> missing = new ArrayList<>();
        if (wanted.strengths().isEmpty()) {
            missing.add("strength of the selected medicine");
        }
        if (other.strengths().isEmpty()) {
            missing.add("strength of this candidate");
        }
        if (wanted.form().isEmpty()) {
            missing.add("dosage form of the selected medicine");
        }
        if (other.form().isEmpty()) {
            missing.add("dosage form of this candidate");
        }

        if (!missing.isEmpty()) {
            return new MatchResult(candidate, MatchLevel.UNVERIFIED,
                    "Same ingredients, but not verifiable - missing in "
                            + "dataset: " + String.join(", ", missing));
        }

        boolean sameStrength = wanted.strengths().equals(other.strengths());
        boolean sameForm = wanted.form().equals(other.form());
        boolean sameName = TextUtil.key(selected.name())
                .equals(TextUtil.key(candidate.name()));

        if (sameStrength && sameForm) {
            return new MatchResult(candidate, MatchLevel.CLOSE_MATCH,
                    "Ingredients, strength and dosage form agree"
                            + (sameName ? " (same product name - another "
                            + "pack or listing)" : ""));
        }
        if (sameForm) {
            return new MatchResult(candidate, MatchLevel.DIFFERENT_STRENGTH,
                    "Strength differs: " + wanted.strengths() + " vs "
                            + other.strengths());
        }
        if (sameStrength) {
            return new MatchResult(candidate, MatchLevel.DIFFERENT_FORM,
                    "Dosage form differs: " + wanted.form() + " vs "
                            + other.form());
        }
        return null;
    }

    /** Null when the composition is unknown (nothing can be matched). */
    private static Spec spec(Medicine medicine) {
        if (medicine.ingredients().isEmpty()) {
            return null;
        }

        List<Ingredient> sorted = medicine.ingredients().stream()
                .sorted(Comparator.comparing(Ingredient::name))
                .toList();

        String key = sorted.stream().map(Ingredient::name)
                .collect(Collectors.joining("+"));
        boolean strengthsKnown =
                sorted.stream().allMatch(Ingredient::hasStrength);
        String strengths = strengthsKnown
                ? sorted.stream().map(Ingredient::strength)
                .collect(Collectors.joining("+"))
                : "";

        return new Spec(key, strengths, medicine.dosageForm());
    }
}
