package com.mirada.ui;

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
import com.mirada.update.UpdateCoordinator;

import static com.mirada.i18n.Messages.tr;

/**
 * About: what this build is and where it came from — Termina's window, with Mirada's extras: the
 * versions of the runtime and of the bundled codecs, which are what a bug report needs first.
 * Built the first time it is opened.
 */
final class AboutWindow {

    private static final double WIDTH = 480;

    private final Stage stage = new Stage();

    private final UpdateCoordinator updates;
    private final Label updateLine = new Label();
    private final Hyperlink updateLink = new Hyperlink(tr("about.openReleasePage"));
    private final Runnable refreshUpdate = this::refreshUpdate;

    /** @param copyDetails runs Copy Details ({@code help.copyDetails}), the command the button is for */
    AboutWindow(
            Path settingsFile,
            Consumer<String> openLink,
            UpdateCoordinator updates,
            Runnable copyDetails,
            String stylesheet) {
        this.updates = updates;
        stage.setTitle(tr("about.title"));
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
        Label version = new Label(tr("about.version", AppInfo.VERSION));
        version.getStyleClass().add("about-version");
        Label tagline = new Label(tr("about.tagline"));
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
        Hyperlink releaseNotes = new Hyperlink(tr("about.releaseNotes"));
        releaseNotes.setOnAction(e -> openLink.accept(AppInfo.releaseNotesUrl()));
        VBox links = new VBox(0, homepage, releaseNotes, pauta);

        // The last update check's answer, and nothing claimed before one (ABOUT-02, ABOUT-04, WIN-07).
        updateLine.getStyleClass().add("about-update");
        updateLink.setOnAction(e -> {
            String page = updates.releasePage();
            if (page != null) openLink.accept(page);
        });
        HBox update = new HBox(4, updateLine, updateLink);
        update.setAlignment(Pos.CENTER_LEFT);

        // Version, build, commit and Pauta edition first, then the runtime, the OS and the settings
        // file: what a bug report needs, and what Copy details copies (ABOUT-02, ABOUT-03).
        String environment = AppInfo.environment(AppInfo.runtime(), settingsFile);
        Label environmentBlock = new Label(environment);
        environmentBlock.getStyleClass().add("about-path");
        environmentBlock.setWrapText(true);
        Button copy = new Button(tr("about.copyDetails"));
        // The command copies what this block shows: the same AppInfo.environment.
        copy.setOnAction(e -> {
            copyDetails.run();
            copy.setText(tr("about.copied"));
        });
        stage.setOnHidden(e -> {
            copy.setText(tr("about.copyDetails"));
            // Listening only while open: a closed About keeps nothing alive.
            updates.removeListener(refreshUpdate);
        });

        Label credits = new Label(tr("about.credits"));
        credits.getStyleClass().add("about-credits");
        credits.setWrapText(true);

        Button close = new Button(tr("about.close"));
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(spacer, close);

        VBox root = new VBox(10, heading, details, links, update, environmentBlock, copy, credits, footer);
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
        refreshUpdate();
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - WIDTH) / 2));
        stage.setY(from.getY() + 80);
        updates.addListener(refreshUpdate);
        stage.show();
    }

    /** {@code Built to Pauta 4.0.1}: the link's text, which names the edition and never "latest". */
    static String pautaLink(String edition) {
        return tr("about.pautaLink", edition);
    }

    private void refreshUpdate() {
        updateLine.setText(updates.summary());
        boolean available = updates.state() == UpdateCoordinator.State.AVAILABLE;
        updateLink.setVisible(available);
        updateLink.setManaged(available);
    }

    /** Its stage, closed with the viewer's. */
    Stage stage() {
        return stage;
    }
}
