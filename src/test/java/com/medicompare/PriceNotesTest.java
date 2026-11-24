package com.medicompare;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthetic values only. */
class PriceNotesTest {
    private static Medicine med(
            String form, String pack, String price, boolean discontinued) {
        return new Medicine("x", "Foo", "M", pack,
                price == null ? null : new BigDecimal(price), discontinued,
                "Foo (1mg)", List.of(new Ingredient("foo", "1mg")),
                form, "parsed", List.of(), List.of(), "");
    }

    @Test
    void perUnitFromTabletAndCapsuleCounts() {
        assertEquals("5.0000", PriceNotes.perUnit(
                med("tablet", "strip of 10 tablets", "50", false))
                .toPlainString());
        assertEquals("2.0000", PriceNotes.perUnit(
                med("sustained release tablet", "strip of 15 tablet sr",
                        "30", false)).toPlainString());
        assertEquals("3.0000", PriceNotes.perUnit(
                med("soft gelatin capsule",
                        "strip of 10 soft gelatin capsules", "30", false))
                .toPlainString());
    }

    @Test
    void noPerUnitForOtherFormsOrMissingData() {
        assertNull(PriceNotes.perUnit(
                med("syrup", "bottle of 100 ml Syrup", "50", false)));
        assertNull(PriceNotes.perUnit(
                med("tablet", "strip of 10 tablets", null, false)));
        assertNull(PriceNotes.perUnit(
                med("tablet", "blister", "50", false)));
    }

    @Test
    void missingPriceIsNotShownAsZero() {
        assertEquals("price not listed", PriceNotes.shortPrice(
                med("tablet", "strip of 10 tablets", null, false)));
        assertEquals("price not listed", PriceNotes.shortPrice(
                med("tablet", "strip of 10 tablets", "0", false)));
    }

    @Test
    void lowestCloseMatchIgnoresDiscontinuedAndPartialMatches() {
        var selected = med("tablet", "strip of 10 tablets", "100", false);
        var cheapGone = med("tablet", "strip of 10 tablets", "10", true);
        var okay = med("tablet", "strip of 10 tablets", "50", false);
        var cheapPartial = med("tablet", "strip of 10 tablets", "1", false);

        var matches = List.of(
                new SubstituteFinder.MatchResult(cheapGone,
                        SubstituteFinder.MatchLevel.CLOSE_MATCH, ""),
                new SubstituteFinder.MatchResult(okay,
                        SubstituteFinder.MatchLevel.CLOSE_MATCH, ""),
                new SubstituteFinder.MatchResult(cheapPartial,
                        SubstituteFinder.MatchLevel.DIFFERENT_STRENGTH, ""));

        String text = PriceNotes.lowestCloseMatch(selected, matches);
        assertTrue(text.contains("50% lower"), text);
        assertTrue(text.contains("not current prices"));
    }

    @Test
    void textUtilParsesPricesAndCases() {
        assertEquals("1234.50", TextUtil.parsePrice("₹1,234.50").toPlainString());
        assertNull(TextUtil.parsePrice("n/a"));
        assertEquals("Glaxo Smithkline Ltd",
                TextUtil.displayCase("GLAXO  SMITHKLINE LTD"));
        assertEquals("Mixed CASE kept", TextUtil.displayCase("Mixed CASE kept"));
    }

    @Test
    void anUnusuallyLowPriceIsFlaggedNotPresentedAsASaving() {
        var selected = med("tablet", "strip of 10 tablets", "100", false);
        var matches = new java.util.ArrayList<SubstituteFinder.MatchResult>();
        for (String price : List.of("1", "60", "62", "65", "70", "75")) {
            matches.add(new SubstituteFinder.MatchResult(
                    med("tablet", "strip of 10 tablets", price, false),
                    SubstituteFinder.MatchLevel.CLOSE_MATCH, ""));
        }

        String text = PriceNotes.lowestCloseMatch(selected, matches);
        assertTrue(text.contains("WARNING"), text);
        assertTrue(text.contains("data error"), text);

        // a normal spread produces no warning
        matches.remove(0);
        assertTrue(!PriceNotes.lowestCloseMatch(selected, matches)
                .contains("WARNING"));
    }
}
