package com.mirada.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Just enough JSON to read GitHub's latest-release answer: objects, arrays, strings, numbers,
 * booleans and null, into {@code Map}, {@code List}, {@code String}, {@code Double}, {@code Boolean}
 * and null. A whole JSON library for four fields would be a dependency to ship, license and keep in
 * the native image's metadata.
 */
final class Json {

    /** What could not be read, and where. */
    static final class Malformed extends Exception {
        Malformed(String message, int at) {
            super(message + " at " + at);
        }
    }

    /** Nesting deeper than this is refused rather than followed down the stack. */
    static final int MAX_DEPTH = 64;

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /** The value {@code text} holds, which must be all of it. */
    static @Nullable Object parse(String text) throws Malformed {
        Json json = new Json(text);
        Object value = json.value(0);
        json.space();
        if (json.at != text.length()) throw new Malformed("text after the value", json.at);
        return value;
    }

    private @Nullable Object value(int depth) throws Malformed {
        if (depth > MAX_DEPTH) throw new Malformed("nested too deep", at);
        space();
        if (at >= text.length()) throw new Malformed("no value", at);
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", null);
            default -> number();
        };
    }

    private Map<String, @Nullable Object> object(int depth) throws Malformed {
        Map<String, @Nullable Object> map = new LinkedHashMap<>();
        at++;
        space();
        if (peek() == '}') {
            at++;
            return map;
        }
        while (true) {
            space();
            if (peek() != '"') throw new Malformed("expected a name", at);
            String name = string();
            space();
            expect(':');
            map.put(name, value(depth + 1));
            space();
            char c = next();
            if (c == '}') return map;
            if (c != ',') throw new Malformed("expected , or }", at - 1);
        }
    }

    private List<@Nullable Object> array(int depth) throws Malformed {
        List<@Nullable Object> list = new ArrayList<>();
        at++;
        space();
        if (peek() == ']') {
            at++;
            return list;
        }
        while (true) {
            list.add(value(depth + 1));
            space();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw new Malformed("expected , or ]", at - 1);
        }
    }

    private String string() throws Malformed {
        at++; // the opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return out.toString();
            if (c < 0x20) throw new Malformed("control character in a string", at - 1);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) throw new Malformed("short \\u escape", at);
                    try {
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    } catch (NumberFormatException e) {
                        throw new Malformed("bad \\u escape", at);
                    }
                    at += 4;
                }
                default -> throw new Malformed("bad escape", at - 1);
            }
        }
    }

    private Double number() throws Malformed {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
        if (start == at) throw new Malformed("unexpected character", at);
        try {
            return Double.valueOf(text.substring(start, at));
        } catch (NumberFormatException e) {
            throw new Malformed("bad number", start);
        }
    }

    private @Nullable Object word(String word, @Nullable Object value) throws Malformed {
        if (!text.startsWith(word, at)) throw new Malformed("unexpected word", at);
        at += word.length();
        return value;
    }

    private void expect(char c) throws Malformed {
        if (next() != c) throw new Malformed("expected " + c, at - 1);
    }

    private char peek() throws Malformed {
        if (at >= text.length()) throw new Malformed("unexpected end", at);
        return text.charAt(at);
    }

    private char next() throws Malformed {
        char c = peek();
        at++;
        return c;
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
    }
}
