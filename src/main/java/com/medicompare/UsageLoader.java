package com.medicompare;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads the 250K Medicines Usage, Side Effects and Substitutes dataset
 * (medicine_dataset.csv).
 *
 * Only these columns are used: name, use0..use4, substitute0..substitute4,
 * Therapeutic Class. Side effects, chemical class, habit-forming and action
 * class are deliberately not read. The file has no price, composition or
 * dosage form.
 *
 * When a name appears more than once, the first row is used.
 */
public final class UsageLoader {
    private static final Set<String> NAME = Set.of("name");
    private static final Set<String> CLASS = Set.of("therapeuticclass");
    private static final Pattern USES = Pattern.compile("^use\\d+$");
    private static final Pattern SUBSTITUTES =
            Pattern.compile("^substitute\\d+$");

    public record Result(Map<String, UsageInfo> byName, LoadReport report) {
    }

    private UsageLoader() {
    }

    public static Result load(Path path) throws IOException {
        return CsvTable.read(path, table -> {
            int name = table.column(NAME);
            int therapeuticClass = table.column(CLASS);
            List<Integer> uses = table.columns(USES);
            List<Integer> substitutes = table.columns(SUBSTITUTES);

            if (name < 0 || (uses.isEmpty() && substitutes.isEmpty())) {
                throw new IllegalArgumentException(
                        path.getFileName() + " does not look like the "
                                + "usage/substitutes file: it needs a "
                                + "\"name\" column and \"use\" and/or "
                                + "\"substitute\" columns. Columns found: "
                                + table.headers());
            }

            LoadReport report = new LoadReport(
                    path.getFileName().toString());
            report.charset = table.charset;

            Map<String, UsageInfo> byName = new HashMap<>();
            int columnCount = table.headers().size();

            table.forEachRow(row -> {
                if (row.size() > columnCount) {
                    report.skippedExtraFields++;
                    return;
                }

                String productName = TextUtil.displayCase(row.get(name));
                if (productName.isBlank()) {
                    report.skippedNoName++;
                    return;
                }

                String key = TextUtil.key(productName);
                if (byName.containsKey(key)) {
                    report.duplicatesDropped++;
                    return;
                }

                byName.put(key, new UsageInfo(
                        productName,
                        values(row, uses),
                        values(row, substitutes),
                        TextUtil.displayCase(row.get(therapeuticClass))));
                report.loaded++;
            });

            report.rowsRead = table.rowsRead();
            report.stoppedEarly = table.stoppedEarly();
            return new Result(byName, report);
        });
    }

    private static List<String> values(CsvTable.Row row, List<Integer> cols) {
        List<String> values = new ArrayList<>();
        for (int column : cols) {
            String value = row.get(column);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }
}
