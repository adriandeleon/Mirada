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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.AppInfo;
import com.mirada.image.NativeLibraries;
import org.jspecify.annotations.Nullable;

/**
 * About: what this build is and where it came from — Termina's window, with Mirada's extras: the
 * versions of the runtime and of the bundled codecs, which are what a bug report needs first.
 * Built the first time it is opened.
 */
final class AboutWindow {

    private static final double WIDTH = 480;
    private static final String COPY_DETAILS = "Copy details";
    private static final String COPIED = "Copied";

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
        details.getChildren().addAll(new Label(AppInfo.COPYRIGHT), new Label(AppInfo.LICENSE));

        Hyperlink homepage = new Hyperlink(AppInfo.HOMEPAGE);
        homepage.setOnAction(e -> openLink.accept(AppInfo.HOMEPAGE));
        // The edition Mirada meets, its numbered page, never latest/ (ABOUT-05, ABOUT-06).
        Hyperlink pauta = new Hyperlink(pautaLink(AppInfo.PAUTA_EDITION));
        pauta.setOnAction(e -> openLink.accept(AppInfo.pautaUrl()));
        // This build's release notes, or the changelog for a snapshot (DOCS-05).
        Hyperlink releaseNotes = new Hyperlink("Release notes");
        releaseNotes.setOnAction(e -> openLink.accept(AppInfo.releaseNotesUrl()));
        VBox links = new VBox(0, homepage, releaseNotes, pauta);

        // Version, build, commit and Pauta edition first, then the runtime, the OS and the settings
        // file: what a bug report needs, and what Copy details copies (ABOUT-02, ABOUT-03).
        String environment = AppInfo.environment(
                runtime(
                        System.getProperty("java.runtime.version"),
                        System.getProperty("javafx.runtime.version"),
                        codecVersions()),
                settingsFile);
        Label environmentBlock = new Label(environment);
        environmentBlock.getStyleClass().add("about-path");
        environmentBlock.setWrapText(true);
        Button copy = new Button(COPY_DETAILS);
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(environment);
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText(COPIED);
        });
        stage.setOnHidden(e -> copy.setText(COPY_DETAILS));

        Label credits =
                new Label("Decoding by JavaFX, libjpeg-turbo, libwebp, TwelveMonkeys ImageIO, libheif, libde265,"
                        + " dav1d and libjxl; compiled by GraalVM Native Image, with JavaFX linked in by StaticFX;"
                        + " EXIF by metadata-extractor; themes by AtlantaFX. Their licences are in NOTICE.");
        credits.getStyleClass().add("about-credits");
        credits.setWrapText(true);

        Button close = new Button("Close");
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(spacer, close);

        VBox root = new VBox(10, heading, details, links, environmentBlock, copy, credits, footer);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("about");
        VBox.setMargin(footer, new Insets(8, 0, 0, 0));
        root.setPrefWidth(WIDTH);
        Scene scene = new Scene(root);
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
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - WIDTH) / 2));
        stage.setY(from.getY() + 80);
        stage.show();
    }

    /** {@code Built to Pauta 4.0.1}: the link's text, which names the edition and never "latest". */
    static String pautaLink(String edition) {
        return "Built to Pauta " + edition;
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
        Path versions = NativeLibraries.versionsFile();
        if (versions == null) return null;
        try {
            return String.join(", ", Files.readAllLines(versions, StandardCharsets.UTF_8))
                    .replace(" (asm: true)", "")
                    .replace(" (asm: false)", " (no asm)")
                    .replace(" (simd: ON)", "")
                    .replace(" (simd: OFF)", " (no SIMD)");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
