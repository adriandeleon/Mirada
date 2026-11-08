package com.mirada.update;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.mirada.AppInfo;
import com.mirada.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the update check runs (DIAG-05): the window schedules it after the first paint, and it waits
 * the start-up delay, then asks once a run, at most once a day, never when switched off; and what About
 * says before and after (ABOUT-04). No toolkit: the timer and the threads are handed in.
 */
class UpdateCoordinatorTest {

    private static final long NOW = 100 * UpdateCheck.INTERVAL_MS;

    @TempDir
    Path folder;

    private Settings settings;
    private final List<Duration> delays = new ArrayList<>();
    private final List<Runnable> waiting = new ArrayList<>();
    private final List<String> said = new ArrayList<>();
    private final AtomicLong asked = new AtomicLong();
    private UpdateService.Outcome answer = new UpdateService.Outcome(null, null);
    private String property;

    @BeforeEach
    void setUp() {
        settings = new Settings(folder.resolve("settings.properties"));
        // The suite switches the automatic check off; these tests are the one place it is on.
        property = System.getProperty(UpdateCoordinator.PROPERTY);
        System.clearProperty(UpdateCoordinator.PROPERTY);
    }

    @AfterEach
    void restore() {
        if (property != null) System.setProperty(UpdateCoordinator.PROPERTY, property);
    }

    private UpdateCoordinator coordinator() {
        return new UpdateCoordinator(
                settings,
                version -> {
                    asked.incrementAndGet();
                    return answer;
                },
                (delay, task) -> {
                    delays.add(delay);
                    waiting.add(task);
                },
                Runnable::run,
                Runnable::run,
                (text, severity) -> said.add(severity + " " + text),
                () -> NOW);
    }

    /** The first paint has happened and the delay has passed. */
    private void timePasses() {
        List<Runnable> due = List.copyOf(waiting);
        waiting.clear();
        due.forEach(Runnable::run);
    }

    @Test
    void checksOnceARunAfterTheDelay() {
        UpdateCoordinator updates = coordinator();
        assertTrue(updates.scheduleStartupCheck());
        assertEquals(0, asked.get(), "nothing is asked before the delay");
        assertEquals(List.of(UpdateCoordinator.STARTUP_DELAY), delays);
        timePasses();
        assertEquals(1, asked.get());
        assertFalse(updates.scheduleStartupCheck(), "a second window, or a second call, asks nothing");
        timePasses();
        assertEquals(1, asked.get());
        assertEquals(NOW, settings.lastUpdateCheck());
    }

    @Test
    void notWhenSwitchedOff() {
        settings.setCheckForUpdates(false);
        assertFalse(coordinator().scheduleStartupCheck());
        timePasses();
        assertEquals(0, asked.get());
    }

    @Test
    void notWhenSwitchedOffDuringTheDelay() {
        UpdateCoordinator updates = coordinator();
        assertTrue(updates.scheduleStartupCheck());
        settings.setCheckForUpdates(false);
        timePasses();
        assertEquals(0, asked.get());
    }

    @Test
    void notWhenTheTestRunSwitchesItOff() {
        System.setProperty(UpdateCoordinator.PROPERTY, "off");
        assertFalse(coordinator().scheduleStartupCheck());
    }

    @Test
    void notWhenADayHasNotPassed() {
        settings.setLastUpdateCheck(NOW - 60_000);
        assertFalse(coordinator().scheduleStartupCheck());
        settings.setLastUpdateCheck(NOW - UpdateCheck.INTERVAL_MS);
        assertTrue(coordinator().scheduleStartupCheck());
    }

    @Test
    void aNewerReleaseIsSaidAndShownInAbout() {
        answer = new UpdateService.Outcome(new ReleaseInfo("9.0.0", ""), null);
        UpdateCoordinator updates = coordinator();
        updates.scheduleStartupCheck();
        timePasses();
        assertEquals(UpdateCoordinator.State.AVAILABLE, updates.state());
        assertEquals(List.of("INFO Mirada 9.0.0 is available"), said);
        assertEquals("Update: Mirada 9.0.0 is available", updates.summary());
        assertEquals(AppInfo.RELEASES_URL, updates.releasePage());
    }

    @Test
    void theAutomaticCheckIsQuietUnlessThereIsNews() {
        UpdateCoordinator updates = coordinator();
        updates.scheduleStartupCheck();
        timePasses();
        assertEquals(UpdateCoordinator.State.UP_TO_DATE, updates.state());
        assertTrue(said.isEmpty(), said.toString());
        answer = new UpdateService.Outcome(null, "timed out");
        updates.checkNow(false);
        assertTrue(said.isEmpty(), said.toString());
    }

    @Test
    void aCheckByHandSaysWhatItFound() {
        UpdateCoordinator updates = coordinator();
        updates.checkNow(true);
        assertEquals("INFO Mirada " + AppInfo.VERSION + " is the latest", said.getLast());
        answer = new UpdateService.Outcome(null, "timed out");
        updates.checkNow(true);
        assertEquals("WARNING Couldn't check for updates: timed out", said.getLast());
        assertEquals("Updates: couldn't check (timed out)", updates.summary());
        assertNull(updates.releasePage());
    }

    @Test
    void aboutClaimsNothingBeforeAnAnswer() {
        UpdateCoordinator updates = coordinator();
        assertEquals("Updates: not checked yet", updates.summary());
        settings.setCheckForUpdates(false);
        assertEquals("Updates: not checked (switched off in Settings)", updates.summary());
    }

    @Test
    void listenersAreRemoved() {
        UpdateCoordinator updates = coordinator();
        Runnable listener = () -> {};
        updates.addListener(listener);
        updates.removeListener(listener);
        assertEquals(0, updates.listenerCount());
    }
}
