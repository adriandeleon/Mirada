package com.mirada;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
