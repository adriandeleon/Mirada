# Changelog

Notable changes, newest first. Versions follow [semantic versioning](https://semver.org); until
1.0.0 the minor number moves for anything user-visible.

## 0.2.0 — unreleased

### Fixed

- **macOS: an image opened from Finder shows**: a double-click, "Open With" or a file dropped on the
  Dock icon opened an empty window in 0.1.0, because the file never reached Mirada. On an Intel
  Mac, the file reaches Mirada only when it is already open.
- **macOS: Move to Trash has a key**: ⌘⌫, as in Finder. Its Delete key is Backspace, which steps
  back an image and never deletes.
- **macOS and Windows: Escape no longer closes the window**; it stops the slideshow and leaves full
  screen. Linux keeps GNOME's habit.

### Changed

- **A trackpad pans and swipes**: pinch to zoom, two fingers to move about a zoomed-in image, and a
  sideways swipe for the next or previous image when there is nothing to pan. A mouse wheel still
  zooms, and Ctrl or ⌘ with any scroll zooms (a Windows touchpad's pinch arrives that way).
- **macOS and Windows: the system's accent colour** for the current thumbnail, the palette's
  highlighted row and every selection, in place of a fixed blue, and followed as it changes. A pale
  or deep accent is adjusted for text so it stays legible. Linux keeps the blue.
- **Settings are kept where each platform keeps them**: `~/Library/Application Support/Mirada` on
  macOS and `%APPDATA%\Mirada` on Windows, moved there from `~/.mirada` on the first start. Linux
  keeps `~/.mirada`.
- **Windows calls it the Recycle Bin**: "Move to Recycle Bin", and so do the messages.
- **The header follows the platform**: no full-screen button on a Mac, whose green window button
  is one, and the title on the left on Windows, as its own title bars have it.
- **macOS has Preview's and Finder's keys** besides Mirada's own: Return renames, ⌘I shows the
  properties, ⌘R and ⌘L rotate, ⌃⌘F is full screen; the function keys still work. Ctrl+R and
  Ctrl+Shift+R rotate on every platform.

### Added

- **macOS: a menu bar.** File, Image, View, Go and Help hold every command, with Preview's ⌘ keys
  beside them and ticks on what is on (the panels, the order); About and Settings… (⌘,) are in the
  Mirada menu, and the Help menu's search finds any command by name. The header's ☰ menu is gone
  on a Mac.
- **A saved edit keeps the photo's colour profile**, in JPEG and PNG: a wide-gamut photo (an
  iPhone's Display P3, a camera's Adobe RGB) no longer saves duller than it was in apps that manage
  colour. Read from JPEG, PNG, WebP, HEIC and AVIF.

## 0.1.0 — 2026-11-01

The first version: open a photo, look, arrow through its folder, close.

### Added

- **WebP, TIFF and PSD**, alongside JPEG, PNG, GIF and BMP. An animated WebP shows its first frame.
- **HEIC, AVIF and JPEG XL**, through libheif and libjxl, built from source for each platform and
  bundled (3–4 MB): nothing to install, and the same versions everywhere. JPEG XL decodes on every
  core.
- **The packaged app shows the first image about an eighth of a second after launch** (Linux,
  measured): it is compiled ahead of time into a native executable (GraalVM, with JavaFX linked in
  by StaticFX) on Linux, Windows and Apple silicon, and uses a third less memory. Intel Macs get a
  jlink runtime with an AOT cache trained across every format.
- **JPEG decodes two to three times faster**, through libjpeg-turbo (bundled, like the other
  codecs); JavaFX still takes the CMYK and 12-bit files it declines. A 24-megapixel photo opens
  in about 170 ms from launch.
- **The first image decodes while the window is still being built**, for every format but PNG,
  GIF and BMP, so a large photo is often ready before the window is.
- **An editor** (E): crop, freely or held to a shape (square, 5:4, 4:3, 3:2, 16:9, or the
  original's), turn and flip, then save the result as a new file in JPEG, PNG, TIFF or BMP. The
  original is untouched unless you choose it in the save dialog. Every step has a key, and every
  step can be undone (Ctrl+Z) and redone (Ctrl+Shift+Z or Ctrl+Y), Reset included.
- **A saved edit keeps the photo's EXIF**: camera, lens, exposure, date and location, from a JPEG,
  HEIC, AVIF, WebP, PNG or JPEG XL, into a JPEG or PNG. It is marked upright and given the edit's
  size, and the old thumbnail is left out.
- **Animated WebP, AVIF and JPEG XL play**, as GIFs already did. The still shows at once and starts
  moving when the rest of the frames are in.
- **WebP opens ten times faster**, through libwebp (bundled): a 24-megapixel WebP in about a fifth
  of a second instead of two.
- **JPEG XL shows in the right colours**: it is converted to sRGB like every other format, rather
  than shown in whatever colour space it was stored in.
- **Move to Trash** (Delete), **Rename** (F2), **a slideshow** (F5, full screen, at the speed set in
  Settings), and **sorting by name, date or size**, reversible and remembered.
- **Keep the zoom when stepping** (Z), to compare the same detail across a burst of shots; and the
  arrow keys pan a zoomed image (Shift+Left and Right, Up and Down).
- **A Keyboard Shortcuts window** (Ctrl+?), listing every key from the same table the keys run on.
- **Stepping back is instant further back**: the last two dozen images leave a preview behind,
  shown at once while the full resolution decodes again. Thumbnails of JPEGs decode at an eighth
  of the size.
- **Installers**: deb, rpm and tar.gz for Linux (x64 and arm64), dmg for macOS (Apple silicon and
  Intel), msi for Windows, each registering Mirada for the image types it opens. Not signed yet.
  On macOS, a double-click in Finder, "Open With" and a file dropped on the Dock icon open the
  image, at launch and while Mirada is running.
- **An icon**, in the family of Editora, Termina and Torrenta: a teal iris in a periwinkle eye.
- **32-bit BMPs with transparency open.** JavaFX's own decoder fails on them; Mirada now hands
  any file JavaFX cannot decode to ImageIO before calling it an error.
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
