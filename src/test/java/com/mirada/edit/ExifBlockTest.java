package com.mirada.edit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExifBlockTest {

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(ExifBlockTest.class.getResource(name), name)
                .toURI());
    }

    /** The block as the JPEG fixture carries it, which every other fixture was made from. */
    private static byte[] jpegBlock() throws Exception {
        return Objects.requireNonNull(ExifBlock.read(fixture("/com/mirada/image/exif.jpg")));
    }

    @Test
    void readsTheSameBlockFromEveryFormatThatCarriesOne() throws Exception {
        byte[] jpeg = jpegBlock();
        assertEquals('M', jpeg[0]);
        assertEquals(42, jpeg[3]);
        // ImageMagick made the others from exif.jpg and copied its EXIF across.
        for (String name : new String[] {"exif.heic", "exif.avif", "exif.webp", "exif.png"}) {
            byte[] block = ExifBlock.read(fixture(name));
            assertNotNull(block, name);
            assertEquals(model(block), "Canon EOS R6", name);
        }
    }

    @Test
    void readsTheExifBoxOfAJpegXlContainer(@TempDir Path dir) throws Exception {
        byte[] block = jpegBlock();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(new byte[] {0, 0, 0, 12, 'J', 'X', 'L', ' ', 0x0D, 0x0A, (byte) 0x87, 0x0A});
        file.write(box("ftyp", "jxl \0\0\0\0jxl ".getBytes(StandardCharsets.ISO_8859_1)));
        file.write(box("jxlc", new byte[] {(byte) 0xFF, 0x0A, 1, 2, 3}));
        file.write(box("Exif", concat(new byte[] {0, 0, 0, 0}, block)));
        Path jxl = dir.resolve("a.jxl");
        Files.write(jxl, file.toByteArray());
        assertArrayEquals(block, ExifBlock.read(jxl));
    }

    @Test
    void filesWithoutExifHaveNone() throws Exception {
        for (String name : new String[] {"red-blue.png", "red-blue.webp", "red-blue.heic", "red-blue.jxl"}) {
            assertNull(ExifBlock.read(fixture("/com/mirada/image/" + name)), name);
        }
        // A TIFF's own tags describe its pixels; they are not a camera's EXIF.
        assertNull(ExifBlock.read(fixture("/com/mirada/image/red-blue.tif")));
    }

    @Test
    void anEditedCopyIsUprightAtItsNewSizeWithoutTheThumbnail() throws Exception {
        byte[] turned = Objects.requireNonNull(ExifBlock.read(fixture("/com/mirada/matrix/orient-6.jpg")));
        assertEquals(
                6,
                metadata(turned)
                        .getFirstDirectoryOfType(ExifIFD0Directory.class)
                        .getInt(ExifIFD0Directory.TAG_ORIENTATION));

        byte[] edited = Objects.requireNonNull(ExifBlock.forEdited(turned, 1234, 70000));
        Metadata metadata = metadata(edited);
        assertEquals(
                1, metadata.getFirstDirectoryOfType(ExifIFD0Directory.class).getInt(ExifIFD0Directory.TAG_ORIENTATION));
        assertNull(metadata.getFirstDirectoryOfType(ExifThumbnailDirectory.class));
        assertEquals(turned.length, edited.length, "patched in place, nothing moved");
    }

    @Test
    void theSizesAreTheEdits() throws Exception {
        byte[] edited = Objects.requireNonNull(ExifBlock.forEdited(jpegBlock(), 4000, 70000));
        ExifSubIFDDirectory exif = metadata(edited).getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        assertNotNull(exif);
        if (exif.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)) {
            assertEquals(4000, exif.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH));
            // Larger than a SHORT holds: the entry became a LONG.
            assertEquals(70000, exif.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT));
        }
        assertEquals("Canon EOS R6", model(edited));
    }

    @Test
    void aBlockItCannotFollowIsDropped() {
        assertNull(ExifBlock.forEdited(new byte[] {'M', 'M', 0, 42, 0x7F, 0, 0, 0}, 1, 1));
        assertNull(ExifBlock.forEdited("not a tiff".getBytes(StandardCharsets.ISO_8859_1), 1, 1));
        assertNull(ExifBlock.carried(Path.of("no such file.jpg"), 1, 1));
    }

    @Test
    void aSizeEntryWrittenAsAShortIsWidened() {
        // Little-endian, IFD0 at 8 with one entry: ImageWidth, SHORT, 1, value 100.
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0100)
                .putShort((short) 3)
                .putInt(1)
                .putShort((short) 100)
                .putShort((short) 0);
        tiff.putInt(99); // a next IFD, which the copy must drop
        ByteBuffer edited = ByteBuffer.wrap(Objects.requireNonNull(ExifBlock.forEdited(tiff.array(), 100000, 5)))
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(4, edited.getShort(12), "LONG now");
        assertEquals(100000, edited.getInt(18));
        assertEquals(0, edited.getInt(22));
    }

    private static String model(byte[] tiff) throws Exception {
        ExifIFD0Directory ifd0 = metadata(tiff).getFirstDirectoryOfType(ExifIFD0Directory.class);
        return ifd0 == null ? "" : Objects.toString(ifd0.getString(ExifIFD0Directory.TAG_MODEL));
    }

    /** What a reader makes of {@code tiff}, put into a JPEG as its APP1. */
    static Metadata metadata(byte[] tiff) throws Exception {
        Path file = Files.createTempFile("exif", ".jpg");
        try {
            Encoder.write(new Raster(2, 2, new int[] {-1, -1, -1, -1}), file, tiff);
            return ImageMetadataReader.readMetadata(file.toFile());
        } finally {
            Files.delete(file);
        }
    }

    private static byte[] box(String type, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ByteBuffer.allocate(4).putInt(8 + content.length).array());
        out.write(type.getBytes(StandardCharsets.ISO_8859_1));
        out.write(content);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
