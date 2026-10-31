package com.mirada.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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

    private final StackPane host;
    private final Consumer<Command> run;

    private final TextField input = new TextField();
    private final Label prefixChip = new Label();
    private final ListView<Command> list = new ListView<>();
    private final ObservableList<Command> items = FXCollections.observableArrayList();
    private final Label desc = new Label();
    private final Label hint = new Label();
    private final VBox card = new VBox(6);
    private final Region backdrop = new Region();

    private List<Command> commands = List.of();
    /** The query of the current filter pass, read by the cells to highlight the matched characters. */
    private String currentQuery = "";
    /** Why a command cannot run right now, or null when it can. */
    private Function<Command, @Nullable String> unavailable = command -> null;

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
        prefixChip.setText(Command.PALETTE.chord().getDisplayText());
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
        list.setCellFactory(view -> new CommandCell());
        // The query field keeps the keyboard; the list is driven from it.
        list.setFocusTraversable(false);
        list.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> input.requestFocus());
        list.getSelectionModel().selectedItemProperty().addListener((o, was, selected) -> {
            String d = selected == null ? "" : selected.description();
            desc.setText(d.isEmpty() ? " " : d); // one line tall always, so the card never jitters
        });

        desc.getStyleClass().add("palette-desc");
        desc.setMaxWidth(Double.MAX_VALUE);
        desc.setTextOverrun(OverrunStyle.ELLIPSIS);
        desc.setText(" ");
        hint.getStyleClass().add("palette-hint");
        hint.setText(PaletteKeys.legend(PaletteKeys.hint("run", "↵")));

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
        input.clear();
        filter("");
        host.getChildren().addAll(backdrop, card);
        input.requestFocus();
    }

    void hide() {
        if (!isShowing()) return;
        host.getChildren().removeAll(backdrop, card);
    }

    private boolean isEnabled(Command command) {
        return unavailable.apply(command) == null;
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
        Command command = list.getSelectionModel().getSelectedItem();
        if (command == null || !isEnabled(command)) return;
        // Hidden first: the command may open a dialog, and the palette must not sit over it.
        hide();
        run.accept(command);
    }

    private void filter(@Nullable String query) {
        currentQuery = query == null ? "" : query.trim();
        items.setAll(orderedMatches(commands, currentQuery));
        selectFirstEnabled();
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

    /** A row: the command on the left with the match emboldened, its chord right-aligned. */
    private final class CommandCell extends ListCell<Command> {
        private final TextFlow title = new TextFlow();
        private final Label chord = new Label();
        private final HBox layout = new HBox(10, title, spacer(), chord);

        private static Region spacer() {
            Region region = new Region();
            HBox.setHgrow(region, Priority.ALWAYS);
            return region;
        }

        CommandCell() {
            chord.getStyleClass().add("keybinding");
            layout.setAlignment(Pos.CENTER_LEFT);
            setOnMouseClicked(e -> {
                Command item = getItem();
                if (e.getButton() != MouseButton.PRIMARY || isEmpty() || item == null || !isEnabled(item)) return;
                getListView().getSelectionModel().select(item);
                runSelected();
            });
        }

        @Override
        protected void updateItem(@Nullable Command item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            title.getChildren().setAll(MatchText.runs(item.label(), currentQuery));
            chord.setText(item.chord().getDisplayText());
            layout.getStyleClass().remove("palette-disabled");
            setTooltip(null); // cells are recycled: never leave a previous row's explanation behind
            String why = unavailable.apply(item);
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
