package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The interface's language: blank follows the system, a code is kept as written (INTL-02). */
class LanguageSettingTest {

    @TempDir
    Path dir;

    @Test
    void aChosenLanguageSurvivesARestart() throws IOException {
        Path file = dir.resolve("settings.properties");
        Settings settings = new Settings(file);
        settings.load();
        assertEquals("", settings.language(), "the system's, by default");
        assertFalse(Files.exists(file) && Files.readString(file).contains("language="), "not written while blank");
        settings.setLanguage(" ES ");
        Settings again = new Settings(file);
        again.load();
        assertEquals("es", again.language());
        assertTrue(Files.readString(file).contains("language=es"));
    }

    @Test
    void aCodeThisBuildDoesNotBundleIsKept() {
        assertEquals("ja", Settings.languageOf("ja"));
        assertEquals("", Settings.languageOf(null));
    }
}
