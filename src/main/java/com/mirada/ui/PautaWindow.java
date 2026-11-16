package com.mirada.ui;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.AppInfo;
import com.mirada.PautaConformance;
import com.mirada.PautaConformance.Conformance;
import com.mirada.PautaConformance.Entry;
import com.mirada.PautaConformance.Kind;

import static com.mirada.i18n.Messages.tr;

/**
 * The Pauta window (PWIN-01 to PWIN-08), the app template's, built like {@link AboutWindow}: the
 * edition, the conformance and every exception Mirada records. It reads the exceptions through
 * {@link PautaConformance}, the edition from {@link AppInfo}, and takes the conformance from the
 * same helper as About (PWIN-03): it holds no rule ID, edition or conformance name of its own.
 * Built the first time it is opened.
 */
final class PautaWindow {

    private static final double WIDTH = 620;

    private final Stage stage = new Stage();
    private final VBox list = UiZoom.vbox(4);
    private final Button copy = new Button(tr("pauta.copy"));

    /**
     * @param run runs a command: the edition's link, the docs link and Copy as text are commands
     *     (PWIN-04, PWIN-06, PWIN-07, KBD-01)
     * @param openLink opens a rule's own address in this edition, and an exception's issue
     */
    PautaWindow(List<Entry> exceptions, Consumer<Command> run, Consumer<String> openLink, String stylesheet) {
        Conformance conformance = PautaConformance.of(exceptions);
        stage.setTitle(tr("pauta.title"));
        WindowIcon.applyTo(stage);

        Label edition = new Label(tr("pauta.edition", AppInfo.PAUTA_EDITION));
        edition.getStyleClass().add("about-name");
        Label pill = new Label(conformanceName(conformance));
        pill.getStyleClass().addAll("pauta-conformance", conformance.key());
        Label meaning = new Label(meaning(conformance, exceptions));
        meaning.getStyleClass().add("pauta-meaning");
        meaning.setWrapText(true);
        VBox titles = UiZoom.vbox(6, edition, new HBox(pill), meaning);
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox heading = UiZoom.hbox(14);
        heading.setAlignment(Pos.CENTER_LEFT);
        Image icon = WindowIcon.image();
        if (icon != null) {
            ImageView logo = new ImageView(icon);
            UiZoom.bind(logo.fitWidthProperty(), 64);
            UiZoom.bind(logo.fitHeightProperty(), 64);
            logo.setSmooth(true);
            heading.getChildren().add(logo);
        }
        heading.getChildren().add(titles);

        VBox root = UiZoom.vbox(12, heading);
        String counts = counts(exceptions);
        if (!counts.isEmpty()) {
            Label countsLabel = new Label(counts);
            countsLabel.getStyleClass().add("pauta-counts");
            root.getChildren().add(countsLabel);
        }
        fill(PautaConformance.sorted(exceptions), openLink);
        UiZoom.pad(list, 0, 8, 0, 0);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(340);
        scroll.getStyleClass().add("pauta-list");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        // The edition's own address and the website's page, each a command (PWIN-04, PWIN-07).
        VBox links = new VBox(
                0,
                link(tr("pauta.rules", AppInfo.PAUTA_EDITION), Command.PAUTA_RULES, run),
                link(tr("pauta.docs"), Command.PAUTA_DOCS, run));
        // In the content, not a button bar, so pressing it cannot close the window (PWIN-06).
        copy.setOnAction(e -> {
            run.accept(Command.COPY_PAUTA);
            copy.setText(tr("pauta.copied"));
        });
        stage.setOnHidden(e -> copy.setText(tr("pauta.copy")));

        Button close = new Button(tr("pauta.close"));
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = UiZoom.hbox(8, copy, spacer, close);
        footer.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().addAll(links, footer);
        UiZoom.pad(root, 20);
        root.getStyleClass().add("pauta");
        UiZoom.bind(root.prefWidthProperty(), WIDTH);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(stylesheet);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.setScene(scene);
    }

    /** One heading per kind present, then its rows (PWIN-05's order); one line when there are none (PWIN-08). */
    private void fill(List<Entry> sorted, Consumer<String> openLink) {
        if (sorted.isEmpty()) {
            Label none = new Label(none());
            none.setWrapText(true);
            list.getChildren().add(none);
            return;
        }
        Kind group = null;
        for (Entry entry : sorted) {
            if (entry.kind() != group) {
                group = entry.kind();
                Label heading = new Label(tr("pauta.kind." + group.key()));
                heading.getStyleClass().add("pauta-group");
                list.getChildren().add(heading);
            }
            list.getChildren().add(row(entry, openLink));
        }
    }

