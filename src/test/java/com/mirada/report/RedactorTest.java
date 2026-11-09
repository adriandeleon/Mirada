package com.mirada.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a problem report never carries (DIAG-06). */
class RedactorTest {

    private final Redactor redactor = new Redactor("/home/ada", "ada", "lovelace-pc");

    @Test
    void homePathsBecomeTilde() {
        assertEquals(
                "Settings: ~/.mirada/settings.properties",
                redactor.redact("Settings: /home/ada/.mirada/settings.properties"));
        Redactor windows = new Redactor("C:\\Users\\ada", "ada", "");
        assertEquals(
                "~\\Pictures and ~/Pictures", windows.redact("C:\\Users\\ada\\Pictures and C:/Users/ada/Pictures"));
    }

    @Test
    void userAndHostNamesGo() {
        assertEquals("<user> on <host>", redactor.redact("ada on lovelace-pc"));
        // A word that only contains the name is left alone.
        assertEquals("adamant", redactor.redact("adamant"));
    }

    @Test
    void emailAddressesGo() {
        assertEquals("from <email>", redactor.redact("from someone.else@example.com"));
    }

    @Test
    void credentialsInUrlsGo() {
        assertEquals(
                "https://<credentials>@example.com/repo.git",
                redactor.redact("https://bob:hunter2@example.com/repo.git"));
    }

    @Test
    void tokensAndPasswordsGo() {
        String out = redactor.redact(
                "ghp_abcdefghijklmnopqrstuvwxyz0123 Bearer abcdefghijklmnop password=hunter2 api_key: xyz");
        assertFalse(out.contains("ghp_abc"), out);
        assertFalse(out.contains("abcdefghijklmnop"), out);
        assertFalse(out.contains("hunter2"), out);
        assertFalse(out.contains("xyz"), out);
        assertTrue(out.contains("<token>"), out);
    }

    @Test
    void ipAddressesGoButLocalhostStays() {
        assertEquals("<ip> and 127.0.0.1", redactor.redact("192.168.1.20 and 127.0.0.1"));
        assertEquals("to <ip>:8080, <ip>.", redactor.redact("to 10.0.0.7:8080, 10.0.0.8."));
        assertEquals("Java 25.0.1.1+1-LTS, build 1.2.3.4-rc", redactor.redact("Java 25.0.1.1+1-LTS, build 1.2.3.4-rc"));
    }
}
