package com.mirada.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import org.jspecify.annotations.Nullable;

/**
 * The keys in force: a bundled {@link Keymap}, its {@code vertical} bindings while the thumbnails
 * stand down the side, then the user's own on top ({@link #layered}, the one place the layers are
 * applied, KBD-11). Mirada has no plugins, so it has Pauta's first two layers: base keymap → user
 * overrides.
 *
 * <p>The user's keys are {@code keys.<command id>} in the settings file ({@code keys.go.next=n
 * space}; blank takes a command's keys away) and {@code palette.keys}, the palette's, which the
 * settings window edits. A user key wins: the keymap's binding of the same key to another command
 * where it works ({@link Command#context}) is dropped.
 *
 * <p>Keys are {@link KeyChord} tokens. A token whose key is a character no key is named for
 * ({@code C-?}) matches by that character, Shift or not, as {@link KeyCharacterCombination} does.
 *
 * <p>{@link #current} is made the first time a key is pressed or a chord is shown, never at
 * start-up, and again only when the settings or the strip's side change. FX thread only.
 */
final class Keybindings {

    private final Map<Command, List<KeyCombination>> chords = new EnumMap<>(Command.class);
    private final Map<Command.Context, Map<String, Command>> byToken = new EnumMap<>(Command.Context.class);
    private final Map<Command.Context, Map<KeyCombination, Command>> byCharacter = new EnumMap<>(Command.Context.class);

    private Keybindings() {
        for (Command.Context context : Command.Context.values()) {
            byToken.put(context, new HashMap<>());
            byCharacter.put(context, new LinkedHashMap<>());
        }
    }

    /**
     * The layers, in order: {@code base}'s bindings; its {@code vertical} ones in their place when
     * {@code vertical}; then {@code user}'s, by command id, each replacing that command's keys. A user
     * binding none of whose keys can be read is ignored, so a typo never leaves a command keyless;
     * a blank one unbinds. Ids that name no command are skipped. Pure.
     */
    static Keybindings layered(Keymap base, boolean vertical, Map<String, String> user) {
        Map<Command, List<String>> tokens = new EnumMap<>(Command.class);
        base.bindings().forEach((id, keys) -> put(tokens, id, keys));
        if (vertical) base.vertical().forEach((id, keys) -> put(tokens, id, keys));
        // In id order, so which of two clashing user bindings wins never depends on hashing.
        Map<Command, List<String>> users = new EnumMap<>(Command.class);
        new TreeMap<>(user).forEach((id, text) -> {
            List<String> keys = words(text);
            if (!keys.isEmpty() && keys.stream().allMatch(key -> canonical(key) == null)) return;
            put(users, id, keys);
        });
        Keybindings made = new Keybindings();
        // The palette's first: its keys open it from anywhere, so no other binding may take them.
        List<String> palette = users.get(Command.PALETTE);
        if (palette != null) made.bind(Command.PALETTE, palette);
        users.forEach((command, keys) -> {
            if (command != Command.PALETTE) made.bind(command, keys);
        });
        tokens.forEach((command, keys) -> {
            if (!users.containsKey(command)) made.bind(command, keys);
        });
        return made;
    }

    private static void put(Map<Command, List<String>> into, String id, List<String> keys) {
        Command command = Command.byId(id);
        if (command != null) into.put(command, keys);
    }

    /** Binds what is still free of {@code keys} where {@code command} works; the first bound is the one shown. */
    private void bind(Command command, List<String> keys) {
        List<KeyCombination> bound = new ArrayList<>();
        for (String key : keys) {
            String token = canonical(key);
            if (token == null) continue;
            KeyCombination combination = combination(token);
            if (combination == null) continue;
            Map<String, Command> tokens = tokens(command.context());
            Map<KeyCombination, Command> characters = characters(command.context());
            if (combination instanceof KeyCharacterCombination) {
                if (characters.putIfAbsent(combination, command) != null) continue;
            } else if (tokens.putIfAbsent(token, command) != null) {
                continue;
            }
            bound.add(combination);
        }
        chords.put(command, List.copyOf(bound));
    }

    private Map<String, Command> tokens(Command.Context context) {
        return Objects.requireNonNull(byToken.get(context));
    }

    private Map<KeyCombination, Command> characters(Command.Context context) {
        return Objects.requireNonNull(byCharacter.get(context));
    }

    /** The command a key press runs where {@code context}'s keys work, or null for none. */
    @Nullable
    Command commandFor(Command.Context context, KeyEvent event) {
        String token = KeyChord.of(event);
        if (token == null) return null;
        Command command = tokens(context).get(token);
        if (command != null) return command;
        for (Map.Entry<KeyCombination, Command> entry : characters(context).entrySet()) {
            if (entry.getKey().match(event)) return entry.getValue();
        }
        return null;
    }

    /** The command {@code token} runs where {@code context}'s keys work, or null. Pure; for tests and the settings. */
    @Nullable
    Command commandFor(Command.Context context, String token) {
        String canonical = canonical(token);
        if (canonical == null) return null;
        KeyCombination combination = combination(canonical);
        return combination instanceof KeyCharacterCombination
                ? characters(context).get(combination)
                : tokens(context).get(canonical);
    }

