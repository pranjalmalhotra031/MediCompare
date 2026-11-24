package com.medicompare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionParserTest {
    private static Ingredient parse(String text) {
        return CompositionParser.parse(text).orElseThrow();
    }

    @Test
    void readsNameAndStrengthWithOddSpacing() {
        Ingredient i = parse("  Amoxycillin  (500mg) ");
        assertEquals("amoxicillin", i.name());   // spelling variant merged
        assertEquals("500mg", i.strength());
    }

    @Test
    void naStrengthIsUnknownNotAStrength() {
        Ingredient i = parse("Paracetamol (NA)");
        assertEquals("paracetamol", i.name());
        assertEquals("", i.strength());
    }

    @Test
    void liquidAndPercentStrengthsKeepTheirUnits() {
        assertEquals("30mg/5ml", parse("Ambroxol (30mg/5ml)").strength());
        assertEquals("125mg/5ml", parse("X (125 mg / 5 ml)").strength());
        assertEquals("5%w/v", parse("Povidone Iodine (5% w/v)").strength());
        assertEquals("5%w/w", parse("Y (5% w/w)").strength());
        assertEquals("1000iu", parse("Vitamin D3 (1000 IU)").strength());
        assertEquals("1iu", parse("Z (1 i.u)").strength());
    }

    @Test
    void weightUnitsAreConvertedToMilligrams() {
        assertEquals("1000mg", parse("A (1gm)").strength());
        assertEquals("1000mg", parse("A (1 g)").strength());
        assertEquals("0.25mg", parse("A (250mcg)").strength());
        assertEquals("0.5mg", parse("A (0.50 mg)").strength());
    }

    @Test
    void weightAndWeightPerVolumeAreNotConfused() {
        assertTrue(!parse("A (1gm)").strength()
                .equals(parse("A (1gm/5ml)").strength()));
    }

    @Test
    void numberWithoutUnitIsNotAStrength() {
        assertEquals("", parse("A (500)").strength());
    }

    @Test
    void bracketedWordsBelongToTheName() {
        Ingredient i = parse("Progesterone (Natural Micronized) (25mg)");
        assertEquals("progesterone natural micronized", i.name());
        assertEquals("25mg", i.strength());

        Ingredient plain = parse("Doxorubicin (Plain)");
        assertEquals("doxorubicin plain", plain.name());
        assertEquals("", plain.strength());
    }

    @Test
    void digitsInNamesAreKept() {
        assertEquals("vitamin b12", parse("Vitamin B12 (1500mcg)").name());
        assertTrue(!parse("Vitamin B12 (1mg)").name()
                .equals(parse("Vitamin B6 (1mg)").name()));
    }

    @Test
    void saltsAreNotMerged() {
        assertTrue(!parse("Metoprolol Succinate (50mg)").name()
                .equals(parse("Metoprolol Tartrate (50mg)").name()));
    }

    @Test
    void blankEntryGivesNothing() {
        assertTrue(CompositionParser.parse("   ").isEmpty());
        assertTrue(CompositionParser.parse("(500mg)").isEmpty());
    }
}
