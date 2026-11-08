package com.mirada.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mirada.diagnostics.DebugLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code mirada --report}: the redacted report written and the issue opened, with no window (CLI-05). */
class ReportCommandTest {

    @TempDir
    Path folder;

    @Test
    void writesTheRedactedReportAndOpensTheIssue() throws IOException {
        Path home = folder.resolve("home");
        Path config = home.resolve(".mirada");
        Files.createDirectories(config);
        Files.writeString(
                config.resolve(DebugLog.PREVIOUS_FILE), "12:00:00.000  UNCAUGHT: crashed for ada@example.com\n");
        Files.writeString(
                config.resolve(DebugLog.FILE), "12:01:00.000  WARNING could not open " + home + "/Pictures/a.heic\n");
        Redactor redactor = new Redactor(home.toString(), "ada", "");
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        List<String> opened = new ArrayList<>();

        int code = ReportCommand.run(
                config.resolve("settings.properties"),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                url -> opened.add(url),
                redactor);

        assertEquals(0, code);
        String report = Files.readString(config.resolve(ReportCommand.FILE));
        assertTrue(report.contains("Mirada "), report);
        assertTrue(report.contains("crashed for <email>"), report);
        assertTrue(report.contains("could not open ~/Pictures/a.heic"), report);
        assertFalse(report.contains(home.toString()), report);
        assertEquals(1, opened.size());
        assertTrue(URLDecoder.decode(opened.getFirst(), StandardCharsets.UTF_8).contains("crashed for <email>"));
        assertTrue(printed.toString(StandardCharsets.UTF_8).contains("Wrote the redacted report to"));
        assertFalse(Files.exists(config.resolve("settings.properties")), "the report never writes the settings");
    }

    @Test
    void withNoBrowserTheLinkIsPrinted() {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        int code = ReportCommand.run(
                folder.resolve("settings.properties"),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                url -> false,
                new Redactor("/nowhere", "", ""));
        assertEquals(0, code);
        assertTrue(printed.toString(StandardCharsets.UTF_8).contains("Open this link to file the issue:\nhttps://"));
    }

    @Test
    void theBrowserCommandIsThePlatformsOwn() {
        assertEquals(List.of("open", "u"), Browser.command("Mac OS X", "u"));
        assertEquals(List.of("rundll32", "url.dll,FileProtocolHandler", "u"), Browser.command("Windows 11", "u"));
        assertEquals(List.of("xdg-open", "u"), Browser.command("Linux", "u"));
    }
}
