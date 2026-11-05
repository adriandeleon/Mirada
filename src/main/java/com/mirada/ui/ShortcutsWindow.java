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
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Every command that has a key, grouped as {@link Command#group} groups them, with the key beside
 * it: read from {@link Command}, so it cannot fall out of step with what the keys do. Built the
 * first time it is opened.
 */
final class ShortcutsWindow {

    private static final double WIDTH = 460;

    private final Stage stage = new Stage();

    ShortcutsWindow(String stylesheet) {
        stage.setTitle("Keyboard Shortcuts");
        WindowIcon.applyTo(stage);

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
                Label key = new Label(command.chord().getDisplayText());
                key.getStyleClass().add("shortcut-key");
                GridPane.setHgrow(name, Priority.ALWAYS);
                name.setMaxWidth(Double.MAX_VALUE);
                rows.addRow(row++, name, key);
            }
            groups.getChildren().add(new VBox(8, heading, rows));
        }
        Label also = new Label(also(Command.MAC));
        also.setWrapText(true);
        also.getStyleClass().add("about-credits");
        groups.getChildren().add(also);

        ScrollPane scroll = new ScrollPane(groups);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Scene scene = new Scene(scroll, WIDTH, 620);
        scene.getStylesheets().add(stylesheet);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.setScene(scene);
        groups.setAlignment(Pos.TOP_LEFT);
    }

    /**
     * The keys that are not a command's shown chord: the second ways to do things, named as the
     * platform names keys ("Shift+Space" here, "⇧Space" on a Mac), as the rows above are.
     */
    static String also(boolean mac) {
        String steps = "Also: " + key(KeyCode.SPACE) + " and " + key(KeyCode.PAGE_DOWN) + " for the next image, "
                + key(KeyCode.SPACE, KeyCombination.SHIFT_DOWN) + ", " + key(KeyCode.PAGE_UP) + " and "
                + key(KeyCode.BACK_SPACE) + " for the previous; " + key(KeyCode.F1)
                + " for the command palette, which finds every command by name.";
        KeyCombination.Modifier cmd = KeyCombination.SHORTCUT_DOWN;
        return mac
                ? steps + " As in Preview: " + key(KeyCode.R, cmd) + " and " + key(KeyCode.L, cmd) + " to rotate, "
                        + key(KeyCode.I, cmd) + " for the properties, "
                        + key(KeyCode.F, KeyCombination.CONTROL_DOWN, cmd) + " for full screen."
                : steps + " " + key(KeyCode.F11) + " for full screen; " + key(KeyCode.R, cmd) + " and "
                        + key(KeyCode.R, cmd, KeyCombination.SHIFT_DOWN) + " to rotate.";
    }

    private static String key(KeyCode code, KeyCombination.Modifier... modifiers) {
        return new KeyCodeCombination(code, modifiers).getDisplayText();
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
}
