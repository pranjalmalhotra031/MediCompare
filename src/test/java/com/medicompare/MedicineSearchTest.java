package com.medicompare;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicineSearchTest {
    private static Medicine med(String name, String composition, String maker) {
        return new Medicine(name, name, maker, "", null, null, composition,
                List.of(), "", "", List.of(), List.of(), "");
    }

    @Test
    void everyWordMustMatchAcrossNameCompositionAndManufacturer() {
        var search = new MedicineSearch(List.of(
                med("Crocin 500 Tablet", "Paracetamol (500mg)", "Maker One"),
                med("Dolo 650 Tablet", "Paracetamol (650mg)", "Maker Two"),
                med("Brufen 400 Tablet", "Ibuprofen (400mg)", "Maker One")));

        assertEquals(2, search.search("paracetamol", 10).size());
        assertEquals(1, search.search("PARACETAMOL  maker two", 10).size());
        assertTrue(search.search("aspirin", 10).isEmpty());
        assertTrue(search.search("   ", 10).isEmpty());
    }

    @Test
    void nameMatchesRankBeforeCompositionMatches() {
        var search = new MedicineSearch(List.of(
                med("Zed Tablet", "Paracetamol (500mg)", "M"),
                med("Paracetamol Plus Tablet", "Foo (1mg)", "M"),
                med("Paracetamol", "Foo (1mg)", "M")));

        List<Medicine> found = search.search("paracetamol", 10);
        assertEquals("Paracetamol", found.get(0).name());       // exact
        assertEquals("Paracetamol Plus Tablet", found.get(1).name()); // prefix
        assertEquals("Zed Tablet", found.get(2).name());        // composition
    }

    @Test
    void limitIsApplied() {
        var search = new MedicineSearch(List.of(
                med("A Tablet", "X (1mg)", "M"),
                med("B Tablet", "X (1mg)", "M"),
                med("C Tablet", "X (1mg)", "M")));
        assertEquals(2, search.search("x", 2).size());
    }
}
