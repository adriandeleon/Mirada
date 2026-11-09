package com.mirada.report;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.mirada.AppInfo;
import com.mirada.settings.Settings;

/**
 * Builds a problem report and the prefilled GitHub issue link (DIAG-06, Pauta 4.0.1). Nothing is
 * uploaded: the report is shown, copied to the clipboard and opened as a new-issue form the user
 * submits themselves. The form is {@code .github/ISSUE_TEMPLATE/bug_report.yml}, whose {@code
 * environment} and {@code report} fields the link fills.
 */
public final class ProblemReport {

    /** Past this length a link is unreliable; the report then goes by the clipboard instead. */
    static final int MAX_URL = 7500;

    /** How much of the Debug Log to include, from its end. */
    static final int LOG_TAIL = 4000;

    static final String PASTE_HINT = "(The report is on your clipboard: paste it here.)";

    private ProblemReport() {}

    /** The report before redaction: a summary of the settings, then the end of the Debug Log. */
    public static String build(Settings settings, String debugLog) {
        String log = debugLog.length() > LOG_TAIL ? "…" + debugLog.substring(debugLog.length() - LOG_TAIL) : debugLog;
        return "Settings: " + summary(settings) + "\n\nDebug Log:\n" + (log.isBlank() ? "(empty)" : log.strip());
    }

    /** The settings that change what Mirada does, on one line; no paths (the recent folders stay out). */
    static String summary(Settings settings) {
        return "theme=" + settings.theme().name().toLowerCase(java.util.Locale.ROOT)
                + ", sort=" + settings.sorting().by().name().toLowerCase(java.util.Locale.ROOT)
                + (settings.sorting().reversed() ? " reversed" : "")
                + ", keepZoom=" + settings.keepZoom()
                + ", startThumbnails=" + settings.startWithThumbnails()
                + ", startInfo=" + settings.startWithInfo()
                + ", thumbnails=" + (settings.thumbnailsAtSide() ? "side" : "bottom")
                + (settings.thumbnailNames() ? " names" : "")
                + (settings.thumbnailCaptions() ? " captions" : "")
                + ", statusBar=" + settings.statusBar()
                + ", updateCheck=" + settings.checkForUpdates()
                + ", paletteKeys=" + settings.paletteKeys();
    }

    /**
     * The new-issue link with the form's {@code environment} and {@code report} fields filled. When
     * that is too long, the report field asks for a paste from the clipboard instead.
     */
    public static String issueUrl(String environment, String report) {
        String full = url(environment, report);
        if (full.length() <= MAX_URL) return full;
        return url(environment, PASTE_HINT);
    }

    /** Whether the whole report fits in the link. */
    public static boolean fitsInUrl(String environment, String report) {
        return url(environment, report).length() <= MAX_URL;
    }

    private static String url(String environment, String report) {
        return AppInfo.NEW_ISSUE_URL + "?template=bug_report.yml&environment=" + encode(environment) + "&report="
                + encode(report);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
