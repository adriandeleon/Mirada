package com.mirada.os;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;

import com.sun.glass.ui.Application;
import com.sun.glass.ui.Menu;
import com.sun.glass.ui.MenuItem;
import org.jspecify.annotations.Nullable;

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
 *
 * <p>The Settings item has no key equivalent of its own (WIN-15): macOS would run it before the
 * window's key filter and go on showing {@code ⌘,} under a keymap that binds another chord. Its title
 * shows the chord the keymap binds to {@code app.settings} instead, as the menu bar's rows do
 * (MENU-02), and {@link #refresh} writes it again when the keys change.
 */
public final class MacAppMenu {

    /** Asked for when something is logged: starting the logging system is no part of start-up. */
    private static Logger log() {
        return Logger.getLogger(MacAppMenu.class.getName());
    }

    private MacAppMenu() {}

    /** The Settings item once installed (a glass {@code MenuItem}), and what writes its title. */
    private static @Nullable Object settingsItem;

    private static @Nullable Supplier<String> settingsTitle;

    /**
     * Puts {@code about} and {@code settings} at the top of the application menu, on the FX thread,
     * each run on the FX thread; Settings' title, with its chord, is {@code settingsTitle}'s. Returns
     * what it did, or why it did nothing.
     */
    public static String install(String aboutTitle, Runnable about, Supplier<String> settingsTitle, Runnable settings) {
        if (!MacOpenFiles.isMac(System.getProperty("os.name"))) return "none (not macOS)";
        Module ours = MacAppMenu.class.getModule();
        Module javafx = Platform.class.getModule();
        if (!javafx.isExported("com.sun.glass.ui", ours) || !javafx.isExported("com.sun.glass.ui.mac", ours)) {
            log().warning("No application menu items: com.sun.glass.ui.mac is not exported to Mirada");
            return "none (com.sun.glass.ui.mac not exported)";
        }
        MacAppMenu.settingsTitle = settingsTitle;
        return Glass.install(aboutTitle, about, settingsTitle.get(), settings);
    }

    /** Writes the Settings item's title again, its chord the keymap's now (WIN-15). */
    public static void refresh() {
        Object item = settingsItem;
        Supplier<String> title = settingsTitle;
        if (item != null && title != null) Glass.retitle(item, title.get());
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
            MenuItem item = glass.createMenuItem(settingsTitle, callback(settings)); // no key equivalent
            menu.insert(item, 2);
            menu.insert(MenuItem.Separator, 3);
            settingsItem = item;
            return "about, settings";
        }

        static void retitle(Object item, String title) {
            if (item instanceof MenuItem menuItem) menuItem.setTitle(title);
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
