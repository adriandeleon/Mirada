package com.mirada.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mirada.update.Json;
import org.jspecify.annotations.Nullable;

/**
 * A bundled keymap, shipped as a JSON resource in {@code com/mirada/keymaps} (KBD-09): Mirada's own
 * keys ({@code cua.json}, with {@code cua.mac.json} where Cmd takes Ctrl's place and Finder's and
 * Preview's keys join in) and Emacs's ({@code emacs.json}). Each binds command ids ({@link
 * Command#id}) to their keys as {@link KeyChord} tokens, the one shown first:
 *
 * <pre>{@code
 * {
 *   "name": "CUA",
 *   "bindings": { "go.next": ["right", "pagedown", "space"], ... },
 *   "vertical": { "go.next": ["down", "pagedown", "space"], ... }
 * }
 * }</pre>
 *
 * <p>{@code vertical} replaces those commands' keys while the thumbnails stand down the side, so the
 * folder steps along the strip ({@link Command#vertical}). A command a keymap does not name has no
 * key there; the palette still finds it. The layers on top, the user's own keys, are {@link
 * Keybindings}'.
 *
 * <p>Read the first time a key is pressed or a chord is shown, never before the first paint, and
 * each file once.
 */
record Keymap(String id, String name, Map<String, List<String>> bindings, Map<String, List<String>> vertical) {

    /** The bundled keymaps, in the order the settings window lists them. */
    static final List<String> PRESETS = List.of("cua", "emacs");

    /** The keymap for a setting that names none of {@link #PRESETS}: Mirada's own keys. */
    static final String DEFAULT = "cua";

    private static final Map<String, Keymap> LOADED = new ConcurrentHashMap<>();

    /** The bundled keymap the setting names, or the default for a name that is not one. */
    static String resolve(@Nullable String id) {
        return id != null && PRESETS.contains(id) ? id : DEFAULT;
    }

    /** The bundled keymap {@code id} ({@link #resolve}d), its {@code .mac.json} variant on a Mac when it has one. */
    static Keymap load(@Nullable String id, boolean mac) {
        String chosen = resolve(id);
        return LOADED.computeIfAbsent(chosen + (mac ? ".mac" : ""), key -> read(chosen, mac));
    }

    private static Keymap read(String id, boolean mac) {
        String base = "/com/mirada/keymaps/" + id;
        String resource =
                mac && Keymap.class.getResource(base + ".mac.json") != null ? base + ".mac.json" : base + ".json";
        try (InputStream in = Keymap.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("no keymap resource " + resource);
            return parse(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the keymap " + resource, e);
        } catch (Json.Malformed e) {
            throw new IllegalStateException("the keymap " + resource + " is not JSON: " + e.getMessage(), e);
        }
    }

    /** A keymap from its JSON. A binding may be one token or a list of them. Pure. */
    static Keymap parse(String id, String json) throws Json.Malformed {
        if (!(Json.parse(json) instanceof Map<?, ?> tree)) throw new Json.Malformed("not an object", 0);
        String name = tree.get("name") instanceof String given ? given : id;
        return new Keymap(id, name, bindings(tree.get("bindings")), bindings(tree.get("vertical")));
    }

    private static Map<String, List<String>> bindings(@Nullable Object section) throws Json.Malformed {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (section == null) return Collections.unmodifiableMap(out);
        if (!(section instanceof Map<?, ?> map)) throw new Json.Malformed("bindings are not an object", 0);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            List<String> tokens = new ArrayList<>();
            switch (entry.getValue()) {
                case String token -> tokens.add(token);
                case List<?> list -> {
                    for (Object token : list) {
                        if (!(token instanceof String text))
                            throw new Json.Malformed(entry.getKey() + ": not a key", 0);
                        tokens.add(text);
                    }
                }
                case null, default -> throw new Json.Malformed(entry.getKey() + ": not a key or a list of keys", 0);
            }
            out.put(String.valueOf(entry.getKey()), List.copyOf(tokens));
        }
        // Not Map.copyOf: the file's order is kept, for anyone reading the bindings in order.
        return Collections.unmodifiableMap(out);
    }
}
