package com.mirada.ui;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.mirada.image.ExifDetails;
import org.jspecify.annotations.Nullable;

/**
 * The image's properties, as GNOME Image Viewer's sidebar shows them: cards of label-over-value
 * rows. The user's own bookmarks and notes on the image and its folder first, when there are any; the folder (with a button that shows the image in it), then the image (size, format, file size), then the
 * file's dates, then the camera card when the file says what took it, and every metadata tag, folded.
 *
 * <p>Built the first time it is shown, so start-up never pays for it.
 */
final class InfoPanel {

    private final ScrollPane node;
    private final VBox cards = new VBox(14);
    private final Consumer<Command> run;
    private @Nullable ImageDetails shown;
    private Marks shownMarks = Marks.NONE;

    /**
     * What the user has kept on the image and its folder ({@link com.mirada.settings.Bookmarks}):
     * whether each is bookmarked, and its note, {@code ""} for none.
     */
    record Marks(boolean image, String note, boolean folder, String folderNote) {
        static final Marks NONE = new Marks(false, "", false, "");

        boolean any() {
            return image || folder || !note.isEmpty() || !folderNote.isEmpty();
        }
    }

    /** @param run runs a command: the folder's button and the notes' edit buttons are commands */
    InfoPanel(Consumer<Command> run) {
        this.run = run;
        cards.getStyleClass().add("info-cards");
        node = new ScrollPane(cards);
        node.getStyleClass().add("info-panel");
        node.setFitToWidth(true);
        node.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        node.setPrefWidth(320);
        node.setMinWidth(280);
        node.setFocusTraversable(false);
    }

    ScrollPane node() {
        return node;
    }

    void show(@Nullable ImageDetails details, Marks marks) {
        if (details != null && details.equals(shown) && marks.equals(shownMarks)) return;
        shown = details;
        shownMarks = marks;
        cards.getChildren().clear();
        if (details == null) {
            Label none = new Label("No image");
            none.getStyleClass().add("info-empty");
            cards.getChildren().add(none);
            return;
        }
        Locale locale = Locale.getDefault();
        ZoneId zone = ZoneId.systemDefault();

        // The folder in the file manager, with this image selected: Show in Folder, as the menus have it.
        Button open = Icons.button(Command.SHOW_IN_FOLDER, "info-action", () -> run.accept(Command.SHOW_IN_FOLDER));

        if (marks.any()) cards.getChildren().add(marksCard(marks));
        cards.getChildren()
                .add(card(row("Folder", details.folderName(), open), row("Position", details.position(), null)));
        cards.getChildren()
                .add(card(
                        row("Image Size", details.size(), null),
                        row("Image Format", details.format(), null),
                        row("File Size", ImageDetails.fileSize(details.bytes()), null)));
        cards.getChildren()
                .add(card(
                        row("File Created", ImageDetails.date(details.created(), locale, zone), null),
                        row("File Modified", ImageDetails.date(details.modified(), locale, zone), null)));
        List<ExifDetails.Row> camera = details.exif().camera();
        if (!camera.isEmpty()) {
            cards.getChildren()
                    .add(card(camera.stream()
                            .map(r -> row(r.label(), r.value(), null))
                            .toArray(HBox[]::new)));
        }
        if (!details.exif().all().isEmpty()) cards.getChildren().add(allMetadata(details.exif()));
    }

    /**
     * The bookmarks and notes: which of the image and its folder are bookmarked, then each note,
     * with a button that edits it.
     */
    private VBox marksCard(Marks marks) {
        List<HBox> rows = new ArrayList<>();
        @Nullable
        String bookmarked = marks.image() && marks.folder()
                ? "This image and its folder"
                : marks.image() ? "This image" : marks.folder() ? "Its folder" : null;
        if (bookmarked != null) rows.add(row("Bookmarked", bookmarked, null));
        if (!marks.note().isEmpty()) rows.add(row("Note", marks.note(), editButton(Command.NOTE)));
        if (!marks.folderNote().isEmpty())
            rows.add(row("Folder Note", marks.folderNote(), editButton(Command.FOLDER_NOTE)));
        VBox card = card(rows.toArray(HBox[]::new));
        card.getStyleClass().add("marks-card");
        return card;
    }

    private Button editButton(Command command) {
        return Icons.button(command, "info-action", () -> run.accept(command));
    }

    /**
     * Every tag, by directory, folded away until asked for: dozens of rows a photographer sometimes
     * needs and usually does not.
     */
    private static TitledPane allMetadata(ExifDetails exif) {
        VBox sections = new VBox(12);
        sections.getStyleClass().add("metadata-sections");
        for (ExifDetails.Section section : exif.all()) {
            Label title = new Label(section.title());
            title.getStyleClass().add("metadata-section");
            VBox rows = new VBox(4, title);
            for (ExifDetails.Row row : section.rows()) {
                Label name = new Label(row.label());
                name.getStyleClass().add("metadata-label");
                Label value = new Label(row.value());
                value.getStyleClass().add("metadata-value");
                value.setWrapText(true);
                rows.getChildren().add(new VBox(0, name, value));
            }
            sections.getChildren().add(rows);
        }
        TitledPane pane = new TitledPane("All Metadata", sections);
        pane.getStyleClass().add("metadata-pane");
        pane.setExpanded(false);
        pane.setAnimated(false);
        pane.setFocusTraversable(false);
        return pane;
    }

    private static VBox card(HBox... rows) {
        VBox card = new VBox(rows);
        card.getStyleClass().add("info-card");
        for (int i = 1; i < rows.length; i++) rows[i].getStyleClass().add("info-row-after");
        return card;
    }

    private static HBox row(String label, String value, @Nullable Button action) {
        Label name = new Label(label);
        name.getStyleClass().add("info-label");
        Label text = new Label(value);
        text.getStyleClass().add("info-value");
        text.setWrapText(true);
        VBox words = new VBox(2, name, text);
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = new HBox(8, words);
        row.getStyleClass().add("info-row");
        row.setAlignment(Pos.CENTER_LEFT);
        if (action != null) row.getChildren().add(action);
        return row;
    }
}
