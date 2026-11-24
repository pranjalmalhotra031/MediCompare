package com.medicompare;

import com.medicompare.SubstituteFinder.MatchLevel;
import com.medicompare.SubstituteFinder.MatchResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** All data here is synthetic. */
class SubstituteFinderTest {
    private static int counter = 0;

    /** composition like "Paracetamol (500mg)" or "A (5mg) + B (50mg)". */
    private static Medicine med(
            String name, String composition, String form, String price,
            boolean discontinued) {

        List<Ingredient> ingredients = new ArrayList<>();
        if (!composition.isEmpty()) {
            for (String part : composition.split("\\+")) {
                ingredients.add(CompositionParser.parse(part).orElseThrow());
            }
        }
        return new Medicine("id" + (++counter), name, "Maker",
                "strip of 10 tablets",
                price == null ? null : new BigDecimal(price),
                discontinued, composition, ingredients, form,
                form.isEmpty() ? "" : "parsed", List.of(), List.of(), "");
    }

    private static Medicine med(String name, String composition, String form) {
        return med(name, composition, form, "10", false);
    }

    private static List<MatchResult> find(Medicine selected, Medicine... all) {
        List<Medicine> list = new ArrayList<>(List.of(all));
        list.add(selected);
        return new SubstituteFinder(list).find(selected);
    }

    @Test
    void closeMatchNeedsIngredientStrengthAndFormToAgree() {
        var a = med("A Tablet", "Paracetamol (500mg)", "tablet");
        var b = med("B Tablet", "Paracetamol (500mg)", "tablet");

        List<MatchResult> found = find(a, b);
        assertEquals(1, found.size());
        assertEquals(MatchLevel.CLOSE_MATCH, found.get(0).level());
    }

    @Test
    void strengthsStayPairedWithTheirIngredients() {
        var a = med("A", "Amlodipine (5mg) + Atenolol (50mg)", "tablet");
        var same = med("B", "Atenolol (50mg) + Amlodipine (5mg)", "tablet");
        var swapped = med("C", "Amlodipine (50mg) + Atenolol (5mg)", "tablet");

        List<MatchResult> found = find(a, same, swapped);
        assertEquals(2, found.size());
        assertEquals("B", found.get(0).medicine().name());
        assertEquals(MatchLevel.CLOSE_MATCH, found.get(0).level());
        assertEquals("C", found.get(1).medicine().name());
        assertEquals(MatchLevel.DIFFERENT_STRENGTH, found.get(1).level());
    }

    @Test
    void differentStrengthOrFormIsOnlyPartial() {
        var a = med("A", "Paracetamol (500mg)", "tablet");

        assertEquals(MatchLevel.DIFFERENT_STRENGTH, find(a,
                med("B", "Paracetamol (650mg)", "tablet")).get(0).level());
        assertEquals(MatchLevel.DIFFERENT_FORM, find(a,
                med("B", "Paracetamol (500mg)", "capsule")).get(0).level());
        assertTrue(find(a,
                med("B", "Paracetamol (120mg)", "syrup")).isEmpty());
    }

    @Test
    void sustainedReleaseIsNeverCloseToPlain() {
        var plain = med("A", "Metformin (500mg)", "tablet");
        var sr = med("B", "Metformin (500mg)", "sustained release tablet");

        assertEquals(MatchLevel.DIFFERENT_FORM, find(plain, sr).get(0).level());
    }

    @Test
    void unknownStrengthOrFormIsUnverifiedNeverClose() {
        var a = med("A", "Paracetamol (500mg)", "tablet");
        var naStrength = med("B", "Paracetamol (NA)", "tablet");
        var noForm = med("C", "Paracetamol (500mg)", "");

        List<MatchResult> found = find(a, naStrength, noForm);
        assertEquals(2, found.size());
        for (MatchResult r : found) {
            assertEquals(MatchLevel.UNVERIFIED, r.level());
            assertTrue(r.note().contains("missing"));
        }
    }

    @Test
    void productWithoutCompositionIsNeverMatched() {
        var none = med("A", "", "tablet");
        var other = med("B", "Paracetamol (500mg)", "tablet");

        assertTrue(find(none, other).isEmpty());
        assertTrue(find(other, none).isEmpty());
    }

    @Test
    void spellingVariantsMatchButSaltsDoNot() {
        assertEquals(MatchLevel.CLOSE_MATCH, find(
                med("A", "Amoxycillin (500mg)", "capsule"),
                med("B", "Amoxicillin (500mg)", "capsule")).get(0).level());

        assertTrue(find(
                med("A", "Metoprolol Succinate (50mg)", "tablet"),
                med("B", "Metoprolol Tartrate (50mg)", "tablet")).isEmpty());
    }

    @Test
    void gramsAndMilligramsAreTheSameAmount() {
        assertEquals(MatchLevel.CLOSE_MATCH, find(
                med("A", "Paracetamol (1gm)", "tablet"),
                med("B", "Paracetamol (1000mg)", "tablet")).get(0).level());
    }

    @Test
    void closeMatchesOrderedByPerTabletPriceDiscontinuedLastNullsLast() {
        var a = med("A", "Paracetamol (500mg)", "tablet");
        var cheap = med("Cheap", "Paracetamol (500mg)", "tablet", "5", false);
        var dear = med("Dear", "Paracetamol (500mg)", "tablet", "9", false);
        var gone = med("Gone", "Paracetamol (500mg)", "tablet", "1", true);
        var none = med("None", "Paracetamol (500mg)", "tablet", null, false);

        List<MatchResult> found = find(a, none, gone, dear, cheap);
        assertEquals("Cheap", found.get(0).medicine().name());
        assertEquals("Dear", found.get(1).medicine().name());
        assertEquals("None", found.get(2).medicine().name());
        assertEquals("Gone", found.get(3).medicine().name());
    }

    @Test
    void compareChecksTwoSpecificProductsAndLookupByNameIsCaseInsensitive() {
        var a = med("Alpha Tablet", "Paracetamol (500mg)", "tablet");
        var b = med("Beta Tablet", "Paracetamol (500mg)", "tablet");
        var c = med("Gamma Tablet", "Ibuprofen (400mg)", "tablet");
        var finder = new SubstituteFinder(List.of(a, b, c));

        assertEquals(MatchLevel.CLOSE_MATCH,
                finder.compare(a, b).level());
        assertEquals(null, finder.compare(a, c));
        assertEquals(1, finder.findByName("  BETA   tablet ").size());
    }
}
