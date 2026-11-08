package com.mirada;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchTest {

    private static Launch parse(String... args) {
        return Launch.parse(List.of(args), "/home/u");
    }

    @Test
    void filesOpenWithTheUsualFolder() {
        Launch launch = parse("a.jpg");
        assertEquals(Launch.Action.START, launch.action());
        assertNull(launch.configDir());
        assertEquals(List.of("a.jpg"), launch.files());
    }

    @Test
    void helpAndVersionWinWhereverTheyStand() {
        assertEquals(Launch.Action.HELP, parse("a.jpg", "--help").action());
        assertEquals(Launch.Action.VERSION, parse("--dev", "--version").action());
    }

    @Test
    void theConfigFolderComesFromEitherSpelling() {
        assertEquals(Path.of("/tmp/c"), parse("--config-dir", "/tmp/c", "a.jpg").configDir());
        assertEquals(Path.of("/tmp/c"), parse("--config-dir=/tmp/c").configDir());
        assertEquals(List.of("a.jpg"), parse("--config-dir", "/tmp/c", "a.jpg").files());
    }

    @Test
    void devKeepsItsOwnFolderInHome() {
        assertEquals(Path.of("/home/u", ".mirada-dev"), parse("--dev").configDir());
    }

    @Test
    void mistakesAreNamedNotOpenedAsFiles() {
        Launch unknown = parse("--frobnicate");
        assertEquals(Launch.Action.MISTAKE, unknown.action());
        assertTrue(unknown.error().contains("--frobnicate"));
        assertEquals(Launch.Action.MISTAKE, parse("--config-dir").action());
    }

    @Test
    void reportKeepsTheConfigFolderOnEitherSide() {
        assertEquals(Launch.Action.REPORT, parse("--report").action());
        Launch before = parse("--report", "--config-dir", "/tmp/c");
        assertEquals(Launch.Action.REPORT, before.action());
        assertEquals(Path.of("/tmp/c"), before.configDir());
        assertEquals(
                Path.of("/home/u", ".mirada-dev"), parse("--dev", "--report").configDir());
        assertEquals(Launch.Action.HELP, parse("--report", "--help").action());
    }

    @Test
    void afterTheDoubleDashEverythingIsAFile() {
        assertEquals(List.of("--help"), parse("--", "--help").files());
    }

    @Test
    void theUsageNamesEveryOption() {
        for (String option : List.of("--config-dir", "--dev", "--report", "--version", "--help"))
            assertTrue(Launch.USAGE.contains(option), option);
    }
}
