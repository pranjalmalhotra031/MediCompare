package com.medicompare;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Case-insensitive search over name, composition and manufacturer.
 * Every word typed must appear somewhere in those fields.
 */
public final class MedicineSearch {
    private record Entry(Medicine medicine, String text, String name) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public MedicineSearch(List<Medicine> medicines) {
        for (Medicine medicine : medicines) {
            String name = TextUtil.key(medicine.name());
            String text = (name + " " + medicine.composition() + " "
                    + medicine.manufacturer()).toLowerCase(Locale.ROOT);
            entries.add(new Entry(medicine, text, name));
        }
    }

    public List<Medicine> search(String query, int limit) {
        String cleaned = TextUtil.key(query);
        if (cleaned.isEmpty()) {
            return List.of();
        }
        String[] words = cleaned.split(" ");

        record Hit(Medicine medicine, int rank) {
        }

        List<Hit> hits = new ArrayList<>();
        for (Entry entry : entries) {
            boolean all = true;
            for (String word : words) {
                if (!entry.text().contains(word)) {
                    all = false;
                    break;
                }
            }
            if (!all) {
                continue;
            }
            int rank = entry.name().equals(cleaned) ? 0
                    : entry.name().startsWith(cleaned) ? 1
                    : entry.name().contains(cleaned) ? 2 : 3;
            hits.add(new Hit(entry.medicine(), rank));
        }

        return hits.stream()
                .sorted(Comparator.comparingInt(Hit::rank)
                        .thenComparing(hit -> hit.medicine().name(),
                                String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(Hit::medicine)
                .toList();
    }
}
