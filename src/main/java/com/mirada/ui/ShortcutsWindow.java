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
        Label also = new Label(
                "Also: Space and Page Down for the next image, Shift+Space, Page Up and Backspace for the"
                        + " previous; F11 for full screen; F1 for the command palette, which finds every command by name.");
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
