package com.medicompare;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one composition entry of the A-Z catalogue, which looks like
 * "Amoxycillin (500mg)", "Ambroxol (30mg/5ml)", "Povidone Iodine (5% w/v)"
 * or "Paracetamol (NA)".
 *
 * Only what the text says is used. A strength of "NA" (or anything that does
 * not start with a number and a unit) is treated as unknown, never guessed.
 */
final class CompositionParser {
    /** Name, then a final "(...)" group that holds the strength. */
    private static final Pattern NAME_AND_STRENGTH =
            Pattern.compile("^(.*)\\(([^()]*)\\)\\s*$");
    private static final Pattern NUMBER_AND_UNIT =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)(.*)$");
    private static final Pattern NOT_LETTER_OR_DIGIT =
            Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Spelling/naming variants of the SAME substance. Deliberately short.
     * Salts and esters are NOT merged ("metoprolol succinate" and
     * "metoprolol tartrate" stay different).
     */
    private static final Map<String, String> SYNONYMS = Map.of(
            "amoxycillin", "amoxicillin",
            "acetaminophen", "paracetamol",
            "albuterol", "salbutamol",
            "cephalexin", "cefalexin",
            "frusemide", "furosemide",
            "lignocaine", "lidocaine",
            "rifampin", "rifampicin"
    );

    private CompositionParser() {
    }

    /** Empty when the entry has no usable ingredient name. */
    static Optional<Ingredient> parse(String entry) {
        String text = TextUtil.tidy(entry);
        if (text.isEmpty()) {
            return Optional.empty();
        }

        String namePart = text;
        String strength = "";

        Matcher matcher = NAME_AND_STRENGTH.matcher(text);
        if (matcher.matches()) {
            String inner = matcher.group(2);
            strength = canonicalStrength(inner);

            boolean innerIsStrength = !strength.isEmpty()
                    || inner.isBlank() || isNotAvailable(inner);

            // "Progesterone (Natural Micronized)" - the bracket is part of
            // the name, not a strength
            if (innerIsStrength) {
                namePart = matcher.group(1);
            }
        }

        String name = normalizeName(namePart);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Ingredient(name, strength));
    }

    static String normalizeName(String raw) {
        String text = NOT_LETTER_OR_DIGIT
                .matcher(raw.toLowerCase(Locale.ROOT)).replaceAll(" ")
                .trim();
        if (text.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(SYNONYMS.getOrDefault(word, word));
        }
        return out.toString();
    }

    /**
     * "500 mg" -> "500mg", "1 gm" -> "1000mg", "250 mcg" -> "0.25mg",
     * "125 mg / 5 ml" -> "125mg/5ml", "5% w/v" -> "5%w/v".
     * Only the amount before any "/" is unit-converted. Returns "" when
     * there is no number followed by a unit.
     */
    static String canonicalStrength(String raw) {
        String text = raw.toLowerCase(Locale.ROOT)
                .replace('µ', 'u').replace('μ', 'u')
                .replace("i.u", "iu")
                .replaceAll("\\s+", "");

        Matcher matcher = NUMBER_AND_UNIT.matcher(text);
        if (!matcher.matches() || matcher.group(2).isEmpty()) {
            return "";
        }

        BigDecimal number = new BigDecimal(matcher.group(1));
        String rest = matcher.group(2);

        int slash = rest.indexOf('/');
        String unit = slash < 0 ? rest : rest.substring(0, slash);
        String per = slash < 0 ? "" : rest.substring(slash);

        switch (unit) {
            case "g", "gm" -> {
                number = number.movePointRight(3);
                unit = "mg";
            }
            case "mcg", "ug" -> {
                number = number.movePointLeft(3);
                unit = "mg";
            }
            default -> {
            }
        }

        return number.stripTrailingZeros().toPlainString() + unit + per;
    }

    private static boolean isNotAvailable(String inner) {
        String key = TextUtil.headerKey(inner);
        return key.equals("na") || key.equals("nil") || key.equals("none");
    }
}
