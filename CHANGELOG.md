# Changelog

Notable changes, newest first. Versions follow [semantic versioning](https://semver.org); until
1.0.0 the minor number moves for anything user-visible.

## 0.1.0 — unreleased

The first version: open a photo, look, arrow through its folder, close.

### Added

- **WebP, TIFF and PSD**, alongside JPEG, PNG, GIF and BMP. An animated WebP shows its first frame.
- **HEIC, AVIF and JPEG XL**, through libheif and libjxl, built from source for each platform and
  bundled (3–4 MB): nothing to install, and the same versions everywhere. JPEG XL decodes on every
  core.
- **The packaged app shows the first image about a quarter of a second after launch** (Linux,
  measured): a jlink runtime with an AOT cache trained across every format, and on Linux a launcher
  that skips the 300 ms jpackage's spends asking dpkg about itself.
- **Installers**: deb, rpm and tar.gz for Linux (x64 and arm64), dmg for macOS (Apple silicon and
  Intel), msi for Windows, each registering Mirada for the image types it opens. Not signed yet.
  On macOS, a double-click in Finder, "Open With" and a file dropped on the Dock icon open the
  image, at launch and while Mirada is running.
- **An icon**, in the family of Editora, Termina and Torrenta: a teal iris in a periwinkle eye.
- **A file whose metadata cannot be read still opens.** A failure inside the EXIF reader no longer
  takes the image with it, and anything unexpected on the way to the screen is shown and logged
  rather than leaving the previous image up.
- **Opening a file opens its folder.** The images around it are listed in natural name order, so
  `IMG_9` comes before `IMG_10`, and hidden files are left out. Opening a folder starts at its first
  image.
- **Photos are turned upright from their EXIF orientation**, including the mirrored ones.
- **A window laid out as GNOME Image Viewer's.** A header bar with the file name, full screen,
  rotate, properties and the main menu, drawn in the title bar where the platform allows; floating
  previous/next and zoom controls over the image that fade when the pointer rests.
- **A properties sidebar** (I): folder, position, image size, format, file size, dates, and for
  photos a camera card (camera, lens, date taken, exposure, aperture, ISO, focal length, flash,
  location) and every metadata tag under All Metadata.
- **A thumbnail strip** (T), closed by default: the folder's images along the bottom, made in the
  background for the ones in view.
- **Settings** (Ctrl+,): theme (follow the system, light or dark), what opens at start, how long the
  floating controls stay. Applied as you change them, saved in `~/.mirada/settings.properties`.
- **About** (F12): version, licence, and the Java, JavaFX and codec versions.
- **Zoom and pan.** The wheel zooms about the pointer, `+` and `-` step through zoom stops, `0` fits
  the window and `1` shows actual size; double-click toggles the two. Drag to pan. Above 200 % the
  pixels are drawn as crisp squares.
- **Rotate** with `R` and Shift+`R`, **full screen** with `F` or F11, and **Escape** to leave full
  screen or close the window.
- **Stepping is instant** for the image either side: they load once the current image is up, and
  the window keeps the previous image on screen until the next one is ready.
- **A command palette** (Ctrl+Shift+P or F1; Cmd+Shift+P on macOS): every command by name, with its
  key and a one-line description, matched as you type. Commands that cannot run right now, such as
  Next Image on the last image, are greyed with the reason. The same palette as Termina and Editora.
- **Open with Ctrl+O, or drop a file or folder on the window.**
- **Light and dark themes** follow the system setting.
