package com.mirada.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

/**
 * The UI zoom (Pauta WIN-12), the app template's: one setting, {@link Settings#uiZoom}, scales the
 * whole chrome, the image's own zoom aside. The stylesheets are written at 100 % in pixels; at any
 * other zoom every window gets a copy of each with every pixel length multiplied ({@link #scale}),
 * the root font size among them, so every font follows one root size and the menus' 14 and 11 px
 * follow too (MENU-16). Cupertino, the user-agent theme, is scaled the same way ({@link
 * #userAgent}). The sizes the code fixes (an icon's box, the palette's width, a row's height) are
 * bound to {@link #factor} instead of written as plain pixels ({@link #bind}, {@link #pad}, {@link
 * #hbox}).
 *
 * <p>At 100 % every sheet is the bundled file and nothing is read or scaled: the zoom costs start-up
 * nothing. Another zoom reads and scales each sheet once and keeps it (a data: URL); a change swaps
 * the URLs on every window's scene, and on a window that was hidden as it shows again.
 *
 * <p>FX thread, but for {@link #scale} and the reads behind {@link #sheet}.
 */
public final class UiZoom {

    /** One step of Zoom In or Zoom Out, in percent. */
    public static final int STEP = 10;

    /** The app's own stylesheets the zoom scales, under {@code /com/mirada/styles/}. */
    static final List<String> SHEETS = List.of("mirada.css", "ui-font.css");

    private static final String STYLES = "/com/mirada/styles/";

    /** A pixel length: a number with {@code px}, not part of a word, a selector or a colour. */
    private static final Pattern PX = Pattern.compile("(?<![\\w.#-])(-?\\d*\\.?\\d+)px\\b");

    private static final ReadOnlyDoubleWrapper FACTOR = new ReadOnlyDoubleWrapper(1);

    /** Each scaled sheet by "name@percent", as a data: URL; and every URL handed out, by the sheet's name. */
    private static final Map<String, String> SCALED = new ConcurrentHashMap<>();

    private static final Map<String, String> NAME_OF = new ConcurrentHashMap<>();

    private static int percent = Settings.DEFAULT_UI_ZOOM;
    private static boolean watching;

    private UiZoom() {}

    /** The zoom in effect, in percent. */
    public static int percent() {
        return percent;
    }

    /** The zoom as a factor, 1 at 100 %. */
    public static ReadOnlyDoubleProperty factor() {
        return FACTOR.getReadOnlyProperty();
    }

    /** One step larger, up to the largest. Pure. */
    static int in(int percent) {
        return Settings.clampUiZoom(percent + STEP);
    }

    /** One step smaller, down to the smallest. Pure. */
    static int out(int percent) {
        return Settings.clampUiZoom(percent - STEP);
    }

    /**
     * Applies the zoom to the whole app at once: the control theme, the app's sheets on every open
     * window, and every size bound to {@link #factor}. A zoom already in effect does nothing, so the
     * default costs start-up nothing.
     */
    public static void apply(int value) {
        int next = Settings.clampUiZoom(value);
        if (next == percent) return;
        percent = next;
        FACTOR.set(next / 100.0);
        watch();
        Theme.zoomChanged();
        for (Window window : Window.getWindows()) restyle(window.getScene());
    }

