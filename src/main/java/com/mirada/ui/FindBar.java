package com.mirada.ui;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * A find bar over a read-only text, the Debug Log's (Pauta's find bar template; the app template's
 * {@code FindBar}, after Editora's {@code ConsoleFindBar}). A node in the window, never a popup
 * (FIND-01). It searches as you type and shows the count, "No matches" when there are none, with no
 * beep and no dialog (FIND-02, FIND-10); Enter steps to the next match and Shift+Enter to the
 * previous one, wrapping (FIND-06); Escape or Close drops a pending search, clears the match and
 * gives the keyboard back to the log (FIND-07). A text area draws one selection only, so the active
 * match is the selection and the count says how many there are (FIND-05).
 *
 * <p>Previous, Next and Close stay out of the Tab order, since Shift+Enter, Enter and Escape in the
 * field do what they do; Match case stays in it, as no command toggles it (FIND-16). The field takes
 * the Emacs keymap's caret and editing chords while that keymap is in force, the platform's own
 * otherwise, and the find command's chord stays the bar's (FIND-09).
 *
 * <p>The search runs off the FX thread and only its latest request is applied (PERF-01, PERF-03); a
 * query that grows is narrowed from the matches it already has, never the whole log again (PERF-08,
 * {@link LogSearch}). A step asked for while a search runs waits for it. A Refresh or Clear changes
 * the log's text, and the open bar searches the new text.
 */
final class FindBar extends HBox {

