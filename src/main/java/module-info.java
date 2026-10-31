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

    exports com.mirada;
}
