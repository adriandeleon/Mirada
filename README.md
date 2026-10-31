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

**Status:** early, and unreleased. JPEG, PNG, GIF and BMP, turned upright from their EXIF
orientation. No installers yet — build it from source. See the [changelog](CHANGELOG.md).

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
| Ctrl+O | Open |
| Ctrl+W, Ctrl+Q | Close |

On macOS, Cmd takes Ctrl's place.

## Building it

JDK 25 is required (the build refuses others). The Maven wrapper fetches everything else.

```
./mvnw javafx:run                                   # an empty window
./mvnw javafx:run -Djavafx.args=/path/to/photo.jpg  # a photo and its folder
./mvnw verify                                       # tests, null checks, formatting
```

The project notes for contributors, human or not, are in [AGENTS.md](AGENTS.md).

## Licence

MIT — see [LICENSE](LICENSE). Third-party notices are in [NOTICE](NOTICE).
