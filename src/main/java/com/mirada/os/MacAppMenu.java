package com.mirada.os;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;

import com.sun.glass.ui.Application;
import com.sun.glass.ui.Menu;
import com.sun.glass.ui.MenuItem;

/**
 * About and Settings in macOS's application menu, the one named after the app, where every Mac app
 * keeps them. JavaFX builds that menu itself (Hide, Hide Others, Show All, Quit) and offers no way
 * to add to it: a {@code MenuBar} on the system menu bar only gets the menus after it. Glass's
 * {@code MacApplication.getAppleMenu} hands out the menu, and glass's own menu items go into it.
 *
 * <p>{@code MacApplication} is in {@code com.sun.glass.ui.mac}, which exists only in macOS's
 * JavaFX, so it is reached by reflection, and only where that package is exported to Mirada: the
 * macOS launchers pass {@code --add-exports javafx.graphics/com.sun.glass.ui.mac=com.mirada} (the
 * pom's {@code glass.mac.option}), and the native image, built from the class path, needs nothing
 * but the method in its reflection metadata. Without it the menu stays JavaFX's and the commands
 * are still in the palette and the Help menu's search.
 */
public final class MacAppMenu {

    /** Asked for when something is logged: starting the logging system is no part of start-up. */
    private static Logger log() {
        return Logger.getLogger(MacAppMenu.class.getName());
    }

    /** Glass's {@code KeyEvent.MODIFIER_COMMAND}; its package is not exported to Mirada. */
    private static final int COMMAND = 1 << 4;

    private MacAppMenu() {}

    /**
     * Puts {@code about} and {@code settings} (with Cmd-comma) at the top of the application menu,
     * on the FX thread, each run on the FX thread. Returns what it did, or why it did nothing.
     */
    public static String install(String aboutTitle, Runnable about, String settingsTitle, Runnable settings) {
        if (!MacOpenFiles.isMac(System.getProperty("os.name"))) return "none (not macOS)";
        Module ours = MacAppMenu.class.getModule();
        Module javafx = Platform.class.getModule();
        if (!javafx.isExported("com.sun.glass.ui", ours) || !javafx.isExported("com.sun.glass.ui.mac", ours)) {
            log().warning("No application menu items: com.sun.glass.ui.mac is not exported to Mirada");
            return "none (com.sun.glass.ui.mac not exported)";
        }
        return Glass.install(aboutTitle, about, settingsTitle, settings);
    }

    /** Everything that names a glass class, apart, so that verifying {@link MacAppMenu} loads none. */
    static final class Glass {

        private Glass() {}

        static String install(String aboutTitle, Runnable about, String settingsTitle, Runnable settings) {
            Application glass = Application.GetApplication();
            if (glass == null) return "none (no glass application)";
            Object found;
            try {
                found = glass.getClass().getMethod("getAppleMenu").invoke(glass);
            } catch (ReflectiveOperationException | RuntimeException e) {
                log().log(Level.WARNING, "No application menu items", e);
                return "none (" + e + ")";
            }
            if (!(found instanceof Menu menu)) return "none (no application menu)";
            menu.insert(glass.createMenuItem(aboutTitle, callback(about)), 0);
            menu.insert(MenuItem.Separator, 1);
            menu.insert(glass.createMenuItem(settingsTitle, callback(settings), ',', COMMAND), 2);
            menu.insert(MenuItem.Separator, 3);
            return "about, settings";
        }

        private static MenuItem.Callback callback(Runnable action) {
            return new MenuItem.Callback() {
                @Override
                public void action() {
                    action.run();
                }

                @Override
                public void validate() {}
            };
        }
    }
}
