package com.mirada.report;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes private details out of a problem report before the user sees it: the home folder, the user
 * and host names, tokens, passwords, credentials in URLs, e-mail and IP addresses (DIAG-06, Pauta
 * 4.0.1). The user still reads and edits the result before anything leaves the machine. The
 * template's, which is Editora's.
 */
public final class Redactor {

    private record Rule(Pattern pattern, String replacement) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"), "<token>"),
            new Rule(Pattern.compile("(?i)\\b(bearer|token)\\s+[A-Za-z0-9._~+/=-]{12,}"), "$1 <token>"),
            new Rule(
                    Pattern.compile("(?i)\\b(password|passwd|secret|token|api[_-]?key)(\\s*[=:]\\s*)\\S+"),
                    "$1$2<redacted>"),
            new Rule(Pattern.compile("://[^/@\\s:]+:[^/@\\s]+@"), "://<credentials>@"),
            new Rule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email>"),
            // Four numbers on their own, not a version's (Java 25.0.1.1+1, a build 1.2.3.4-rc).
            new Rule(
                    Pattern.compile("(?<![\\w.])(?!127\\.0\\.0\\.1\\b)(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w+-]|\\.\\d)"),
                    "<ip>"));

    private final String home;
    private final String user;
    private final String host;

    public Redactor(String home, String user, String host) {
        this.home = home;
        this.user = user;
        this.host = host;
    }

    /** A redactor for this machine's home folder, user and host. */
    public static Redactor forThisMachine() {
        String host = System.getenv().getOrDefault("HOSTNAME", System.getenv().getOrDefault("COMPUTERNAME", ""));
        return new Redactor(System.getProperty("user.home", ""), System.getProperty("user.name", ""), host);
    }

    public String redact(String text) {
        String out = text;
        if (home.length() > 1) {
            out = out.replace(home, "~");
            // Windows' paths are also written with forward slashes (file: URIs, Java's own messages).
            if (home.indexOf('\\') >= 0) out = out.replace(home.replace('\\', '/'), "~");
        }
        for (Rule rule : RULES) out = rule.pattern.matcher(out).replaceAll(rule.replacement);
        out = word(out, host, "<host>");
        return word(out, user, "<user>");
    }

    private static String word(String text, String word, String replacement) {
        if (word.length() < 2) return text;
        return Pattern.compile("(?i)(?<![A-Za-z0-9])" + Pattern.quote(word) + "(?![A-Za-z0-9])")
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }
}
