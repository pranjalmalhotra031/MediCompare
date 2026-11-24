package com.medicompare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every CSV here is SYNTHETIC (invented brands/prices) but follows the real
 * A-Z file's header layout and value formats.
 */
class CatalogLoaderTest {
    @TempDir
    Path dir;

    private static final String HEADER =
            "id,name,price(₹),Is_discontinued,manufacturer_name,type,"
                    + "pack_size_label,short_composition1,short_composition2\n";

    private Path write(String content, Charset charset) throws Exception {
        Path file = dir.resolve("a.csv");
        Files.write(file, content.getBytes(charset));
        return file;
    }

    @Test
    void messyRowsAreCleanedAndCounted() throws Exception {
        String csv = "\uFEFF" + HEADER
                + "1,  TESTAMOX   CV 625 tablet ,100.50,FALSE,"
                + "  EXAMPLE PHARMA LTD ,allopathy,strip of 10 tablets,"
                + "Amoxycillin  (500mg) ,  Clavulanic Acid (125mg)\r\n"
                // exact duplicate (case/space differ), same price
                + "2,testamox cv 625 TABLET,100.50,FALSE,Example Pharma Ltd,"
                + "allopathy,strip of 10 tablets,Amoxycillin (500mg),"
                + "Clavulanic Acid (125mg)\n"
                // duplicate with a different price
                + "3,Testamox CV 625 Tablet,120,FALSE,Example Pharma Ltd,"
                + "allopathy,strip of 10 tablets,Amoxycillin (500mg),"
                + "Clavulanic Acid (125mg)\n"
                + "\n"
                // no name
                + "4,,10,FALSE,X,allopathy,strip of 10 tablets,Foo (1mg),\n"
                // too many fields (unescaped comma)
                + "5,Bad,1,FALSE,A,B,C,D,E,F,G\n"
                // short row: bad price, no composition
                + "6,Somesyrup 100ml Syrup,abc\n"
                // zero price; NA strength
                + "7,Zeroprice Tablet,0,TRUE,M,allopathy,strip of 10 tablets,"
                + "Paracetamol (NA),\n";

        var result = CatalogLoader.load(write(csv, StandardCharsets.UTF_8));
        var report = result.report();

        assertEquals(7, report.rowsRead);
        assertEquals(3, result.medicines().size());
        assertEquals(2, report.duplicatesDropped);
        assertEquals(1, report.conflictingDuplicates);
        assertEquals(1, report.skippedNoName);
        assertEquals(1, report.skippedExtraFields);
        assertEquals(1, report.shortRows);

        Medicine first = result.medicines().get(0);
        assertEquals("TESTAMOX CV 625 tablet", first.name()); // mixed case kept
        assertEquals("Example Pharma Ltd", first.manufacturer());
        assertEquals("Amoxycillin (500mg) + Clavulanic Acid (125mg)",
                first.composition());
        assertEquals("500mg + 125mg", first.strengthText());
        assertEquals(2, first.ingredients().size());
        assertEquals("tablet", first.dosageForm());
        assertEquals(Boolean.FALSE, first.discontinued());
        assertEquals("100.50", first.price().toPlainString());
        assertEquals("1", first.id());

        Medicine syrup = result.medicines().get(1);
        assertNull(syrup.price(), "unparseable price must be null, not 0");
        assertTrue(syrup.ingredients().isEmpty());
        assertEquals("syrup", syrup.dosageForm());
        assertEquals(1, report.missingComposition);

        Medicine zero = result.medicines().get(2);
        assertNull(zero.price(), "zero price is treated as missing");
        assertTrue(zero.isDiscontinued());
        assertEquals("", zero.strengthText(), "NA strength is unknown");
        assertEquals(1, report.missingStrength);
        assertEquals(2, report.missingPrice);
    }

    @Test
    void packLabelReleaseMarkerAndConflictsAreHandled() throws Exception {
        String csv = HEADER
                + "1,Metx 500 Tablet,10,FALSE,M,allopathy,strip of 10 tablet sr,"
                + "Metformin (500mg),\n"
                + "2,Foo Gel,10,FALSE,M,allopathy,bottle of 10 ml Eye Drop,"
                + "Foo (1mg),\n"
                + "3,Dryx Syrup,10,FALSE,M,allopathy,bottle of 30 ml Dry Syrup,"
                + "Dryx (5mg),\n";

        var result = CatalogLoader.load(write(csv, StandardCharsets.UTF_8));

        assertEquals("sustained release tablet",
                result.medicines().get(0).dosageForm());
        assertEquals("", result.medicines().get(1).dosageForm());
        assertEquals(1, result.report().conflictingDosageForm);
        assertEquals("dry syrup", result.medicines().get(2).dosageForm());
    }

    @Test
    void nonUtf8FileFallsBackToWindows1252() throws Exception {
        String csv = HEADER
                + "1,Caf\u00E9 Tablet,5,FALSE,Maker,allopathy,"
                + "strip of 10 tablets,Foo (1mg),\n";

        var result = CatalogLoader.load(
                write(csv, Charset.forName("windows-1252")));

        assertEquals("windows-1252", result.report().charset);
        assertTrue(result.medicines().get(0).name().contains("é"));
    }

    @Test
    void wrongFileGivesAClearErrorListingItsColumns() throws Exception {
        Path file = write("foo,bar\n1,2\n", StandardCharsets.UTF_8);

        var error = assertThrows(IllegalArgumentException.class,
                () -> CatalogLoader.load(file));
        assertTrue(error.getMessage().contains("[foo, bar]"));
    }

    @Test
    void brokenQuoteStopsGracefullyKeepingEarlierRows() throws Exception {
        String csv = HEADER
                + "1,Good Tablet,5,FALSE,M,allopathy,strip of 10 tablets,"
                + "Foo (1mg),\n"
                + "2,\"Bad \"x\" Tablet,6,FALSE,M,allopathy,"
                + "strip of 10 tablets,Bar (2mg),\n";

        var result = CatalogLoader.load(write(csv, StandardCharsets.UTF_8));

        assertEquals("Good Tablet", result.medicines().get(0).name());
        assertFalse(result.report().stoppedEarly.isEmpty());
    }
}
