package com.medicompare;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects a dosage form from product-name / pack-label text.
 *
 * The A-Z catalogue has no dosage-form column, so the form is parsed from
 * text and always labelled as parsed. Release markers (SR, ER, XR, ...) are
 * part of the form: a sustained-release tablet is never the same form as a
 * plain tablet.
 */
final class DosageForm {
    private record Rule(Pattern pattern, String canonical) {
        static Rule of(String regex, String canonical) {
            return new Rule(Pattern.compile(
                    "\\b(?:" + regex + ")\\b"), canonical);
        }
    }

    /** Order matters: more specific forms come before general ones. */
    private static final Rule[] RULES = {
            Rule.of("oral liquid", "oral liquid"),
            Rule.of("eye\\s*/\\s*ear drops?", "eye/ear drops"),
            Rule.of("(?:eye|ophthalmic|opthalmic) drops?", "eye drops"),
            Rule.of("ear drops?", "ear drops"),
            Rule.of("nasal drops?", "nasal drops"),
            Rule.of("oral drops?", "oral drops"),
            Rule.of("dry syrup", "dry syrup"),
            Rule.of("powder for injection", "powder for injection"),
            Rule.of("injections?", "injection"),
            Rule.of("infusions?", "infusion"),
            Rule.of("inhalers?", "inhaler"),
            Rule.of("respules", "respules"),
            Rule.of("nasal spray", "nasal spray"),
            Rule.of("(?:ophthalmic|opthalmic) suspensions?",
                    "eye suspension"),
            Rule.of("(?:ophthalmic|opthalmic) solutions?",
                    "eye solution"),
            Rule.of("(?:oral )?suspensions?", "suspension"),
            Rule.of("(?:oral )?solutions?", "solution"),
            Rule.of("eye ointments?", "eye ointment"),
            Rule.of("ointments?", "ointment"),
            Rule.of("creams?", "cream"),
            Rule.of("gels?", "gel"),
            Rule.of("syrups?", "syrup"),
            Rule.of("lotions?", "lotion"),
            Rule.of("sprays?", "spray"),
            Rule.of("sachets?", "sachet"),
            Rule.of("lozenges?", "lozenge"),
            Rule.of("pastes?", "paste"),
            Rule.of("suppositor(?:y|ies)", "suppository"),
            Rule.of("patch(?:es)?", "patch"),
            Rule.of("mouth ?wash(?:es)?", "mouthwash"),
            Rule.of("shampoos?", "shampoo"),
            Rule.of("soaps?", "soap"),
            Rule.of("soft gelatin capsules?", "soft gelatin capsule"),
            Rule.of("capsules?", "capsule"),
            Rule.of("tablets?", "tablet"),
            Rule.of("dusting powders?", "dusting powder"),
            Rule.of("powders?", "powder"),
            Rule.of("drops?", "drops")
    };

    private static final String[] LONG_MODIFIERS = {
            "gastro resistant", "prolonged release", "extended release",
            "sustained release", "modified release", "controlled release",
            "delayed release", "enteric coated", "dispersible", "chewable",
            "effervescent", "film coated"
    };

    private static final Pattern RELEASE_ABBREVIATION =
            Pattern.compile("\\b(sr|xr|er|xl|cr|mr|pr|dt|md)\\b");

    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "sr", "sustained release",
            "xr", "extended release",
            "er", "extended release",
            "xl", "extended release",
            "cr", "controlled release",
            "mr", "modified release",
            "pr", "prolonged release",
            "dt", "dispersible",
            "md", "mouth dissolving"
    );

    private DosageForm() {
    }

    /** Result of combining the product name and the pack label. */
    record Resolved(String form, String basis, boolean conflict) {
    }

    /** Normalised form found in the text (with release marker), or "". */
    static String detect(String raw) {
        String text = raw.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');

        String form = "";
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                form = rule.canonical();
                break;
            }
        }
        if (form.isEmpty()) {
            return "";
        }

        String modifier = "";
        for (String candidate : LONG_MODIFIERS) {
            if (text.contains(candidate)) {
                modifier = candidate;
                break;
            }
        }

        if (modifier.isEmpty()
                && (form.equals("tablet") || form.equals("capsule"))) {
            Matcher matcher = RELEASE_ABBREVIATION.matcher(text);
            if (matcher.find()) {
                modifier = ABBREVIATIONS.get(matcher.group(1));
            }
        }

        return modifier.isEmpty() ? form : modifier + " " + form;
    }

    /**
     * Combines what the product name and the pack label say. The pack label
     * often carries release markers ("tablet sr") that the name omits. If
     * the two name different forms, nothing is guessed: conflict = true.
     */
    static Resolved resolve(String name, String packLabel) {
        String fromName = detect(name);
        String fromPack = detect(packLabel);

        if (fromName.isEmpty() && fromPack.isEmpty()) {
            return new Resolved("", "", false);
        }
        if (fromName.isEmpty()) {
            return new Resolved(fromPack, "parsed from pack label", false);
        }
        if (fromPack.isEmpty()) {
            return new Resolved(fromName, "parsed from product name", false);
        }
        if (fromName.equals(fromPack)) {
            return new Resolved(fromName,
                    "parsed from product name and pack label", false);
        }
        if (fromPack.endsWith(fromName)) {
            return new Resolved(fromPack,
                    "parsed from pack label (more specific than the name)",
                    false);
        }
        if (fromName.endsWith(fromPack)) {
            return new Resolved(fromName,
                    "parsed from product name (more specific than the "
                            + "pack label)", false);
        }
        return new Resolved("", "", true);
    }
}
