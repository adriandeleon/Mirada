package com.mirada.ui;

import java.util.List;
import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import static com.mirada.i18n.Messages.tr;

/**
 * The floating controls over the image, GNOME Image Viewer's: previous and next in a pill at the
 * bottom left, zoom and its menu in a pill at the bottom right, and full screen in one of its own at
 * the top left, where it stays within reach in full screen, the header gone. On a Mac too: its
 * green window button goes with the title bar in full screen.
 *
 * <p>They show when the pointer moves over the image and fade out after {@link #IDLE} without
 * movement, so a still image is just the image; never while the pointer is on one of them. The
 * keyboard does everything they do, so they take no focus.
 */
final class OnScreenControls {

    /** How long the pointer may rest before the controls fade. */
    static final Duration IDLE = Duration.seconds(2);

    private final HBox navigation;
    private final HBox zooming;
    private final HBox screen;
    private final Button previous;
    private final Button next;
    private final Button zoom;
    private final MenuButton zoomMenu = new MenuButton();
    private final Button fullScreen;
    private final PauseTransition idle = new PauseTransition(IDLE);
    private boolean fitted = true;
    private boolean hasImage;
    private boolean vertical;

    OnScreenControls(StackPane canvas, Consumer<Command> run) {
        previous = Icons.button(Command.PREVIOUS, "osd-button", () -> run.accept(Command.PREVIOUS));
        next = Icons.button(Command.NEXT, "osd-button", () -> run.accept(Command.NEXT));
        // One button between the two views people want most, as GNOME's: the whole image, and
        // its pixels. It wears the glyph of the one it would show, as the zoom menu does.
        zoom = Icons.button(Command.ACTUAL_SIZE, "osd-button", () -> run.accept(zoomCommand()));
        fullScreen = Icons.button(Command.FULL_SCREEN, "osd-button", () -> run.accept(Command.FULL_SCREEN));
        zoomMenu.setGraphic(Icons.of(Icons.CHEVRON_DOWN));
        Icons.name(zoomMenu, tr("controls.zoom"), KeyCombination.NO_MATCH);
        zoomMenu.getStyleClass().addAll("osd-button", "flat", "no-arrow");
        zoomMenu.setFocusTraversable(false);
        zoomMenu.getItems()
                .addAll(
                        item(Command.FIT, run),
                        item(Command.ACTUAL_SIZE, run),
                        new SeparatorMenuItem(),
                        item(Command.ZOOM_IN, run),
                        item(Command.ZOOM_OUT, run));

        navigation = pill(previous, next);
        // Its rows are written again as it opens, their chords the keymap's (MENU-15): the user's keys
        // may have changed since; once shown, the chords line up in one column (MENU-18).
        zoomMenu.setOnShowing(
                e -> rows().forEach(row -> AppMenus.refresh(row, row.command().label(), false)));
        zoomMenu.setOnShown(e -> AppMenus.alignChords(rows()));
        zooming = pill(zoom, zoomMenu);
        screen = pill(fullScreen);
        StackPane.setAlignment(navigation, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(zooming, Pos.BOTTOM_RIGHT);
        StackPane.setAlignment(screen, Pos.TOP_LEFT);
        UiZoom.stackMargin(navigation, 0, 0, 18, 18);
        UiZoom.stackMargin(zooming, 0, 18, 18, 0);
        UiZoom.stackMargin(screen, 18, 0, 0, 18);
        canvas.getChildren().addAll(navigation, zooming, screen);

        idle.setOnFinished(e -> {
            if (!navigation.isHover() && !zooming.isHover() && !screen.isHover() && !zoomMenu.isShowing()) {
                fade(false);
            }
        });
        canvas.addEventFilter(MouseEvent.MOUSE_MOVED, e -> wake());
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> wake());
        canvas.setOnMouseExited(e -> {
            if (!zoomMenu.isShowing()) fade(false);
        });
        zoomMenu.showingProperty().addListener((o, was, now) -> {
            if (!now) idle.playFromStart();
        });
        setVisible(navigation, false);
        setVisible(zooming, false);
        setVisible(screen, false);
    }

