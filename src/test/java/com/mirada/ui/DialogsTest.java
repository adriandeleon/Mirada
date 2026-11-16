package com.mirada.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every dialog Mirada builds, built and not shown: one helper styles them (WIN-04), one button order
 * on every OS with Cancel left of every action (WIN-11, CONF-05), Mirada's own words on every button
 * (CONF-14), and the red action never the default (CONF-13).
 */
@Tag("fx")
class DialogsTest {

    @BeforeAll
    static void start() throws Exception {
        FxTestSupport.start();
    }

    /** Each dialog Mirada shows, with what it is for. */
    private static List<Dialog<?>> everyDialog() {
        List<Dialog<?>> all = new ArrayList<>();
        for (Confirm.Question q : List.of(
                Confirm.discardEdit("a.jpg"), Confirm.replaceFile("a.jpg"), Confirm.importConfiguration("a.zip"))) {
            all.add(Confirm.dialog(q, null, null, false));
        }
        all.add(Confirm.dialog(Confirm.discardEdit("a.jpg"), null, null, true));
        all.add(NoteDialog.dialog(null, "Note", "a.jpg", ""));
        all.add(ViewerWindow.renameDialog("a.jpg", null));
        return all;
    }

    private static ButtonBar bar(Dialog<?> dialog) {
        return (ButtonBar) dialog.getDialogPane().lookup(".button-bar");
    }

    private static Button button(Dialog<?> dialog, ButtonType type) {
        return (Button) dialog.getDialogPane().lookupButton(type);
    }

    /** The buttons' words, left to right as the dialog draws them. */
    private static List<String> drawn(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.applyCss();
        pane.resize(600, 300);
        pane.layout();
        bar(dialog).layout();
        return pane.getButtonTypes().stream()
                .map(type -> button(dialog, type))
                .sorted(Comparator.comparingDouble(b -> b.localToScene(0, 0).getX()))
                .map(Button::getText)
                .toList();
    }

    @Test
    void everyDialogHasOneButtonOrderCancelFirst() throws Exception {
        String order = Dialogs.BUTTON_ORDER;
        for (char action : "UYNOA".toCharArray()) {
            assertTrue(order.indexOf('C') < order.indexOf(action), "Cancel before " + action);
        }
        for (Dialog<?> dialog : call(DialogsTest::everyDialog)) {
            assertEquals(order, call(() -> bar(dialog).getButtonOrder()), dialog.getTitle());
            assertTrue(
                    call(() -> dialog.getDialogPane().getScene().getStylesheets())
                            .contains(ViewerWindow.stylesheet()),
                    "styled: " + dialog.getTitle());
        }
    }

    @Test
    void theButtonsAreDrawnCancelThenTheActions() throws Exception {
        assertEquals(
                List.of("Cancel", "Replace"),
                call(() -> drawn(Confirm.dialog(Confirm.replaceFile("a.jpg"), null, null, false))));
        assertEquals(
                List.of("Cancel", "Save", "Discard"),
                call(() -> drawn(Confirm.dialog(Confirm.discardEdit("a.jpg"), null, null, true))));
        assertEquals(List.of("Cancel", "Rename"), call(() -> drawn(ViewerWindow.renameDialog("a.jpg", null))));
        assertEquals(List.of("Cancel", "Save"), call(() -> drawn(NoteDialog.dialog(null, "Note", "a.jpg", ""))));
    }

    /** CONF-14: no stock button anywhere, and a stock one would be worded from the catalog anyway. */
    @Test
    void everyButtonIsWordedFromTheCatalog() throws Exception {
        for (Dialog<?> dialog : call(DialogsTest::everyDialog)) {
            List<ButtonType> types = dialog.getDialogPane().getButtonTypes();
            assertTrue(types.size() >= 2, dialog.getTitle());
            for (ButtonType type : types) {
                assertFalse(Dialogs.STOCK.containsKey(type), "stock " + type + " in " + dialog.getTitle());
            }
        }
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN); // what JavaFX would word its OK and Cancel in
            TextInputDialog prompt = call(() -> Dialogs.styled(new TextInputDialog("x"), null));
            assertEquals("OK", call(() -> button(prompt, ButtonType.OK).getText()));
            assertEquals("Cancel", call(() -> button(prompt, ButtonType.CANCEL).getText()));
            fx(() -> prompt.getDialogPane().getButtonTypes().add(ButtonType.APPLY));
            assertEquals("Apply", call(() -> button(prompt, ButtonType.APPLY).getText()), "added later");
        } finally {
            Locale.setDefault(was);
        }
    }

    /**
     * CONF-13: Cancel is the one default where the only action is destructive, Save where saving is
     * an option; the red action never is, and Escape never answers it.
     */
    @Test
    void theRedActionIsNeverTheDefault() throws Exception {
        for (boolean withSave : List.of(false, true)) {
            Dialog<?> dialog = call(() -> Confirm.dialog(Confirm.discardEdit("a.jpg"), null, null, withSave));
            List<Button> defaults = new ArrayList<>();
            for (ButtonType type : dialog.getDialogPane().getButtonTypes()) {
                Button b = button(dialog, type);
                if (b.isDefaultButton()) defaults.add(b);
                if (b.getStyleClass().contains("danger")) {
                    assertFalse(b.isDefaultButton(), "the red action is not the default");
                    assertFalse(b.isCancelButton(), "Escape is not the red action");
                }
            }
            assertEquals(1, defaults.size(), "one default");
            assertEquals(withSave ? "Save" : "Cancel", defaults.getFirst().getText());
            if (withSave) assertTrue(defaults.getFirst().getStyleClass().contains("success"));
        }
    }

    /** WIN-04: every file in the app that builds a dialog hands it to {@link Dialogs#styled}. */
    @Test
    void everyDialogTheAppBuildsGoesThroughTheHelper() throws IOException {
        List<String> missed = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                boolean builds =
                        source.matches("(?s).*new (Alert|Dialog<[^>]*>|TextInputDialog|ChoiceDialog<[^>]*>)\\(.*");
                if (builds && !source.contains("Dialogs.styled(") && !file.endsWith("Dialogs.java"))
                    missed.add(file.toString());
            }
        }
        assertEquals(List.of(), missed);
    }
}
