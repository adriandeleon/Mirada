package com.mirada;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppInfoTest {

    /** A release opens its own GitHub release, whose notes are its CHANGELOG section (DOCS-05). */
    @Test
    void aReleasesNotesAreItsGitHubRelease() {
        assertEquals("https://github.com/adriandeleon/Mirada/releases/tag/v0.4.0", AppInfo.releaseNotesUrl("0.4.0"));
    }

    /** A snapshot has no release yet, so it opens the changelog, Unreleased first. */
    @Test
    void aSnapshotsNotesAreTheChangelog() {
        assertEquals(
                "https://github.com/adriandeleon/Mirada/blob/main/CHANGELOG.md",
                AppInfo.releaseNotesUrl("0.4.0-SNAPSHOT"));
        assertEquals("https://github.com/adriandeleon/Mirada/blob/main/CHANGELOG.md", AppInfo.releaseNotesUrl("0.0.0"));
    }

    @Test
    void theDocumentationIsTheWebsitesGuide() {
        assertEquals("https://mirada-project.dev/docs/", AppInfo.DOCS_URL);
    }

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
