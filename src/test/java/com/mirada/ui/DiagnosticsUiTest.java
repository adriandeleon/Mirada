package com.mirada.ui;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

import com.mirada.diagnostics.MessageLog;
import com.mirada.settings.Settings;
import com.mirada.update.UpdateCoordinator;
import com.mirada.update.UpdateService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The status bar's echo area and the Message Log's view (WIN-01, DIAG-01, MLOG). */
class DiagnosticsUiTest {

    private static final LocalTime NOON = LocalTime.NOON;

    @Test
    void anErrorIsRedAndAWarningAmber() {
        assertEquals(StatusBar.ERROR, StatusBar.styleOf(MessageLog.Severity.ERROR));
        assertEquals(StatusBar.WARNING, StatusBar.styleOf(MessageLog.Severity.WARNING));
        assertNull(StatusBar.styleOf(MessageLog.Severity.INFO));
    }

    @Test
    void theStatusBarStandsAsideForFullScreenAndSlideshows() {
        assertTrue(ViewerWindow.statusBarWanted(true, false, false));
        assertFalse(ViewerWindow.statusBarWanted(false, false, false));
        assertFalse(ViewerWindow.statusBarWanted(true, true, false));
        assertFalse(ViewerWindow.statusBarWanted(true, false, true));
    }

    /** MLOG-03 and MLOG-04: Escape and the keymap's cancel chord close the log; the keymap's copy chord copies. */
    @Test
    void theKeymapsCancelAndCopyChordsReachTheLog() {
        Keybindings emacs = Keybindings.layered(Keymap.load("emacs", false), false, java.util.Map.of());
        Keybindings cua = Keybindings.layered(Keymap.load("cua", false), false, java.util.Map.of());
        assertEquals(PaletteKeys.Action.CANCEL, PaletteKeys.resolve("escape", true, cua));
        assertEquals(PaletteKeys.Action.CANCEL, PaletteKeys.resolve("C-g", true, emacs));
        assertEquals(PaletteKeys.Action.NONE, PaletteKeys.resolve("C-g", true, cua), "no fixed C-g");
        boolean mac = com.mirada.os.Os.isMac();
        javafx.scene.input.KeyEvent shortcutC = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, !mac, false, mac);
        javafx.scene.input.KeyEvent altW = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.W, false, false, true, false);
        assertTrue(MessageLogView.copies(shortcutC, emacs), "the platform's copy in every keymap");
        assertTrue(MessageLogView.copies(shortcutC, cua));
        assertTrue(MessageLogView.copies(altW, emacs), "M-w, Emacs's copy");
        assertFalse(MessageLogView.copies(altW, cua));
    }

    @Test
    void theFilterMatchesAnyPartIgnoringCase() {
        List<MessageLog.Entry> entries = List.of(
                new MessageLog.Entry(NOON, "Copied a.jpg", MessageLog.Severity.INFO),
                new MessageLog.Entry(NOON, "Couldn't copy B.JPG", MessageLog.Severity.ERROR));
        assertEquals(entries, MessageLogView.matching(entries, "  "));
        assertEquals(List.of(entries.get(1)), MessageLogView.matching(entries, " b.jpg"));
    }

    @Test
    void copiedMessagesHaveTheirTimes() {
        assertEquals(
                "12:00:05  Copied a.jpg\n12:00:00  Opened",
                MessageLogView.asText(List.of(
                        new MessageLog.Entry(NOON.plusSeconds(5), "Copied a.jpg", MessageLog.Severity.INFO),
                        new MessageLog.Entry(NOON, "Opened", MessageLog.Severity.INFO))));
    }

    @Test
    @Tag("fx")
    void theEchoAreaShowsTheNewestAndAClickOpensTheLog() throws Exception {
        FxTestSupport.start();
        List<Command> ran = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                MessageLog messages = new MessageLog();
                Settings settings = new Settings(java.nio.file.Path.of("unused-settings.properties"));
                UpdateCoordinator updates = new UpdateCoordinator(
                        settings,
                        version -> new UpdateService.Outcome(null, null),
                        (delay, task) -> {},
                        Runnable::run,
                        Runnable::run,
                        (text, severity) -> {},
                        () -> 0L);
                StatusBar bar = new StatusBar(messages, updates, ran::add);
                assertTrue(bar.node().getChildrenUnmodifiable().isEmpty(), "nothing built before the first paint");
                assertEquals(StatusBar.HEIGHT, bar.node().getPrefHeight());
                messages.add("Couldn't copy a.jpg", MessageLog.Severity.ERROR, NOON);
                bar.finish();
                assertEquals("Couldn't copy a.jpg", bar.echo().getText());
                assertTrue(bar.echo().getStyleClass().contains(StatusBar.ERROR));
                assertTrue(bar.unread().isVisible());
                messages.add("Not renamed: b.jpg already exists", MessageLog.Severity.WARNING, NOON.plusSeconds(3));
                assertTrue(bar.echo().getStyleClass().contains(StatusBar.WARNING));
                assertFalse(bar.echo().getStyleClass().contains(StatusBar.ERROR));
                messages.add("Copied b.jpg", MessageLog.Severity.INFO, NOON.plusSeconds(6));
                assertFalse(bar.echo().getStyleClass().contains(StatusBar.WARNING));
                bar.echo().getOnMouseClicked().handle(click());
                assertEquals(List.of(Command.MESSAGE_LOG), ran);

                // The log, opened: the errors are read, and Escape closes it.
                OverlayHost host = new OverlayHost(new StackPane());
                MessageLogView log = new MessageLogView(host, messages, ran::add);
                log.show(StatusBar.HEIGHT);
                assertTrue(log.isShowing());
                assertEquals(0, messages.unreadErrors());
                assertFalse(bar.unread().isVisible());
                assertEquals(3, log.listed().size());
                assertEquals("Copied b.jpg", log.listed().getFirst().text());
                log.hide();
                assertFalse(log.isShowing());
                done.complete(null);
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        done.get(10, TimeUnit.SECONDS);
    }

    private static MouseEvent click() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                null);
    }
}
