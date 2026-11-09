package com.mirada.report;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.mirada.AppInfo;
import com.mirada.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The report and its link: short in the link, long by the clipboard (DIAG-06). */
class ProblemReportTest {

    @TempDir
    Path folder;

    @Test
    void theReportHasTheSettingsAndTheEndOfTheLog() {
        Settings settings = new Settings(folder.resolve("settings.properties"));
        String log = "x".repeat(ProblemReport.LOG_TAIL) + "the last line";
        String report = ProblemReport.build(settings, log);
        assertTrue(report.startsWith("Settings: theme=system, sort=name"), report);
        assertTrue(report.contains("statusBar=true, updateCheck=true"), report);
        assertTrue(report.contains("Debug Log:\n…"), report);
        assertTrue(report.endsWith("the last line"), report);
        assertTrue(ProblemReport.build(settings, "").endsWith("Debug Log:\n(empty)"));
    }

    @Test
    void aShortReportGoesInTheLink() {
        String url = ProblemReport.issueUrl("Mirada 0.4.0", "it broke");
        assertTrue(url.startsWith(AppInfo.NEW_ISSUE_URL + "?template=bug_report.yml&environment="), url);
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertTrue(decoded.contains("environment=Mirada 0.4.0"), decoded);
        assertTrue(decoded.contains("report=it broke"), decoded);
        assertTrue(ProblemReport.fitsInUrl("Mirada 0.4.0", "it broke"));
    }

    @Test
    void aLongReportAsksForAPaste() {
        String report = "line of the log\n".repeat(1000);
        assertFalse(ProblemReport.fitsInUrl("Mirada 0.4.0", report));
        String url = ProblemReport.issueUrl("Mirada 0.4.0", report);
        assertTrue(url.length() <= ProblemReport.MAX_URL, String.valueOf(url.length()));
        assertTrue(URLDecoder.decode(url, StandardCharsets.UTF_8).contains(ProblemReport.PASTE_HINT));
    }

    @Test
    void theFormHasTheFieldsTheLinkFills() throws Exception {
        String form = java.nio.file.Files.readString(Path.of(".github/ISSUE_TEMPLATE/bug_report.yml"));
        assertTrue(form.contains("id: environment"), "bug_report.yml lost its environment field");
        assertTrue(form.contains("id: report"), "bug_report.yml lost its report field");
    }
}
