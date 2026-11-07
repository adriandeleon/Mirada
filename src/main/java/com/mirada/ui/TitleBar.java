package com.mirada.ui;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
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
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.jspecify.annotations.Nullable;

/**
 * The window's top row, GNOME Image Viewer's: the file name in the middle, rotate, info and the main
 * menu on the right, and the window buttons after them. macOS and Windows get their own conventions
 * ({@link Layout}). Full screen is a floating control over the image ({@link OnScreenControls}).
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
     * Where the row puts the title on a platform. Windows puts it on the left, as its own title bars
     * do; a Mac centres it, as AppKit does, and so does GNOME. Every platform has the main menu, a
     * Mac's beside its menu bar ({@link MacMenus}), which holds the same commands.
     */
    record Layout(boolean titleLeft) {

        static Layout of(String osName) {
            return new Layout(osName.toLowerCase(Locale.ROOT).startsWith("windows"));
        }
    }

    private static final Layout LAYOUT = Layout.of(System.getProperty("os.name", ""));

    /**
     * "folder / name" on one line. Short of room, the file name keeps its width and the folder gives
     * way: cut with an ellipsis, then gone with its slash once too little of it would show. An
     * {@link HBox} would shrink both alike and cut the name while the folder still showed.
     */
    static final class Heading extends HBox {

        /** Less of the folder than this tells nothing, and it goes. */
        static final double MIN_FOLDER = 48;

        private final Label folder;
        private final Label slash;
        private final Label title;

        Heading(Label folder, Label slash, Label title) {
            super(folder, slash, title);
            this.folder = folder;
            this.slash = slash;
            this.title = title;
            folder.setMinWidth(0);
            title.setMinWidth(0);
        }

        /**
         * The folder's width in {@code room}, given the others' preferred widths: all it asks for,
         * what the name and slash leave, or none when that is under {@link #MIN_FOLDER}.
         */
        static double folderWidth(double room, double folder, double slash, double title, double spacing) {
            double left = room - title - slash - 2 * spacing;
            if (left >= folder) return folder;
            return left >= MIN_FOLDER ? left : 0;
        }

        @Override
        protected void layoutChildren() {
            Insets insets = getInsets();
            double room = Math.max(0, getWidth() - insets.getLeft() - insets.getRight());
            double height = getHeight() - insets.getTop() - insets.getBottom();
            double spacing = snapSpaceX(getSpacing());
            double slashWidth = snapSizeX(slash.prefWidth(-1));
            double titleWant = snapSizeX(title.prefWidth(-1));
            double folderWidth = folder.isManaged()
                    ? snapSizeX(folderWidth(room, folder.prefWidth(-1), slashWidth, titleWant, spacing))
                    : 0;
            boolean named = folderWidth > 0;
            folder.setVisible(named);
            slash.setVisible(named);
            double before = named ? folderWidth + spacing + slashWidth + spacing : 0;
            double titleWidth = Math.min(titleWant, room - before);
            double x = insets.getLeft();
            if (getAlignment().getHpos() == HPos.CENTER) x += Math.max(0, (room - before - titleWidth) / 2);
            x = snapPositionX(x);
            if (named) {
                layoutInArea(folder, x, insets.getTop(), folderWidth, height, 0, HPos.LEFT, VPos.CENTER);
                x += folderWidth + spacing;
                layoutInArea(slash, x, insets.getTop(), slashWidth, height, 0, HPos.LEFT, VPos.CENTER);
                x += slashWidth + spacing;
            }
            layoutInArea(title, x, insets.getTop(), titleWidth, height, 0, HPos.LEFT, VPos.CENTER);
        }
    }

    private final Stage stage;
    private final Label title = new Label();
    /** The folder being shown, before the file name; its full path is its tooltip. */
    private final Label folder = new Label();
    /** The "/" between the folder and the file name, shown with the folder. */
    private final Label slash = new Label("/");

    private final Heading heading = new Heading(folder, slash, title);
    private @Nullable Tooltip folderTip;
    private @Nullable Path folderShown;
    private final Button rotate = button(Icons.ROTATE);
    private final Consumer<Command> run;
    private final BooleanProperty infoShowing;
    private final BooleanProperty thumbnailsShowing;
    private final ToggleButton info = new ToggleButton();
    private final MenuButton menu = new MenuButton();
    private final Region row;
    private final boolean extended;

    /**
     * @param run runs a command, the same as its key would
     * @param infoShowing the sidebar's state, which the info button shows and toggles
     * @param thumbnailsShowing the strip's state, which the menu ticks and toggles
     */
    TitleBar(Stage stage, Consumer<Command> run, BooleanProperty infoShowing, BooleanProperty thumbnailsShowing) {
        this.stage = stage;
        this.run = run;
        this.infoShowing = infoShowing;
        this.thumbnailsShowing = thumbnailsShowing;
        this.extended = Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW);

        title.getStyleClass().add("header-title");
        folder.getStyleClass().add("header-folder");
        slash.getStyleClass().add("header-folder");
        // Shown only while there is a folder (managed) and room for it (Heading.layoutChildren).
        for (Label label : new Label[] {folder, slash}) {
            label.setVisible(false);
            label.setManaged(false);
        }
        heading.getStyleClass().add("header-heading");
        heading.setAlignment(LAYOUT.titleLeft() ? Pos.CENTER_LEFT : Pos.CENTER);
        heading.setMinWidth(0);
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
        for (Node node : new Node[] {rotate, info, menu}) node.setFocusTraversable(false);
        // Icons only: these are what a screen reader says for them (VoiceOver, Narrator, Orca).
        rotate.setAccessibleText(Command.ROTATE_CLOCKWISE.label());
        info.setAccessibleText(Command.INFO.label());
        menu.setAccessibleText("Main Menu");

        HBox right = new HBox(4, rotate, info, menu);
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox left = new HBox(4);
        if (LAYOUT.titleLeft()) left.getChildren().add(heading);
        left.setAlignment(Pos.CENTER_LEFT);
        @Nullable Heading centre = LAYOUT.titleLeft() ? null : heading;
        if (extended) {
            if (!stage.isShowing()) stage.initStyle(StageStyle.EXTENDED);
            HeaderBar bar = new HeaderBar();
            bar.getStyleClass().add("header-bar");
            bar.setLeft(left);
            bar.setCenter(centre);
            bar.setRight(right);
            // The bar drags the window by its background; the title is a label, so it is marked to
            // drag too: a title bar you cannot drag by its title is a trap.
            for (Node node : new Node[] {heading, title, folder, slash}) {
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
        Tooltip tip = new Tooltip(folderShown == null ? "" : folderShown.toString());
        folderTip = tip;
        folder.setTooltip(tip);
        rotate.setTooltip(new Tooltip("Rotate Right (R); Shift-click rotates left"));
        info.setTooltip(new Tooltip("Image Properties (I)"));
        menu.setTooltip(new Tooltip("Main Menu"));
        menu.getItems()
                .addAll(
                        item(Command.OPEN, run),
                        item(Command.OPEN_FOLDER, run),
                        item(Command.OPEN_RECENT, run),
                        new SeparatorMenuItem(),
                        item(Command.PALETTE, run),
                        new SeparatorMenuItem(),
                        panel(Command.THUMBNAILS, thumbnailsShowing),
                        panel(Command.INFO, infoShowing),
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
        // The palette's keys may have changed since.
        menu.setOnShowing(e -> MenuChord.refresh(menu.getItems()));
    }

    Region node() {
        return row;
    }

    void setTitle(String text) {
        title.setText(text);
    }

    /**
     * Names the folder being shown before the file name, as "folder / name", or none ({@code null}):
     * its own name, the whole path in the tooltip. Stepping within a folder changes nothing here.
     */
    void setFolder(@Nullable Path shown) {
        if (Objects.equals(shown, folderShown)) return;
        folderShown = shown;
        Path name = shown == null ? null : shown.getFileName();
        folder.setText(shown == null ? "" : name == null ? shown.toString() : name.toString());
        // Whether they show is the heading's to say, by the room it has.
        folder.setManaged(shown != null);
        slash.setManaged(shown != null);
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
        if (extended) HeaderBar.setSystemButtonHeight(stage, full ? 0 : HeaderBar.USE_DEFAULT_SIZE);
    }

    private static Button button(String icon) {
        Button button = new Button();
        button.setGraphic(Icons.of(icon));
        button.getStyleClass().addAll("header-button", "flat");
        return button;
    }

    /** A panel the menu ticks while it is open, and opens or closes as the key does. */
    private static MenuItem panel(Command command, BooleanProperty showing) {
        CheckMenuItem item = new CheckMenuItem(command.label(), Icons.menu(command));
        item.selectedProperty().bindBidirectional(showing);
        item.setUserData(command);
        return MenuChord.on(item, command);
    }

    private static MenuItem item(Command command, Consumer<Command> run) {
        MenuItem item = new MenuItem(command.label(), Icons.menu(command));
        item.setOnAction(e -> run.accept(command));
        item.setUserData(command);
        return MenuChord.on(item, command);
    }
}
