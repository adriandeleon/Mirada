package com.mirada.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading GitHub's answer, comparing versions, and once a day (DIAG-05). */
class UpdateCheckTest {

    @Test
    void readsTheLatestRelease() {
        ReleaseInfo release = UpdateCheck.parseLatest("""
                {"tag_name": "v0.5.0", "html_url": "https://github.com/adriandeleon/Mirada/releases/tag/v0.5.0",
                 "draft": false, "prerelease": false, "assets": [{"name": "x", "size": 12.5e3}], "body": "a \\"quote\\" \\u00e9"}
                """);
        assertEquals(new ReleaseInfo("0.5.0", "https://github.com/adriandeleon/Mirada/releases/tag/v0.5.0"), release);
    }

    @Test
    void draftsPreReleasesAndNonsenseAreNotReleases() {
        assertNull(UpdateCheck.parseLatest("{\"tag_name\": \"v1.0.0\", \"draft\": true}"));
        assertNull(UpdateCheck.parseLatest("{\"tag_name\": \"v1.0.0\", \"prerelease\": true}"));
        assertNull(UpdateCheck.parseLatest("{\"tag_name\": \"\"}"));
        assertNull(UpdateCheck.parseLatest("[]"));
        assertNull(UpdateCheck.parseLatest("<html>rate limited</html>"));
        assertNull(UpdateCheck.parseLatest("{\"tag_name\": \"v1\""));
        assertNull(UpdateCheck.parseLatest("{\"tag_name\": \"v1\"} trailing"));
    }

    @Test
    void comparesVersionsByNumber() {
        assertTrue(UpdateCheck.isNewer("0.9.0", "0.10.0"));
        assertTrue(UpdateCheck.isNewer("0.4.1", "v0.5.0"));
        assertFalse(UpdateCheck.isNewer("0.5.0", "0.5.0"));
        assertFalse(UpdateCheck.isNewer("0.5.1", "0.5.0"));
        assertTrue(UpdateCheck.isNewer("0.5.0-rc1", "0.5.0"));
        assertFalse(UpdateCheck.isNewer("0.5.0", "garbage"));
    }

    @Test
    void aSnapshotIsTheReleaseItLeadsTo() {
        assertFalse(UpdateCheck.isNewer("0.5.0-SNAPSHOT", "0.5.0"));
        assertTrue(UpdateCheck.isNewer("0.5.0-SNAPSHOT", "0.5.1"));
    }

    @Test
    void atMostOnceADay() {
        long now = 10 * UpdateCheck.INTERVAL_MS;
        assertTrue(UpdateCheck.isDue(0, now));
        assertFalse(UpdateCheck.isDue(now - 1000, now));
        assertTrue(UpdateCheck.isDue(now - UpdateCheck.INTERVAL_MS, now));
        assertTrue(UpdateCheck.isDue(now + 1000, now), "a clock moved back");
    }

    @Test
    void jsonReadsWhatGitHubSends() throws Json.Malformed {
        assertEquals(java.util.List.of(1.0, true, "x"), Json.parse(" [1, true, \"x\"] "));
        assertNull(Json.parse("null"));
        assertEquals("tab\tline\n/", Json.parse("\"tab\\tline\\n\\/\""));
        assertThrows(Json.Malformed.class, () -> Json.parse("{\"a\" 1}"));
        assertThrows(Json.Malformed.class, () -> Json.parse("[".repeat(Json.MAX_DEPTH + 2)));
    }
}
