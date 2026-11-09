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

import static com.mirada.i18n.Messages.tr;

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
    private final Button bookmark;
    private final Button note;
    private String noteText = "";

    /** @param run runs a command, the same as its key would */
    MarksBadge(StackPane canvas, Consumer<Command> run) {
        bookmark = Icons.button(Command.BOOKMARK, "osd-button", () -> run.accept(Command.BOOKMARK));
        bookmark.getStyleClass().add("mark-bookmark");
        note = Icons.button(Command.NOTE, "osd-button", () -> run.accept(Command.NOTE));
        Tooltip noteTip = note.getTooltip();
        noteTip.setWrapText(true);
        noteTip.setMaxWidth(360);
        noteTip.setShowDelay(Duration.millis(250));
        noteTip.setShowDuration(Duration.INDEFINITE);
        relabel();
        for (Button button : new Button[] {bookmark, note})
            button.managedProperty().bind(button.visibleProperty());
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

    /**
     * The buttons' names from the commands' chords now (TBAR-03): the bookmark's says what a click
     * does, takes it off; the note's is the note itself, as clicking it edits it.
     */
    void relabel() {
        Icons.name(bookmark, tr("marks.takeBookmarkOff"), Command.BOOKMARK.chord());
        Icons.name(note, tr("marks.note", noteText), Command.NOTE.chord());
    }

    /** Shows the marks of the image on screen; nothing at all while {@code hidden} (a slideshow). */
    void show(boolean bookmarked, String noteText, boolean hidden) {
        bookmark.setVisible(bookmarked);
        note.setVisible(!noteText.isEmpty());
        this.noteText = noteText;
        relabel();
        pill.setVisible(!hidden && (bookmarked || !noteText.isEmpty()));
    }
}
