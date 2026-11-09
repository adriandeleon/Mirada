# Decisions

One record for each choice a newcomer would question: what was known, what was decided, and what it
costs. Numbered in order; a record is not edited once accepted, only superseded by a later one.

| Record | Decision |
|---|---|
| [0001](0001-native-image-over-jlink.md) | Ship a GraalVM native image, not the jlink image |
| [0002](0002-libjpeg-turbo-over-imageio.md) | Decode JPEG with libjpeg-turbo, not ImageIO or JavaFX |
| [0003](0003-codecs-built-from-source.md) | Build the native codecs from source, never from a package manager |
| [0004](0004-properties-config.md) | Settings and bookmarks in `.properties` files |
| [0005](0005-no-testfx-yet.md) | No TestFX yet: plain JUnit, JavaFX's Headless platform and our own driver (superseded by 0006) |
| [0006](0006-testfx-and-coverage-gate.md) | TestFX over the Headless glass, and a coverage gate at `verify` |
