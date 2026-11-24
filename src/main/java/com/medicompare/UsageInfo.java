package com.medicompare;

import java.util.List;

/**
 * Information from the usage/substitutes file for one product name.
 * Substitute entries are product NAMES only; they are not assumed to match
 * until checked against the catalogue.
 */
public record UsageInfo(
        String name,
        List<String> uses,
        List<String> substituteNames,
        String therapeuticClass
) {
}
