package com.mirada.os;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Moves a file to the desktop's trash, from where it can be put back: the undo for "delete".
 *
 * <ul>
 *   <li><b>Linux</b>: the freedesktop.org trash, {@code ~/.local/share/Trash} (or under {@code
 *       XDG_DATA_HOME}): an info file naming where it came from and when, then the file itself,
 *       moved, never copied. A file on another file system than home goes through {@code gio
 *       trash}, which knows each volume's own trash folder.
 *   <li><b>Windows</b>: the Recycle Bin, through the shell's {@code SHFileOperationW} with undo.
 *   <li><b>macOS</b>: AWT's {@code Desktop.moveToTrash}, AWT being headful there ({@link #headless}).
 * </ul>
 *
 * <p>Only Linux's own trash can be put back from here ({@link #restore}): Mirada wrote the info file
 * and knows where the file went. The Recycle Bin and the Mac's Trash say nothing of where it went,
 * and reading the Mac's needs Full Disk Access; the user puts those back from the desktop's own.
 */
public final class Trash {

    private Trash() {}

    /**
     * Whether AWT must be headless on {@code osName}: everywhere but macOS, where {@code
     * Desktop.moveToTrash} needs it headful. Decided in {@code App.main} before any AWT class loads;
     * headful, AWT still loads only when a file is first trashed.
     */
    public static boolean headless(@Nullable String osName) {
        return !MacOpenFiles.isMac(osName);
    }

    /**
     * A file moved to the trash: where it was, and, when Mirada can put it back, where it is now
     * and the info file that records it.
     */
    public record Trashed(
            Path original, @Nullable Path inTrash, @Nullable Path info) {

        public static Trashed gone(Path original) {
            return new Trashed(original, null, null);
        }

        public boolean restorable() {
            return inTrash != null && info != null;
        }
    }

