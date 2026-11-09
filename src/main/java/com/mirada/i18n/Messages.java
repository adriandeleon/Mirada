package com.mirada.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Every string the interface shows, by key (Pauta INTL-07): {@code messages.properties} in this
 * package is English, the base, and {@code messages_<code>.properties} each language over it, read
 * with a UTF-8 reader from Mirada's own module. Ported from Editora's {@code i18n/Messages}.
 *
 * <p>{@link #tr(String)} gives the language in use, else English, else the key itself, so a missing
 * key shows rather than throws ({@code MessagesTest} holds every literal key to the catalog). English
 * is read the first time anything asks, so a test or {@code --report} sees real text without {@link
 * #init}. The language is chosen once, before the window is built ({@code App}); a change in Settings
 * takes effect at the next start.
 */
public final class Messages {

    /** The bundled languages, code to its own name, in the order Settings lists them. */
    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("en", "English");
        LANGUAGES.put("es", "Español");
        LANGUAGES.put("pt", "Português");
        LANGUAGES.put("fr", "Français");
        LANGUAGES.put("it", "Italiano");
        LANGUAGES.put("de", "Deutsch");
    }

    private static volatile @Nullable Map<String, String> base;
    private static volatile Map<String, String> active = Map.of();
    private static volatile String current = "en";
    private static volatile boolean chosen;

    private Messages() {}

    /** English, read once. */
    private static Map<String, String> base() {
        Map<String, String> read = base;
        if (read == null) {
            synchronized (Messages.class) {
                read = base;
                if (read == null) {
                    read = load("en");
                    base = read;
                }
            }
        }
        return read;
    }

    /** Uses {@code language} from now on, English for a code that is not bundled. */
    public static synchronized void init(String language) {
        Map<String, String> english = base();
        current = LANGUAGES.containsKey(language) ? language : "en";
        active = "en".equals(current) ? english : load(current);
        chosen = true;
    }

    /** Whether {@link #init} has chosen a language yet. */
    public static boolean initialized() {
        return chosen;
    }

    private static Map<String, String> load(String language) {
        String name = "en".equals(language) ? "messages.properties" : "messages_" + language + ".properties";
        Properties properties = new Properties();
        try (InputStream in = Messages.class.getResourceAsStream(name)) {
            if (in != null) properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            // A missing or broken catalog is no strings: tr falls back to English, then the key.
        }
        Map<String, String> out = new HashMap<>();
        for (String key : properties.stringPropertyNames()) out.put(key, properties.getProperty(key));
        return Collections.unmodifiableMap(out);
    }

    /** The string for {@code key}: the language in use, else English, else the key itself. */
    public static String tr(String key) {
        String value = active.get(key);
        if (value == null) value = base().get(key);
        return value != null ? value : key;
    }

    /**
     * The string for {@code key} with {@link MessageFormat} arguments.
     *
     * <p>One rule decides how a catalog value is written: a value with a placeholder ({@code {0}},
     * {@code {1,number,#}}) is a {@code MessageFormat} pattern, so a literal apostrophe in it is
     * doubled ({@code l''image {0}}); a value without one is shown exactly as written. The rule
     * follows the value, not the call. A plain {@code {0}} groups a number's digits by the locale
     * ("12,345"): a count reads well so, an identifier does not and uses {@code {0,number,#}}.
     */
    public static String tr(String key, @Nullable Object... args) {
        String pattern = tr(key);
        if (args == null || args.length == 0 || pattern.indexOf('{') < 0) return pattern;
        try {
            return new MessageFormat(pattern, Locale.forLanguageTag(current)).format(args);
        } catch (RuntimeException e) {
            return pattern; // a malformed pattern never takes the window down
        }
    }

    /** {@code key}'s English, whatever the language in use, or the key itself. */
    public static String english(String key) {
        return base().getOrDefault(key, key);
    }

    /** Whether {@code key} is in English's catalog. */
    public static boolean has(String key) {
        return base().containsKey(key);
    }

    /** Every key of the English catalog. */
    public static Set<String> keys() {
        return Collections.unmodifiableSet(base().keySet());
    }

    /** The language in use, {@code "es"} for Spanish. */
    public static String current() {
        return current;
    }

    /** The bundled languages, code to its own name, English first. */
    public static Map<String, String> available() {
        return Collections.unmodifiableMap(LANGUAGES);
    }

    /** A language's own name ({@code "es"} is "Español"), or the code if it is not bundled. */
    public static String languageName(String code) {
        return LANGUAGES.getOrDefault(code, code);
    }

    /**
     * The language to use: {@code chosen} if it is bundled, else the system's if it is, else English.
     * Pure.
     */
    public static String resolve(@Nullable String chosen, Set<String> available, @Nullable String system) {
        if (chosen != null && available.contains(chosen)) return chosen;
        if (system != null && available.contains(system)) return system;
        return "en";
    }
}
