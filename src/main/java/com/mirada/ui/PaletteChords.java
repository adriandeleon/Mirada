package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

/**
 * The keys that open the command palette: the user's to choose, in the settings file as {@link
 * KeyChord} tokens ({@code palette.keys=C-S-p M-x f1}) or from the settings window. By default
 * Shift+Ctrl+P (Shift+Cmd+P on a Mac), Emacs's M-x (Alt+X; Option+X on a Mac) and F1.
 *
 * <p>The key filter asks {@link #opens} before {@link Command#forKey}, so a chord chosen here wins
 * over the key's built-in command. The first chord is the one shown beside the palette, in the
 * header's menu, the shortcuts window and the Mac's Help menu ({@link Command#chord()}).
 *
 * <p>FX thread only, like the settings it follows.
 */
final class PaletteChords {

    /** The setting as last given; read into {@link #current} when first needed, not at start-up. */
    private static String keys = Settings.defaultPaletteKeys(Command.MAC);

    private static @Nullable List<KeyCombination> current;

    private PaletteChords() {}

    /** The chords in use now, never empty. */
    static List<KeyCombination> current() {
        List<KeyCombination> chords = current;
        if (chords == null) {
            chords = parse(keys, Command.MAC);
            current = chords;
        }
        return chords;
    }

    /** Follows the setting: {@code keys} as the settings file holds them. Whether they changed. */
    static boolean use(String keys) {
        if (keys.equals(PaletteChords.keys)) return false;
        PaletteChords.keys = keys;
        current = null;
        return true;
    }

    /**
     * Whether the open palette moves or edits by this key itself, so it must not close the palette
     * even when it is one of the palette's keys: C-p is the row above there, M-f a word forward.
     * Pure.
     */
    static boolean keptByPalette(@Nullable String token) {
        return PaletteKeys.resolve(token, true) != PaletteKeys.Action.NONE || TextInputKeys.actionFor(token) != null;
    }

    /** Whether {@code e} is one of the chords that open the palette. */
    static boolean opens(KeyEvent e) {
        for (KeyCombination chord : current()) if (chord.match(e)) return true;
        return false;
    }

    /**
     * The chords {@code keys} names, each once, in order; those it cannot read are skipped, and when
     * none is left the default stands, so a bad edit never leaves the palette without a key. Pure.
     */
    static List<KeyCombination> parse(@Nullable String keys, boolean mac) {
        List<KeyCombination> chords = new ArrayList<>();
        for (String token : tokens(keys)) {
            KeyCombination chord = KeyChord.toCombination(token);
            if (chord != null && !chords.contains(chord)) chords.add(chord);
        }
        String fallback = Settings.defaultPaletteKeys(mac);
        if (chords.isEmpty() && !fallback.equals(keys)) return parse(fallback, mac);
        return List.copyOf(chords);
    }

    /**
     * {@code keys} with {@code token} added at the end, or taken out if it is already there — but the
     * last one is never taken out. What the settings window's "Add a key" does with the key pressed.
     * Tokens that name no key are dropped on the way. Pure.
     */
    static String toggled(String keys, String token) {
        List<String> readable = new ArrayList<>();
        for (String each : tokens(keys)) {
            if (KeyChord.toCombination(each) != null && !readable.contains(each)) readable.add(each);
        }
        if (readable.contains(token)) {
            if (readable.size() > 1) readable.remove(token);
        } else if (KeyChord.toCombination(token) != null) {
            readable.add(token);
        }
        return String.join(" ", readable);
    }

    /** The chords as the platform writes keys, "Ctrl+Shift+P, Alt+X, F1". */
    static String display(List<KeyCombination> chords) {
        return chords.stream().map(KeyCombination::getDisplayText).collect(Collectors.joining(", "));
    }

    private static List<String> tokens(@Nullable String keys) {
        if (keys == null || keys.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String token : keys.trim().split("[\\s,]+")) if (!token.isEmpty()) out.add(token);
        return out;
    }
}
