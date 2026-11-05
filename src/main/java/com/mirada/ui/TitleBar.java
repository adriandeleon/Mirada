package com.mirada.ui;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.jspecify.annotations.Nullable;

/**
 * The window's top row, GNOME Image Viewer's: full screen on the left, the file name in the middle,
 * rotate, info and the main menu on the right, and the window buttons after them. macOS and Windows
 * get their own conventions ({@link Layout}).
 *
 * <p>Where the platform supports JavaFX 27's extended windows this is a {@link HeaderBar}: the stage
 * gives up its title bar and keeps its window buttons, drawn over the right of this row. Termina's
 * three traps apply: HeaderBar has no style class (so {@code header-bar} is added), an unsupported
 * {@code EXTENDED} quietly becomes {@code DECORATED} (so it is asked first, and the same controls go
 * in an ordinary row under the native title bar instead), and full screen keeps the window buttons
 * unless their height is set to zero ({@link #fullScreen}).
 */
final class TitleBar {

    /**
     * What the row holds on a platform. A Mac has no full-screen button, as its green window button
     * is one and sits beside it, and no menu, as the menu bar holds its items ({@link MacMenus});
     * its title stays centred, as AppKit centres one. Windows puts the title on the left, after the
     * button, as its own title bars do. Linux is GNOME's.
     */
    record Layout(boolean fullScreenButton, boolean menuButton, boolean titleLeft) {

        static Layout of(String osName) {
            String os = osName.toLowerCase(Locale.ROOT);
            if (os.startsWith("mac")) return new Layout(false, false, false);
            if (os.startsWith("windows")) return new Layout(true, true, true);
            return new Layout(true, true, false);
        }
    }

    private static final Layout LAYOUT = Layout.of(System.getProperty("os.name", ""));

    private final Stage stage;
    private final Label title = new Label();
    /** The folder being shown, under the file name; its full path is its tooltip. */
    private final Label folder = new Label();

    private final VBox heading = new VBox(title, folder);
    private @Nullable Tooltip folderTip;
    private @Nullable Path folderShown;
    private final Button fullScreen = button(Icons.FULL_SCREEN);
    private final Button rotate = button(Icons.ROTATE);
    private final Consumer<Command> run;
    private final ToggleButton info = new ToggleButton();
    private final MenuButton menu = new MenuButton();
    private final Region row;
    private final boolean extended;

    /**
     * @param run runs a command, the same as its key would
     * @param infoShowing the sidebar's state, which the info button shows and toggles
     */
    TitleBar(Stage stage, Consumer<Command> run, BooleanProperty infoShowing) {
        this.stage = stage;
        this.run = run;
        this.extended = Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW);