    /** From the first change on, a window that shows again picks up the zoom it missed while hidden. */
    private static void watch() {
        if (watching) return;
        watching = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) restyle(window.getScene());
            }
        });
    }

    /** Swaps every one of the app's sheets on {@code scene}, and on its root (a dialog's pane), for the zoom's. */
    static void restyle(@Nullable Scene scene) {
        if (scene == null) return;
        swap(scene.getStylesheets());
        Parent root = scene.getRoot();
        if (root != null) swap(root.getStylesheets());
    }

    private static void swap(ObservableList<String> sheets) {
        for (int i = 0; i < sheets.size(); i++) {
            String name = NAME_OF.get(sheets.get(i));
            if (name == null) continue;
            String now = sheet(name);
            if (!now.equals(sheets.get(i))) sheets.set(i, now);
        }
    }

    /**
     * The URL of one of the app's stylesheets at the zoom in effect: the bundled file at 100 %, its
     * precompiled {@code .bss} where the build made one (PERF-17, {@link StyleCompiler}).
     */
    static String sheet(String name) {
        String url = percent == Settings.DEFAULT_UI_ZOOM ? bundled(name) : scaled(name, STYLES + name, UiZoom.class);
        NAME_OF.putIfAbsent(url, name);
        return url;
    }

    /** A bundled sheet's URL: its {@code .bss} when the release build precompiled it, else the {@code .css}. */
    static String bundled(String name) {
        java.net.URL binary = UiZoom.class.getResource(STYLES + StyleCompiler.binaryName(name));
        if (binary != null) return binary.toExternalForm();
        return Objects.requireNonNull(UiZoom.class.getResource(STYLES + name), name)
                .toExternalForm();
    }

    /**
     * The control theme's stylesheet ({@code atlantafx.base.theme.Theme#getUserAgentStylesheet}) at the
     * zoom in effect: as given at 100 %, else a scaled copy.
     */
    static String userAgent(String path) {
        if (percent == Settings.DEFAULT_UI_ZOOM) return path;
        return scaled(path, path, atlantafx.base.theme.Theme.class);
    }

    private static String scaled(String name, String resource, Class<?> owner) {
        return scaledAt(name, resource, owner, percent);
    }

    private static String scaledAt(String name, String resource, Class<?> owner, int at) {
        return SCALED.computeIfAbsent(name + "@" + at, k -> dataUrl(scale(text(resource, owner), at)));
    }

    /** The control theme's two sheets, light and dark: the ones {@link Theme} hands {@link #userAgent}. */
    private static List<String> themeSheets() {
        return List.of(
                new atlantafx.base.theme.CupertinoLight().getUserAgentStylesheet(),
                new atlantafx.base.theme.CupertinoDark().getUserAgentStylesheet());
    }

    /**
     * Reads and scales every sheet at {@code value} ahead of {@link #apply}, the control theme's light
     * and dark ones too, so a change of the zoom or the theme on the FX thread only swaps URLs
     * (PERF-01). At 100 % there is nothing to do. Any thread.
     */
    static void prepare(int value) {
        int at = Settings.clampUiZoom(value);
        if (at == Settings.DEFAULT_UI_ZOOM) return;
        for (String name : SHEETS) scaledAt(name, STYLES + name, UiZoom.class, at);
        for (String sheet : themeSheets()) scaledAt(sheet, sheet, atlantafx.base.theme.Theme.class, at);
    }

    /** Whether {@link #prepare} has been done for {@code value}: {@link #apply} then reads nothing. */
    static boolean prepared(int value) {
        int at = Settings.clampUiZoom(value);
        if (at == Settings.DEFAULT_UI_ZOOM) return true;
        for (String name : SHEETS) if (!SCALED.containsKey(name + "@" + at)) return false;
        for (String sheet : themeSheets()) if (!SCALED.containsKey(sheet + "@" + at)) return false;
        return true;
    }

    /**
     * Applies the zoom {@code value} once its sheets are read and scaled: at once when they are
     * ({@link #prepared}), else after {@link #prepare} on a thread of its own, back on the FX thread,
     * unless {@code still} says by then that another zoom was asked for (PERF-01).
     */
    public static void applySoon(int value, java.util.function.IntPredicate still) {
        if (prepared(value)) {
            apply(value);
            return;
        }
        Thread.ofVirtual().name("ui-zoom").start(() -> {
            prepare(value);
            javafx.application.Platform.runLater(() -> {
                if (still.test(value)) apply(value);
            });
        });
    }

    private static String text(String resource, Class<?> owner) {
        try (InputStream in = Objects.requireNonNull(owner.getResourceAsStream(resource), resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A stylesheet at {@code percent}: every pixel length multiplied, rounded to a hundredth. An em is
     * relative to the root size already, and a bare number (a border's width, a shadow's radius) is
     * left alone. Pure.
     */
    static String scale(String css, int percent) {
        if (percent == Settings.DEFAULT_UI_ZOOM) return css;
        double factor = percent / 100.0;
        Matcher m = PX.matcher(css);
        StringBuilder out = new StringBuilder(css.length() + 256);
        while (m.find()) {
            double scaled = Math.round(Double.parseDouble(m.group(1)) * factor * 100) / 100.0;
            String number = scaled == Math.rint(scaled)
                    ? Long.toString((long) scaled)
                    : String.format(Locale.ROOT, "%s", scaled);
            m.appendReplacement(out, Matcher.quoteReplacement(number + "px"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** A stylesheet as a {@code data:} URL, which a scene and the user-agent sheet both take. */
    static String dataUrl(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Sizes the code fixes, at 100 %, bound to the zoom -------------------------------------

    /** {@code px} at the zoom in effect now: for a size read once, a new window's first size. */
    static double px(double px) {
        return px * FACTOR.get();
    }

    /** {@code px} at the current zoom, following it. */
    static DoubleBinding times(double px) {
        return FACTOR.multiply(px);
    }

    /** Binds {@code property} to {@code px} at the current zoom. */
    static void bind(DoubleProperty property, double px) {
        property.bind(times(px));
    }

    /** Insets of {@code top right bottom left} at the current zoom, following it. */
    static ObjectBinding<Insets> insets(double top, double right, double bottom, double left) {
        return Bindings.createObjectBinding(
                () -> {
                    double f = FACTOR.get();
                    return new Insets(top * f, right * f, bottom * f, left * f);
                },
                FACTOR);
    }

    /** Binds {@code region}'s padding to {@code top right bottom left} at the current zoom. */
    static <R extends Region> R pad(R region, double top, double right, double bottom, double left) {
        region.paddingProperty().bind(insets(top, right, bottom, left));
        return region;
    }

    /** Binds {@code region}'s padding to {@code all} on every side at the current zoom. */
    static <R extends Region> R pad(R region, double all) {
        return pad(region, all, all, all, all);
    }

    /** A row of {@code children} {@code spacing} apart at the current zoom. */
    static HBox hbox(double spacing, Node... children) {
        HBox box = new HBox(children);
        bind(box.spacingProperty(), spacing);
        return box;
    }

    /** A column of {@code children} {@code spacing} apart at the current zoom. */
    static VBox vbox(double spacing, Node... children) {
        VBox box = new VBox(children);
        bind(box.spacingProperty(), spacing);
        return box;
    }

    /** {@code node}'s margin in a {@link StackPane}, at the current zoom. */
    static void stackMargin(Node node, double top, double right, double bottom, double left) {
        margin(node, insets(top, right, bottom, left), StackPane::setMargin);
    }

    /** {@code node}'s margin in an {@link HBox}, at the current zoom. */
    static void hboxMargin(Node node, double top, double right, double bottom, double left) {
        margin(node, insets(top, right, bottom, left), HBox::setMargin);
    }

    /** {@code node}'s margin in a {@link VBox}, at the current zoom. */
    static void vboxMargin(Node node, double top, double right, double bottom, double left) {
        margin(node, insets(top, right, bottom, left), VBox::setMargin);
    }

    /**
     * A margin is a constraint, not a property: set now and again on each change. The binding is kept
     * on the node, which the zoom holds only weakly, so a node let go is not kept by its margin.
     */
    private static void margin(Node node, ObjectBinding<Insets> insets, BiConsumer<Node, Insets> set) {
        set.accept(node, insets.get());
        insets.addListener((o, was, now) -> set.accept(node, now));
        // A margin set again (the Message Log's, as the status bar comes and goes) replaces the last.
        if (node.getProperties().put(UiZoom.class.getName() + ".margin", insets) instanceof ObjectBinding<?> last)
            last.dispose();
    }
}