    /** The full-screen button's way: into full screen, or out of it ({@link Icons#setFullScreen}). */
    void setFullScreen(boolean full) {
        Icons.relabel(fullScreen, Command.FULL_SCREEN);
    }

    /**
     * Up and down arrows for previous and next while the thumbnails stand down the side, where the
     * folder runs top to bottom and Up and Down step through it ({@link Command#vertical()}).
     */
    void setVertical(boolean value) {
        if (value == vertical) return;
        vertical = value;
        relabel();
    }

    /**
     * Every button's glyph and name again, from the commands' chords now: the strip's axis or the
     * user's keys changed (TBAR-03).
     */
    void relabel() {
        Icons.relabel(previous, Command.PREVIOUS);
        Icons.relabel(next, Command.NEXT);
        Icons.relabel(zoom, zoomCommand());
        Icons.relabel(fullScreen, Command.FULL_SCREEN);
    }

    /** What the zoom button does now: the image's pixels while it is fitted, the whole image after. */
    private Command zoomCommand() {
        return fitted ? Command.ACTUAL_SIZE : Command.FIT;
    }

    /** The zoom button's menu, for the tests. */
    MenuButton zoomMenu() {
        return zoomMenu;
    }

    /** Every button, for the tests. */
    List<Control> buttons() {
        return List.of(previous, next, zoom, zoomMenu, fullScreen);
    }

    /** How long the pointer may rest before the controls fade, in seconds (Settings). */
    void setIdle(int seconds) {
        idle.setDuration(Duration.seconds(seconds));
    }

    /** Brings the controls back and restarts the idle clock. */
    void wake() {
        if (!hasImage) return;
        fade(true);
        idle.playFromStart();
    }

    /**
     * What the controls can do right now: stepping where there is somewhere to step to (the same
     * rule as the palette's), zooming when an image is on screen.
     */
    void update(int index, int size, boolean imageShowing, boolean fittedNow) {
        hasImage = imageShowing;
        fitted = fittedNow;
        previous.setDisable(Command.PREVIOUS.unavailable(index, size, imageShowing) != null);
        next.setDisable(Command.NEXT.unavailable(index, size, imageShowing) != null);
        Icons.relabel(zoom, zoomCommand());
        zoom.setDisable(!imageShowing);
        zoomMenu.setDisable(!imageShowing);
        if (!imageShowing) fade(false);
    }

    private void fade(boolean in) {
        for (Region pill : new Region[] {navigation, zooming, screen}) {
            if (in == pill.isVisible() && pill.getOpacity() == (in ? 1 : 0)) continue;
            FadeTransition transition = new FadeTransition(Duration.millis(in ? 120 : 250), pill);
            transition.setToValue(in ? 1 : 0);
            if (in) {
                setVisible(pill, true);
            } else {
                transition.setOnFinished(e -> {
                    if (pill.getOpacity() == 0) setVisible(pill, false);
                });
            }
            // A jump to the end while the system asks for reduced motion (ACC-04).
            Motion.play(transition);
        }
    }

    private static void setVisible(Region pill, boolean visible) {
        pill.setVisible(visible);
        if (!visible) pill.setOpacity(0);
    }

    private static HBox pill(Node... buttons) {
        HBox pill = UiZoom.hbox(2, buttons);
        pill.getStyleClass().add("osd");
        pill.setAlignment(Pos.CENTER);
        pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.setPickOnBounds(false);
        return pill;
    }

    /**
     * A zoom row drawn as the menus' are (MENU-18): the glyph in the 22 px icon column, the title,
     * and the chord at least 18 px after the widest title.
     */
    private static MenuItem item(Command command, Consumer<Command> run) {
        AppMenus.Row row = AppMenus.inWindowRow(command);
        row.item().setOnAction(e -> run.accept(command));
        return row.item();
    }

    /** The zoom menu's rows. */
    private List<AppMenus.Row> rows() {
        return zoomMenu.getItems().stream()
                .map(item -> item.getProperties().get(AppMenus.ROW))
                .filter(AppMenus.Row.class::isInstance)
                .map(AppMenus.Row.class::cast)
                .toList();
    }
}
