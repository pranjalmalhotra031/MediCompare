package com.medicompare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthetic data in the real file's layout (extra columns are ignored). */
class UsageLoaderTest {
    @TempDir
    Path dir;

    private Path write(String content) throws Exception {
        Path file = dir.resolve("u.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void readsUsesSubstitutesAndClassAndKeepsTheFirstDuplicate()
            throws Exception {
        String csv = "id,name,substitute0,substitute1,sideEffect0,use0,use1,"
                + "Chemical Class,Habit Forming,Therapeutic Class,Action Class\n"
                + "1,TESTBRAND 10 TABLET,Altbrand 10 Tablet,,Nausea,Allergy,"
                + "Itching,x,No,ANTI ALLERGIC,y\n"
                + "2,Testbrand 10 Tablet,Dup,,,,,,,,\n"
                + "3,,Nameless,,,,,,,,\n";

        var result = UsageLoader.load(write(csv));
        UsageInfo info = result.byName().get("testbrand 10 tablet");

        assertEquals(1, result.byName().size());
        assertEquals(1, result.report().duplicatesDropped);
        assertEquals(1, result.report().skippedNoName);
        assertEquals(List.of("Altbrand 10 Tablet"), info.substituteNames());
        assertEquals(List.of("Allergy", "Itching"), info.uses());
        assertEquals("Anti Allergic", info.therapeuticClass());
    }

    @Test
    void wrongFileGivesAClearError() throws Exception {
        Path file = write("foo,bar\n1,2\n");
        var error = assertThrows(IllegalArgumentException.class,
                () -> UsageLoader.load(file));
        assertTrue(error.getMessage().contains("[foo, bar]"));
    }

    @Test
    void usageIsAttachedWithoutChangingTheRest() {
        Medicine base = new Medicine("1", "Foo", "M", "pack", null, null,
                "Foo (1mg)", List.of(new Ingredient("foo", "1mg")),
                "tablet", "parsed", List.of(), List.of(), "");
        Medicine joined = base.withUsage(new UsageInfo("Foo",
                List.of("Pain"), List.of("Bar"), "Analgesic"));

        assertEquals(List.of("Pain"), joined.uses());
        assertEquals(List.of("Bar"), joined.listedSubstitutes());
        assertEquals("Analgesic", joined.therapeuticClass());
        assertEquals(base.ingredients(), joined.ingredients());
    }
}
