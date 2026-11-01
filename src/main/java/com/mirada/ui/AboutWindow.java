package com.mirada.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.AppInfo;
import org.jspecify.annotations.Nullable;

/**
 * About: what this build is and where it came from — Termina's window, with Mirada's extras: the
 * versions of the runtime and of the bundled codecs, which are what a bug report needs first.
 * Built the first time it is opened.
 */
final class AboutWindow {

    private final Stage stage = new Stage();

    AboutWindow(Path settingsFile, Consumer<String> openLink, String stylesheet) {
        stage.setTitle("About " + AppInfo.NAME);
        stage.setResizable(false);
        WindowIcon.applyTo(stage);

        HBox heading = new HBox(14);
        heading.setAlignment(Pos.CENTER_LEFT);
        Image icon = WindowIcon.image();
        if (icon != null) {
            ImageView logo = new ImageView(icon);
            logo.setFitWidth(72);
            logo.setFitHeight(72);
            logo.setSmooth(true);
            heading.getChildren().add(logo);
        }
        Label name = new Label(AppInfo.NAME);
        name.getStyleClass().add("about-name");
        // The snapshot suffix stays: it says at a glance that a build came off a working tree.
        Label version = new Label("Version " + AppInfo.VERSION);
        version.getStyleClass().add("about-version");
        Label tagline = new Label("A fast, folder-oriented image viewer");
        tagline.getStyleClass().add("about-version");
        heading.getChildren().add(new VBox(2, name, version, tagline));

        VBox details = new VBox(3);
        details.getStyleClass().add("about-details");
        if (!AppInfo.BUILD_TIME.isBlank()) details.getChildren().add(new Label("Built " + AppInfo.BUILD_TIME));
        details.getChildren().addAll(new Label(AppInfo.COPYRIGHT), new Label(AppInfo.LICENSE));

        Hyperlink homepage = new Hyperlink(AppInfo.HOMEPAGE);
        homepage.setOnAction(e -> openLink.accept(AppInfo.HOMEPAGE));

        Label runtime = new Label(runtime(
                System.getProperty("java.runtime.version"),
                System.getProperty("javafx.runtime.version"),
                codecVersions()));
        runtime.getStyleClass().add("about-path");
        runtime.setWrapText(true);

        Label settings = new Label("Settings: " + settingsFile);
        settings.getStyleClass().add("about-path");
        settings.setWrapText(true);

        Label credits = new Label("Decoding by JavaFX, TwelveMonkeys ImageIO, libheif, libde265, dav1d and libjxl;"
                + " EXIF by metadata-extractor; themes by AtlantaFX. Their licences are in NOTICE.");
        credits.getStyleClass().add("about-credits");
        credits.setWrapText(true);

        Button close = new Button("Close");
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(spacer, close);

        VBox root = new VBox(10, heading, details, homepage, runtime, settings, credits, footer);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("about");
        VBox.setMargin(footer, new Insets(8, 0, 0, 0));
        Scene scene = new Scene(root, 480, 440);
        scene.getStylesheets().add(stylesheet);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.setScene(scene);
    }

    /** Shows the window beside {@code from}, or brings it forward if it is already up. */
    void show(Window from) {
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - 480) / 2));
        stage.setY(from.getY() + 80);
        stage.show();
    }

    /** {@code Java 25.0.1 · JavaFX 27 · Codecs: libheif v1.23.7, ...}, leaving out what is unknown. */
    static String runtime(@Nullable String java, @Nullable String javafx, @Nullable String codecs) {
        StringBuilder out = new StringBuilder();
        if (java != null) out.append("Java ").append(java);
        if (javafx != null)
            out.append(out.isEmpty() ? "" : " · ").append("JavaFX ").append(javafx);
        out.append(out.isEmpty() ? "" : "\n")
                .append("Codecs: ")
                .append(codecs == null || codecs.isBlank() ? "the system's, or none" : codecs);
        return out.toString();
    }

    /** The bundle's VERSIONS.txt, joined on one line; null without a bundle. */
    private static @Nullable String codecVersions() {
        String folder = System.getProperty("mirada.codecs");
        if (folder == null || folder.isBlank()) return null;
        try {
            return String.join(", ", Files.readAllLines(Path.of(folder, "VERSIONS.txt"), StandardCharsets.UTF_8))
                    .replace(" (asm: true)", "")
                    .replace(" (asm: false)", " (no asm)");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
