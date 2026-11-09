package com.mirada.update;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

import com.mirada.AppInfo;
import com.mirada.diagnostics.MessageLog;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * The update check, once per run of Mirada: a few seconds after the first image is up, at most once
 * a day, and only while Settings allows it. A newer release puts a notice in the status bar and a
 * line in About; Check for Updates runs it by hand. It never downloads or installs anything (DIAG-05,
 * Pauta 4.0.1). The template's, with the toolkit taken out: the window hands it a timer and the FX
 * thread, so its rules are tested without JavaFX.
 *
 * <p>FX thread only, but for the check itself, which runs on {@code worker}.
 */
public final class UpdateCoordinator {

    /** What About and the status bar show. */
    public enum State {
        /** No check has answered this run (ABOUT-04: never "up to date" before one has). */
        NOT_CHECKED,
        CHECKING,
        AVAILABLE,
        UP_TO_DATE,
        FAILED
    }

    /** Set to {@code off} by the tests, the headless runs and the timing tours, so none reaches GitHub. */
    public static final String PROPERTY = "mirada.updateCheck";

    /** How long after the first image the automatic check waits: start-up is not its time. */
    public static final Duration STARTUP_DELAY = Duration.ofSeconds(5);

    /** Asked for when something is logged: starting the logging system is no part of start-up. */
    private static Logger log() {
        return Logger.getLogger(UpdateCoordinator.class.getName());
    }

    private final Settings settings;
    private final Function<String, UpdateService.Outcome> check;
    private final BiConsumer<Duration, Runnable> later;
    private final Executor worker;
    private final Executor fxThread;
    private final BiConsumer<String, MessageLog.Severity> say;
    private final LongSupplier clock;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private State state = State.NOT_CHECKED;
    private @Nullable ReleaseInfo latest;
    private @Nullable String error;
    private boolean scheduled;
    /** The current request; an answer to an older one is dropped. */
    private long generation;

    /**
     * @param check asks whether a release newer than the version given is out; blocking
     * @param later runs a task after a delay, on the FX thread
     * @param worker where {@code check} runs
     * @param fxThread where its answer is handled
     * @param say puts a line in the status bar
     * @param clock the time, in milliseconds since the epoch
     */
    public UpdateCoordinator(
            Settings settings,
            Function<String, UpdateService.Outcome> check,
            BiConsumer<Duration, Runnable> later,
            Executor worker,
            Executor fxThread,
            BiConsumer<String, MessageLog.Severity> say,
            LongSupplier clock) {
        this.settings = settings;
        this.check = check;
        this.later = later;
        this.worker = worker;
        this.fxThread = fxThread;
        this.say = say;
        this.clock = clock;
    }

    public State state() {
        return state;
    }

    /** The newer release, once a check has found one; else null. */
    public @Nullable ReleaseInfo latest() {
        return latest;
    }

    /** Why the last check failed, or null. */
    public @Nullable String error() {
        return error;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Whether the automatic check may run at all: the setting, and not switched off for a test run. */
    public boolean automaticAllowed() {
        return settings.checkForUpdates() && !"off".equals(System.getProperty(PROPERTY));
    }

    /**
     * After the first image is up: schedules the automatic check, once a run, if it is allowed and a
     * day has passed since the last one. Returns whether it did.
     */
    public boolean scheduleStartupCheck() {
        if (scheduled || !automaticAllowed()) return false;
        if (!UpdateCheck.isDue(settings.lastUpdateCheck(), clock.getAsLong())) return false;
        scheduled = true;
        later.accept(STARTUP_DELAY, () -> {
            // Switched off in the seconds since: nothing is asked.
            if (automaticAllowed()) checkNow(false);
        });
        return true;
    }

    /**
     * Checks now, off the FX thread. A check by hand ({@code manual}) also says "up to date" and why
     * it failed; the automatic one speaks only of a newer release.
     */
    public void checkNow(boolean manual) {
        if (state == State.CHECKING) return;
        state = State.CHECKING;
        fire();
        long mine = ++generation;
        worker.execute(() -> {
            UpdateService.Outcome outcome = check.apply(AppInfo.VERSION);
            fxThread.execute(() -> {
                if (mine == generation) finish(outcome, manual);
            });
        });
    }

    void finish(UpdateService.Outcome outcome, boolean manual) {
        latest = outcome.newer();
        error = outcome.error();
        ReleaseInfo newer = latest;
        String failure = error;
        if (newer != null) {
            state = State.AVAILABLE;
            say.accept(tr("update.available", newer.version()), MessageLog.Severity.INFO);
        } else if (failure != null) {
            state = State.FAILED;
            log().info("Update check failed: " + failure);
            if (manual) say.accept(tr("update.failed", failure), MessageLog.Severity.WARNING);
        } else {
            state = State.UP_TO_DATE;
            if (manual) say.accept(tr("update.latest", AppInfo.VERSION), MessageLog.Severity.INFO);
        }
        // A failure counts too: GitHub is asked at most once a day, answer or not.
        settings.setLastUpdateCheck(clock.getAsLong());
        fire();
    }

    /**
     * What About says of updates: nothing claimed before a check has answered, or with the check
     * switched off (ABOUT-04).
     */
    public String summary() {
        ReleaseInfo newer = latest;
        String failure = error;
        return switch (state) {
            case NOT_CHECKED ->
                settings.checkForUpdates() ? tr("update.summary.notChecked") : tr("update.summary.switchedOff");
            case CHECKING -> tr("update.summary.checking");
            case AVAILABLE ->
                newer == null ? tr("update.summary.notChecked") : tr("update.summary.available", newer.version());
            case UP_TO_DATE -> tr("update.summary.latest");
            case FAILED -> failure == null ? tr("update.summary.failed") : tr("update.summary.failedBecause", failure);
        };
    }

    /** The newer release's page, or the releases page when it gave none; null when none is known. */
    public @Nullable String releasePage() {
        ReleaseInfo newer = latest;
        if (newer == null) return null;
        return newer.url().isBlank() ? AppInfo.RELEASES_URL : newer.url();
    }

    /** How many listeners are registered: a closed About must leave none. */
    int listenerCount() {
        return listeners.size();
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
