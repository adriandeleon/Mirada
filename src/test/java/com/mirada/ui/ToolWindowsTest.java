package com.mirada.ui;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.mirada.AppInfo;
import com.mirada.diagnostics.MessageLog;
import com.mirada.report.Redactor;
import com.mirada.settings.Settings;
import com.mirada.update.ReleaseInfo;
import com.mirada.update.UpdateCoordinator;
import com.mirada.update.UpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The smaller windows and the chrome around the image: every link, button and menu item does what
 * it says (opens its link, runs its command, copies what it names), and Escape closes each window.
 */
@Tag("fx")
class ToolWindowsTest {

    @TempDir
    Path dir;

    private final List<String> links = new ArrayList<>();
    private final List<Command> ran = new ArrayList<>();
    private Settings settings = new Settings(Path.of("unset"));
    private Stage owner = null;

    @BeforeEach
    void setUp() throws Exception {
        FxTestSupport.start();
        settings = new Settings(dir.resolve("settings.properties"));
        owner = call(Stage::new);
        fx(() -> {
            owner.setX(0);
            owner.setY(0);
            owner.setWidth(800);
            owner.setHeight(600);
        });
    }

    @AfterEach
    void close() throws Exception {
        FxToolkit.cleanupStages();
    }

    /** The button or link under {@code root} whose text is {@code text}. */
    private static ButtonBase named(Parent root, String text) throws Exception {
        return call(() -> {
            for (Node node : all(root)) {
                if (node instanceof ButtonBase button && text.equals(button.getText())) return button;
            }
            throw new AssertionError("no button " + text);
        });
    }

