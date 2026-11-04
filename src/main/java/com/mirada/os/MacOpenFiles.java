package com.mirada.os;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.application.Platform;

import org.jspecify.annotations.Nullable;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Where macOS hands Mirada the image it was asked to open: a double-click in Finder, "Open With",
 * a file dropped on the Dock icon. macOS does not pass these as arguments; it sends an "open
 * documents" Apple Event, which AppKit gives the application's delegate, JavaFX's {@code
 * GlassApplication}.
 *
 * <p>Since JavaFX 25 (JDK-8354631) that delegate answers {@code application:openURLs:}, which
 * AppKit then prefers to {@code application:openFiles:}, and all it does with the URLs is post an
 * {@code AWTEmbeddedEvent} notification for an AWT embedded in JavaFX. Glass's own open-files
 * event, the one its {@code Application.EventHandler} receives, never comes, and AWT's {@code
 * Desktop} handlers hear only what arrives after AWT has started, which is too late for the event
 * that launched the app. So Mirada observes that notification itself, through the Objective-C
 * runtime ({@link Observer}), registered from {@code App.main} before JavaFX starts: nothing is
 * missed, AWT is not needed, and a file that arrives before the window is held for it.
 *
 * <p>Everywhere else the file arrives as an argument (the Linux menu entry's {@code %f}, Windows'
 * registered command), and this does nothing.
 */
public final class MacOpenFiles {

    private MacOpenFiles() {}

    /** Files that arrived and have not been handed on. Guarded by the class. */
    private static final List<Path> arrived = new ArrayList<>();
    /** Where they go, once {@link #receive} has said; null before. Guarded by the class. */
    private static @Nullable Consumer<Path> receiver;
    /** A hand-over is queued on the FX thread. Guarded by the class. */
    private static boolean handingOver;

    /**
     * From {@code App.main}, before {@code launch}: starts listening, so the event that launched the
     * app is heard. Returns what it did, or why it did nothing; a failure costs Finder's files, not
     * the launch.
     */
    public static String listen() {
        if (!isMac(System.getProperty("os.name"))) return "none (not macOS)";
        try {
            Observer.register();
            return "files";
        } catch (Throwable e) {
            System.err.println("[mirada] no open-file handler: " + e);
            return "none (" + e + ")";
        }
    }

    /**
     * From {@code App.start}, on the FX thread: from now on the files go to {@code open}, on the FX
     * thread, the ones already arrived first, except one that repeats {@code argument}, the file on
     * the command line ({@link #besides}).
     */
    public static void receive(@Nullable Path argument, Consumer<Path> open) {
        synchronized (MacOpenFiles.class) {
            receiver = besides(argument, open);
        }
        handOver();
    }

    /** From AppKit's thread, which is the FX thread once JavaFX is up: one URL of an event. */
    static void arrived(Path file) {
        synchronized (MacOpenFiles.class) {
            arrived.add(file);
            if (receiver == null || handingOver) return;
            handingOver = true;
        }
        // AppKit posts one notification per URL, in a loop; the hand-over runs after it, so it sees
        // the whole event.
        Platform.runLater(MacOpenFiles::handOver);
    }

    private static void handOver() {
        Consumer<Path> to;
        Optional<Path> file;
        synchronized (MacOpenFiles.class) {
            handingOver = false;
            to = receiver;
            if (to == null) return;
            file = first(arrived);
            arrived.clear();
        }
        file.ifPresent(to);
    }

