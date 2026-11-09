package com.mirada;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        for (String option :
                List.of("--config-dir", "--dev", "--new-instance", "--no-session", "--report", "--version", "--help"))
            assertTrue(Launch.USAGE.contains(option), option);
    }

    @Test
    void aLaunchHandsOffToTheRunningMiradaUnlessToldToStartItsOwn() {
        assertTrue(parse("a.jpg").handsOff());
        assertTrue(parse().handsOff());
        Launch own = parse("--new-instance", "a.jpg");
        assertTrue(own.newInstance());
        assertFalse(own.handsOff());
        assertEquals(List.of("a.jpg"), own.files());
        assertFalse(parse("--report").handsOff());
        assertFalse(parse("--help").handsOff());
        assertFalse(parse("--frobnicate").handsOff());
    }

    @Test
    void handedOffFilesAreResolvedWhereThisLaunchStands() {
        Path cwd = Path.of("/home/u/Pictures").toAbsolutePath();
        Path elsewhere = Path.of("/tmp/x.jpg").toAbsolutePath();
        assertEquals(
                List.of(cwd.resolve("a.jpg").toString(), cwd.resolve("b c.jpg").toString(), elsewhere.toString()),
                Launch.absolute(List.of("a.jpg", "./sub/../b c.jpg", elsewhere.toString()), cwd));
        assertEquals(List.of(), Launch.absolute(List.of(), cwd));
        // No path at all: passed on, for the running Mirada to refuse as this one would have.
        assertEquals(List.of("a\0b"), Launch.absolute(List.of("a\0b"), cwd));
    }

    @Test
    void noSessionIsTakenAndOpensOnlyWhatIsNamed() {
        Launch launch = parse("--no-session", "a.jpg");
        assertEquals(Launch.Action.START, launch.action());
        assertEquals(List.of("a.jpg"), launch.files());
        assertTrue(launch.handsOff());
    }
}
