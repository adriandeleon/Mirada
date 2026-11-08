# Changelog

All notable changes to Mirada are documented in this file, newest first.

The format is based on [Keep a Changelog 1.1](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html); until 1.0.0 the minor number
moves for anything user-visible. Each entry opens with a bold one-line summary written for users.

## [Unreleased]

### Added

- **Help → Documentation opens the user guide** at
  [mirada-project.dev/docs](https://mirada-project.dev/docs/) in your browser: in the palette, in the
  header's menu and the Mac's Help menu right after Command Palette, and as a link in an empty
  window. **About links this version's release notes** (the changelog, for a development build).
- **`mirada --help` and `mirada --version`** print and exit without opening a window; `--version`
  names the build, the commit and the Pauta edition. `--config-dir <folder>` keeps settings and
  bookmarks in another folder, and `--dev` in `~/.mirada-dev`, apart from your own.
- **About names the commit and the [Pauta](https://pauta-project.dev/4.0.1/) edition Mirada is
  built to (4.0.1), with Copy details**, which copies the version, runtime, codecs and OS for a bug
  report.
- **The menus show each command's key**, right-aligned beside it in Editora's small periwinkle: the
  header's menu, the right-click menus over the image and the thumbnail strip, and the zoom menu.
  The palette's is whichever key Settings gives it. They only name the keys: pressing one runs it as
  before, and never twice, nor while typing in the palette.
- **The thumbnail strip has a menu of its own**: right-click it to hide it, stand it at the side or
  along the bottom, or show names instead of pictures or under them. The header's menu ticks
  Thumbnails and Image Properties while they are open, and turns them on and off.
- **With the thumbnails at the side, Up and Down step through the folder**, as the strip runs, and
  the arrows over the image point up and down; Left and Right pan a zoomed-in image instead, and
  Shift+Up and Shift+Down pan up and down. Along the bottom, the keys are as before.
- **Open Image and Open Folder buttons in an empty window**, under "Open an image with Ctrl+O",
  and **Open Folder…** (Ctrl+Shift+F, ⇧⌘F on a Mac) in the menus and the palette: a folder can now
  be opened without dropping it on the window. Open… is now called Open Image….
- **Bookmarks and notes on images and folders**, as Editora keeps them on files. `B` bookmarks the
  image and Shift+`B` its folder; `N` writes a note of your own on the image and Shift+`N` on the
  folder, kept by Mirada (`bookmarks.properties`, beside the settings), never in the file.
  `]` and `[` step to the next and previous bookmarked image in the folder, and **Go to
  Bookmark…** (Ctrl+B) finds any bookmark or note, in any folder, by its name or its note's words.
  A bookmarked image wears a bookmark in its corner and in the thumbnail strip, its note is a
  click away over the image, and the properties sidebar shows both, the folder's too. A rename in
  Mirada keeps them.
- **Bookmarks and Notes** (Ctrl+Shift+B, ⇧⌘B on a Mac; also in the header's menu, the right-click
  menu and the palette): a window listing every bookmark and note, as Editora's Bookmarks and
  Personal Notes tool windows do, grouped by folder, each image with its thumbnail and its note.
  Type to filter by name, folder or note; show all, only bookmarks or only notes (Ctrl+1–3).
  Return or a double-click opens the image in the viewer, F2 edits the note, Ctrl+B takes the
  bookmark off or puts it on, and Ctrl+Delete removes the mark (asking first when it has a note).
  A mark whose file has gone is listed dimmed, so it can be removed.
- **An AppImage for Linux** (x64 and arm64): one file that runs on any distribution with GTK 3,
  without installing anything or needing root.

### Changed

- **Leaving the editor with an edit not yet saved asks first**: Cancel, Escape, Close Window and the
  window's close button offer Discard or Cancel. Saving under a name that only the added extension
  makes into an existing file asks before replacing it.
- **Removing a note asks with a Remove button**, and says the note cannot be brought back.

- **Full screen is a floating button over the image's top left**, with the previous/next and zoom
  controls, in place of the header's button; in full screen it leaves it, so the mouse has a way
  back with the header gone. A Mac has it too. An image's bookmark and note badge stands beside it.
- **macOS: the header's ☰ menu is back**, beside the menu bar rather than instead of it, so the
  window's own commands are a click away there as on Linux and Windows.
- **The header shows the folder before the file name**, as "folder / photo.jpg" on one line. It
  stood on a second line under the name, which the header was too short to show.
- **A saved edit keeps the photo's XMP**: its title, description, keywords, rating and rights, as
  Lightroom, Photos or a phone wrote them, in a JPEG, PNG or TIFF; the orientation and size in it
  become the edit's.
- **A saved TIFF keeps the EXIF and the colour profile**, as JPEG and PNG already did: the camera,
  the date, the exposure, the location, and the colours of a wide-gamut photo.
- **The macOS download is notarized by Apple, and every release is signed**: the dmg opens without
  **Open Anyway**, and a release is no longer published without its signatures. The checksums are
  now `checksums_sha256.txt`, signed as `checksums_sha256.txt.asc` (they were `SHA256SUMS` and
  `SHA256SUMS.asc`); the deb also carries a debsigs origin signature, which `debsig-verify` checks;
  each Linux file keeps its `.asc`, and the signing key stays `mirada-signing-key.asc`.

### Fixed

- **A settings or bookmarks file that will not read is no longer lost.** One that will not decode is
  kept beside it as `….corrupt.bak`, and Mirada starts from the defaults; one that cannot be read at
  all is left alone, where before the next bookmark could replace every mark in it.
- **An error no code caught is said over the window** and written to standard error, instead of
  passing unseen.
- **A click anywhere outside a menu closes it.** On Linux a click on the image could leave the
  right-click menu open, and each further right-click opened another beside it. Any press in the
  window now closes its open menus, the right-click menus and the header's and zoom menus alike.
- **The minimize, maximize and close buttons are legible in the dark theme on Linux.** JavaFX drew
  them by the system's light or dark setting, not Mirada's theme, so a dark theme over a light
  setting left them dark grey on the dark header bar; they now follow the theme.

## [0.3.0] - 2026-11-05

### Added

- **The command palette opens with M-x too** (Alt+X; Option+X on macOS), besides Ctrl+Shift+P and
  F1, and **its keys are yours to choose**: Settings → Keys → Command palette adds a key by pressing
  it, or takes one off by pressing it again; or edit `palette.keys` in `settings.properties`. A key
  chosen there wins over what it did before.

### Changed

- **The thumbnail strip fills four to fifty times faster with photos**: a camera's or phone's JPEG
  carries a small copy of itself, and the strip now shows that (when it is large enough and the
  photo's shape) instead of decoding the photo. Up to four thumbnails are made at once, the ones on
  screen first, and those scrolled past before they were made are no longer made at all.

### Fixed

- **Windows: Mirada starts on a PC without the Visual C++ runtime.** 0.2.x needed it installed
  and otherwise failed at once ("vcruntime140.dll was not found", or exit code 0xC0000135); the
  three DLLs now come with Mirada.
- **The thumbnail strip no longer disappears when it is moved from the side back to the bottom.**
- **Copy works on a greyscale PNG**: it did nothing, as the clipboard takes only colour pixels; it
  now goes as colour.

## [0.2.2] - 2026-11-05

### Fixed

- **Linux x64: Mirada starts again on Debian 13, Ubuntu 24.04 and 22.04.** 0.2.0 and 0.2.1 were
  built on Ubuntu 26.04 and needed its glibc 2.43 ("version `GLIBC_2.43' not found"). The arm64
  packages were not affected.

## [0.2.1] - 2026-11-05

### Fixed

- **Linux: the deb upgrades and uninstalls.** Installing a new version over 0.1.0 or 0.2.0 failed
  with "pre-removal script subprocess returned error exit status 2", as did removing them: their
  removal script did not parse. Installing this version over either works (dpkg warns about the old
  script once).

## [0.2.0] - 2026-11-05

### Added

- **macOS: a menu bar.** File, Image, View, Go and Help hold every command, with Preview's ⌘ keys
  beside them and ticks on what is on (the panels, the order); About and Settings… (⌘,) are in the
  Mirada menu, and the Help menu's search finds any command by name. The header's ☰ menu is gone
  on a Mac.
- **Go to Image** (Ctrl+P, ⌘P): the palette over the folder's file names, the current one under the
  cursor; type part of a name and press Enter to show it.
- **Open Recent** (Ctrl+Shift+O, ⇧⌘O): the palette over the last ten folders, by name, the path under
  the list.
- **Copy** (Ctrl+C, ⌘C): the image as it stands on screen, turned as shown, and its file, so it
  pastes into an app as a picture and into a folder as the file.
- **Show in Finder / Show in File Explorer / Show in Folder**: the image's folder in the file
  manager, with the image selected.
- **Undo Move to Trash** (Ctrl+Z) on Linux: puts the last image moved to the Trash back where it
  was, and again for the one before, up to twenty. The Recycle Bin and the Mac's Trash do not say
  where a file went, so there it is put back from the desktop's own.
- **Drag a photo out**: a fitted image, or a thumbnail, drags its file into a chat, a mail or a
  folder. Zoomed in, dragging still pans.
- **The thumbnail strip can stand down the left side and list file names instead of pictures**:
  Thumbnails at the Side and Names Instead of Thumbnails, in the palette, the menus and Settings.
  Names load no thumbnails at all, which suits a folder of thousands.
- **Names under thumbnails**: each thumbnail can have its file name centred under it, along the
  bottom or down the side (Names Under Thumbnails, in the palette, the menus and Settings).
- **The header names the folder being shown**, under the file name, with its whole path as the
  tooltip.
- **Icons in the menus**: the main menu, the right-click menu and the zoom menu show each command's
  icon, and settings and panels are ticked when on.
- **A checkerboard behind transparent images**, so their transparent parts show as such.
- **Screen readers name the icon buttons** in the header and the floating controls.
- **A saved edit keeps the photo's colour profile**, in JPEG and PNG: a wide-gamut photo (an
  iPhone's Display P3, a camera's Adobe RGB) no longer saves duller than it was in apps that manage
  colour. Read from JPEG, PNG, WebP, HEIC and AVIF.

### Changed

- **A trackpad pans and swipes**: pinch to zoom, two fingers to move about a zoomed-in image, and a
  sideways swipe for the next or previous image when there is nothing to pan. A mouse wheel still
  zooms, and Ctrl or ⌘ with any scroll zooms (a Windows touchpad's pinch arrives that way).
- **macOS has Preview's and Finder's keys** besides Mirada's own: Return renames, ⌘I shows the
  properties, ⌘R and ⌘L rotate, ⌃⌘F is full screen; the function keys still work. Ctrl+R and
  Ctrl+Shift+R rotate on every platform.
- **The header follows the platform**: no full-screen button on a Mac, whose green window button
  is one, and the title on the left on Windows, as its own title bars have it.
- **macOS and Windows: the system's accent colour** for the current thumbnail, the palette's
  highlighted row and every selection, in place of a fixed blue, and followed as it changes. A pale
  or deep accent is adjusted for text so it stays legible. Linux keeps the blue.
- **Settings are kept where each platform keeps them**: `~/Library/Application Support/Mirada` on
  macOS and `%APPDATA%\Mirada` on Windows, moved there from `~/.mirada` on the first start. Linux
  keeps `~/.mirada`.
- **Windows calls it the Recycle Bin**: "Move to Recycle Bin", and so do the messages.
- **The window opens where it was**, at its size, and maximised if it was; a place no longer on any
  screen (a monitor unplugged) is left to the system.
- **The pointer hides in full screen** after the same rest as the floating controls.

### Fixed

- **macOS: an image opened from Finder shows**: a double-click, "Open With" or a file dropped on the
  Dock icon opened an empty window in 0.1.0, because the file never reached Mirada. On an Intel
  Mac, the file reaches Mirada only when it is already open.
- **macOS: Move to Trash has a key**: ⌘⌫, as in Finder. Its Delete key is Backspace, which steps
  back an image and never deletes.
- **macOS and Windows: Escape no longer closes the window**; it stops the slideshow and leaves full
  screen. Linux keeps GNOME's habit.

## [0.1.0] - 2026-11-01

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
