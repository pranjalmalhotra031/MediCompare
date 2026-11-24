package com.medicompare;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One product from the A-Z catalogue, optionally enriched with usage text
 * from the second dataset. Fields the data does not provide stay empty/null;
 * nothing is guessed.
 *
 * ingredients is EMPTY when the composition is missing or unreadable.
 * dosageFormBasis says how dosageForm was obtained (the catalogue has no
 * dosage-form column, so it is parsed from the name and pack label).
 */
public record Medicine(
        String id,
        String name,
        String manufacturer,
        String packLabel,
        BigDecimal price,
        Boolean discontinued,
        String composition,
        List<Ingredient> ingredients,
        String dosageForm,
        String dosageFormBasis,
        List<String> uses,
        List<String> listedSubstitutes,
        String therapeuticClass
) {
    public Medicine {
        id = nz(id);
        name = nz(name);
        manufacturer = nz(manufacturer);
        packLabel = nz(packLabel);
        composition = nz(composition);
        dosageForm = nz(dosageForm);
        dosageFormBasis = nz(dosageFormBasis);
        therapeuticClass = nz(therapeuticClass);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        uses = uses == null ? List.of() : List.copyOf(uses);
        listedSubstitutes = listedSubstitutes == null
                ? List.of() : List.copyOf(listedSubstitutes);
    }

    public boolean hasPrice() {
        return price != null && price.signum() > 0;
    }

    public boolean isDiscontinued() {
        return Boolean.TRUE.equals(discontinued);
    }

    /** "500mg + 125mg" when every ingredient has a strength, else "". */
    public String strengthText() {
        if (ingredients.isEmpty()
                || !ingredients.stream().allMatch(Ingredient::hasStrength)) {
            return "";
        }
        return ingredients.stream()
                .map(Ingredient::strength)
                .collect(Collectors.joining(" + "));
    }

    public Medicine withUsage(UsageInfo info) {
        return new Medicine(id, name, manufacturer, packLabel, price,
                discontinued, composition, ingredients, dosageForm,
                dosageFormBasis, info.uses(), info.substituteNames(),
                info.therapeuticClass());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