    /** Whether a trashed file can be put back from here on this platform: Linux's own trash only. */
    public static boolean restores() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return !os.startsWith("windows") && !os.startsWith("mac");
    }

    /** Moves {@code file} to the trash, or says why it could not. */
    public static Trashed move(Path file) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            recycle(file);
            return Trashed.gone(file);
        } else if (os.startsWith("mac")) {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                throw new IOException(tr("trashOs.notAvailable"));
            }
            if (!Desktop.getDesktop().moveToTrash(file.toFile())) throw new IOException(tr("trashOs.finderRefused"));
            return Trashed.gone(file);
        } else {
            return freedesktop(file);
        }
    }

    /**
     * Puts a trashed file back where it was, and removes its info file, as the file manager's
     * Restore does. Refuses rather than overwrite: a file of that name may have come since.
     */
    public static void restore(Trashed trashed) throws IOException {
        Path inTrash = trashed.inTrash();
        Path info = trashed.info();
        if (inTrash == null || info == null) throw new IOException(tr("trashOs.onlyFromDesktopTrash"));
        if (!Files.exists(inTrash, LinkOption.NOFOLLOW_LINKS)) throw new IOException(tr("trashOs.noLongerInTrash"));
        if (Files.exists(trashed.original(), LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(trashed.original().toString(), null, tr("trashOs.nameTaken"));
        }
        Files.move(inTrash, trashed.original(), StandardCopyOption.ATOMIC_MOVE);
        Files.deleteIfExists(info);
    }

    // ---- Linux ----

    private static Trashed freedesktop(Path file) throws IOException {
        return freedesktop(file, homeTrash(System.getenv("XDG_DATA_HOME"), System.getProperty("user.home", ".")));
    }

    static Trashed freedesktop(Path file, Path trash) throws IOException {
        Path files = trash.resolve("files");
        Path info = trash.resolve("info");
        Files.createDirectories(files);
        Files.createDirectories(info);
        String base = fileName(file);
        // The info file is created first and exclusively: it is what reserves the name.
        for (int attempt = 0; attempt < 1000; attempt++) {
            String name = numbered(base, attempt);
            Path infoFile = info.resolve(name + ".trashinfo");
            try {
                Files.writeString(
                        infoFile,
                        trashInfo(file.toAbsolutePath(), LocalDateTime.now()),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException taken) {
                continue;
            }
            try {
                Path inTrash = files.resolve(name);
                Files.move(file, inTrash, StandardCopyOption.ATOMIC_MOVE);
                return new Trashed(file.toAbsolutePath(), inTrash, infoFile);
            } catch (AtomicMoveNotSupportedException otherFileSystem) {
                Files.deleteIfExists(infoFile);
                gioTrash(file);
                return Trashed.gone(file);
            } catch (IOException e) {
                Files.deleteIfExists(infoFile);
                throw e;
            }
        }
        throw new IOException(tr("trashOs.tooManyNamed", base));
    }

    /** {@code $XDG_DATA_HOME/Trash}, or {@code ~/.local/share/Trash} without it. */
    static Path homeTrash(@Nullable String dataHome, String home) {
        Path data = dataHome != null && !dataHome.isBlank() && Path.of(dataHome).isAbsolute()
                ? Path.of(dataHome)
                : Path.of(home, ".local", "share");
        return data.resolve("Trash");
    }

    /** {@code photo.jpg}, then {@code photo.2.jpg}, {@code photo.3.jpg}: the name kept recognisable. */
    static String numbered(String name, int attempt) {
        if (attempt == 0) return name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        return stem + "." + (attempt + 1) + extension;
    }

    /** The spec's info file: the original path, percent-encoded but for slashes, and local time. */
    static String trashInfo(Path original, LocalDateTime when) {
        return "[Trash Info]\nPath=" + encodePath(original.toString()) + "\nDeletionDate="
                + when.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n";
    }

    static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean plain = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || "/-_.~".indexOf(c) >= 0;
            if (plain) out.append((char) c);
            else out.append('%').append(String.format("%02X", c));
        }
        return out.toString();
    }

    private static void gioTrash(Path file) throws IOException {
        Process gio;
        try {
            gio = ToolPath.process(
                            List.of("gio", "trash", "--", file.toAbsolutePath().toString()))
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException noGio) {
            throw new IOException(tr("trashOs.noGio"));
        }
        try {
            if (!gio.waitFor(10, TimeUnit.SECONDS)) {
                gio.destroy();
                throw new IOException(tr("trashOs.gioDidNotFinish"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(tr("trashOs.interrupted"));
        }
        if (gio.exitValue() != 0) {
            String said = new String(gio.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            throw new IOException(said.isEmpty() ? tr("trashOs.gioFailed") : said);
        }
    }

    // ---- Windows ----

    private static final int FO_DELETE = 3;
    private static final short FOF_SILENT = 0x0004;
    private static final short FOF_NOCONFIRMATION = 0x0010;
    private static final short FOF_ALLOWUNDO = 0x0040;
    private static final short FOF_NOERRORUI = 0x0400;

    /** {@code SHFILEOPSTRUCTW}, naturally aligned as it is on 64-bit Windows. */
    private static final StructLayout FILE_OP = MemoryLayout.structLayout(
            ADDRESS.withName("hwnd"),
            JAVA_INT.withName("wFunc"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("pFrom"),
            ADDRESS.withName("pTo"),
            JAVA_SHORT.withName("fFlags"),
            MemoryLayout.paddingLayout(2),
            JAVA_INT.withName("fAnyOperationsAborted"),
            ADDRESS.withName("hNameMappings"),
            ADDRESS.withName("lpszProgressTitle"));

    private static void recycle(Path file) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup shell = SymbolLookup.libraryLookup("shell32", arena);
            MethodHandle operation = Linker.nativeLinker()
                    .downcallHandle(
                            shell.find("SHFileOperationW").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS));
            // A list of paths, each ended by a NUL, the list by another: UTF-16, two zero chars.
            byte[] path = (file.toAbsolutePath() + "\0\0").getBytes(StandardCharsets.UTF_16LE);
            MemorySegment from = arena.allocate(path.length);
            from.copyFrom(MemorySegment.ofArray(path));
            MemorySegment op = arena.allocate(FILE_OP);
            op.set(JAVA_INT, FILE_OP.byteOffset(MemoryLayout.PathElement.groupElement("wFunc")), FO_DELETE);
            op.set(ADDRESS, FILE_OP.byteOffset(MemoryLayout.PathElement.groupElement("pFrom")), from);
            op.set(JAVA_SHORT, FILE_OP.byteOffset(MemoryLayout.PathElement.groupElement("fFlags")), (short)
                    (FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_SILENT | FOF_NOERRORUI));
            int result = (int) operation.invoke(op);
            boolean aborted =
                    op.get(JAVA_INT, FILE_OP.byteOffset(MemoryLayout.PathElement.groupElement("fAnyOperationsAborted")))
                            != 0;
            if (result != 0 || aborted) throw new IOException(tr("trashOs.recycleBinRefused", result));
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(tr("trashOs.recycleBinUnreachable"), e);
        }
    }

    private static String fileName(Path file) {
        Path name = file.getFileName();
        return name == null ? "file" : name.toString();
    }
}
