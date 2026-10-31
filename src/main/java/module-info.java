module com.mirada {
    // The null annotations NullAway reads; compile-time only (pom.xml).
    requires static org.jspecify;
    requires javafx.controls;
    requires javafx.graphics;
    // The Cupertino control themes; the module opens its theme package, so the stylesheets
    // resolve to URLs from here.
    requires atlantafx.base;
    // EXIF orientation and the camera line. An automatic module (Automatic-Module-Name in its
    // manifest); its dependency xmpcore is one too, resolved because automatic modules read all.
    requires com.drew.metadata;

    // ImageIO and BufferedImage, for the formats JavaFX does not decode. AWT itself is never used:
    // App.main forces it headless before any AWT class can load.
    requires java.desktop;
    // The TwelveMonkeys readers are reached only through ImageIO's service lookup, so nothing here
    // names a class of theirs. Required anyway so a future jlink image keeps them: jlink drops a
    // module that only a ServiceLoader would find.
    requires com.twelvemonkeys.imageio.webp;
    requires com.twelvemonkeys.imageio.tiff;
    requires com.twelvemonkeys.imageio.psd;

    exports com.mirada;
}
