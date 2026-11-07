package com.mirada.ui;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * The image's own marks near its top-left corner, in a floating control's pill, right of where the
 * full-screen button floats ({@link OnScreenControls}) so the two never cover each other: a bookmark while it
 * is bookmarked, a note while it has one, its words in the tooltip. Unlike the floating controls it
 * stays, as it says something about the image rather than offering to do something to it; clicked,
 * the bookmark comes off and the note opens to edit. Built the first time an image has a mark, so a
 * user who never makes one never pays for it.
 */
final class MarksBadge {

    private final HBox pill;
    private final Button bookmark = new Button();
    private final Button note = new Button();
    private final Tooltip noteTip = new Tooltip();

    /** @param run runs a command, the same as its key would */
    MarksBadge(StackPane canvas, Consumer<Command> run) {
        bookmark.setGraphic(Icons.of(Icons.BOOKMARK));
        bookmark.setTooltip(new Tooltip(
                "Bookmarked: click to take it off (" + Command.BOOKMARK.chord().getDisplayText() + ")"));
        bookmark.setAccessibleText("Bookmarked");
        bookmark.getStyleClass().add("mark-bookmark");
        bookmark.setOnAction(e -> run.accept(Command.BOOKMARK));
        note.setGraphic(Icons.of(Icons.NOTE));
        noteTip.setWrapText(true);
        noteTip.setMaxWidth(360);
        noteTip.setShowDelay(Duration.millis(250));
        noteTip.setShowDuration(Duration.INDEFINITE);
        note.setTooltip(noteTip);
        note.setOnAction(e -> run.accept(Command.NOTE));
        for (Button button : new Button[] {bookmark, note}) {
            button.getStyleClass().addAll("osd-button", "flat");
            button.setFocusTraversable(false);
            button.managedProperty().bind(button.visibleProperty());
        }
        pill = new HBox(2, bookmark, note);
        pill.getStyleClass().addAll("osd", "marks-badge");
        pill.setAlignment(Pos.CENTER);
        pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.setPickOnBounds(false);
        StackPane.setAlignment(pill, Pos.TOP_LEFT);
        // 18 from the edge, past the full-screen pill (a 34-pixel button in 6 of padding), 8 apart.
        StackPane.setMargin(pill, new Insets(18, 0, 0, 18 + 46 + 8));
        canvas.getChildren().add(pill);
    }

    /** Shows the marks of the image on screen; nothing at all while {@code hidden} (a slideshow). */
    void show(boolean bookmarked, String noteText, boolean hidden) {
        bookmark.setVisible(bookmarked);
        note.setVisible(!noteText.isEmpty());
        noteTip.setText(noteText + "\n\nClick to edit (" + Command.NOTE.chord().getDisplayText() + ")");
        note.setAccessibleText("Note: " + noteText);
        pill.setVisible(!hidden && (bookmarked || !noteText.isEmpty()));
    }
}
