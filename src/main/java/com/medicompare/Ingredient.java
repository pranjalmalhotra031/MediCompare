package com.medicompare;

/**
 * One active ingredient of a medicine.
 *
 * name:     normalised for comparison (lower case, spelling variants merged)
 * strength: canonical text such as "500mg" or "30mg/5ml"; empty when the
 *           dataset gives none ("NA", blank) or it cannot be read
 */
public record Ingredient(String name, String strength) {
    public boolean hasStrength() {
        return !strength.isEmpty();
    }
}
