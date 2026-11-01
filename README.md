<img src="branding/mirada.png" alt="" width="96" align="left" hspace="14" vspace="4">

# Mirada

**A fast, folder-oriented image viewer.** Double-click a photo, look, arrow through the folder, close.

[![CI](https://github.com/adriandeleon/Mirada/actions/workflows/ci.yml/badge.svg)](https://github.com/adriandeleon/Mirada/actions/workflows/ci.yml)
[![licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)
[![JDK 25](https://img.shields.io/badge/JDK-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![JavaFX 27](https://img.shields.io/badge/JavaFX-27-blue.svg)](https://openjfx.io/)
[![platforms](https://img.shields.io/badge/platforms-Linux%20%7C%20macOS%20%7C%20Windows-lightgrey.svg)](#building-it)

Java 25 + JavaFX 27.

An image viewer is the app you open a hundred times a day for three seconds each. Mirada treats the
folder as the document: open one photo and the rest of the folder is already there, in the order
you'd number it, one arrow key away. The image fills the window; a single status line under it says
which file, where it sits in the folder, how large it is, and what took it.

<br clear="left">

**Status:** early, and unreleased. JPEG, PNG, GIF, BMP, WebP, TIFF, PSD, HEIC, AVIF and JPEG XL,
turned upright. The HEIC, AVIF and JPEG XL decoders (libheif, libjxl) are built from source and
bundled; the build downloads them. See the [changelog](CHANGELOG.md).

## Installing it

Installers for Linux (x64 and arm64), macOS (Apple silicon and Intel) and Windows will be on the
[releases page](https://github.com/adriandeleon/Mirada/releases). Each registers Mirada for the image
types it opens. None is signed yet, so each platform asks once:

- **Linux** — `sudo apt install ./Mirada-<version>-linux-x64.deb` (apt fetches what it needs), or
  the `.rpm` with `dnf`. The `.tar.gz` runs from wherever it is unpacked: `bin/Mirada`.
- **macOS** — open the `.dmg` and drag Mirada to Applications. macOS refuses the first launch of an
  unnotarized app: open **System Settings → Privacy & Security**, find Mirada near the bottom, and
  choose **Open Anyway**.
- **Windows** — run the `.msi`. SmartScreen warns about an unknown publisher: **More info → Run
  anyway**.

## Using it

Open a photo or a folder from the command line, with Ctrl+O, or by dropping it on the window.
Drag to pan a zoomed image; the wheel zooms about the pointer.

| Key | Does |
|---|---|
| Right, Page Down, Space | Next image |
| Left, Page Up, Backspace, Shift+Space | Previous image |
| Home / End | First / last image |
| `+` / `-` | Zoom in / out |
| `0` / `1` | Fit to window / actual size (double-click toggles them) |
| `R` / Shift+`R` | Rotate right / left |
| `F`, F11 | Full screen |
| Escape | Leave full screen, or close |
| Ctrl+Shift+P, F1 | Command palette: every command, by name |
| Ctrl+O | Open |
| Ctrl+W, Ctrl+Q | Close |

On macOS, Cmd takes Ctrl's place.

## Building it

JDK 25 is required (the build refuses others). The Maven wrapper fetches everything else, including
the native codec bundle for your platform from the
[codecs-1 release](https://github.com/adriandeleon/Mirada/releases/tag/codecs-1).

```
./mvnw javafx:run                                   # an empty window
./mvnw javafx:run -Djavafx.args=/path/to/photo.jpg  # a photo and its folder
./mvnw verify                                       # tests, null checks, formatting
./mvnw clean -Pdist -DskipTests package             # the app, in target/dist
```

Add `-Dskip.installers=false` to wrap it into this platform's installers, in `target/installers`.

The app in `target/dist` starts in about a quarter of a second: a jlink runtime with Mirada in it,
an AOT cache trained by stepping through every format, and the codec bundle.

The project notes for contributors, human or not, are in [AGENTS.md](AGENTS.md).

## Licence

MIT — see [LICENSE](LICENSE). Third-party notices are in [NOTICE](NOTICE).
