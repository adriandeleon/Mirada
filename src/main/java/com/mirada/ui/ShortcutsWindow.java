package com.mirada.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Every command that has a key, grouped as {@link Command#group} groups them, with its keys beside
 * it: read from the keys in force ({@link Keybindings#current}: the keymap and the user's own), so
 * it cannot fall out of step with what the keys do. Built the first time it is opened.
 */
final class ShortcutsWindow {

    private static final double WIDTH = 460;

    private final Stage stage = new Stage();

    private final ScrollPane scroll = new ScrollPane();

    ShortcutsWindow(String stylesheet) {
        stage.setTitle("Keyboard Shortcuts");
        WindowIcon.applyTo(stage);

        scroll.setContent(content());
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Scene scene = new Scene(scroll, WIDTH, 620);
        scene.getStylesheets().add(stylesheet);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.setScene(scene);
    }

    /** Reads the keys again, after they changed in the settings or the strip moved. */
    void refresh() {
        scroll.setContent(content());
    }

    private static VBox content() {
        VBox groups = new VBox(18);
        groups.setPadding(new Insets(20));
        groups.getStyleClass().add("shortcuts");
        for (Map.Entry<String, List<Command>> group : grouped().entrySet()) {
            Label heading = new Label(group.getKey());
            heading.getStyleClass().add("shortcuts-group");
            GridPane rows = new GridPane();
            rows.setHgap(24);
            rows.setVgap(6);
            int row = 0;
            for (Command command : group.getValue()) {
                Label name = new Label(command.label());
                Label key = new Label(keys(Keybindings.current().chords(command)));
                key.getStyleClass().add("shortcut-key");
                GridPane.setHgrow(name, Priority.ALWAYS);
                name.setMaxWidth(Double.MAX_VALUE);
                rows.addRow(row++, name, key);
            }
            groups.getChildren().add(new VBox(8, heading, rows));
        }
        groups.setAlignment(Pos.TOP_LEFT);
        return groups;
    }

    /** How many of a command's keys a row shows: the first ways, not every spelling of a key. */
    static final int KEYS_SHOWN = 3;

    /**
     * A command's keys as its row shows them: the first {@value #KEYS_SHOWN} that look different
     * ({@code plus} and the keypad's {@code add} are both "+"), as the platform names keys.
     */
    static String keys(List<KeyCombination> chords) {
        return chords.stream()
                .map(KeyCombination::getDisplayText)
                .distinct()
                .limit(KEYS_SHOWN)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** The commands with a key, by group, in the order {@link Command} declares them. */
    static Map<String, List<Command>> grouped() {
        Map<String, List<Command>> out = new LinkedHashMap<>();
        for (Command command : Command.values()) {
            if (command.chord() == KeyCombination.NO_MATCH) continue;
            out.computeIfAbsent(command.group(), g -> new ArrayList<>()).add(command);
        }
        return out;
    }

    /** Shows the window beside {@code from}, or brings it forward if it is already up. */
    void show(Window from) {
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - WIDTH) / 2));
        stage.setY(from.getY() + 60);
        stage.show();
    }

    /** Its stage, closed with the viewer's. */
    Stage stage() {
        return stage;
    }
}
