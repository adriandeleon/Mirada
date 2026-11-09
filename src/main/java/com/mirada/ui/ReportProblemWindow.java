package com.mirada.ui;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
import com.mirada.diagnostics.DebugLog;
import com.mirada.diagnostics.MessageLog;
import com.mirada.report.ProblemReport;
import com.mirada.report.Redactor;
import com.mirada.settings.Settings;

import static com.mirada.i18n.Messages.tr;

/**
 * Help › Report a Problem: a report with private details taken out, for the user to read and edit,
 * then copied to the clipboard and opened as a prefilled GitHub issue (DIAG-06, Pauta 4.0.1). Nothing
 * is uploaded: the user submits the issue, or does not. Made afresh each time, from the log as it is.
 */
final class ReportProblemWindow {

    private static final double WIDTH = 680;

    private final Stage stage = new Stage();
    private final TextArea report = new TextArea();
    private final String environment;
    private final Consumer<String> openLink;
    private final BiConsumer<String, MessageLog.Severity> say;

    ReportProblemWindow(
            Settings settings,
            Redactor redactor,
            Consumer<String> openLink,
            BiConsumer<String, MessageLog.Severity> say,
            String stylesheet) {
        this.openLink = openLink;
        this.say = say;
        this.environment = redactor.redact(AppInfo.environment(AppInfo.runtime(), settings.file()));
        report.setText(redactor.redact(ProblemReport.build(settings, DebugLog.text())));
        report.setWrapText(true);
        report.setPrefRowCount(18);
        report.getStyleClass().add("report-text");

        stage.setTitle(tr("report.title"));
        WindowIcon.applyTo(stage);
        Label note = new Label(tr("report.note"));
        note.setWrapText(true);
        Label environmentBlock = new Label(environment);
        environmentBlock.getStyleClass().add("about-path");
        environmentBlock.setWrapText(true);

        Button submit = new Button(tr("report.submit"));
        submit.setDefaultButton(true);
        submit.setOnAction(e -> submit());
        Button cancel = new Button(tr("report.cancel"));
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, spacer, cancel, submit);

        VBox root = new VBox(10, note, environmentBlock, report, footer);
        VBox.setVgrow(report, Priority.ALWAYS);
        root.setPadding(new Insets(16));
        root.setPrefWidth(WIDTH);
        root.getStyleClass().add("report-problem");
        Scene scene = new Scene(root);
        scene.getStylesheets().add(stylesheet);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.setScene(scene);
    }

    void show(Window from) {
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - WIDTH) / 2));
        stage.setY(from.getY() + 60);
        stage.show();
        report.requestFocus();
        report.positionCaret(0);
    }

    /** Copies the report as edited, opens the prefilled issue, and closes. */
    void submit() {
        String text = report.getText();
        ClipboardContent content = new ClipboardContent();
        content.putString(environment + "\n\n" + text);
        Clipboard.getSystemClipboard().setContent(content);
        openLink.accept(ProblemReport.issueUrl(environment, text));
        say.accept(
                ProblemReport.fitsInUrl(environment, text) ? tr("report.opened") : tr("report.openedTooLong"),
                MessageLog.Severity.INFO);
        stage.hide();
    }

    TextArea report() {
        return report;
    }

    Stage stage() {
        return stage;
    }
}
