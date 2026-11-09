package com.mirada.report;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.mirada.os.ToolPath;

/**
 * Opens a link in the default browser without JavaFX or AWT, for {@code mirada --report}, which must
 * work when the app itself cannot start. Inside the app, {@code HostServices.showDocument} does this.
 */
public final class Browser {

    private Browser() {}

    /** The command that opens {@code url} on {@code osName}. */
    static List<String> command(String osName, String url) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) return List.of("open", url);
        if (os.startsWith("windows")) return List.of("rundll32", "url.dll,FileProtocolHandler", url);
        return List.of("xdg-open", url);
    }

    /** Starts the browser; false when it could not be started. */
    public static boolean open(String url) {
        try {
            ToolPath.process(command(System.getProperty("os.name", ""), url))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
