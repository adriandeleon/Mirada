package com.mirada;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppInfoTest {

    @Test
    void theRuntimeNamesJavaJavaFxAndTheCodecs() {
        assertEquals("Java 25 · JavaFX 27\nCodecs: libheif v1.23.7", AppInfo.runtime("25", "27", "libheif v1.23.7"));
        assertEquals("Codecs: the system's, or none", AppInfo.runtime(null, null, null));
        // --report never starts JavaFX, so its version is left out rather than guessed.
        assertEquals("Java 25\nCodecs: the system's, or none", AppInfo.runtime("25", null, null));
    }

    @Test
    void theLinksAreTheRepositorysOwn() {
        assertTrue(AppInfo.NEW_ISSUE_URL.startsWith(AppInfo.HOMEPAGE));
        assertTrue(AppInfo.LATEST_RELEASE_API.endsWith("/repos/adriandeleon/Mirada/releases/latest"));
    }
}
