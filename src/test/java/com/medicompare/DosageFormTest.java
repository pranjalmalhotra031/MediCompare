package com.medicompare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DosageFormTest {
    @Test
    void detectsCommonFormsFromNamesAndPackLabels() {
        assertEquals("tablet", DosageForm.detect("Augmentin 625 Duo Tablet"));
        assertEquals("tablet", DosageForm.detect("strip of 10 tablets"));
        assertEquals("syrup", DosageForm.detect("bottle of 100 ml Syrup"));
        assertEquals("injection", DosageForm.detect("vial of 1 Injection"));
        assertEquals("eye drops", DosageForm.detect("bottle of 5 ml Eye Drop"));
        assertEquals("eye solution", DosageForm.detect("Ophthalmic Solution"));
        assertEquals("dry syrup", DosageForm.detect("bottle of 30 ml Dry Syrup"));
        assertEquals("powder for injection",
                DosageForm.detect("vial of Powder for Injection"));
        assertEquals("", DosageForm.detect("strip of 10"));
    }

    @Test
    void releaseMarkersBecomePartOfTheFormForTabletsAndCapsules() {
        assertEquals("sustained release tablet",
                DosageForm.detect("strip of 10 tablet sr"));
        assertEquals("dispersible tablet", DosageForm.detect("Foo DT Tablet"));
        assertEquals("extended release capsule",
                DosageForm.detect("Foo XL Capsule"));
        assertEquals("injection", DosageForm.detect("Foo MD Injection"));
    }

    @Test
    void packLabelMakesTheFormMoreSpecific() {
        var r = DosageForm.resolve("Metx 500 Tablet", "strip of 10 tablet sr");
        assertEquals("sustained release tablet", r.form());
        assertFalse(r.conflict());

        var dry = DosageForm.resolve("Azee Syrup", "bottle of 15 ml Dry Syrup");
        assertEquals("dry syrup", dry.form());
    }

    @Test
    void agreeingSourcesAndSingleSources() {
        assertEquals("tablet", DosageForm.resolve(
                "Foo Tablet", "strip of 10 tablets").form());
        assertEquals("tablet", DosageForm.resolve(
                "Foo 500", "strip of 10 tablets").form());
        assertEquals("syrup", DosageForm.resolve(
                "Foo Syrup", "bottle of 100 ml").form());
    }

    @Test
    void disagreementIsNeverGuessed() {
        var r = DosageForm.resolve("Foo Gel", "bottle of 10 ml Eye Drop");
        assertEquals("", r.form());
        assertTrue(r.conflict());

        assertTrue(DosageForm.resolve("Alporate Syrup",
                "bottle of 200 ml Oral Solution").conflict());
        assertTrue(DosageForm.resolve("Aim7 60 XR Tablet",
                "strip of 15 tablet sr").conflict());
    }
}