    private static List<Node> all(Parent root) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(root);
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent parent) nodes.addAll(all(parent));
            else nodes.add(child);
        }
        return nodes;
    }

    private static void press(Stage stage, KeyCode code) throws Exception {
        fx(() -> {
            Scene scene = Objects.requireNonNull(stage.getScene());
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
        });
    }

    private UpdateCoordinator updates(UpdateService.Outcome outcome) {
        return new UpdateCoordinator(
                settings,
                version -> outcome,
                (delay, task) -> {},
                Runnable::run,
                Runnable::run,
                (text, severity) -> {},
                () -> 0L);
    }

    @Test
    void aboutsLinksOpenTheirPagesAndCopyDetailsCopiesTheEnvironment() throws Exception {
        UpdateCoordinator updates =
                updates(new UpdateService.Outcome(new ReleaseInfo("99.0.0", "https://example.org/release"), null));
        AboutWindow about = call(() -> new AboutWindow(
                settings.file(), links::add, updates, () -> ran.add(Command.COPY_DETAILS), ViewerWindow.stylesheet()));
        fx(() -> about.show(owner));
        Parent root = call(() -> about.stage().getScene().getRoot());
        fx(() -> named(root, AppInfo.HOMEPAGE).fire());
        fx(() -> named(root, "Release notes").fire());
        fx(() -> named(root, AboutWindow.pautaLink(AppInfo.PAUTA_EDITION)).fire());
        assertEquals(List.of(AppInfo.HOMEPAGE, AppInfo.releaseNotesUrl(), AppInfo.pautaUrl()), links);

        ButtonBase copy = named(root, "Copy details");
        fx(copy::fire);
        assertEquals(List.of(Command.COPY_DETAILS), ran, "the button runs the command");
        assertFalse(call(copy::getText).equals("Copy details"), "it says it copied");

        // A newer release, once checked, is named with a link to it.
        fx(() -> updates.checkNow(true));
        assertTrue(call(() -> all(root).stream()
                .anyMatch(n -> n instanceof Labeled l
                        && l.getText() != null
                        && l.getText().contains("99.0.0"))));
        ButtonBase release = call(() -> all(root).stream()
                .filter(n -> n instanceof javafx.scene.control.Hyperlink h
                        && h.isVisible()
                        && !links.isEmpty()
                        && h.getText() != null
                        && !h.getText().equals(AppInfo.HOMEPAGE)
                        && !h.getText().equals("Release notes")
                        && !h.getText().startsWith("Built to"))
                .map(n -> (ButtonBase) n)
                .findFirst()
                .orElseThrow());
        fx(release::fire);
        assertEquals("https://example.org/release", links.getLast());

        press(about.stage(), KeyCode.ESCAPE);
        assertFalse(call(() -> about.stage().isShowing()));
        fx(() -> about.show(owner));
        assertEquals("Copy details", call(copy::getText), "reset once closed");
        fx(() -> named(root, "Close").fire());
        assertFalse(call(() -> about.stage().isShowing()));
    }

    @Test
    void reportAProblemCopiesTheReportAndOpensAPrefilledIssue() throws Exception {
        List<String> said = new ArrayList<>();
        ReportProblemWindow report = call(() -> new ReportProblemWindow(
                settings,
                Redactor.forThisMachine(),
                links::add,
                (text, severity) -> said.add(text),
                ViewerWindow.stylesheet()));
        fx(() -> report.show(owner));
        TextArea text = report.report();
        assertTrue(call(text::getText).contains(AppInfo.VERSION)
                || !call(text::getText).isEmpty());
        fx(() -> text.setText("It turned my photo upside down."));
        Parent root = call(() -> report.stage().getScene().getRoot());
        fx(() -> named(root, "Copy and Open Issue").fire());
        assertEquals(1, links.size());
        assertTrue(links.getFirst().startsWith("https://github.com/"), links.getFirst());
        assertTrue(call(() -> Clipboard.getSystemClipboard().getString()).contains("It turned my photo upside down."));
        assertTrue(said.getFirst().startsWith("Opened a new issue"));
        assertFalse(call(() -> report.stage().isShowing()));

        fx(() -> report.show(owner));
        fx(() -> named(root, "Cancel").fire());
        assertFalse(call(() -> report.stage().isShowing()));
        fx(() -> report.show(owner));
        press(report.stage(), KeyCode.ESCAPE);
        assertFalse(call(() -> report.stage().isShowing()));
    }

    @Test
    void theDebugLogsButtonsRunTheirCommands() throws Exception {
        DebugLogWindow log = call(() -> new DebugLogWindow(ran::add, ViewerWindow.stylesheet()));
        fx(() -> log.show(owner));
        Parent root = call(() -> log.stage().getScene().getRoot());
        for (String button : List.of("Refresh", "Copy", "Clear", "Export…", "Report a Problem…")) {
            fx(() -> named(root, button).fire());
        }
        assertEquals(
                List.of(
                        Command.COPY_DEBUG_LOG,
                        Command.CLEAR_DEBUG_LOG,
                        Command.EXPORT_DEBUG_LOG,
                        Command.REPORT_PROBLEM),
                ran);
        press(log.stage(), KeyCode.F5);
        fx(() -> log.show(owner));
        press(log.stage(), KeyCode.ESCAPE);
        assertFalse(call(() -> log.stage().isShowing()));
    }

    @Test
    void theMessageLogFiltersAndItsButtonsRunTheirCommands() throws Exception {
        MessageLog messages = new MessageLog();
        messages.add("Copied a.png", MessageLog.Severity.INFO, LocalTime.NOON);
        messages.add("Couldn't open b.png: broken", MessageLog.Severity.ERROR, LocalTime.NOON.plusSeconds(1));
        StackPane host = new StackPane();
        fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(host, 600, 400));
            stage.show();
        });
        MessageLogView log = call(() -> new MessageLogView(host, messages, ran::add));
        fx(() -> log.show(0));
        assertEquals(2, call(() -> log.listed().size()));
        javafx.scene.control.TextField filter = FxTestSupport.field(log, "filter");
        fx(() -> filter.setText("b.png"));
        assertEquals(1, call(() -> log.listed().size()));
        Parent card = FxTestSupport.field(log, "card");
        fx(() -> named(card, "Copy").fire());
        fx(() -> named(card, "Clear").fire());
        assertEquals(List.of(Command.COPY_MESSAGES, Command.CLEAR_MESSAGES), ran);
        // A message said while it is open joins the list.
        fx(() -> filter.clear());
        fx(() -> messages.add("Renamed to c.png", MessageLog.Severity.INFO, LocalTime.NOON.plusSeconds(2)));
        assertEquals(3, call(() -> log.listed().size()));
        // From the filter, Down and Return go into the list; there, the shortcut and C copies.
        javafx.scene.control.ListView<?> list = FxTestSupport.field(log, "list");
        fx(filter::requestFocus);
        FxTestSupport.waitUntil("the filter's focus", filter::isFocused);
        logKey(card, KeyCode.DOWN, false, false);
        FxTestSupport.waitUntil("the list's focus", list::isFocused);
        assertEquals(0, call(() -> list.getSelectionModel().getSelectedIndex()));
        logKey(card, KeyCode.C, true, false);
        assertEquals(Command.COPY_MESSAGES, ran.getLast());
        logKey(card, KeyCode.C, true, true); // with Shift, not a copy
        assertEquals(Command.COPY_MESSAGES, ran.getLast());
        logKey(card, KeyCode.G, false, true); // Alt+G: not C-g
        assertTrue(call(log::isShowing));
        logKey(card, KeyCode.G, true, false); // C-g closes, as Escape does
        assertFalse(call(log::isShowing));
        fx(() -> log.show(0));
        assertEquals(List.of(), call(() -> MessageLogView.matching(log.listed(), "no such words")));
        logKey(card, KeyCode.ESCAPE, false, false);
        assertFalse(call(log::isShowing));
        fx(() -> log.show(0));
        Node backdrop = FxTestSupport.field(log, "backdrop");
        fx(() -> backdrop.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                1,
                1,
                1,
                1,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null)));
        assertFalse(call(log::isShowing), "a press outside closes it");
    }

    @Test
    void theSidebarShowsTheMarksAndItsButtonsEditTheNotesAndOpenTheFolder() throws Exception {
        Path photo = WindowFixture.png(dir.resolve("photos"), "a.png", 30, 20, java.awt.Color.RED);
        InfoPanel panel = call(() -> new InfoPanel(ran::add));
        ImageDetails details = ImageDetails.read(photo, 0, 1, 30, 20);
        fx(() -> panel.show(details, new InfoPanel.Marks(true, "By the sea", true, "Summer")));
        List<String> shown = call(() -> {
            List<String> found = new ArrayList<>();
            collect(panel.node().getContent(), found);
            return found;
        });
        assertTrue(shown.contains("This image and its folder"), shown.toString());
        assertTrue(shown.contains("By the sea"));
        assertTrue(shown.contains("Summer"));
        List<ButtonBase> buttons = call(() -> {
            List<ButtonBase> found = new ArrayList<>();
            for (Node node : all((Parent) panel.node().getContent())) if (node instanceof ButtonBase b) found.add(b);
            return found;
        });
        for (ButtonBase button : buttons) fx(button::fire);
        assertEquals(Set.of(Command.NOTE, Command.FOLDER_NOTE, Command.SHOW_IN_FOLDER), Set.copyOf(ran));
        assertEquals(3, ran.size());
        fx(() -> panel.show(details, new InfoPanel.Marks(false, "", true, "")));
        fx(() -> panel.show(null, InfoPanel.Marks.NONE));
    }

    private static void logKey(Parent card, KeyCode code, boolean shortcut, boolean alt) throws Exception {
        fx(() -> {
            Node target = card.getScene() != null && card.getScene().getFocusOwner() != null
                    ? card.getScene().getFocusOwner()
                    : card;
            boolean mac = Command.MAC;
            Event.fireEvent(
                    target,
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, shortcut && !mac, alt, shortcut && mac));
        });
    }

    private static void collect(Node node, List<String> found) {
        if (node instanceof Labeled labeled && labeled.getText() != null) found.add(labeled.getText());
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) collect(child, found);
    }

    @Test
    void theBadgesButtonsTakeTheBookmarkOffAndEditTheNote() throws Exception {
        StackPane canvas = new StackPane();
        MarksBadge badge = call(() -> new MarksBadge(canvas, ran::add));
        fx(() -> badge.show(true, "A note", false));
        for (Node node : call(() -> all(canvas))) {
            if (node instanceof ButtonBase button) fx(button::fire);
        }
        assertEquals(List.of(Command.BOOKMARK, Command.NOTE), ran);
        fx(() -> badge.show(false, "", true));
    }

    @Test
    void theHeadersButtonsAndMenuRunTheirCommands() throws Exception {
        SimpleBooleanProperty info = new SimpleBooleanProperty(false);
        SimpleBooleanProperty thumbnails = new SimpleBooleanProperty(false);
        SimpleBooleanProperty statusBar = new SimpleBooleanProperty(true);
        TitleBar bar = call(() -> new TitleBar(owner, ran::add, info, thumbnails, statusBar));
        fx(() -> {
            owner.setScene(new Scene(new javafx.scene.layout.BorderPane(null, bar.node(), null, null, null)));
            owner.show();
            bar.finish();
            bar.setTitle("a.png");
            bar.setFolder(dir);
            bar.setImageShowing(true);
        });
        ButtonBase rotate = FxTestSupport.field(bar, "rotate");
        fx(rotate::fire);
        fx(() -> {
            rotate.fireEvent(new MouseEvent(
                    MouseEvent.MOUSE_PRESSED,
                    1,
                    1,
                    1,
                    1,
                    MouseButton.PRIMARY,
                    1,
                    true,
                    false,
                    false,
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    true,
                    null));
            rotate.fire();
        });
        assertEquals(List.of(Command.ROTATE_CLOCKWISE, Command.ROTATE_COUNTERCLOCKWISE), ran);
        MenuButton menu = FxTestSupport.field(bar, "menu");
        List<MenuItem> items = call(() -> flatten(menu.getItems()));
        assertFalse(items.isEmpty());
        ran.clear();
        for (MenuItem item : items) {
            if (item instanceof javafx.scene.control.SeparatorMenuItem || item instanceof Menu) continue;
            fx(item::fire);
        }
        assertTrue(ran.contains(Command.OPEN) && ran.contains(Command.PALETTE), ran.toString());
        fx(() ->
                bar.fullScreen((javafx.scene.layout.BorderPane) owner.getScene().getRoot(), true));
        fx(() ->
                bar.fullScreen((javafx.scene.layout.BorderPane) owner.getScene().getRoot(), false));
        fx(() -> bar.setFolder(null));
    }

    private static List<MenuItem> flatten(List<MenuItem> items) {
        List<MenuItem> all = new ArrayList<>();
        for (MenuItem item : items) {
            all.add(item);
            if (item instanceof Menu menu) all.addAll(flatten(menu.getItems()));
        }
        return all;
    }

    @Test
    void theMacsMenuBarRunsCommandsAndGreysAndTicksThemWhileOpen() throws Exception {
        MenuBar bar = call(() -> MacMenus.build(
                ran::add,
                command -> command == Command.COPY ? "The image is still loading." : null,
                command -> command == Command.INFO,
                command -> command == Command.FULL_SCREEN ? "Leave Full Screen" : command.label()));
        List<String> headings = bar.getMenus().stream().map(Menu::getText).toList();
        assertEquals(MacMenus.HEADINGS.stream().map(MacMenus.Heading::title).toList(), headings);
        for (Menu menu : bar.getMenus()) {
            fx(() -> menu.getOnShowing().handle(new Event(Menu.ON_SHOWING)));
            for (MenuItem item : menu.getItems()) {
                if (item.getUserData() == Command.COPY) assertTrue(item.isDisable(), "greyed while it cannot run");
                if (item.getUserData() == Command.INFO) assertTrue(((CheckMenuItem) item).isSelected(), "ticked");
                if (item.getUserData() == Command.FULL_SCREEN)
                    assertTrue(item.getText().startsWith("Leave Full Screen"), item.getText());
            }
            fx(() -> menu.getOnHidden().handle(new Event(Menu.ON_HIDDEN)));
            for (MenuItem item : menu.getItems()) assertFalse(item.isDisable(), "enabled again once closed");
        }
        MenuItem first = bar.getMenus().getFirst().getItems().getFirst();
        fx(first::fire);
        assertEquals(List.of((Command) first.getUserData()), ran);
    }
}
