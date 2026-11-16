package com.mirada.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppMenusTest {

    /**
     * Every viewer command is in a menu once, on every OS, but About's links
     * and Copy Details (About's button), the Pauta window's links and Copy as text, each setting's
     * own (the Settings window's), and Escape and panning, which are keys. The editor's tools and the Bookmarks
     * window's are their own windows'.
     */
    @Test
    void theMenusHoldTheViewersCommandsOnce() {
        List<Command> inMenus = new ArrayList<>();
        for (AppMenus.Heading heading : AppMenus.HEADINGS) heading.groups().forEach(inMenus::addAll);
        Set<Command> expected = new HashSet<>();
        for (Command command : Command.values()) {
            if (command.context() == Command.Context.VIEWER) expected.add(command);
        }
        expected.removeAll(Set.of(
                Command.COPY_DETAILS,
                Command.HOME_PAGE,
                Command.RELEASES,
                Command.RELEASE_NOTES,
                Command.PAUTA_RULES,
                Command.PAUTA_DOCS,
                Command.COPY_PAUTA,
                Command.ESCAPE,
                Command.PAN_LEFT,
                Command.PAN_RIGHT,
                Command.PAN_UP,
                Command.PAN_DOWN));
        // The settings' own commands are the Settings window's rows, in the palette (SET-06).
        expected.removeIf(command -> command.group().equals("Settings")
                && command != Command.EXPORT_CONFIG
                && command != Command.IMPORT_CONFIG);
        assertEquals(expected, new HashSet<>(inMenus));
        assertEquals(inMenus.size(), new HashSet<>(inMenus).size(), "a command in two menus");
    }

    /** A row's title ends in its command's chord, written there as the native menu draws text only. */
    @Test
    void aRowsChordIsInItsTitle() {
        assertEquals(
                "Rotate Right   " + Command.ROTATE_CLOCKWISE.chord().getDisplayText(),
                AppMenus.title("Rotate Right", Command.ROTATE_CLOCKWISE));
        assertEquals("Sort by Name", AppMenus.title("Sort by Name", Command.SORT_BY_NAME));
    }

    /** No key equivalents (MENU-02), no icons, and every bound command's chord shown (MENU-15). */
    @Test
    @Tag("fx")
    void theBarHasNoAcceleratorsAndShowsEveryChord() throws Exception {
        FxTestSupport.start();
        CompletableFuture<MenuBar> built = new CompletableFuture<>();
        Platform.runLater(() -> built.complete(AppMenus.system(c -> {}, c -> null, c -> false, Command::label)));
        MenuBar bar = built.get(10, TimeUnit.SECONDS);
        for (Menu menu : bar.getMenus()) {
            for (MenuItem item : menu.getItems()) {
                if (!(item.getUserData() instanceof Command command)) continue;
                assertNull(item.getAccelerator(), command.name());
                assertNull(item.getGraphic(), command.name());
                KeyCombination chord = command.chord();
                if (chord != KeyCombination.NO_MATCH)
                    assertTrue(item.getText().endsWith(chord.getDisplayText()), item.getText());
            }
        }
    }
    /**
     * Help's rows in Pauta's order (DOCS-06, DOCS-07, DOCS-08, ABOUT-01): Command Palette,
     * Documentation, the keyboard reference, Check for Updates, Message Log, Debug Log, Report a
     * Problem, Pauta Conformance, About last. Each log's own commands follow it.
     */
    @Test
    void helpHoldsItsRowsInPautasOrderWithAboutLast() {
        AppMenus.Heading help = AppMenus.HEADINGS.getLast();
        assertEquals("Help", help.title());
        List<Command> rows = help.groups().stream().flatMap(List::stream).toList();
        List<Command> order = List.of(
                Command.PALETTE,
                Command.DOCUMENTATION,
                Command.SHORTCUTS,
                Command.CHECK_FOR_UPDATES,
                Command.MESSAGE_LOG,
                Command.DEBUG_LOG,
                Command.REPORT_PROBLEM,
                Command.PAUTA,
                Command.ABOUT);
        assertEquals(order, rows.stream().filter(order::contains).toList());
        assertEquals(Command.ABOUT, rows.getLast());
    }

    /** WIN-14: the File menu ends with Quit, on every OS. */
    @Test
    void fileEndsWithQuit() {
        AppMenus.Heading file = AppMenus.HEADINGS.getFirst();
        assertEquals("File", file.title());
        assertEquals(Command.QUIT, file.groups().getLast().getLast());
    }
}
