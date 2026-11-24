package com.medicompare;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small text-cleaning helpers shared by the loaders and matchers. */
final class TextUtil {
    private static final Pattern INVISIBLE =
            Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern FIRST_NUMBER =
            Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private TextUtil() {
    }

    /** Removes BOM/zero-width/control characters and collapses whitespace. */
    static String tidy(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String text = raw.replace('\u00A0', ' ');
        text = INVISIBLE.matcher(text).replaceAll("");
        text = CONTROL.matcher(text).replaceAll(" ");
        text = SPACES.matcher(text).replaceAll(" ");
        return text.trim();
    }

    /** Lower-case tidy text, used only for comparing and look-ups. */
    static String key(String raw) {
        return tidy(raw).toLowerCase(Locale.ROOT);
    }

    /** Lower-case letters and digits only - used to match column headers. */
    static String headerKey(String raw) {
        return NOT_ALNUM.matcher(key(raw)).replaceAll("");
    }

    /**
     * For display only: a value that is entirely UPPER or lower case is
     * Title-Cased; mixed-case values are left exactly as supplied.
     */
    static String displayCase(String raw) {
        String text = tidy(raw);

        boolean hasLetter = text.chars().anyMatch(Character::isLetter);
        boolean shouted = text.equals(text.toUpperCase(Locale.ROOT));
        boolean flat = text.equals(text.toLowerCase(Locale.ROOT));

        if (!hasLetter || (!shouted && !flat)) {
            return text;
        }

        StringBuilder out = new StringBuilder();
        boolean startOfWord = true;

        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ' || c == '-' || c == '/' || c == '(';
        }

        return out.toString();
    }

    /** Tri-state parse of flags such as TRUE/FALSE/1/0/yes/no. */
    static Boolean parseFlag(String raw) {
        return switch (key(raw)) {
            case "true", "1", "yes", "y", "t" -> Boolean.TRUE;
            case "false", "0", "no", "n", "f" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** First number in the text (commas and the rupee sign ignored), or null. */
    static BigDecimal parsePrice(String raw) {
        if (raw == null) {
            return null;
        }

        Matcher matcher = FIRST_NUMBER.matcher(raw.replace("₹", ""));
        if (!matcher.find()) {
            return null;
        }

        try {
            return new BigDecimal(matcher.group().replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
