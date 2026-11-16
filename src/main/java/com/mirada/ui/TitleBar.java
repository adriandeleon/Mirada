package com.mirada.ui;

import java.nio.file.Path;
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
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
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

import static com.mirada.i18n.Messages.tr;

/**
 * The window's top row, GNOME Image Viewer's: the menu bar on the left (Windows and Linux), the file
 * name in the middle, rotate and info on the right, and the window buttons after them. The same on
 * every OS (OS-04), but that a Mac's menus are in its menu bar at the top of the screen ({@link
 * AppMenus}), as the OS's way wins there, and its header holds none (WIN-03). Full screen is a
 * floating control over the image ({@link OnScreenControls}).
 *
 * <p>Where the platform supports JavaFX 27's extended windows, and the user keeps the menus in the
 * title bar (the setting, on by default; always on a Mac), this is a {@link HeaderBar}: the stage
 * gives up its title bar and keeps its window buttons, drawn over the right of this row. Termina's
 * three traps apply: HeaderBar has no style class (so {@code header-bar} is added), an unsupported
 * {@code EXTENDED} quietly becomes {@code DECORATED} (so it is asked first, and the same controls go
 * in an ordinary row under the native title bar instead), and full screen keeps the window buttons
 * unless their height is set to zero ({@link #fullScreen}). With the setting off the window keeps the
 * system's title bar and the same row, menus and all, goes under it.
 */
final class TitleBar {

    /**
     * Whether the window draws its own title bar ({@code StageStyle.EXTENDED} with a {@link
     * HeaderBar}): where the platform can, on a Mac always (its header holds no menus), elsewhere
     * while the menus are to be in the title bar (WIN-03).
     */
    static boolean usesHeaderBar(boolean mac, boolean titleBarMenus, boolean extendedSupported) {
        return extendedSupported && (mac || titleBarMenus);
    }

    /**
     * Why the menus can't be put in the title bar here, or null when they can: the setting and its
     * command exist on every OS (OS-01), disabled with this reason where the system keeps the menus
     * itself or cannot draw the title bar.
     */
    static @Nullable String titleBarMenusUnavailable(boolean mac, boolean extendedSupported) {
        if (mac) return tr("reason.titleBarMenusMac");
        if (!extendedSupported) return tr("reason.titleBarMenusUnsupported");
        return null;
    }

    /** {@link #titleBarMenusUnavailable(boolean, boolean)} for this OS and this toolkit. */
    static @Nullable String titleBarMenusUnavailable() {
        return titleBarMenusUnavailable(Command.MAC, Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW));
    }

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
    private final Button rotate;
    private final ToggleButton info = new ToggleButton();
    /** Where the menu bar goes, on the left, once it is built ({@link #finish}). */
    private final HBox left = new HBox(4);

    private @Nullable MenuBar menus;
    private final Region row;
    private final boolean extended;

    /**
     * @param run runs a command, the same as its key would
     * @param infoShowing the sidebar's state, which the info button shows and toggles
     * @param extended whether the window draws its own title bar ({@link #usesHeaderBar}); decided
     *     before the window is first shown, as a stage's style cannot change after
     */
    TitleBar(Stage stage, Consumer<Command> run, BooleanProperty infoShowing, boolean extended) {
        this.stage = stage;
        this.extended = extended;

        title.getStyleClass().add("header-title");
        folder.getStyleClass().add("header-folder");
        slash.getStyleClass().add("header-folder");
        // Shown only while there is a folder (managed) and room for it (Heading.layoutChildren).
        for (Label label : new Label[] {folder, slash}) {
            label.setVisible(false);
            label.setManaged(false);
        }
        heading.getStyleClass().add("header-heading");
        heading.setAlignment(Pos.CENTER);
        heading.setMinWidth(0);
        // A shift-click turns the other way. An ActionEvent carries no modifiers, so the press that
        // leads to it records them.
        boolean[] shift = {false};
        rotate = Icons.button(Command.ROTATE_CLOCKWISE, "header-button", () -> {
            run.accept(shift[0] ? Command.ROTATE_COUNTERCLOCKWISE : Command.ROTATE_CLOCKWISE);
            shift[0] = false;
        });
        rotate.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> shift[0] = e.isShiftDown());
        info.setGraphic(Icons.menu(Command.INFO));
        info.getStyleClass().addAll("header-button", "flat");
        info.selectedProperty().bindBidirectional(infoShowing);
        info.setFocusTraversable(false);
        // Icons only: their names are what a screen reader says (VoiceOver, Narrator, Orca).
        Icons.name(info, Command.INFO);

        HBox right = new HBox(4, rotate, info);
        right.setAlignment(Pos.CENTER_RIGHT);
        left.setAlignment(Pos.CENTER_LEFT);
        Heading centre = heading;
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
     * The folder's tooltip and the menu bar, added after the first image is on screen: neither is
     * seen before then, and building them first measurably delayed that image
     * (ViewerWindow.afterFirstPaint). The buttons are named as they are built ({@link Icons#button}).
     *
     * @param bar the in-window menu bar ({@link AppMenus#inWindow}), or null on a Mac, whose menus
     *     are the system's
     */
    void finish(@Nullable MenuBar bar) {
        Tooltip tip = new Tooltip(folderShown == null ? "" : folderShown.toString());
        folderTip = tip;
        folder.setTooltip(tip);
        if (bar != null) {
            menus = bar;
            left.getChildren().setAll(bar);
        }
    }

    /** The in-window menu bar, once built; null before, and on a Mac. */
    @Nullable
    MenuBar menuBar() {
        return menus;
    }

    /** Whether this row is a {@link HeaderBar}, the window drawing its own title bar. */
    boolean extended() {
        return extended;
    }

    /** The buttons' names again, from the commands' chords now: the user's keys changed (TBAR-03). */
    void relabel() {
        Icons.relabel(rotate, Command.ROTATE_CLOCKWISE);
        Icons.name(info, Command.INFO);
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
}
