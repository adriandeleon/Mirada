package com.mirada.ui;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import org.jspecify.annotations.Nullable;

/**
 * A command's chord as a menu shows it, right-aligned beside the label, and nothing more: it never
 * matches a key. JavaFX draws a menu item's accelerator, but a {@code MenuButton}'s or a context
 * menu's are also put into the scene, where a bare one would fire under the palette's text field
 * (typing "r" would rotate the image) and every other would run twice. Keys are {@link
 * Command#forKey}'s alone; this only names them.
 */
final class MenuChord extends KeyCombination {

    private final String text;

    private MenuChord(String text) {
        this.text = text;
    }

    /** What to show beside {@code command} in a menu, or null when it has no key. */
    static @Nullable KeyCombination of(Command command) {
        KeyCombination chord = command.chord();
        return chord == KeyCombination.NO_MATCH ? null : new MenuChord(chord.getDisplayText());
    }

    /** What to show beside an item whose key is a window's own, not a command's. */
    static KeyCombination shown(KeyCombination chord) {
        return new MenuChord(chord.getDisplayText());
    }

    /** Shows {@code command}'s chord beside {@code item}. */
    static <T extends MenuItem> T on(T item, Command command) {
        item.setAccelerator(of(command));
        return item;
    }

    /**
     * Shows each item's chord again, by the command in its user data: the palette's keys are the
     * user's, and the stepping keys follow the strip.
     */
    static void refresh(Iterable<MenuItem> items) {
        for (MenuItem item : items) {
            if (item.getUserData() instanceof Command command) item.setAccelerator(of(command));
            if (item instanceof Menu menu) refresh(menu.getItems());
        }
    }

    @Override
    public boolean match(KeyEvent event) {
        return false;
    }

    @Override
    public String getName() {
        return text;
    }

    @Override
    public String getDisplayText() {
        return text;
    }

    // KeyCombination compares modifiers only, which would make every MenuChord equal to every bare
    // key in the scene's accelerator map.
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
