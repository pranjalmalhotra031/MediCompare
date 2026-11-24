package com.medicompare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Price wording helpers. Everything here comes from dataset prices only -
 * none of it is a live or current price.
 */
public final class PriceNotes {
    public static final String BASIS =
            "Listed price in the dataset; the file does not document its "
                    + "basis (e.g. MRP). Per-tablet figures assume it "
                    + "covers the whole listed pack.";

    private static final Pattern PACK_COUNT = Pattern.compile(
            "\\bof\\s+(\\d+)\\s+(?:soft gelatin\\s+)?(?:tablet|capsule)s?\\b",
            Pattern.CASE_INSENSITIVE);

    private PriceNotes() {
    }

    public static String shortPrice(Medicine medicine) {
        if (!medicine.hasPrice()) {
            return "price not listed";
        }
        String text = "₹" + medicine.price().toPlainString();
        String unit = perUnitText(medicine);
        return unit.isEmpty() ? text : text + " (" + unit + ")";
    }

    /**
     * Price per tablet/capsule, only for tablets and capsules whose pack
     * label states a count ("strip of 10 tablets"). Null otherwise.
     */
    public static BigDecimal perUnit(Medicine medicine) {
        if (!medicine.hasPrice()) {
            return null;
        }
        String form = medicine.dosageForm();
        if (!form.endsWith("tablet") && !form.endsWith("capsule")) {
            return null;
        }

        Matcher matcher = PACK_COUNT.matcher(medicine.packLabel());
        if (!matcher.find()) {
            return null;
        }
        int count = Integer.parseInt(matcher.group(1));
        if (count <= 0) {
            return null;
        }
        return medicine.price().divide(
                BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    public static String perUnitText(Medicine medicine) {
        BigDecimal unit = perUnit(medicine);
        return unit == null ? ""
                : "≈ ₹" + unit.setScale(2, RoundingMode.HALF_UP)
                + " per tablet/capsule";
    }

    /**
     * Lowest per-unit price among available (not discontinued) close
     * matches, compared with the selection. Empty if it cannot be worked out.
     */
    public static String lowestCloseMatch(
            Medicine selected, List<SubstituteFinder.MatchResult> matches) {

        BigDecimal base = perUnit(selected);
        if (base == null || base.signum() <= 0) {
            return "";
        }

        Medicine best = null;
        BigDecimal bestPrice = null;
        List<BigDecimal> prices = new ArrayList<>();

        for (SubstituteFinder.MatchResult match : matches) {
            if (match.level() != SubstituteFinder.MatchLevel.CLOSE_MATCH
                    || match.medicine().isDiscontinued()) {
                continue;
            }
            BigDecimal price = perUnit(match.medicine());
            if (price == null) {
                continue;
            }
            prices.add(price);
            if (bestPrice == null || price.compareTo(bestPrice) < 0) {
                best = match.medicine();
                bestPrice = price;
            }
        }

        if (best == null) {
            return "";
        }
        if (bestPrice.compareTo(base) >= 0) {
            return "No available close match has a lower listed per-unit "
                    + "price than the selection.";
        }

        BigDecimal percent = base.subtract(bestPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 0, RoundingMode.HALF_UP);

        Collections.sort(prices);
        BigDecimal median = prices.get(prices.size() / 2);

        String text = "The lowest listed per-unit price among available "
                + "close matches is ₹"
                + bestPrice.setScale(2, RoundingMode.HALF_UP)
                + " (" + best.name() + ") vs ₹"
                + base.setScale(2, RoundingMode.HALF_UP)
                + " for the selection, about " + percent + "% lower in the "
                + "dataset. These are dataset prices, not current prices.";

        // a value far below everything else is more likely a data error
        // than a saving, so say so instead of presenting it as a bargain
        if (prices.size() >= 5 && bestPrice.multiply(BigDecimal.valueOf(4))
                .compareTo(median) < 0) {
            text += " WARNING: this is far below the median of ₹"
                    + median.setScale(2, RoundingMode.HALF_UP)
                    + " for the " + prices.size() + " available close "
                    + "matches - it may be a data error; verify it before "
                    + "relying on it.";
        }
        return text;
    }
}
