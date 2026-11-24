package com.medicompare;

import java.util.List;

/** Default provider: no pharmacy feed is configured. */
public final class NoPharmacyPriceProvider implements PharmacyPriceProvider {
    @Override
    public List<PharmacyQuote> quotesFor(Medicine medicine) {
        return List.of();
    }

    @Override
    public String description() {
        return "No pharmacy price feed is configured.";
    }
}
