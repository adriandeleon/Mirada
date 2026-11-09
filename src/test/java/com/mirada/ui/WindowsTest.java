package com.mirada.ui;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import javafx.application.ColorScheme;

import com.mirada.settings.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The decisions in the theme, the About window and the properties sidebar. */
class WindowsTest {

    @Test
    void theThemeFollowsTheSystemUnlessTold() {
        assertTrue(Theme.dark(Settings.ThemeChoice.SYSTEM, ColorScheme.DARK));
        assertFalse(Theme.dark(Settings.ThemeChoice.SYSTEM, ColorScheme.LIGHT));
        assertTrue(Theme.dark(Settings.ThemeChoice.DARK, ColorScheme.LIGHT));
        assertFalse(Theme.dark(Settings.ThemeChoice.LIGHT, ColorScheme.DARK));
    }

    @Test
    void fileSizesAreDecimalAsGnomeShowsThem() {
        assertEquals("989 bytes", ImageDetails.fileSize(989));
        assertEquals("80.7 kB", ImageDetails.fileSize(80_700));
        assertEquals("4.2 MB", ImageDetails.fileSize(4_200_000));
        assertEquals("Unknown", ImageDetails.fileSize(-1));
    }

    @Test
    void formatsByTheirUsualNames() {
        assertEquals("JPEG", ImageDetails.formatName(Path.of("a.JPG")));
        assertEquals("JPEG XL", ImageDetails.formatName(Path.of("a.jxl")));
        assertEquals("HEIC", ImageDetails.formatName(Path.of("a.heic")));
        assertEquals("Photoshop", ImageDetails.formatName(Path.of("a.psd")));
    }

    @Test
    void datesInTheUsersLocale() {
        FileTime time = FileTime.from(Instant.parse("2020-08-27T16:32:13Z"));
        assertTrue(ImageDetails.date(time, Locale.US, ZoneId.of("UTC")).contains("2020"));
        assertEquals("Unknown", ImageDetails.date(null, Locale.US, ZoneId.of("UTC")));
    }
}
