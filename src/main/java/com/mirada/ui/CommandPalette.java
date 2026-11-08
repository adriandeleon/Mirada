package com.mirada.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

import org.jspecify.annotations.Nullable;

/**
 * Every command, findable by typing part of its name — Termina's palette, which is Editora's, in
 * shape and in behaviour, so the three apps' palettes look and rank alike.
 *
 * <p>The card has no title: the first row <em>is</em> the query, led by a chip naming the chord that
 * opened it, then eight rows of commands with the matched characters emboldened and the chord on the
 * right, a line describing the highlighted command, and a legend of the keys. The cursor only rests
 * on a command that can run; one that cannot (Next Image on the last image) is listed greyed with
 * the reason as its tooltip.
 *
 * <p>An in-scene overlay rather than a window of its own, so the image stays where it is and the
 * next key after the palette closes reaches the viewer again.
 *
 * <p>The same card finds an image of the folder by its name ({@link #showImages}, Go to Image),
 * as an editor's Ctrl+P finds a file: the rows are then the folder's file names, in the folder's
 * order until something is typed, the current one under the cursor.
 *
 * <p>The commands are {@link Command}'s; matching ({@link FuzzyMatch}), the keys ({@link
 * PaletteKeys}, {@link TextInputKeys}) and the highlighting ({@link MatchText}) are Termina's,
 * verbatim, so a ranking or navigation fix belongs in all three projects.
 */
final class CommandPalette {

    /** Height of one command row, and how many the list shows before it scrolls. */
    static final double ROW_HEIGHT = 36;

    /** Fixed, so the card does not jump as the match count changes under the typing. */
    static final int VISIBLE_ROWS = 8;

    /** What a match through a keyword loses against any match on the label: more than a label can score. */
    static final int KEYWORD_PENALTY = 1_000_000;

    /** A row: a command to run, or an image of the folder to go to. */
    sealed interface Row permits CommandRow, ImageRow {}

    record CommandRow(Command command) implements Row {}

    /** The folder's image at {@code index}, by its file name. */
    record ImageRow(int index, String name) implements Row {}

    private final StackPane host;
    private final Consumer<Command> run;

    private final TextField input = new TextField();
    private final Label prefixChip = new Label();
    private final ListView<Row> list = new ListView<>();
    private final ObservableList<Row> items = FXCollections.observableArrayList();
    private final Label desc = new Label();
    private final Label hint = new Label();
    private final VBox card = new VBox(6);
    private final Region backdrop = new Region();

    private List<Command> commands = List.of();
    /** The query of the current filter pass, read by the cells to highlight the matched characters. */
    private String currentQuery = "";
    /** Why a command cannot run right now, or null when it can. */
    private Function<Command, @Nullable String> unavailable = command -> null;
    /** The folder's file names while finding an image, in the folder's order; empty for commands. */
    private List<String> images = List.of();
    /** The image showing, which an empty query puts the cursor on. */
    private int currentImage;

    private IntConsumer goTo = index -> {};

    private Choices choices = new Choices(Command.GO_TO_IMAGE, "", "", "go", List.of(), -1, index -> "", index -> "");
    private boolean findingImages;
    /** The command whose chord the chip shows, for {@link #refreshKeys}. */
    private Command openedBy = Command.PALETTE;

    CommandPalette(StackPane host, Consumer<Command> run) {
        this.host = host;
        this.run = run;

        backdrop.getStyleClass().add("palette-backdrop");
        // Dismiss on press and consume it, so the click that closes the palette does not also
        // reach the image behind it.
        backdrop.setOnMousePressed(e -> {
            hide();
            e.consume();
        });

        prefixChip.getStyleClass().add("palette-prefix");
        input.getStyleClass().add("palette-input");
        input.setPromptText("Run command…");
        input.textProperty().addListener((o, was, now) -> filter(now));
        HBox inputRow = new HBox(9, prefixChip, input);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.getStyleClass().add("palette-input-row");
        HBox.setHgrow(input, Priority.ALWAYS);

        list.setItems(items);
        list.getStyleClass().add("palette-list");
        list.setPlaceholder(new Label("No matching commands"));
        // A whole number of rows: a fixed pixel height cuts the last row in half.
        list.setFixedCellSize(ROW_HEIGHT);
        list.setPrefHeight(listHeight(0));
        list.insetsProperty().addListener((o, was, in) -> list.setPrefHeight(listHeight(in.getTop() + in.getBottom())));
        list.setCellFactory(view -> new RowCell());
        // The query field keeps the keyboard; the list is driven from it.
        list.setFocusTraversable(false);
        list.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> input.requestFocus());
        list.getSelectionModel().selectedItemProperty().addListener((o, was, selected) -> {
            String d = description(selected);
            desc.setText(d.isEmpty() ? " " : d); // one line tall always, so the card never jitters
        });

