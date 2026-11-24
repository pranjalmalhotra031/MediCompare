package com.medicompare;

import java.math.BigDecimal;
import java.time.Instant;

/** One price/availability reading from a real, authorised pharmacy feed. */
public record PharmacyQuote(
        String pharmacy,
        BigDecimal priceInr,
        String availability,
        Instant retrievedAt,
        String sourceUrl
) {
}