        title.getStyleClass().add("header-title");
        folder.getStyleClass().add("header-subtitle");
        folder.setVisible(false);
        folder.setManaged(false);
        heading.setAlignment(LAYOUT.titleLeft() ? Pos.CENTER_LEFT : Pos.CENTER);
        heading.setMinWidth(0);
        fullScreen.setOnAction(e -> run.accept(Command.FULL_SCREEN));
        // A shift-click turns the other way. An ActionEvent carries no modifiers, so the press that
        // leads to it records them.
        boolean[] shift = {false};
        rotate.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> shift[0] = e.isShiftDown());
        rotate.setOnAction(e -> {
            run.accept(shift[0] ? Command.ROTATE_COUNTERCLOCKWISE : Command.ROTATE_CLOCKWISE);
            shift[0] = false;
        });
        info.setGraphic(Icons.of(Icons.INFO));
        info.getStyleClass().addAll("header-button", "flat");
        info.selectedProperty().bindBidirectional(infoShowing);
        menu.setGraphic(Icons.of(Icons.MENU));
        menu.getStyleClass().addAll("header-button", "flat", "no-arrow");
        for (Node node : new Node[] {fullScreen, rotate, info, menu}) node.setFocusTraversable(false);

        HBox right = new HBox(4, rotate, info);
        if (LAYOUT.menuButton()) right.getChildren().add(menu);
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox left = new HBox(4);
        if (LAYOUT.fullScreenButton()) left.getChildren().add(fullScreen);
        if (LAYOUT.titleLeft()) left.getChildren().add(heading);
        left.setAlignment(Pos.CENTER_LEFT);
        @Nullable VBox centre = LAYOUT.titleLeft() ? null : heading;
        if (extended) {
            if (!stage.isShowing()) stage.initStyle(StageStyle.EXTENDED);
            HeaderBar bar = new HeaderBar();
            bar.getStyleClass().add("header-bar");
            bar.setLeft(left);
            bar.setCenter(centre);
            bar.setRight(right);
            // The bar drags the window by its background; the title is a label, so it is marked to
            // drag too: a title bar you cannot drag by its title is a trap.
            for (Node node : new Node[] {heading, title, folder}) {
                HeaderBar.setDragType(node, HeaderDragType.DRAGGABLE);
            }
            row = bar;
        } else {
            BorderPane plain = new BorderPane(centre, null, right, null, left);
            plain.getStyleClass().addAll("header-bar", "header-row");
            row = plain;
        }
    }

    /**
     * The tooltips and the menu's items, added after the first image is on screen: neither is seen
     * before then, and building them first measurably delayed that image (ViewerWindow.afterFirstPaint).
     */
    void finish() {
        fullScreen.setTooltip(new Tooltip("Full Screen (F)"));
        Tooltip tip = new Tooltip(folderShown == null ? "" : folderShown.toString());
        folderTip = tip;
        folder.setTooltip(tip);
        rotate.setTooltip(new Tooltip("Rotate Right (R); Shift-click rotates left"));
        info.setTooltip(new Tooltip("Image Properties (I)"));
        if (!LAYOUT.menuButton()) return;
        menu.setTooltip(new Tooltip("Main Menu"));
        menu.getItems()
                .addAll(
                        item(Command.OPEN, run),
                        new SeparatorMenuItem(),
                        item(Command.PALETTE, run),
                        new SeparatorMenuItem(),
                        item(Command.THUMBNAILS, run),
                        item(Command.INFO, run),
                        new SeparatorMenuItem(),
                        item(Command.EDIT, run),
                        item(Command.ROTATE_COUNTERCLOCKWISE, run),
                        item(Command.ROTATE_CLOCKWISE, run),
                        item(Command.SLIDESHOW, run),
                        new SeparatorMenuItem(),
                        item(Command.SETTINGS, run),
                        item(Command.SHORTCUTS, run),
                        item(Command.ABOUT, run),
                        new SeparatorMenuItem(),
                        item(Command.CLOSE, run));
    }

    Region node() {
        return row;
    }

    void setTitle(String text) {
        title.setText(text);
    }

    /**
     * Names the folder being shown under the file name, or none ({@code null}): its own name, the
     * whole path in the tooltip. Stepping within a folder changes nothing here.
     */
    void setFolder(@Nullable Path shown) {
        if (Objects.equals(shown, folderShown)) return;
        folderShown = shown;
        Path name = shown == null ? null : shown.getFileName();
        folder.setText(shown == null ? "" : name == null ? shown.toString() : name.toString());
        folder.setVisible(shown != null);
        folder.setManaged(shown != null);
        Tooltip tip = folderTip;
        if (tip != null) tip.setText(shown == null ? "" : shown.toString());
    }

    /** Disables what needs an image on screen. */
    void setImageShowing(boolean showing) {
        rotate.setDisable(!showing);
    }

    /**
     * Takes the row out of the window for full screen and puts it back after; the window buttons are
     * the stage's, so they go with a height of zero, not with the row.
     */
    void fullScreen(BorderPane root, boolean full) {
        root.setTop(full ? null : row);
        fullScreen.setGraphic(Icons.of(full ? Icons.LEAVE_FULL_SCREEN : Icons.FULL_SCREEN));
        if (extended) HeaderBar.setSystemButtonHeight(stage, full ? 0 : HeaderBar.USE_DEFAULT_SIZE);
    }

    private static Button button(String icon) {
        Button button = new Button();
        button.setGraphic(Icons.of(icon));
        button.getStyleClass().addAll("header-button", "flat");
        return button;
    }

    private static MenuItem item(Command command, Consumer<Command> run) {
        MenuItem item = new MenuItem(command.label(), Icons.menu(command));
        item.setOnAction(e -> run.accept(command));
        return item;
    }
}