        desc.getStyleClass().add("palette-desc");
        desc.setMaxWidth(Double.MAX_VALUE);
        desc.setTextOverrun(OverrunStyle.ELLIPSIS);
        desc.setText(" ");
        hint.getStyleClass().add("palette-hint");

        card.getStyleClass().add("command-palette");
        card.getChildren().addAll(inputRow, list, desc, hint);
        card.setPrefWidth(620);
        card.setMaxSize(620, Region.USE_PREF_SIZE); // hug the content; don't stretch to fill the overlay
        StackPane.setAlignment(card, Pos.TOP_CENTER);

        // On the card, so it runs before the ListView's own Up/Down and before the TextField
        // swallows anything. Enter and Escape would otherwise never reach us.
        card.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        // Emacs caret movement and editing in the query field, after the palette's own navigation.
        TextInputKeys.install(input);
    }

    /** The list height that shows exactly {@link #VISIBLE_ROWS} rows inside {@code insets} of chrome. */
    static double listHeight(double insets) {
        return VISIBLE_ROWS * ROW_HEIGHT + insets;
    }

    boolean isShowing() {
        return host.getChildren().contains(card);
    }

    /**
     * Shows the palette over {@code commands}.
     *
     * @param unavailable why a command cannot run right now, or null when it can; those are listed
     *     greyed, with the reason as their tooltip, and stepped over
     */
    void show(List<Command> commands, Function<Command, @Nullable String> unavailable) {
        if (isShowing()) return;
        this.commands = List.copyOf(commands);
        this.unavailable = unavailable;
        open(false, Command.PALETTE, "Run command…", "No matching commands");
    }

    /**
     * Shows the palette over the folder's images, by {@code names} in the folder's order, with the
     * cursor on {@code current}; choosing one hands its index to {@code goTo}.
     */
    void showImages(List<String> names, int current, IntConsumer goTo) {
        int size = names.size();
        showChoices(
                new Choices(
                        Command.GO_TO_IMAGE,
                        "Go to image…",
                        "No matching images",
                        "go",
                        names,
                        current,
                        index -> "Image " + (index + 1) + " of " + size,
                        index -> Integer.toString(index + 1)),
                goTo);
    }

    /**
     * A list to choose one from by name, other than the commands: the folder's images, the recent
     * folders. Each is ranked as a file name ({@link #orderedImages}).
     *
     * @param openedBy the command whose chord the chip shows
     * @param verb what Enter does, for the legend
     * @param current the one under the cursor before anything is typed, or -1 for the first
     * @param describe the line under the list for the one under the cursor
     * @param detail what stands on the right of each row
     */
    record Choices(
            Command openedBy,
            String prompt,
            String nothing,
            String verb,
            List<String> names,
            int current,
            IntFunction<String> describe,
            IntFunction<String> detail) {}

    void showChoices(Choices choices, IntConsumer choose) {
        if (isShowing()) return;
        this.choices = choices;
        this.images = List.copyOf(choices.names());
        this.currentImage = choices.current();
        this.goTo = choose;
        open(true, choices.openedBy(), choices.prompt(), choices.nothing(), choices.verb());
    }

    private void open(boolean findingImages, Command openedBy, String prompt, String nothing) {
        open(findingImages, openedBy, prompt, nothing, "run");
    }

    private void open(boolean findingImages, Command openedBy, String prompt, String nothing, String verb) {
        this.findingImages = findingImages;
        this.openedBy = openedBy;
        prefixChip.setText(openedBy.chord().getDisplayText());
        input.setPromptText(prompt);
        hint.setText(PaletteKeys.legend(PaletteKeys.hint(verb, "↵")));
        list.setPlaceholder(new Label(nothing));
        input.clear();
        filter("");
        host.getChildren().addAll(backdrop, card);
        input.requestFocus();
    }

    /**
     * Shows the keys in force again, while it is up: after the keymap or the user's keys changed in
     * the settings (PAL-09). The chip and each row's chord are read again; the legend is the
     * palette's own keys, which no keymap changes. Closed, there is nothing to do: every open reads
     * them.
     */
    void refreshKeys() {
        if (!isShowing()) return;
        prefixChip.setText(openedBy.chord().getDisplayText());
        list.refresh();
    }

    void hide() {
        if (!isShowing()) return;
        host.getChildren().removeAll(backdrop, card);
        images = List.of(); // a large folder's names are not kept while the palette is closed
    }

    private boolean isEnabled(Row row) {
        return !(row instanceof CommandRow(Command command)) || unavailable.apply(command) == null;
    }

    private String description(@Nullable Row row) {
        return switch (row) {
            case null -> "";
            case CommandRow(Command command) -> command.description();
            case ImageRow(int index, String _) -> choices.describe().apply(index);
        };
    }

    private void onKey(KeyEvent e) {
        PaletteKeys.Action action = PaletteKeys.action(e, true);
        switch (action) {
            case CANCEL -> hide();
            case ACCEPT -> runSelected();
            default -> {
                // The cursor only ever rests on an enabled command; greyed-out ones are stepped over.
                if (!PaletteKeys.navigate(list, action, this::isEnabled)) return; // typing: reaches the field
            }
        }
        e.consume();
    }

    private void runSelected() {
        Row row = list.getSelectionModel().getSelectedItem();
        if (row == null || !isEnabled(row)) return;
        // Hidden first: the command may open a dialog, and the palette must not sit over it.
        IntConsumer go = goTo;
        hide();
        switch (row) {
            case CommandRow(Command command) -> run.accept(command);
            case ImageRow(int index, String _) -> go.accept(index);
        }
    }

    private void filter(@Nullable String query) {
        currentQuery = query == null ? "" : query.trim();
        List<Row> rows = new ArrayList<>();
        if (findingImages) {
            for (int index : orderedImages(images, currentQuery)) rows.add(new ImageRow(index, images.get(index)));
        } else {
            for (Command command : orderedMatches(commands, currentQuery)) rows.add(new CommandRow(command));
        }
        items.setAll(rows);
        if (findingImages && currentQuery.isEmpty() && currentImage >= 0 && currentImage < rows.size()) {
            list.getSelectionModel().select(currentImage);
            list.scrollTo(Math.max(0, currentImage - VISIBLE_ROWS / 2));
        } else {
            selectFirstEnabled();
        }
    }

    /**
     * The images matching {@code query}, as indices into {@code names}: all of them in the folder's
     * order for an empty query, otherwise by fuzzy score, ties in the folder's order, so a burst of
     * IMG_0412, IMG_0413… keeps its sequence under the cursor.
     */
    static List<Integer> orderedImages(List<String> names, @Nullable String query) {
        String q = query == null ? "" : query.trim();
        List<Integer> indices = new ArrayList<>(names.size());
        if (q.isEmpty()) {
            for (int i = 0; i < names.size(); i++) indices.add(i);
            return indices;
        }
        int[] scores = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            scores[i] = FuzzyMatch.scoreOf(names.get(i), q);
            if (scores[i] != FuzzyMatch.NO_SCORE) indices.add(i);
        }
        indices.sort(
                Comparator.comparingInt((Integer i) -> scores[i]).reversed().thenComparingInt(i -> i));
        return indices;
    }

    /**
     * The command ordering. An empty query keeps {@link Command#inPalette()}'s order; a real query
     * uses the fuzzy score, then the shorter and then the alphabetical label, so equal-scoring rows
     * do not shuffle under the cursor between keystrokes.
     */
    static List<Command> orderedMatches(Collection<Command> commands, @Nullable String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>(commands);
        record Scored(Command command, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (Command command : commands) {
            int score = FuzzyMatch.scoreOf(command.label(), q);
            if (score == FuzzyMatch.NO_SCORE) score = keywordScore(command, q);
            if (score != FuzzyMatch.NO_SCORE) scored.add(new Scored(command, score));
        }
        scored.sort(Comparator.comparingInt(Scored::score)
                .reversed()
                .thenComparingInt((Scored s) -> s.command().label().length())
                .thenComparing(s -> s.command().label(), String.CASE_INSENSITIVE_ORDER));
        List<Command> matches = new ArrayList<>(scored.size());
        for (Scored s : scored) matches.add(s.command());
        return matches;
    }

    /**
     * The score of {@code command} for a query that did not match its label, through its keywords:
     * the query has to begin with (the start of) one, and is then matched against "keywords label".
     * Always below a label match.
     */
    static int keywordScore(Command command, String query) {
        String keywords = command.keywords();
        if (keywords.isEmpty()) return FuzzyMatch.NO_SCORE;
        int best = FuzzyMatch.NO_SCORE;
        for (String keyword : keywords.split(" ")) {
            int token = query.indexOf(' ');
            int head = Math.min(token < 0 ? query.length() : token, keyword.length());
            if (head == 0 || !keyword.regionMatches(true, 0, query, 0, head)) continue;
            int score = FuzzyMatch.scoreOf(keyword + " " + command.label(), query);
            if (score != FuzzyMatch.NO_SCORE) best = Math.max(best, score - KEYWORD_PENALTY);
        }
        return best;
    }

    /** Selects the first <em>enabled</em> result; the cursor never rests on a greyed command. */
    private void selectFirstEnabled() {
        for (int i = 0; i < items.size(); i++) {
            if (isEnabled(items.get(i))) {
                list.getSelectionModel().select(i);
                list.scrollTo(i);
                return;
            }
        }
        list.getSelectionModel().clearSelection();
    }

    /**
     * A row: the command or file name on the left with the match emboldened; a command's chord, or
     * an image's place in the folder, right-aligned.
     */
    private final class RowCell extends ListCell<Row> {
        private final TextFlow title = new TextFlow();
        private final Label chord = new Label();
        private final HBox layout = new HBox(10, title, spacer(), chord);

        private static Region spacer() {
            Region region = new Region();
            HBox.setHgrow(region, Priority.ALWAYS);
            return region;
        }

        RowCell() {
            chord.getStyleClass().add("keybinding");
            layout.setAlignment(Pos.CENTER_LEFT);
            setOnMouseClicked(e -> {
                Row item = getItem();
                if (e.getButton() != MouseButton.PRIMARY || isEmpty() || item == null || !isEnabled(item)) return;
                getListView().getSelectionModel().select(item);
                runSelected();
            });
        }

        @Override
        protected void updateItem(@Nullable Row item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            layout.getStyleClass().remove("palette-disabled");
            setTooltip(null); // cells are recycled: never leave a previous row's explanation behind
            if (item instanceof ImageRow(int index, String name)) {
                title.getChildren().setAll(MatchText.runs(name, currentQuery));
                chord.setText(choices.detail().apply(index));
                setGraphic(layout);
                return;
            }
            Command command = ((CommandRow) item).command();
            title.getChildren().setAll(MatchText.runs(command.label(), currentQuery));
            chord.setText(command.chord().getDisplayText());
            String why = unavailable.apply(command);
            if (why != null) {
                layout.getStyleClass().add("palette-disabled");
                Tooltip tip = new Tooltip(why);
                tip.setWrapText(true);
                tip.setMaxWidth(380);
                tip.getStyleClass().add("palette-disabled-tooltip");
                setTooltip(tip);
            }
            setGraphic(layout);
        }
    }
}
