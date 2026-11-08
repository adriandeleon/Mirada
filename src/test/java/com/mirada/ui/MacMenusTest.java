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

import com.mirada.image.FxToolkit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacMenusTest {

    /** Every command the palette lists is in a menu once, but About and Settings: the app menu's. */
    @Test
    void theMenusHoldThePalettesCommandsOnce() {
        List<Command> inMenus = new ArrayList<>();
        for (MacMenus.Heading heading : MacMenus.HEADINGS) heading.groups().forEach(inMenus::addAll);
        Set<Command> expected = new HashSet<>(Command.inPalette());
        expected.remove(Command.ABOUT);
        expected.remove(Command.SETTINGS);
        expected.add(Command.PALETTE);
        assertEquals(expected, new HashSet<>(inMenus));
        assertEquals(inMenus.size(), new HashSet<>(inMenus).size(), "a command in two menus");
    }

    /** A row's title ends in its command's chord, written there as the native menu draws text only. */
    @Test
    void aRowsChordIsInItsTitle() {
        assertEquals(
                "Rotate Right   " + Command.ROTATE_CLOCKWISE.chord().getDisplayText(),
                MacMenus.title("Rotate Right", Command.ROTATE_CLOCKWISE));
        assertEquals("Sort by Name", MacMenus.title("Sort by Name", Command.SORT_BY_NAME));
    }

    /** No key equivalents (MENU-02), no icons, and every bound command's chord shown (MENU-15). */
    @Test
    @Tag("fx")
    void theBarHasNoAcceleratorsAndShowsEveryChord() throws Exception {
        FxToolkit.start();
        CompletableFuture<MenuBar> built = new CompletableFuture<>();
        Platform.runLater(() -> built.complete(MacMenus.build(c -> {}, c -> null, c -> false, Command::label)));
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
    /** Help opens with the palette, then the documentation (DOCS-06, DOCS-07, Pauta 4.0.1). */
    @Test
    void helpPutsTheDocumentationRightAfterThePalette() {
        MacMenus.Heading help = MacMenus.HEADINGS.getLast();
        assertEquals("Help", help.title());
        assertEquals(
                List.of(Command.PALETTE, Command.DOCUMENTATION),
                help.groups().getFirst().subList(0, 2));
    }
}
