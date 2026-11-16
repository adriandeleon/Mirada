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

import static com.mirada.i18n.Messages.tr;

/**
 * The image's properties, as GNOME Image Viewer's sidebar shows them: cards of label-over-value
 * rows. The user's own bookmarks and notes on the image and its folder first, when there are any; the folder (with a button that shows the image in it), then the image (size, format, file size), then the
 * file's dates, then the camera card when the file says what took it, and every metadata tag, folded.
 *
 * <p>Built the first time it is shown, so start-up never pays for it.
 */
final class InfoPanel {

    private final ScrollPane node;
    private final VBox cards = UiZoom.vbox(14);
    private final Consumer<Command> run;
    private @Nullable ImageDetails shown;
    private Marks shownMarks = Marks.NONE;
    /** All Metadata on screen now, null when the image has none. */
    private @Nullable TitledPane metadata;
    /** Whether All Metadata is unfolded: the user's choice, kept from image to image. */
    private boolean metadataExpanded;

    /**
     * The most tags All Metadata lists (PERF-08): a file can carry thousands (a maker note, an XMP
     * history), each a few nodes; the count of the rest is said under the last.
     */
    static final int MAX_METADATA_ROWS = 300;

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
        UiZoom.bind(node.prefWidthProperty(), 320);
        UiZoom.bind(node.minWidthProperty(), 280);
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
        metadata = null;
        if (details == null) {
            Label none = new Label(tr("info.noImage"));
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
                .add(card(
                        row(tr("info.folder"), details.folderName(), open),
                        row(tr("info.position"), details.position(), null)));
        cards.getChildren()
                .add(card(
                        row(tr("info.imageSize"), details.size(), null),
                        row(tr("info.imageFormat"), details.format(), null),
                        row(tr("info.fileSize"), ImageDetails.fileSize(details.bytes()), null)));
        cards.getChildren()
                .add(card(
                        row(tr("info.fileCreated"), ImageDetails.date(details.created(), locale, zone), null),
                        row(tr("info.fileModified"), ImageDetails.date(details.modified(), locale, zone), null)));
        List<ExifDetails.Row> camera = details.exif().camera();
        if (!camera.isEmpty()) {
            cards.getChildren()
                    .add(card(camera.stream()
                            .map(r -> row(r.label(), r.value(), null))
                            .toArray(HBox[]::new)));
        }
        if (!details.exif().all().isEmpty()) {
            TitledPane pane = allMetadata(details.exif(), metadataExpanded);
            pane.expandedProperty().addListener((o, was, now) -> metadataExpanded = now);
            metadata = pane;
            cards.getChildren().add(pane);
        }
    }

    /** Whether All Metadata is unfolded now. */
    boolean metadataExpanded() {
        return metadataExpanded;
    }

    /**
     * Unfolds All Metadata, or folds it (the {@link Command#ALL_METADATA} command, KBD-15: the
     * keyboard's way to the folded card, which is out of the Tab order), and scrolls it into sight.
     */
    void setMetadataExpanded(boolean expanded) {
        metadataExpanded = expanded;
        TitledPane pane = metadata;
        if (pane == null) return;
        pane.setExpanded(expanded);
        if (!expanded) return;
        // Its title at the top of the sidebar, its first tags under it.
        node.layout();
        double room = cards.getHeight() - node.getViewportBounds().getHeight();
        if (room > 0) node.setVvalue(Math.min(1, pane.getBoundsInParent().getMinY() / room));
    }

    /**
     * The bookmarks and notes: which of the image and its folder are bookmarked, then each note,
     * with a button that edits it.
     */
    private VBox marksCard(Marks marks) {
        List<HBox> rows = new ArrayList<>();
        @Nullable
        String bookmarked = marks.image() && marks.folder()
                ? tr("info.bookmarkedBoth")
                : marks.image() ? tr("info.bookmarkedImage") : marks.folder() ? tr("info.bookmarkedFolder") : null;
        if (bookmarked != null) rows.add(row(tr("info.bookmarked"), bookmarked, null));
        if (!marks.note().isEmpty()) rows.add(row(tr("info.note"), marks.note(), editButton(Command.NOTE)));
        if (!marks.folderNote().isEmpty())
            rows.add(row(tr("info.folderNote"), marks.folderNote(), editButton(Command.FOLDER_NOTE)));
        VBox card = card(rows.toArray(HBox[]::new));
        card.getStyleClass().add("marks-card");
        return card;
    }

    private Button editButton(Command command) {
        return Icons.button(command, "info-action", () -> run.accept(command));
    }

    /**
     * Every tag, by directory, folded away until asked for: dozens of rows a photographer sometimes
     * needs and usually does not. Its rows are made the first time it is unfolded, and at most
     * {@link #MAX_METADATA_ROWS} of them.
     */
    private static TitledPane allMetadata(ExifDetails exif, boolean expanded) {
        TitledPane pane = new TitledPane();
        pane.setText(tr("info.allMetadata"));
        pane.getStyleClass().add("metadata-pane");
        pane.setAnimated(false);
        pane.setFocusTraversable(false);
        if (expanded) pane.setContent(metadataRows(exif));
        else {
            pane.expandedProperty().addListener((o, was, now) -> {
                if (now && pane.getContent() == null) pane.setContent(metadataRows(exif));
            });
        }
        pane.setExpanded(expanded);
        return pane;
    }

    private static VBox metadataRows(ExifDetails exif) {
        VBox sections = UiZoom.vbox(12);
        sections.getStyleClass().add("metadata-sections");
        List<ExifDetails.Section> shown = firstRows(exif.all(), MAX_METADATA_ROWS);
        for (ExifDetails.Section section : shown) {
            Label title = new Label(section.title());
            title.getStyleClass().add("metadata-section");
            VBox rows = UiZoom.vbox(4, title);
            for (ExifDetails.Row row : section.rows()) {
                Label name = new Label(row.label());
                name.getStyleClass().add("metadata-label");
                Label value = new Label(row.value());
                value.getStyleClass().add("metadata-value");
                value.setWrapText(true);
                rows.getChildren().add(UiZoom.vbox(0, name, value));
            }
            sections.getChildren().add(rows);
        }
        int left = rowCount(exif.all()) - rowCount(shown);
        if (left > 0) {
            Label more = new Label(tr("info.moreMetadata", left));
            more.getStyleClass().add("metadata-label");
            more.setWrapText(true);
            sections.getChildren().add(more);
        }
        return sections;
    }

    /**
     * The sections cut to their first {@code max} rows in all, in order; a section left with no row
     * is left out. Pure.
     */
    static List<ExifDetails.Section> firstRows(List<ExifDetails.Section> sections, int max) {
        List<ExifDetails.Section> kept = new ArrayList<>();
        int room = max;
        for (ExifDetails.Section section : sections) {
            if (room <= 0) break;
            List<ExifDetails.Row> rows = section.rows();
            if (rows.size() > room) rows = rows.subList(0, room);
            if (!rows.isEmpty()) kept.add(new ExifDetails.Section(section.title(), List.copyOf(rows)));
            room -= rows.size();
        }
        return kept;
    }

    private static int rowCount(List<ExifDetails.Section> sections) {
        int count = 0;
        for (ExifDetails.Section section : sections) count += section.rows().size();
        return count;
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
        VBox words = UiZoom.vbox(2, name, text);
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = UiZoom.hbox(8, words);
        row.getStyleClass().add("info-row");
        row.setAlignment(Pos.CENTER_LEFT);
        if (action != null) row.getChildren().add(action);
        return row;
    }
}