    /** Every key bound to {@code command}, the one to show first; empty for none. */
    List<KeyCombination> chords(Command command) {
        return chords.getOrDefault(command, List.of());
    }

    /** The key to show beside {@code command}, or {@link KeyCombination#NO_MATCH} for none. */
    KeyCombination chord(Command command) {
        List<KeyCombination> all = chords(command);
        return all.isEmpty() ? KeyCombination.NO_MATCH : all.getFirst();
    }

    /**
     * A token with its modifiers in {@link KeyChord}'s order ({@code S-C-z} is {@code C-S-z}), or
     * null for one that names no key. A character no key is named for stays a character: {@code C-?}.
     * Pure.
     */
    static @Nullable String canonical(String token) {
        if (token.isBlank()) return null;
        KeyCombination named = KeyChord.toCombination(token.trim());
        if (named instanceof KeyCodeCombination code) {
            return KeyChord.token(
                    code.getCode(),
                    code.getControl() == KeyCombination.ModifierValue.DOWN,
                    code.getAlt() == KeyCombination.ModifierValue.DOWN,
                    code.getMeta() == KeyCombination.ModifierValue.DOWN,
                    code.getShift() == KeyCombination.ModifierValue.DOWN);
        }
        Typed character = Typed.of(token.trim());
        if (character.key().codePointCount(0, character.key().length()) != 1) return null;
        StringBuilder sb = new StringBuilder();
        if (character.control()) sb.append("C-");
        if (character.alt()) sb.append("M-");
        if (character.meta()) sb.append("Cmd-");
        return sb.append(character.key()).toString();
    }

    /** What a canonical token matches: a key by its code, or a character, Shift or not. */
    private static @Nullable KeyCombination combination(String token) {
        KeyCombination named = KeyChord.toCombination(token);
        if (named != null) return named;
        Typed character = Typed.of(token);
        List<KeyCombination.Modifier> modifiers = new ArrayList<>();
        if (character.control()) modifiers.add(KeyCombination.CONTROL_DOWN);
        if (character.alt()) modifiers.add(KeyCombination.ALT_DOWN);
        if (character.meta()) modifiers.add(KeyCombination.META_DOWN);
        modifiers.add(KeyCombination.SHIFT_ANY);
        return new KeyCharacterCombination(character.key(), modifiers.toArray(KeyCombination.Modifier[]::new));
    }

    /** A character token's modifiers and its character; Shift is part of the character, so it is dropped. */
    private record Typed(boolean control, boolean alt, boolean meta, String key) {
        static Typed of(String token) {
            boolean control = false;
            boolean alt = false;
            boolean meta = false;
            String rest = token;
            while (true) {
                if (rest.startsWith("Cmd-") && rest.length() > 4) {
                    meta = true;
                    rest = rest.substring(4);
                } else if (rest.startsWith("C-") && rest.length() > 2) {
                    control = true;
                    rest = rest.substring(2);
                } else if (rest.startsWith("M-") && rest.length() > 2) {
                    alt = true;
                    rest = rest.substring(2);
                } else if (rest.startsWith("S-") && rest.length() > 2) {
                    rest = rest.substring(2);
                } else {
                    return new Typed(control, alt, meta, rest);
                }
            }
        }
    }

    /** The tokens of a setting: spaces or commas between them. */
    static List<String> words(@Nullable String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String word : text.trim().split("[\\s,]+")) if (!word.isEmpty()) out.add(word);
        return out;
    }

    // --- the keys in force ---

    private static String keymap = Keymap.DEFAULT;
    private static Map<String, String> user = Map.of();
    private static @Nullable Keybindings along;
    private static @Nullable Keybindings sideways;

    /**
     * The keys in force now: the keymap the settings name for this platform, the strip's side, the
     * user's keys. Made when first asked, after the first paint.
     */
    /**
     * Reads and parses the keymap {@code keymapId} names, on the calling thread, so the first key press
     * finds it parsed: the window asks on a background thread after its first paint (PERF-01). Any
     * thread; the parsed keymap is kept by {@link Keymap#load}.
     */
    static void preload(String keymapId) {
        Keymap.load(keymapId, Command.MAC);
    }

    static Keybindings current() {
        boolean vertical = Command.vertical();
        Keybindings made = vertical ? sideways : along;
        if (made == null) {
            made = layered(Keymap.load(keymap, Command.MAC), vertical, user);
            if (vertical) sideways = made;
            else along = made;
        }
        return made;
    }

    /**
     * Follows the settings: the keymap's name, the user's {@code keys.*} and the palette's keys,
     * which go on last so nothing else takes them. Whether anything changed; nothing is read or
     * parsed here.
     */
    static boolean use(String keymapId, Map<String, String> keys, String paletteKeys) {
        Map<String, String> layer = new LinkedHashMap<>(keys);
        layer.put(Command.PALETTE.id(), paletteKeys);
        if (keymapId.equals(keymap) && layer.equals(user)) return false;
        keymap = keymapId;
        user = Map.copyOf(layer);
        along = null;
        sideways = null;
        return true;
    }
}
