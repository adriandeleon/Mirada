package com.mirada.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The message catalogs held in step (Pauta INTL-01, INTL-08, INTL-09, QUAL-12): the six languages
 * have the base's keys and no others, every value is there, an apostrophe is written as the value's
 * kind wants, and every key a source asks for by name exists. Ported from Editora's MessagesTest.
 */
class MessagesTest {

    /** Every language but English, the base. */
    static final List<String> LANGUAGES = List.of("es", "pt", "fr", "it", "de");

    /** The base ("") and every language. */
    static final List<String> ALL = List.of("", "es", "pt", "fr", "it", "de");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+(,[^}]*)?}");

    @AfterEach
    void english() {
        Messages.init("en");
    }

    static Properties catalog(String language) {
        String name = language.isEmpty() ? "messages.properties" : "messages_" + language + ".properties";
        Properties properties = new Properties();
        try (InputStream in = Messages.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing catalog " + name);
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return properties;
    }

    private static String name(String language) {
        return language.isEmpty() ? "en" : language;
    }

    /** The bundled list and the catalogs on disk are the same six (INTL-01, INTL-02). */
    @Test
    void theSixLanguagesAreBundledAndListed() {
        assertEquals(
                List.of("en", "es", "pt", "fr", "it", "de"),
                List.copyOf(Messages.available().keySet()));
        for (String language : ALL) assertFalse(catalog(language).isEmpty(), name(language) + " is empty");
    }

    /**
     * Every line is a comment, blank, a continuation or {@code key=value}: a merge's conflict markers
     * would otherwise load as keys with empty values in all six at once and pass the parity check.
     */
    @Test
    void everyLineIsACommentAKeyOrAContinuation() throws IOException {
        for (String language : ALL) {
            String file = language.isEmpty() ? "messages.properties" : "messages_" + language + ".properties";
            String text;
            try (InputStream in = Messages.class.getResourceAsStream(file)) {
                assertNotNull(in);
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean continued = false;
            Set<String> seen = new TreeSet<>();
            for (String line : text.split("\n", -1)) {
                boolean continuation = continued;
                continued = line.endsWith("\\") && !line.endsWith("\\\\");
                if (continuation || line.isBlank() || line.startsWith("#")) continue;
                assertFalse(
                        line.startsWith("<<<<<<<") || line.startsWith(">>>>>>>") || line.equals("======="),
                        file + ": a merge's conflict marker: " + line);
                int equals = line.indexOf('=');
                assertTrue(equals > 0, file + ": not key=value: " + line);
                assertTrue(seen.add(line.substring(0, equals).strip()), file + ": a key twice: " + line);
            }
        }
    }

    /** INTL-08: each language has exactly the base's keys. */
    @Test
    void everyLanguageHasExactlyTheBasesKeys() {
        Set<String> base = catalog("").stringPropertyNames();
        assertFalse(base.isEmpty());
        for (String language : LANGUAGES) {
            Set<String> keys = catalog(language).stringPropertyNames();
            Set<String> missing = new TreeSet<>(base);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(base);
            assertTrue(missing.isEmpty(), language + " lacks " + missing);
            assertTrue(extra.isEmpty(), language + " has keys the base does not: " + extra);
        }
    }

    /** INTL-08: no value is empty, in any catalog. */
    @Test
    void noValueIsEmpty() {
        for (String language : ALL) {
            Properties catalog = catalog(language);
            for (String key : catalog.stringPropertyNames())
                assertFalse(catalog.getProperty(key).isBlank(), name(language) + ": " + key + " is empty");
        }
    }

    /** QUAL-12, KBD-03: every command title has its palette line, in the base and so in each language. */
    @Test
    void everyCommandTitleHasItsDescription() {
        Set<String> keys = catalog("").stringPropertyNames();
        Set<String> missing = new TreeSet<>();
        for (String key : keys)
            if (key.startsWith("command.") && !key.endsWith(".desc") && !keys.contains(key + ".desc")) missing.add(key);
        assertTrue(missing.isEmpty(), "titles without a .desc: " + missing);
    }

    /** INTL-09: a value without a placeholder is shown as written, so its apostrophe is single. */
    @Test
    void aValueWithoutAPlaceholderNeverDoublesAnApostrophe() {
        List<String> wrong = new ArrayList<>();
        for (String language : ALL) {
            Properties catalog = catalog(language);
            for (String key : catalog.stringPropertyNames()) {
                String value = catalog.getProperty(key);
                if (!PLACEHOLDER.matcher(value).find() && value.contains("''"))
                    wrong.add(name(language) + " " + key + " = " + value);
            }
        }
        assertTrue(wrong.isEmpty(), "write a single apostrophe:\n" + String.join("\n", wrong));
    }

    /** INTL-09: a value with a placeholder is a MessageFormat pattern, where a lone apostrophe quotes. */
    @Test
    void aValueWithAPlaceholderNeverHasALoneApostrophe() {
        List<String> wrong = new ArrayList<>();
        for (String language : ALL) {
            Properties catalog = catalog(language);
            for (String key : catalog.stringPropertyNames()) {
                String value = catalog.getProperty(key);
                if (PLACEHOLDER.matcher(value).find() && value.replace("''", "").indexOf('\'') >= 0)
                    wrong.add(name(language) + " " + key + " = " + value);
            }
        }
        assertTrue(wrong.isEmpty(), "double the apostrophe (l' → l''):\n" + String.join("\n", wrong));
    }

    /** Every pattern formats in its own language, and keeps each placeholder the base has. */
    @Test
    void everyPatternFormatsWithEveryPlaceholderOfTheBase() {
        Properties base = catalog("");
        List<String> wrong = new ArrayList<>();
        for (String language : ALL) {
            Properties catalog = catalog(language);
            for (String key : base.stringPropertyNames()) {
                String english = base.getProperty(key);
                String value = catalog.getProperty(key);
                if (value == null || !PLACEHOLDER.matcher(english).find()) continue;
                Set<String> want = indices(english);
                if (!want.equals(indices(value))) {
                    wrong.add(name(language) + " " + key + ": placeholders " + indices(value) + ", base " + want);
                    continue;
                }
                try {
                    new java.text.MessageFormat(value, Locale.forLanguageTag(name(language)))
                            .format(new Object[] {1, 2, 3, 4});
                } catch (IllegalArgumentException e) {
                    wrong.add(name(language) + " " + key + ": " + e.getMessage());
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    /**
     * INTL-10: a number that is an identifier or a measure read as one (a zoom percent, an image's
     * size, an HTTP status) is {@code {n,number,#}} in English, never digit-grouped; each language
     * keeps it so, and formats it ungrouped.
     */
    @Test
    void anIdentifierIsNeverDigitGroupedInAnyLanguage() {
        Pattern identifier = Pattern.compile("\\{(\\d+),number,#}");
        Properties base = catalog("");
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (String key : base.stringPropertyNames()) {
            Matcher m = identifier.matcher(base.getProperty(key));
            Set<String> ids = new TreeSet<>();
            while (m.find()) ids.add(m.group(1));
            if (ids.isEmpty()) continue;
            for (String language : ALL) {
                String value = catalog(language).getProperty(key, "");
                Object[] args = {1234567, 1234567, 1234567, 1234567};
                String out = new java.text.MessageFormat(value, Locale.forLanguageTag(name(language))).format(args);
                checked++;
                if (!out.contains("1234567")) wrong.add(name(language) + " " + key + " groups it: " + out);
            }
        }
        assertTrue(checked > 0, "no identifier patterns found");
        assertTrue(wrong.isEmpty(), "use {n,number,#}:\n" + String.join("\n", wrong));
    }

    private static Set<String> indices(String value) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("\\{(\\d+)").matcher(value);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** {@code tr("key")} and {@code tr("key", …)} with a literal key, and a ternary's two. */
    private static final Pattern LITERAL_KEY = Pattern.compile("\\btr\\(\\s*\"([^\"\\\\]+)\"\\s*[,)]");

    private static final Pattern TERNARY_KEYS =
            Pattern.compile("\\btr\\([^()\";]*\\?\\s*\"([^\"\\\\]+)\"\\s*:\\s*\"([^\"\\\\]+)\"\\s*[,)]");

    /**
     * INTL-07: every key a source asks for by name exists. A missing one is no error at run time, tr
     * shows the key itself, so only this finds it.
     */
    @Test
    void everyLiteralKeyInTheSourcesExists() throws IOException {
        Path sources = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sources), "run from the project's root");
        Set<String> known = catalog("").stringPropertyNames();
        Set<String> missing = new TreeSet<>();
        int found = 0;
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file :
                    files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file);
                for (Pattern pattern : List.of(LITERAL_KEY, TERNARY_KEYS)) {
                    Matcher m = pattern.matcher(source);
                    while (m.find()) {
                        for (int g = 1; g <= m.groupCount(); g++) {
                            found++;
                            if (!known.contains(m.group(g)))
                                missing.add(m.group(g) + "  (" + sources.relativize(file) + ")");
                        }
                    }
                }
            }
        }
        assertTrue(found > 300, "the scan no longer finds the tr calls: " + found);
        assertTrue(missing.isEmpty(), "keys the sources use that the base lacks:\n" + String.join("\n", missing));
    }

    @Test
    void trFallsBackToEnglishThenTheKey() {
        Messages.init("es");
        assertEquals("no.such.key", Messages.tr("no.such.key"));
        assertEquals("es", Messages.current());
        Messages.init("xx");
        assertEquals("en", Messages.current(), "a language that is not bundled is English");
        assertEquals(catalog("").getProperty("command.go.next"), Messages.tr("command.go.next"));
    }

    @Test
    void aValueWithoutAPlaceholderIsShownAsWrittenEvenGivenArguments() {
        Messages.init("en");
        String key = catalog("").stringPropertyNames().stream()
                .filter(k -> {
                    String v = catalog("").getProperty(k);
                    return v.contains("'") && !PLACEHOLDER.matcher(v).find();
                })
                .findFirst()
                .orElseThrow();
        assertEquals(Messages.tr(key), Messages.tr(key, "ignored"));
    }

    @Test
    void theLanguageIsTheChosenThenTheSystemsThenEnglish() {
        Set<String> bundled = Messages.available().keySet();
        assertEquals("fr", Messages.resolve("fr", bundled, "de"));
        assertEquals("de", Messages.resolve("", bundled, "de"));
        assertEquals("de", Messages.resolve(null, bundled, "de"));
        assertEquals("en", Messages.resolve("", bundled, "ja"));
        assertEquals("en", Messages.resolve("ru", bundled, "ja"));
    }
}