    /** The rule's ID linked to its address in this edition, its level, the reason and the entry's link. */
    private static Node row(Entry entry, Consumer<String> openLink) {
        Hyperlink id = new Hyperlink(entry.rule());
        id.getStyleClass().add("pauta-rule");
        id.setOnAction(e -> openLink.accept(entry.url()));
        UiZoom.bind(id.minWidthProperty(), 84);
        Label level = new Label(entry.level());
        level.getStyleClass().add("pauta-level");
        UiZoom.bind(level.minWidthProperty(), 60);
        TextFlow reason = new TextFlow(new Text(entry.reason()));
        reason.getStyleClass().add("pauta-reason");
        String target = entry.link();
        if (target != null) {
            Hyperlink link = new Hyperlink(linkText(target));
            link.setOnAction(e -> openLink.accept(target));
            reason.getChildren().addAll(new Text(" "), link);
        }
        HBox.setHgrow(reason, Priority.ALWAYS);
        HBox row = UiZoom.hbox(8, id, level, reason);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("pauta-row");
        return row;
    }

    private static Hyperlink link(String text, Command command, Consumer<Command> run) {
        Hyperlink link = new Hyperlink(text);
        link.setOnAction(e -> run.accept(command));
        return link;
    }

    /** {@code #212} for an issue, {@code Decision 0007} for a decision record, else "Details". */
    static String linkText(String link) {
        String issue = PautaConformance.issueNumber(link);
        if (issue != null) return issue;
        String decision = PautaConformance.decisionNumber(link);
        if (decision != null) return tr("pauta.decision", decision);
        return tr("pauta.link");
    }

    /** Complete, Core or Partial, in the active language. */
    static String conformanceName(Conformance conformance) {
        return tr("pauta.conformance." + conformance.key());
    }

    /** The sentence under the pill: what the conformance means for Mirada. */
    static String meaning(Conformance conformance, List<Entry> exceptions) {
        return switch (conformance) {
            case COMPLETE -> tr("pauta.conformance.complete.desc", AppInfo.NAME);
            case CORE ->
                tr("pauta.conformance.core.desc", AppInfo.NAME, PautaConformance.pending(exceptions, "SHOULD"));
            case PARTIAL ->
                tr("pauta.conformance.partial.desc", AppInfo.NAME, PautaConformance.pending(exceptions, "MUST"));
        };
    }

    /** "3 pending · 1 deviation"; a kind with none is left out, and no exceptions is empty. */
    static String counts(List<Entry> exceptions) {
        StringJoiner line = new StringJoiner(" · ");
        for (Map.Entry<Kind, Integer> count :
                PautaConformance.counts(exceptions).entrySet()) {
            line.add(tr("pauta.count." + count.getKey().key(), count.getValue()));
        }
        return line.toString();
    }

    /** The one line where the list would be when there are no exceptions (PWIN-08). */
    static String none() {
        return tr("pauta.none", AppInfo.NAME, AppInfo.PAUTA_EDITION);
    }

    /**
     * Copy as text (PWIN-06): the edition, the conformance and the list as Markdown, one line per
     * exception, then the edition's address.
     */
    static String markdown(List<Entry> exceptions) {
        Conformance conformance = PautaConformance.of(exceptions);
        StringBuilder text = new StringBuilder();
        text.append("# ")
                .append(tr("pauta.edition", AppInfo.PAUTA_EDITION))
                .append(": ")
                .append(conformanceName(conformance))
                .append("\n\n")
                .append(meaning(conformance, exceptions))
                .append('\n');
        if (exceptions.isEmpty()) text.append('\n').append(none()).append('\n');
        Kind group = null;
        for (Entry entry : PautaConformance.sorted(exceptions)) {
            if (entry.kind() != group) {
                group = entry.kind();
                text.append("\n## ").append(tr("pauta.kind." + group.key())).append("\n\n");
            }
            text.append("- [")
                    .append(entry.rule())
                    .append("](")
                    .append(entry.url())
                    .append(") ")
                    .append(entry.level())
                    .append(": ")
                    .append(entry.reason());
            String link = entry.link();
            if (link != null)
                text.append(" [")
                        .append(linkText(link))
                        .append("](")
                        .append(link)
                        .append(')');
            text.append('\n');
        }
        text.append('\n').append(AppInfo.pautaUrl()).append('\n');
        return text.toString();
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

    VBox list() {
        return list;
    }

    Button copyButton() {
        return copy;
    }
}
