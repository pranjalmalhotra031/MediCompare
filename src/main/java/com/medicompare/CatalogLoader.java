package com.medicompare;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads the A-Z Medicine Dataset of India
 * (A_Z_medicines_dataset_of_India.csv).
 *
 * Columns in that file: id, name, price(₹), Is_discontinued,
 * manufacturer_name, type, pack_size_label, short_composition1,
 * short_composition2. Headers are matched ignoring case, spaces and
 * punctuation. The "type" column is not used (it holds "allopathy", not a
 * dosage form).
 */
public final class CatalogLoader {
    private static final Set<String> ID = Set.of("id");
    private static final Set<String> NAME = Set.of("name");
    private static final Set<String> PRICE = Set.of("price", "pricerupees");
    private static final Set<String> DISCONTINUED =
            Set.of("isdiscontinued", "discontinued");
    private static final Set<String> MANUFACTURER =
            Set.of("manufacturername", "manufacturer");
    private static final Set<String> PACK =
            Set.of("packsizelabel", "packsize");
    private static final Pattern COMPOSITION =
            Pattern.compile("^shortcomposition\\d*$");

    public record Result(List<Medicine> medicines, LoadReport report) {
    }

    private CatalogLoader() {
    }

    public static Result load(Path path) throws IOException {
        return CsvTable.read(path, table -> {
            int id = table.column(ID);
            int name = table.column(NAME);
            int price = table.column(PRICE);
            int discontinued = table.column(DISCONTINUED);
            int manufacturer = table.column(MANUFACTURER);
            int pack = table.column(PACK);
            List<Integer> composition = table.columns(COMPOSITION);

            if (name < 0 || composition.isEmpty()) {
                throw new IllegalArgumentException(
                        path.getFileName() + " does not look like the A-Z "
                                + "medicine catalogue: it needs a \"name\" "
                                + "column and \"short_composition\" "
                                + "column(s). Columns found: "
                                + table.headers());
            }

            LoadReport report = new LoadReport(
                    path.getFileName().toString());
            report.charset = table.charset;

            List<Medicine> medicines = new ArrayList<>();
            Map<String, BigDecimal> seen = new HashMap<>();
            int columnCount = table.headers().size();

            table.forEachRow(row -> {
                if (row.size() > columnCount) {
                    report.skippedExtraFields++;
                    return;
                }
                if (row.size() < columnCount) {
                    report.shortRows++;
                }

                String productName = TextUtil.displayCase(row.get(name));
                if (productName.isBlank()) {
                    report.skippedNoName++;
                    return;
                }

                BigDecimal listedPrice = TextUtil.parsePrice(row.get(price));
                if (listedPrice != null && listedPrice.signum() <= 0) {
                    listedPrice = null;
                }
                if (listedPrice == null) {
                    report.missingPrice++;
                }

                String packLabel = row.get(pack);

                // composition: one entry per column, in column order
                List<String> entries = new ArrayList<>();
                List<Ingredient> ingredients = new ArrayList<>();
                boolean readable = true;
                for (int column : composition) {
                    String entry = row.get(column);
                    if (entry.isEmpty()) {
                        continue;
                    }
                    entries.add(entry);
                    Optional<Ingredient> parsed =
                            CompositionParser.parse(entry);
                    if (parsed.isPresent()) {
                        ingredients.add(parsed.get());
                    } else {
                        readable = false;
                    }
                }
                if (!readable) {
                    ingredients.clear(); // never half-trust a composition
                }

                String compositionText = String.join(" + ", entries);
                if (compositionText.isEmpty()) {
                    report.missingComposition++;
                } else if (ingredients.stream()
                        .anyMatch(item -> !item.hasStrength())) {
                    report.missingStrength++;
                }

                DosageForm.Resolved form =
                        DosageForm.resolve(productName, packLabel);
                if (form.form().isEmpty()) {
                    report.missingDosageForm++;
                }
                if (form.conflict()) {
                    report.conflictingDosageForm++;
                }

                String code = row.get(id);
                if (code.isEmpty()) {
                    code = "row " + row.number();
                }

                Medicine medicine = new Medicine(
                        code,
                        productName,
                        TextUtil.displayCase(row.get(manufacturer)),
                        packLabel,
                        listedPrice,
                        TextUtil.parseFlag(row.get(discontinued)),
                        compositionText,
                        ingredients,
                        form.form(),
                        form.basis(),
                        List.of(), List.of(), "");

                String duplicateKey = String.join("|",
                        TextUtil.key(medicine.name()),
                        TextUtil.key(medicine.manufacturer()),
                        TextUtil.key(medicine.packLabel()),
                        TextUtil.key(medicine.composition()));

                if (seen.containsKey(duplicateKey)) {
                    report.duplicatesDropped++;
                    BigDecimal first = seen.get(duplicateKey);
                    if (first != null && medicine.price() != null
                            && first.compareTo(medicine.price()) != 0) {
                        report.conflictingDuplicates++;
                    }
                    return;
                }

                seen.put(duplicateKey, medicine.price());
                medicines.add(medicine);
                report.loaded++;
            });

            report.rowsRead = table.rowsRead();
            report.stoppedEarly = table.stoppedEarly();
            return new Result(medicines, report);
        });
    }
}
