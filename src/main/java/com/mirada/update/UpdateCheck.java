package com.mirada.update;

import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/** The decisions behind the update check, apart from the network so they are tested on their own. */
public final class UpdateCheck {

    /** At most once a day: something the user did not ask for should not ask more often (DIAG-05). */
    public static final long INTERVAL_MS = 24L * 60 * 60 * 1000;

    private UpdateCheck() {}

    /**
     * Reads a GitHub {@code releases/latest} answer; null for a draft, a pre-release or anything
     * that is not one.
     */
    public static @Nullable ReleaseInfo parseLatest(String json) {
        Object tree;
        try {
            tree = Json.parse(json);
        } catch (Json.Malformed e) {
            return null;
        }
        if (!(tree instanceof Map<?, ?> release)) return null;
        if (Boolean.TRUE.equals(release.get("draft")) || Boolean.TRUE.equals(release.get("prerelease"))) return null;
        if (!(release.get("tag_name") instanceof String tag) || tag.isBlank()) return null;
        String url = release.get("html_url") instanceof String page ? page : "";
        return new ReleaseInfo(normalize(tag), url);
    }

    /** Strips a leading {@code v}. */
    public static String normalize(String tag) {
        String t = tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    /**
     * Whether {@code latest} is newer than {@code current}. A {@code -SNAPSHOT} counts as the release
     * it leads to, so a development build is not told to "update" to the release it is ahead of.
     */
    public static boolean isNewer(String current, String latest) {
        String c = normalize(current);
        int snapshot = c.toUpperCase(Locale.ROOT).indexOf("-SNAPSHOT");
        if (snapshot >= 0) c = c.substring(0, snapshot);
        return compare(normalize(latest), c) > 0;
    }

    /** Compares dotted versions by number (0.10.0 is newer than 0.9.0); a suffix sorts below the release. */
    public static int compare(String a, String b) {
        String[] left = core(a).split("\\.");
        String[] right = core(b).split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int cmp =
                    Integer.compare(number(i < left.length ? left[i] : "0"), number(i < right.length ? right[i] : "0"));
            if (cmp != 0) return cmp;
        }
        return Boolean.compare(a.indexOf('-') < 0, b.indexOf('-') < 0);
    }

    private static String core(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    /** A segment that is not a number sorts lowest, so a malformed tag is never offered as an update. */
    private static int number(String segment) {
        try {
            return Integer.parseInt(segment.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Whether a day has passed since the last check. A time in the future (the clock moved back) is due. */
    public static boolean isDue(long lastCheckMs, long nowMs) {
        return lastCheckMs <= 0 || lastCheckMs > nowMs || nowMs - lastCheckMs >= INTERVAL_MS;
    }
}