    static boolean isMac(@Nullable String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    /** One window shows one folder, so of several files opened at once the first is the one shown. */
    static Optional<Path> first(List<Path> files) {
        return files.stream().findFirst();
    }

    /** The file a {@code file:} URL names; nothing for any other URL, or one that is not a path. */
    static Optional<Path> fileOf(@Nullable String url) {
        if (url == null) return Optional.empty();
        try {
            URI uri = URI.create(url);
            if (!"file".equalsIgnoreCase(uri.getScheme())) return Optional.empty();
            return Optional.of(Path.of(uri));
        } catch (IllegalArgumentException | FileSystemNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * {@code open}, less the event that repeats the command line, should AppKit turn a file named on
     * it into an "open documents" event as well: a launch with an argument would open it twice, the
     * second time starting the folder over. Only the first event is dropped, and only when it names
     * the same file: a later double-click on it is the user's.
     */
    static Consumer<Path> besides(@Nullable Path argument, Consumer<Path> open) {
        if (argument == null) return open;
        AtomicBoolean first = new AtomicBoolean(true);
        return file -> {
            if (first.getAndSet(false) && same(file, argument)) return;
            open.accept(file);
        };
    }

    /** The same file, however each was spelled (AppKit's is absolute, the argument as typed). */
    static boolean same(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException | SecurityException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    /**
     * An Objective-C class made at run time, {@code MiradaOpenFiles}, whose one method is an FFM
     * upcall into {@link #received}, and an instance of it observing {@code AWTEmbeddedEvent}: the
     * notification glass posts with {@code {name: "openURL", url: …}} for each URL AppKit hands its
     * delegate. Apart, so that only macOS loads it.
     */
    static final class Observer {

        private Observer() {}

        private static final Linker LINKER = Linker.nativeLinker();
        private static final Arena FOREVER = Arena.global();

        // What the observer's method needs, looked up once by register(); null until then.
        private static @Nullable MethodHandle send;
        private static @Nullable MethodHandle sendWith;
        private static MemorySegment userInfo = MemorySegment.NULL;
        private static MemorySegment objectForKey = MemorySegment.NULL;
        private static MemorySegment utf8String = MemorySegment.NULL;
        private static MemorySegment nameKey = MemorySegment.NULL;
        private static MemorySegment urlKey = MemorySegment.NULL;

        static void register() throws Throwable {
            SymbolLookup objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", FOREVER);
            // NSString and NSNotificationCenter, before JavaFX has loaded AppKit and with it Foundation.
            SymbolLookup.libraryLookup("/System/Library/Frameworks/Foundation.framework/Foundation", FOREVER);
            MethodHandle getClass = function(objc, "objc_getClass", FunctionDescriptor.of(ADDRESS, ADDRESS));
            MethodHandle sel = function(objc, "sel_registerName", FunctionDescriptor.of(ADDRESS, ADDRESS));
            MethodHandle allocateClass = function(
                    objc, "objc_allocateClassPair", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
            MethodHandle addMethod = function(
                    objc, "class_addMethod", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            MethodHandle registerClass = function(objc, "objc_registerClassPair", FunctionDescriptor.ofVoid(ADDRESS));
            // objc_msgSend, cast to each shape it is called with: arm64 passes no variadic arguments.
            MethodHandle msgSend = function(objc, "objc_msgSend", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            MethodHandle msgSendWith =
                    function(objc, "objc_msgSend", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            MethodHandle addObserver = function(
                    objc,
                    "objc_msgSend",
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            userInfo = (MemorySegment) sel.invokeExact(global("userInfo"));
            objectForKey = (MemorySegment) sel.invokeExact(global("objectForKey:"));
            utf8String = (MemorySegment) sel.invokeExact(global("UTF8String"));
            nameKey = string(getClass, sel, msgSend, msgSendWith, "name");
            urlKey = string(getClass, sel, msgSend, msgSendWith, "url");

            MemorySegment nsObject = (MemorySegment) getClass.invokeExact(FOREVER.allocateFrom("NSObject"));
            MemorySegment observerClass =
                    (MemorySegment) allocateClass.invokeExact(nsObject, FOREVER.allocateFrom("MiradaOpenFiles"), 0L);
            if (observerClass.equals(MemorySegment.NULL)) throw new IllegalStateException("MiradaOpenFiles exists");
            send = msgSend;
            sendWith = msgSendWith;
            MemorySegment callback = (MemorySegment) sel.invokeExact(FOREVER.allocateFrom("openFiles:"));
            MemorySegment implementation = LINKER.upcallStub(
                    MethodHandles.lookup()
                            .findStatic(
                                    Observer.class,
                                    "received",
                                    MethodType.methodType(
                                            void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)),
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS),
                    FOREVER);
            // v@:@ — returns nothing; takes self, the selector and one object, the notification.
            byte added =
                    (byte) addMethod.invokeExact(observerClass, callback, implementation, FOREVER.allocateFrom("v@:@"));
            if (added == 0) throw new IllegalStateException("class_addMethod refused");
            registerClass.invokeExact(observerClass);

            MemorySegment observer = (MemorySegment)
                    msgSend.invokeExact(observerClass, (MemorySegment) sel.invokeExact(FOREVER.allocateFrom("new")));
            MemorySegment centerClass =
                    (MemorySegment) getClass.invokeExact(FOREVER.allocateFrom("NSNotificationCenter"));
            MemorySegment center = (MemorySegment) msgSend.invokeExact(
                    centerClass, (MemorySegment) sel.invokeExact(FOREVER.allocateFrom("defaultCenter")));
            MemorySegment name = string(getClass, sel, msgSend, msgSendWith, "AWTEmbeddedEvent");
            addObserver.invokeExact(
                    center,
                    (MemorySegment) sel.invokeExact(FOREVER.allocateFrom("addObserver:selector:name:object:")),
                    observer,
                    callback,
                    name,
                    MemorySegment.NULL);
        }

        /** The observer's method: an upcall from AppKit's thread, so nothing may escape it. */
        static void received(MemorySegment self, MemorySegment command, MemorySegment notification) {
            try {
                MethodHandle msgSend = send;
                MethodHandle msgSendWith = sendWith;
                if (msgSend == null || msgSendWith == null) return;
                MemorySegment info = (MemorySegment) msgSend.invokeExact(notification, userInfo);
                if (info.equals(MemorySegment.NULL)) return;
                MemorySegment name = (MemorySegment) msgSendWith.invokeExact(info, objectForKey, nameKey);
                if (!"openURL".equals(text(msgSend, name))) return;
                MemorySegment url = (MemorySegment) msgSendWith.invokeExact(info, objectForKey, urlKey);
                fileOf(text(msgSend, url)).ifPresent(MacOpenFiles::arrived);
            } catch (Throwable e) {
                System.err.println("[mirada] an opened file was lost: " + e);
            }
        }

        private static MethodHandle function(SymbolLookup library, String name, FunctionDescriptor shape) {
            return LINKER.downcallHandle(library.find(name).orElseThrow(), shape);
        }

        /** A retained NSString, kept for the life of the process. */
        private static MemorySegment string(
                MethodHandle getClass, MethodHandle sel, MethodHandle msgSend, MethodHandle msgSendWith, String text)
                throws Throwable {
            MemorySegment stringClass = (MemorySegment) getClass.invokeExact(global("NSString"));
            MemorySegment allocated =
                    (MemorySegment) msgSend.invokeExact(stringClass, (MemorySegment) sel.invokeExact(global("alloc")));
            return (MemorySegment) msgSendWith.invokeExact(
                    allocated, (MemorySegment) sel.invokeExact(global("initWithUTF8String:")), global(text));
        }

        /** An NSString's characters, or null for nil. */
        private static @Nullable String text(MethodHandle msgSend, MemorySegment string) throws Throwable {
            if (string.equals(MemorySegment.NULL)) return null;
            MemorySegment utf8 = (MemorySegment) msgSend.invokeExact(string, utf8String);
            return utf8.equals(MemorySegment.NULL)
                    ? null
                    : utf8.reinterpret(Long.MAX_VALUE).getString(0);
        }

        private static MemorySegment global(String text) {
            return FOREVER.allocateFrom(text);
        }
    }
}
