package com.medicompare;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class Main {
    static final String CATALOG_FILE =
            "A_Z_medicines_dataset_of_India.csv";
    static final String USAGE_FILE = "medicine_dataset.csv";

    private static final int SEARCH_LIMIT = 40;
    private static final int CLOSE_LIMIT = 15;
    private static final int OTHER_LIMIT = 5;
    private static final int LISTED_SUBSTITUTE_VERSIONS = 3;

    /** Usage: Main [data-folder]   (default: ./data) */
    public static void main(String[] args) {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
        Path catalogPath = dataDir.resolve(CATALOG_FILE);
        Path usagePath = dataDir.resolve(USAGE_FILE);

        if (!Files.exists(catalogPath)) {
            System.out.println("Medicine catalogue not found: "
                    + catalogPath.toAbsolutePath());
            System.out.println("Download \"" + CATALOG_FILE + "\" from Kaggle "
                    + "(see README.md) and put it in the data folder.");
            System.out.println("If the file is there, the program was "
                    + "started from the wrong folder: set the working "
                    + "directory to the project root.");
            return;
        }

        PharmacyPriceProvider pharmacyPrices =
                new NoPharmacyPriceProvider();

        try {
            DatasetInfo info = DatasetInfo.load(dataDir);

            System.out.println("Loading datasets from "
                    + dataDir.toAbsolutePath() + " ...");

            // the usage file is read in the background while the
            // catalogue is read, which saves roughly half the wait
            CompletableFuture<UsageLoader.Result> usageTask =
                    Files.exists(usagePath)
                            ? CompletableFuture.supplyAsync(() -> {
                        try {
                            return UsageLoader.load(usagePath);
                        } catch (Exception exception) {
                            throw new CompletionException(exception);
                        }
                    })
                            : null;

            CatalogLoader.Result catalog = CatalogLoader.load(catalogPath);
            catalog.report().print("A-Z medicine catalogue");

            Map<String, UsageInfo> usage = Map.of();

            if (usageTask == null) {
                System.out.println("  " + USAGE_FILE + " not found - "
                        + "continuing without use/substitute text "
                        + "(optional file, see README.md).");
            } else {
                try {
                    UsageLoader.Result result = usageTask.join();
                    result.report().print("usage & substitutes");
                    usage = result.byName();
                } catch (CompletionException exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    System.out.println("  " + USAGE_FILE
                            + " could not be loaded - " + cause.getMessage()
                            + " (continuing without it)");
                }
            }

            List<Medicine> medicines =
                    addUsage(catalog.medicines(), usage);

            System.out.println("Building the match index "
                    + "(a few seconds)...");
            SubstituteFinder finder = new SubstituteFinder(medicines);
            MedicineSearch search = new MedicineSearch(medicines);
            String catalogVersion = info.version("catalog");

            System.out.printf("Ready: %,d medicines. Catalogue: %s%n",
                    medicines.size(), catalogVersion);

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\nSearch by medicine or ingredient "
                            + "(example: paracetamol), or press Enter "
                            + "to quit: ");

                    if (!scanner.hasNextLine()) {
                        return;
                    }
                    String query = scanner.nextLine().trim();
                    if (query.isBlank()) {
                        return;
                    }

                    searchOnce(scanner, query, search, finder, usage,
                            pharmacyPrices, catalogVersion);
                }
            }
        } catch (Exception exception) {
            System.out.println("Could not read the datasets: "
                    + exception.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private static List<Medicine> addUsage(
            List<Medicine> medicines, Map<String, UsageInfo> usage) {

        if (usage.isEmpty()) {
            return medicines;
        }

        List<Medicine> enriched = new ArrayList<>(medicines.size());
        int attached = 0;

        for (Medicine medicine : medicines) {
            UsageInfo info = usage.get(TextUtil.key(medicine.name()));
            if (info != null) {
                enriched.add(medicine.withUsage(info));
                attached++;
            } else {
                enriched.add(medicine);
            }
        }

        System.out.printf("Attached use/substitute text to %,d medicines "
                + "by exact name match.%n", attached);
        return enriched;
    }

    private static void searchOnce(
            Scanner scanner,
            String query,
            MedicineSearch search,
            SubstituteFinder finder,
            Map<String, UsageInfo> usage,
            PharmacyPriceProvider pharmacyPrices,
            String catalogVersion) {

        List<Medicine> results = search.search(query, SEARCH_LIMIT);

        if (results.isEmpty()) {
            System.out.println("No records matched. Try a shorter "
                    + "ingredient or medicine name.");
            return;
        }

        System.out.println("\nSearch results (up to " + SEARCH_LIMIT + "):");
        for (int i = 0; i < results.size(); i++) {
            printListing(i + 1, results.get(i));
        }

        System.out.print("\nEnter a result number to see details and "
                + "possible matches, or 0 to search again: ");

        if (!scanner.hasNextLine()) {
            return;
        }

        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException exception) {
            System.out.println("Please enter one of the displayed numbers.");
            return;
        }
        if (choice == 0) {
            return;
        }
        if (choice < 1 || choice > results.size()) {
            System.out.println("That number is outside the displayed range.");
            return;
        }

        Medicine selected = results.get(choice - 1);
        printDetails(selected, catalogVersion);

        List<SubstituteFinder.MatchResult> matches = finder.find(selected);
        printMatches(selected, matches);
        printListedSubstitutes(selected, finder);
        printPricing(selected, matches, pharmacyPrices);

        System.out.println("\nThese datasets do not include live pharmacy "
                + "stock or location-based availability.");
        System.out.println("Possible matches are for review only, not "
                + "medical advice. Ask a doctor or pharmacist before "
                + "changing a medicine.");
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private static void printListing(int number, Medicine m) {
        String prefix = number > 0 ? number + ". " : "- ";

        System.out.printf("%s%s | %s | %s | %s | pack: %s | id: %s%s%n",
                prefix,
                m.name(),
                orNotListed(m.composition()),
                orNotListed(m.manufacturer()),
                PriceNotes.shortPrice(m),
                orNotListed(m.packLabel()),
                orNotListed(m.id()),
                m.isDiscontinued() ? " | DISCONTINUED" : "");
    }

    private static void printDetails(Medicine m, String catalogVersion) {
        System.out.println("\n=== " + m.name() + " ===");
        System.out.println("Source:        A-Z Medicine Dataset of India "
                + "(Kaggle), id " + orNotListed(m.id()));
        System.out.println("Dataset date:  " + catalogVersion);
        System.out.println("Composition:   " + orNotListed(m.composition()));

        String strength = m.strengthText();
        System.out.println("Strength:      " + (strength.isEmpty()
                ? "not fully listed in dataset" : strength
                + "  [parsed from composition text]"));

        System.out.println("Dosage form:   " + (m.dosageForm().isEmpty()
                ? "not determinable from dataset text"
                : m.dosageForm() + "  [" + m.dosageFormBasis() + "]"));
        System.out.println("Manufacturer:  " + orNotListed(m.manufacturer()));
        System.out.println("Use:           " + (m.uses().isEmpty()
                ? "not in the loaded datasets"
                : String.join("; ", m.uses())
                + "  [usage dataset, exact name match]"));
        if (!m.therapeuticClass().isEmpty()) {
            System.out.println("Therapeutic class: " + m.therapeuticClass()
                    + "  [usage dataset]");
        }
        System.out.println("Pack:          " + orNotListed(m.packLabel()));
        System.out.println("Status:        " + (m.discontinued() == null
                ? "not stated in dataset"
                : m.isDiscontinued() ? "marked DISCONTINUED in dataset"
                : "not marked discontinued in dataset"));

        if (m.hasPrice()) {
            System.out.println("Dataset price: ₹" + m.price().toPlainString());
            String perUnit = PriceNotes.perUnitText(m);
            if (!perUnit.isEmpty()) {
                System.out.println("               " + perUnit);
            }
            System.out.println("               " + PriceNotes.BASIS);
            System.out.println("               Not a current/live price.");
        } else {
            System.out.println("Dataset price: not listed in dataset");
        }
        System.out.println("Availability:  not provided by this dataset");
    }

    private static void printMatches(
            Medicine selected,
            List<SubstituteFinder.MatchResult> matches) {

        System.out.println("\nPossible matches from the dataset "
                + "(ingredients, strength and dosage form compared; "
                + "dosage forms are parsed from product-name/pack text "
                + "because the dataset has no such column). Ordered by "
                + "per-tablet/capsule price where it can be worked out, "
                + "discontinued products last:");

        if (matches.isEmpty()) {
            System.out.println(selected.ingredients().isEmpty()
                    ? "This record has no usable composition in the "
                    + "dataset, so it cannot be matched."
                    : "No candidates found. This does not mean no "
                    + "alternative exists.");
            return;
        }

        for (SubstituteFinder.MatchLevel level
                : SubstituteFinder.MatchLevel.values()) {

            List<SubstituteFinder.MatchResult> group = matches.stream()
                    .filter(match -> match.level() == level)
                    .toList();
            if (group.isEmpty()) {
                continue;
            }

            int limit = level == SubstituteFinder.MatchLevel.CLOSE_MATCH
                    ? CLOSE_LIMIT : OTHER_LIMIT;

            System.out.printf("%n[%s] - %,d found%n", level.label,
                    group.size());

            for (SubstituteFinder.MatchResult match
                    : group.stream().limit(limit).toList()) {
                printListing(0, match.medicine());
                System.out.println("      " + match.note());
            }
            if (group.size() > limit) {
                System.out.printf("  ... %,d more not shown%n",
                        group.size() - limit);
            }
        }
    }

    private static void printListedSubstitutes(
            Medicine selected, SubstituteFinder finder) {

        if (selected.listedSubstitutes().isEmpty()) {
            return;
        }

        System.out.println("\nSubstitute names listed by the usage dataset "
                + "(names only - checked against the catalogue below):");

        for (String name : selected.listedSubstitutes()) {
            List<Medicine> found = finder.findByName(name);

            if (found.isEmpty()) {
                System.out.println("- " + name + " | not found in the "
                        + "catalogue, so composition/strength/form "
                        + "cannot be verified");
                continue;
            }

            for (Medicine candidate : found.stream()
                    .limit(LISTED_SUBSTITUTE_VERSIONS).toList()) {

                SubstituteFinder.MatchResult result =
                        finder.compare(selected, candidate);

                System.out.println("- " + name + " | "
                        + (result == null
                        ? "NOT confirmed: composition differs from the "
                        + "selected medicine in the catalogue"
                        : result.level().label + " - " + result.note())
                        + " | " + PriceNotes.shortPrice(candidate)
                        + " | pack: " + orNotListed(candidate.packLabel())
                        + (candidate.isDiscontinued()
                        ? " | DISCONTINUED" : ""));
            }
        }
    }

    private static void printPricing(
            Medicine selected,
            List<SubstituteFinder.MatchResult> matches,
            PharmacyPriceProvider pharmacyPrices) {

        System.out.println("\nPrice notes:");

        String comparison = PriceNotes.lowestCloseMatch(selected, matches);
        if (!comparison.isEmpty()) {
            System.out.println("- " + comparison);
        }

        System.out.println("- " + PriceNotes.BASIS);

        List<PharmacyQuote> quotes = pharmacyPrices.quotesFor(selected);

        if (quotes.isEmpty()) {
            System.out.println("- Multi-pharmacy comparison: not available. "
                    + pharmacyPrices.description() + " The dataset holds "
                    + "one price per product, not prices from several "
                    + "pharmacies. Comparing pharmacies needs a separate, "
                    + "authorised data source.");
            return;
        }

        System.out.println("- Pharmacy quotes ("
                + pharmacyPrices.description() + "):");
        for (PharmacyQuote quote : quotes) {
            System.out.printf("    %s | ₹%s | %s | retrieved %s | %s%n",
                    quote.pharmacy(), quote.priceInr().toPlainString(),
                    quote.availability(), quote.retrievedAt(),
                    quote.sourceUrl());
        }
    }

    private static String orNotListed(String value) {
        return value == null || value.isBlank() ? "not listed" : value;
    }
}
