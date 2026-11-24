package com.medicompare;

/** What happened while loading one CSV file. */
public final class LoadReport {
    public final String file;
    public String charset = "";
    public int rowsRead;
    public int loaded;
    public int skippedNoName;
    public int skippedExtraFields;
    public int shortRows;
    public int duplicatesDropped;
    public int conflictingDuplicates;
    public int missingPrice;
    public int missingComposition;
    public int missingStrength;
    public int missingDosageForm;
    public int conflictingDosageForm;
    public String stoppedEarly = "";

    LoadReport(String file) {
        this.file = file;
    }

    public void print(String kind) {
        System.out.printf("  %s (%s, %s): %,d rows read, %,d loaded%n",
                file, kind, charset, rowsRead, loaded);
        line("skipped - no name", skippedNoName);
        line("skipped - more fields than headers (misaligned)",
                skippedExtraFields);
        line("rows shorter than the header (missing values left blank)",
                shortRows);
        line("duplicate rows dropped", duplicatesDropped);
        line("  of which had a different price", conflictingDuplicates);
        line("loaded without a usable price", missingPrice);
        line("loaded without composition", missingComposition);
        line("loaded with an unknown strength for some ingredient",
                missingStrength);
        line("loaded without a recognisable dosage form",
                missingDosageForm);
        line("  of which name and pack label disagreed (left blank)",
                conflictingDosageForm);
        if (!stoppedEarly.isEmpty()) {
            System.out.println("  WARNING: " + stoppedEarly);
        }
    }

    private static void line(String label, int count) {
        if (count > 0) {
            System.out.printf("    - %s: %,d%n", label, count);
        }
    }
}
