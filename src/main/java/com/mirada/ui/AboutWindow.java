package com.mirada.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

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
import com.mirada.PautaConformance;
import com.mirada.PautaConformance.Conformance;
import com.mirada.update.UpdateCoordinator;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * About: what this build is and where it came from — Termina's window, with Mirada's extras: the
 * versions of the runtime and of the bundled codecs, which are what a bug report needs first.
 * Every link and button runs a command (KBD-01), so the palette reaches each of them too. Built the
 * first time it is opened.
 */
final class AboutWindow {

    private static final double WIDTH = 480;

    private final Stage stage = new Stage();

    private final UpdateCoordinator updates;
    private final Label updateLine = new Label();
    private final Hyperlink updateLink = new Hyperlink(tr("about.openReleasePage"));
    private final Runnable refreshUpdate = this::refreshUpdate;

    /** @param run runs a command: what each link and button does (KBD-01) */
    AboutWindow(Path settingsFile, Consumer<Command> run, UpdateCoordinator updates, String stylesheet) {
        this.updates = updates;
        stage.setTitle(tr("about.title"));
        stage.setResizable(false);
        WindowIcon.applyTo(stage);

        HBox heading = UiZoom.hbox(14);
        heading.setAlignment(Pos.CENTER_LEFT);
        Image icon = WindowIcon.image();
        if (icon != null) {
            ImageView logo = new ImageView(icon);
            UiZoom.bind(logo.fitWidthProperty(), 72);
            UiZoom.bind(logo.fitHeightProperty(), 72);
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
        heading.getChildren().add(UiZoom.vbox(2, name, version, tagline));

        VBox details = UiZoom.vbox(3);
        details.getStyleClass().add("about-details");
        details.getChildren().addAll(new Label(AppInfo.COPYRIGHT), new Label(AppInfo.LICENSE));

        // The home page, the releases (which hold the source too) and this build's notes (ABOUT-02,
        // DOCS-05); then the edition Mirada works toward or is built to, its numbered page, and the
        // conformance, which opens the Pauta window (ABOUT-05, ABOUT-06, ABOUT-09, PWIN-04).
        Conformance conformance = PautaConformance.conformance();
        VBox links = new VBox(
                0,
                link(AppInfo.HOMEPAGE, Command.HOME_PAGE, run),
                link(tr("about.releases"), Command.RELEASES, run),
                link(tr("about.releaseNotes"), Command.RELEASE_NOTES, run),
                link(pautaLink(conformance, AppInfo.PAUTA_EDITION), Command.PAUTA_RULES, run),
                link(conformanceLink(conformance), Command.PAUTA, run));

        // The last update check's answer, and nothing claimed before one (ABOUT-02, ABOUT-04, WIN-07),
        // in its state's colour (ABOUT-10).
        updateLine.getStyleClass().add("about-update");
        updateLink.setOnAction(e -> run.accept(Command.OPEN_RELEASE));
        HBox update = UiZoom.hbox(4, updateLine, updateLink);
        update.setAlignment(Pos.CENTER_LEFT);

        // Version, build, commit, Pauta edition and conformance first, then the runtime, the OS and
        // the settings file: what a bug report needs, and what Copy details copies (ABOUT-02,
        // ABOUT-03, ABOUT-05, ABOUT-09).
        String environment = AppInfo.environment(AppInfo.runtime(), settingsFile);
        Label environmentBlock = new Label(environment);
        environmentBlock.getStyleClass().add("about-path");
        environmentBlock.setWrapText(true);
        Button copy = new Button(tr("about.copyDetails"));
        // The command copies what this block shows: the same AppInfo.environment.
        copy.setOnAction(e -> {
            run.accept(Command.COPY_DETAILS);
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

        VBox root = UiZoom.vbox(10, heading, details, links, update, environmentBlock, copy, credits, footer);
        UiZoom.pad(root, 20);
        root.getStyleClass().add("about");
        UiZoom.vboxMargin(footer, 8, 0, 0, 0);
        UiZoom.bind(root.prefWidthProperty(), WIDTH);
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

    private static Hyperlink link(String text, Command command, Consumer<Command> run) {
        Hyperlink link = new Hyperlink(text);
        link.setOnAction(e -> run.accept(command));
        return link;
    }

    /**
     * {@code Built to Pauta 6.1.0}, or at Partial {@code Working toward Pauta 6.1.0}: Mirada does not
     * claim an edition whose every MUST it does not meet yet (ABOUT-07, ABOUT-09). Never "latest".
     */
    static String pautaLink(Conformance conformance, String edition) {
        return tr(conformance == Conformance.PARTIAL ? "about.pautaWorkingToward" : "about.pautaLink", edition);
    }

    /** {@code Pauta conformance: Partial}, the helper's answer in the active language (ABOUT-09). */
    static String conformanceLink(Conformance conformance) {
        return tr("about.pautaConformance", PautaWindow.conformanceName(conformance));
    }

    /** The update line's style for each answer of the check (ABOUT-10); null, so muted, before one. */
    static @Nullable String updateStyle(UpdateCoordinator.State state) {
        return switch (state) {
            case AVAILABLE -> "update-available";
            case UP_TO_DATE -> "up-to-date";
            case FAILED -> "update-failed";
            case NOT_CHECKED, CHECKING -> null;
        };
    }

    private static final List<String> UPDATE_STYLES = List.of("update-available", "up-to-date", "update-failed");

    private void refreshUpdate() {
        updateLine.setText(updates.summary());
        UpdateCoordinator.State state = updates.state();
        updateLine.getStyleClass().removeAll(UPDATE_STYLES);
        String style = updateStyle(state);
        if (style != null) updateLine.getStyleClass().add(style);
        boolean available = state == UpdateCoordinator.State.AVAILABLE;
        updateLink.setVisible(available);
        updateLink.setManaged(available);
    }

    /** Its stage, closed with the viewer's. */
    Stage stage() {
        return stage;
    }
}
