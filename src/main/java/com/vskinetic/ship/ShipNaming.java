package com.vskinetic.ship;

import java.util.Locale;

public final class ShipNaming {
    public static final String CRASH_SUFFIX = "-crashed";

    private static final String[] ADJECTIVES = {
            "iron", "swift", "amber", "onyx", "lunar", "storm", "frost", "hollow"
    };

    private static final String[] NOUNS = {
            "falcon", "comet", "anchor", "spire", "wisp", "raven", "harbor", "atlas"
    };

    private ShipNaming() {
    }

    public static String codename(long shipId) {
        int a = Math.floorMod(Long.hashCode(shipId), ADJECTIVES.length);
        int n = Math.floorMod((int) (shipId * 31L + 17L), NOUNS.length);
        return ADJECTIVES[a] + " " + NOUNS[n];
    }

    public static String defaultDisplayName(long shipId) {
        return "Mayday " + codename(shipId);
    }

    public static String slugify(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");

        if (cleaned.isBlank()) {
            return "ship";
        }

        return cleaned;
    }

    public static String withCrashSuffix(String slug) {
        return slug.endsWith(CRASH_SUFFIX) ? slug : slug + CRASH_SUFFIX;
    }

    public static String withoutCrashSuffix(String slug) {
        return slug.endsWith(CRASH_SUFFIX)
                ? slug.substring(0, slug.length() - CRASH_SUFFIX.length())
                : slug;
    }
}
