package com.mirada.report;

import java.io.IOException;
import java.util.List;

import com.mirada.os.Os;
import com.mirada.os.ToolPath;

/**
 * Opens a link in the default browser without JavaFX or AWT, for {@code mirada --report}, which must
 * work when the app itself cannot start. Inside the app, {@code HostServices.showDocument} does this.
 */
public final class Browser {

    private Browser() {}

    /** The command that opens {@code url} on {@code os}. */
    static List<String> command(Os.Kind os, String url) {
        return switch (os) {
            case MAC -> List.of("open", url);
            case WINDOWS -> List.of("rundll32", "url.dll,FileProtocolHandler", url);
            case LINUX -> List.of("xdg-open", url);
        };
    }

    /** Starts the browser; false when it could not be started. */
    public static boolean open(String url) {
        try {
            ToolPath.process(command(Os.host(), url))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
