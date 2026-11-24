package com.medicompare;

import java.util.List;

/**
 * Extension point for multi-pharmacy price comparison.
 *
 * The bundled datasets hold one price per product and nothing about
 * individual pharmacies, so the default implementation returns nothing.
 * To add real comparison, implement this interface against a source you are
 * authorised to use (a pharmacy partner API or licensed feed) and pass it to
 * Main. Implementations must return only genuine, timestamped quotes.
 */
public interface PharmacyPriceProvider {
    List<PharmacyQuote> quotesFor(Medicine medicine);

    String description();
}
