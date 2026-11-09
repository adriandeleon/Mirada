package com.mirada.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Parent;
import javafx.stage.Stage;

import com.mirada.settings.Bookmarks;
import com.mirada.settings.Settings;
import com.mirada.update.UpdateCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.field;
import static com.mirada.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first frame comes first (PERF-14, TABS-14, PERF-15): a window opened on an image builds only
 * what that frame shows, and what nobody waits for (the header's tooltips and menu, the status
 * bar's labels, the window icon, the bookmarks file, Open Recent's list, the update check) starts on
 * the pulse after the image is painted, not before.
 */
@Tag("fx")
class StartupDeferralTest {

    /** Every panel, tool window and piece of chrome built on first use, by the field that holds it. */
    private static final List<String> BUILT_ON_FIRST_USE = List.of(
            "controls",
            "info",
            "strip",
            "about",
            "settingsWindow",
            "shortcutsWindow",
            "bookmarksWindow",
            "editor",
            "messageLog",
            "debugLog",
            "marksBadge",
            "macMenus",
            "openButtons");

    @TempDir
    Path dir;

    private String updateCheck = "";

    @AfterEach
    void cleanUp() throws Exception {
        System.setProperty("mirada.updateCheck", updateCheck);
        FxToolkit.cleanupStages();
    }

    /** What the window had built and started, read in the same FX turn that made it. */
    private record Before(
            Map<String, Object> built,
            boolean titleBarFinished,
            int statusBarChildren,
            int icons,
            boolean bookmarksLoaded,
            List<Path> recent,
            boolean updateScheduled) {}

    @Test
    void aWindowOpenedOnAnImageBuildsNoPanelAndStartsNoDeferredWorkBeforeItsFirstPaint() throws Exception {
        WindowFixture.threeImages(dir.resolve("photos"));
        Path config = dir.resolve("config");
        // The automatic update check on, as a user has it, to see when it is scheduled. It asks a
        // closed local port (surefire's mirada.updateUrl) if it ever runs.
        updateCheck = System.getProperty("mirada.updateCheck", "");
        System.setProperty("mirada.updateCheck", "on");
        FxTestSupport.start();
        Settings settings = new Settings(config.resolve("settings.properties"));
        settings.load();
        settings.setWindow(new Settings.WindowPlace(0, 0, 1000, 760, false));
        // A bookmark already on the image: its badge too waits for the marks to be read.
        Bookmarks marks = new Bookmarks(Bookmarks.fileBeside(settings.file()));
        marks.toggle(dir.resolve("photos/a.png").toAbsolutePath(), false);

        Stage stage = call(Stage::new);
        List<ViewerWindow> made = new ArrayList<>();
        Before before = call(() -> {
            ViewerWindow window = new ViewerWindow(stage, settings, link -> {});
            made.add(window);
            window.show();
            window.open(dir.resolve("photos/a.png"));
            Map<String, Object> built = new LinkedHashMap<>();
            for (String name : BUILT_ON_FIRST_USE) {
                Object value = field(window, name);
                if (value != null) built.put(name, value);
            }
            TitleBar titleBar = field(window, "titleBar");
            StatusBar statusBar = field(window, "statusBar");
            Bookmarks bookmarks = field(window, "bookmarks");
            UpdateCoordinator updates = field(window, "updates");
            return new Before(
                    built,
                    !((javafx.scene.control.MenuButton) field(titleBar, "menu"))
                            .getItems()
                            .isEmpty(),
                    ((Parent) statusBar.node()).getChildrenUnmodifiable().size(),
                    stage.getIcons().size(),
                    bookmarks.loaded(),
                    List.copyOf(settings.recent()),
                    field(updates, "scheduled"));
        });

        assertEquals(Map.of(), before.built(), "built before the first paint");
        assertFalse(before.titleBarFinished(), "the header's tooltips and menu wait");
        assertEquals(0, before.statusBarChildren(), "the status bar's labels wait");
        assertEquals(0, before.icons(), "the window icon waits");
        assertFalse(before.bookmarksLoaded(), "the bookmarks file is not read before the image");
        assertEquals(List.of(), before.recent(), "Open Recent is not written before the image");
        assertFalse(before.updateScheduled(), "the update check is not scheduled before the image");

        ViewerWindow window = made.getFirst();
        waitUntil("the first paint", () -> field(window, "afterFirstPaint") == null);
        assertNotNull(call(() -> field(window, "shownImage")), "released after the image was painted");
        assertFalse(call(() -> ((javafx.scene.control.MenuButton) field(field(window, "titleBar"), "menu"))
                .getItems()
                .isEmpty()));
        assertEquals(1, call(() -> stage.getIcons().size()));
        assertTrue(call(() -> ((Bookmarks) field(window, "bookmarks")).loaded()));
        assertEquals(List.of(dir.resolve("photos").toAbsolutePath()), call(settings::recent));
        assertTrue(call(() -> FxTestSupport.<Boolean>field(field(window, "updates"), "scheduled")));
        // The marks, read, are shown: the badge is built now, for the bookmarked image.
        assertNotNull(call(() -> field(window, "marksBadge")));
        // And still no panel nobody opened.
        for (String name : List.of("info", "strip", "about", "settingsWindow", "bookmarksWindow", "editor")) {
            assertNull(call(() -> field(window, name)), name + " built without being opened");
        }
    }

    @Test
    void panelsTheSettingsOpenAtStartAreBuiltWithTheWindowAndNothingElse() throws Exception {
        WindowFixture.threeImages(dir.resolve("photos"));
        try (WindowFixture fixture = WindowFixture.open(dir.resolve("config"), dir.resolve("photos"), s -> {
            s.setStartWithInfo(true);
            s.setStartWithThumbnails(true);
        })) {
            assertNotNull(fixture.field("info"), "the sidebar is in the first frame");
            assertNotNull(fixture.field("strip"), "the strip is in the first frame");
            for (String name : List.of("controls", "about", "settingsWindow", "bookmarksWindow", "editor")) {
                assertNull(fixture.field(name), name + " built without being opened");
            }
            fixture.waitForImage("a.png");
        }
    }

    @Test
    void aWindowOpenedWithNothingReleasesItsDeferredWorkAfterHalfASecond() throws Exception {
        try (WindowFixture fixture = WindowFixture.open(dir.resolve("config"), null)) {
            assertNotNull(fixture.field("afterFirstPaint"), "nothing released at once");
            fixture.waitForFirstPaint();
            assertNotNull(fixture.field("openButtons"), "an empty window offers Open Image and Open Folder");
            assertEquals(1, call(() -> fixture.stage.getIcons().size()));
        }
    }
}