    /** Searches the log, one virtual thread a search; a superseded one is interrupted. */
    private static final Executor SEARCHING = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("log-search-", 0).factory());

    private final TextArea target;
    private final LatestOnly searches = new LatestOnly(SEARCHING, Platform::runLater);
    /** The log's text, and how many times it has changed: what a search and a narrowing are for. */
    private String text;

    private long textVersion;
    /** The matches of the last search applied, or null before the first. */
    private @Nullable LogSearch result;
    /** A step asked for while a search was running: true forward, false back, null none. */
    private @Nullable Boolean pendingStep;

    private final TextField query = new TextField();
    private final CheckBox matchCase = new CheckBox(tr("find.matchCase"));
    private final Label count = new Label();
    /** The first key searches at once, then at most once a pause while typing goes on (PERF-06). */
    private final FxThrottle searching = new FxThrottle(BookmarksWindow.FILTER_PAUSE_MS, this::refine);

    /** Where the caret was when the bar opened: a search starts there (FIND-05). */
    private int anchor;

    FindBar(TextArea target) {
        this.target = target;
        this.text = target.getText();
        UiZoom.bind(spacingProperty(), 6);
        target.textProperty().addListener((o, was, now) -> {
            text = now == null ? "" : now;
            textVersion++;
            if (isOpen()) refine(); // the new text is searched, off the FX thread
        });
        getStyleClass().add("find-bar");
        setAlignment(Pos.CENTER_LEFT);
        query.getStyleClass().add("find-field");
        query.setPromptText(tr("find.prompt"));
        query.setAccessibleText(tr("find.prompt")); // the prompt goes when typing starts (ACC-01)
        query.setPrefColumnCount(24);
        count.getStyleClass().add("find-count");
        count.setMinWidth(Region.USE_PREF_SIZE);
        Button previous = new Button(tr("find.previous"));
        Button next = new Button(tr("find.next"));
        Button close = new Button(tr("find.close"));
        for (Button button : List.of(previous, next, close)) {
            button.getStyleClass().add("flat");
            button.setFocusTraversable(false); // Shift+Enter, Enter and Escape in the field (FIND-16)
        }
        previous.setOnAction(e -> step(false));
        next.setOnAction(e -> step(true));
        close.setOnAction(e -> close());
        query.textProperty().addListener((o, was, now) -> searching.request());
        matchCase.selectedProperty().addListener((o, was, now) -> searching.request());
        query.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                searching.flush();
                step(!e.isShiftDown());
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        TextInputKeys.install(query, Keybindings::emacs);
        getChildren().addAll(query, matchCase, count, previous, next, close);
        setVisible(false);
        setManaged(false);
    }

    /**
     * The bar's command, {@code log.find} (FIND-04): opens the bar, or, run while it is open,
     * steps to the next match with the focus kept in the field.
     */
    void openOrNext() {
        if (isOpen()) {
            step(true);
            query.requestFocus();
        } else {
            open();
        }
    }

    /** Shows the bar, seeding the query with a one-line selection when there is one. */
    void open() {
        // FIND-05: the search starts from where the caret was when the bar opened, so refining the
        // query never runs away down the text.
        anchor = target.getSelection().getStart();
        String seed = target.getSelectedText();
        if (!seed.isBlank() && !seed.contains("\n")) query.setText(seed);
        setVisible(true);
        setManaged(true);
        refine();
        query.requestFocus();
        query.selectAll();
    }

    boolean isOpen() {
        return isVisible();
    }

    /** Closes the bar: the pending search is dropped, the match cleared, and the log has the keyboard. */
    void close() {
        searching.cancel();
        searches.cancel();
        pendingStep = null;
        setVisible(false);
        setManaged(false);
        target.deselect();
        target.requestFocus();
    }

    TextField field() {
        return query;
    }

    CheckBox matchCase() {
        return matchCase;
    }

    String countText() {
        return count.getText();
    }

    /** Whether a search is waiting for the typing pause or running: the tests wait on it. */
    boolean searching() {
        return searching.pending() || searches.pending();
    }

    /**
     * Searches for the query as it is now: narrowed from the last search when the query only grew, on
     * the FX thread in O(matches); otherwise in the background, its result applied only if no later
     * request superseded it.
     */
    private void refine() {
        if (!isOpen()) return;
        String needle = query.getText();
        boolean matchCaseNow = matchCase.isSelected();
        LogSearch last = result;
        LogSearch narrowed = last == null ? null : last.narrow(textVersion, needle, matchCaseNow);
        if (narrowed != null || needle.isEmpty()) {
            searches.cancel();
            found(narrowed != null ? narrowed : LogSearch.of(text, textVersion, needle, matchCaseNow));
            return;
        }
        String searched = text;
        long version = textVersion;
        searches.submit(() -> LogSearch.of(searched, version, needle, matchCaseNow), this::found);
    }

    /** Applies a search: the match at or after the anchor, else the first, is what typing keeps in view. */
    private void found(LogSearch search) {
        result = search;
        List<int[]> matches = search.matches();
        if (matches.isEmpty()) {
            show(search, -1);
        } else {
            int at = 0;
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i)[0] >= anchor) {
                    at = i;
                    break;
                }
            }
            select(search, at);
        }
        Boolean step = pendingStep;
        pendingStep = null;
        if (step != null) step(step);
    }

    /** Whether the last search applied is for the text, the query and the case setting as they are now. */
    private boolean current(@Nullable LogSearch search) {
        return search != null
                && search.version() == textVersion
                && search.needle().equals(query.getText())
                && search.matchCase() == matchCase.isSelected();
    }

    /** The next match after the selection ({@code forward}) or the last one before it, wrapping around. */
    void step(boolean forward) {
        LogSearch search = result;
        if (search == null || searches.pending() || !current(search)) {
            pendingStep = forward; // taken up when the search lands
            if (!searches.pending()) refine();
            return;
        }
        List<int[]> matches = search.matches();
        if (matches.isEmpty()) {
            show(search, -1);
            return;
        }
        IndexRange selection = target.getSelection();
        select(search, stepFrom(matches, selection.getStart(), forward));
    }

    /**
     * The match a step lands on from a selection starting at {@code from}: the first after it going
     * forward, the last before it going back, wrapping past either end (FIND-06). Pure.
     */
    static int stepFrom(List<int[]> matches, int from, boolean forward) {
        if (forward) {
            for (int i = 0; i < matches.size(); i++) if (matches.get(i)[0] > from) return i;
            return 0;
        }
        for (int i = matches.size() - 1; i >= 0; i--) if (matches.get(i)[0] < from) return i;
        return matches.size() - 1;
    }

    private void select(LogSearch search, int at) {
        int[] match = search.matches().get(at);
        target.selectRange(match[0], match[1]);
        show(search, at);
    }

    private void show(LogSearch search, int at) {
        List<int[]> matches = search.matches();
        if (search.needle().isEmpty()) count.setText("");
        else if (matches.isEmpty()) count.setText(tr("find.none"));
        else {
            String total = search.capped() ? matches.size() + "+" : Integer.toString(matches.size());
            count.setText(tr("find.count", at + 1, total));
        }
    }
}
