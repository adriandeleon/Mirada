# Things that will bite you

What the code does not say by itself, by part of the app. Packaging's are in
[building-and-packaging.md](building-and-packaging.md#things-that-will-bite-you-when-packaging), and
looking at the UI without the desktop in [testing.md](testing.md#looking-at-the-ui-headless).

## Decoding and the first image

- **The first image decodes before JavaFX is up** (`HeadStart`, started from `App.main`). JavaFX's
  own decoder cannot (it asks for the screens' scale), so that head start needs a decoder of ours:
  every format but PNG, GIF and BMP. `ImageCache.load` claims it for that one file; the first claim
  closes the door, so a head start that loses the race gives up instead of holding its pixels.
  `WritableImage` still needs the toolkit: `ArgbPixels` is the toolkit-free form, wrapped later.
- **libjpeg-turbo must not change how a photo looks.** It ignores an embedded ICC profile exactly
  as JavaFX does; the JDK's ImageIO reader converts it, which is why ImageIO is not the JPEG path.
  What TurboJPEG will not decode to 8-bit RGB (CMYK, YCCK, 12-bit) is an `IOException`, and
  `ImageCache` hands the file to JavaFX: the matrix's CMYK JPEG is the test.
- **Animations decode after the still is up.** `ImageCache.animation` decodes every frame of a WebP,
  AVIF/HEIC sequence or JPEG XL on the low-priority thread once the first frame shows, so stepping
  costs no more for an animated file; `ViewerWindow` then swaps frames with `replace`. The frames
  are whole pictures (libwebp and libjxl compose partial ones) and limited to `Animation.MAX_PIXELS`
  in all; a larger animation stays a still. HEIF sequences need libheif 1.20, colour output for JPEG
  XL libjxl 0.9: both are optional bindings, so an older system library still decodes stills.
- **libheif and libjxl turn the image upright themselves; the EXIF orientation must then be
  skipped.** HEIF readers are required to apply the container's `irot`/`imir`, and iPhones write an
  EXIF orientation too; applying both turns the photo twice. `Decoder.turnsUpright` says which
  decoders do it, and `ImageCache` hands those an `Exif.upright()`. The matrix's `heic-grid.heic`
  is laid out as an iPhone's (`scripts/write-heic-grid.py`: cropped tiles, irot and EXIF 6, the
  profile on the tiles), and `NativeDecodersTest` checks it is turned once; no real iPhone photo yet.
- **The FFM bindings are hand-written against the C headers.** `heif_error` is returned by value (a
  struct; the handle takes a `SegmentAllocator` first), `JxlPixelFormat` has 4 bytes of padding
  before its `size_t`, and `JxlBasicInfo`'s offsets (`xsize` 4, `ysize` 8, `alpha_bits` 60) are
  libjxl's ABI from 0.7 to 0.12. A version that moves them breaks decoding, not the build:
  `NativeDecodersTest` is what notices. CI sets `MIRADA_REQUIRE_CODECS=1` on Linux and macOS so
  those tests fail rather than skip when a library stops loading.
- **Native access is enabled for `com.mirada`** in the javafx plugin's options (and for the
  classpath in surefire's `argLine`); without it the JDK warns on every launch. A jlink launcher
  will need the same flag.
- **Windows finds a DLL's dependencies beside the program, not beside the DLL.** `jxl.dll` loaded
  by full path cannot find `brotlidec.dll` next to it, so `NativeLibraries.preloadOnWindows` loads
  every DLL in the bundle first, retrying until nothing more loads. Linux and macOS libraries carry
  `$ORIGIN` / `@loader_path` rpaths instead, which `check-codecs.sh` asserts on Linux.
- **libjxl writes to stderr on a bad file** (`decode.cc: invalid signature`). Noise, not a failure.
- **The TwelveMonkeys readers are found by service lookup**, so nothing names their classes.
  `module-info` requires them anyway, or a jlink image would drop them silently, and
  `ImageFormatsTest.everyImageIoExtensionHasAReader` fails if one leaves the pom.

## Showing an image

- **JavaFX ignores EXIF orientation.** Phones store pixels as the sensor read them and tag how the
  phone was held; untreated, portrait photos lie on their side. `Orientation` is "mirror first, then
  turn clockwise", which is the order JavaFX applies a node's `scaleX` and `rotate`, and
  `ExifTest.mirrorThenTurnPutsTheCornerWhereTheTagSays` checks all eight against the tag's
  definition. Change one without the other and the mirrored orientations (2, 4, 5, 7) go wrong
  while the common ones still look right.
- **The window waits for both the pixels and the EXIF before it swaps images.** Showing the image as
  soon as it decodes flashes it sideways for a frame; showing it before it decodes flashes an empty
  canvas. `ImageCache.Entry.ready` completes when both are in, and `ViewerWindow.display` drops a
  result for any image other than the one most recently asked for (`wanted`).
- **A preview stands in at the full image's size.** `ImageViewport` lays out by `imageWidth` and
  `imageHeight`, not the `Image`'s own, so a 1280-pixel preview fills exactly what the full image
  will, and `replace` swaps the pixels without touching zoom or turns.
- **The viewport turns an `ImageView`, it never copies pixels.** The view is laid out unturned at the
  zoomed size and rotated about its centre, so `layoutChildren` places that centre on the centre of
  where the turned image should stand. Sizes the user sees (properties, fit) are the turned ones:
  `contentWidth`/`contentHeight`.
- **The cache holds full resolution, so it is small.** A decoded image is four bytes a pixel: a
  48-megapixel photo is about 190 MB. Three entries (the current image and one either side).
  Downsampled previews for the fitted view would let it hold more; not done.
- **A strip thumbnail of a JPEG is usually the camera's own** (`Thumbnails.embedded`): IFD1's
  JPEG, taken when it is at least 160 pixels and within 2 % of the photo's shape (`Thumbnails.fits`),
  else the photo at an eighth. metadata-extractor's "adjusted" thumbnail offset counts from the APP1
  segment, not the file, so `Thumbnails.exifTiff` walks the segments to find the TIFF block. On this
  machine's photo folders that took 56–100 % of them and made each 1–5 ms instead of 13–52. The
  queue is newest first and a cell scrolled past releases its request (`Thumbnails.release`).

## Editing and saving

- **The editor works on a preview and saves from the full image.** `Editor` shrinks the image to
  1600 pixels, makes it upright by the file's orientation (`Edit.upright`, the same "mirror, then
  turn" as `Orientation`), and every turn and flip re-arranges that preview at once. The crop is kept
  as fractions (`Crop`), so the same edit applies to the full image when saving, off the FX thread.
  It takes the window's centre, below the header, so the window's own controls stay usable; while
  it is open, `ViewerWindow.run` refuses what would change the image under it.
- **Saving never overwrites by default.** The save dialog suggests "name (edited).ext" in a format
  Mirada can write (HEIC, AVIF, JPEG XL and WebP become JPEG or PNG); the file is written beside its
  target and moved over it.
- **A saved edit keeps the EXIF, patched in place, never rebuilt.** `ExifBlock` reads the raw TIFF
  block (JPEG's APP1, PNG's eXIf, WebP's EXIF chunk, the HEIF Exif item through iinf and iloc, JPEG
  XL's Exif box) and changes only fixed-size values: orientation to 1, the image sizes to the
  edit's (a SHORT entry widened to LONG in place), and IFD0's link to the thumbnail cut. Nothing
  moves, so maker notes, whose offsets other tools break, survive. `Encoder` puts it into JPEG
  (after JFIF) and PNG (after IHDR), but not a block over JPEG's 64 KB; BMP gets none.
- **A saved TIFF is written by `Encoder.writeTiff`, not ImageIO, around the EXIF block.** The
  block is a TIFF structure whose offsets count from its own header, so the file *starts* with it,
  in its byte order: every offset, maker notes' too, stays true unread. Uncompressed strips and a
  new IFD0 follow, and the header points there; that IFD0 takes the block's IFD0 tags except those
  saying how pixels are stored (`STORAGE_TAGS`), and the profile and XMP as tags 34675 and 700.
- **A saved edit keeps the XMP, as text with a few values changed** (`Xmp`): the orientation to 1
  and the sizes to the edit's wherever it repeats EXIF's (`tiff:`, `exif:`, whatever prefixes the
  packet binds them to), and the xmp: thumbnail, Camera Raw's crop and the extended-XMP note go. A
  packet whose orientation it cannot find to set (another form, a default namespace) is dropped, as
  it would turn the upright picture again. JPEG's extended XMP (past 64 KB) is not carried.
- **A saved edit keeps the ICC profile only while it still describes the pixels.** `IccProfile`
  reads it (JPEG's APP2 segments, PNG's iCCP, WebP's ICCP, the HEIF primary item's colr through
  ipma) and `Encoder` writes it into JPEG, PNG and TIFF beside the EXIF. Mirada shows pixels as
  they are stored, profile unapplied, so carrying it is right for every decoder that leaves them so;
  `Decoder.keepsColours` names the two that do not: libjxl (asked for sRGB) and ImageIO, whose
  TwelveMonkeys WebP reader converts to sRGB. `ImageCache.keepsColours` counts a WebP as ImageIO's
  when libwebp is missing. Only an RGB profile is carried, as the edit is RGB: a CMYK JPEG's profile
  is dropped. A HEIF that gives its colours as nclx codes has no profile to carry; one is not made
  up for it. `NativeDecodersTest` checks each decoder against P3-tagged fixtures.
- **Undo is a history of whole steps** (`History`): each records the edit and the shape. A drag
  records once, when the mouse is released; a run of arrow presses records once, as a "run" that
  joins steps until something else happens. Setting the shape from code (open, reset, undo) goes
  through `Editor.quiet`, or its listener would record a step of its own.

## The window

- **Inter is the interface's face, on every platform** (`Fonts`, Pauta ICON-01, ICON-02). Its four
  faces are under `com/mirada/fonts/inter/` with their `OFL.txt`; `App.start` starts registering
  them on a thread of their own (`Fonts.load`) and waits for them just before the window is shown
  (`Fonts.ready`), which then puts `ui-font.css` on every window's scene as it appears: dialogs,
  menus and tooltips are scenes of their own and share no stylesheet. JavaFX does not fall through
  a font-family list, so without the sheet everything is drawn in the "System" face. The wait
  measured 0 ms on the Headless platform (the faces are in before the window is built), and launch
  to the first image was faster with Inter, about 500 ms against 720, than with "System". Menus pin
  Inter 14px and their keys 11px in absolute pixels (MENU-16): a popup's CSS parent is the node it
  was shown from, and `em` would follow that node's size. The native image needs the `.ttf` files and
  the sheet in its resource metadata, added by hand.
- **Every colour `mirada.css` uses is named once, in the `.root` block at its top** (PLAT-06,
  ICON-08): the rules below use the names, and `StyleContrastTest` fails a fixed colour anywhere
  else. A colour that differs between the themes is a `ladder()` of `-color-bg-default`, as
  `-state-key` is. Cupertino's tinted text and emphasis fills are overridden there, moved the least
  they need to read (ICON-10, ICON-11): tinted text 4.5:1 on both grounds and on its own subtle
  fill, white 4.5:1 on an emphasis fill, and the accent's fill, which is the focus ring too, 3:1 on
  every ground (ICON-12; Cupertino Light's #007aff was 4.0:1 under white). `Accent` applies the same
  rules to the system's accent on macOS and Windows, and gives the stylesheet's values back for
  Cupertino's blue. The test reads Cupertino's palette from AtlantaFX's jar and the overrides from
  `mirada.css`, ladders resolved, and does the WCAG arithmetic. The keyboard's focus ring is one
  `:focus-visible` layer at the end of `mirada.css`, in `-focus-ring`; keep it last.

- **Only what the first frame shows is built before it.** The header bar is; the floating controls
  are built on the first pointer movement over the image, the properties sidebar and the thumbnail
  strip the first time they open, About and Settings the first time they are asked for, and the
  folder's tooltip, the header's menu's items and the window icon run from
  `ViewerWindow.afterFirstPaint`, on the pulse after the first image is shown (or half a second
  after start without one). Building them first measured +40 ms; deferred, +20 ms over the old
  window. Put new chrome behind the same door. `HeaderBar` itself is not the cost: switched off, the
  start-up was the same. The header's three buttons are named, tooltips included, as they are built
  (`Icons.button`, which names a button with its glyph): on the Headless platform that was within
  the runs' noise.
- **Settings changes are frequent now; theme changes are not.** The window's place is saved as it
  closes (and from `App.stop`, as Cmd-Q may not hide it first), the recent folders on every folder
  opened (after the first paint: writing the settings is not start-up's), and every change runs every
  settings listener. `Theme.apply` returns at once unless the theme itself changed, as restyling
  every window costs tens of milliseconds.
- **Copy puts the picture as it stands on screen and the file.** `ViewerWindow.copy` turns the
  full image upright by the file's orientation and the user's turns (`Edit.upright`, then
  `Raster.turned`, off the FX thread) and puts it on the clipboard with the file, so an app that
  takes images pastes the picture and a folder the file. JavaFX's clipboard refuses grey pixels (a
  grey PNG loads so): `putOnClipboard` converts those to ARGB. The metadata recording's tours copy
  every image they show (`-Dmirada.tourCopies`), as nothing else there reaches the clipboard.
- **The window buttons on Linux are JavaFX's, and follow the scene's colour scheme, not the
  stylesheet.** An extended window's minimize, maximize and close are drawn by JavaFX
  (`HeaderButtonOverlay`), light or dark by `Scene.Preferences.colorScheme`, which falls back to
  the system's: `Theme` sets it on every window from the theme chosen, or a dark theme over a light
  system draws the light buttons, dark grey on the dark header. An unfocused window draws them
  `#777` in either style; `mirada.css` lightens the dark one, as a scene's stylesheet reaches the
  overlay. To tell the styles apart in a screenshot: focused, dark draws white glyphs, light `#333`.
- **A window covered by others runs at about one frame a second here.** GNOME throttles a covered
  XWayland window's frame callbacks, so every JavaFX animation and even mouse delivery slows to that
  rate: the floating controls seemed to fade seconds late in a test until timestamps showed wake and
  idle on time and only the pulses slow. Judge timing on an uncovered window, or by timestamps.

## Keys, commands and the palette

- **Every command is in `Command`, with a dotted id** (`go.next`, `editor.save`, `help.copyDetails`;
  `<area>.<camelCase>`, KBD-02), its name, its description, its group and where its keys work
  (`Command.Context`: the viewer, the editor while it is open, the Bookmarks and Notes window). The
  key filters, the palettes, the menus and the buttons all run commands from there (KBD-01): the
  editor's tools (`Editor.run`), the Bookmarks window's row actions (`BookmarksWindow.run`) and
  About's Copy details (`help.copyDetails`) included. Translations will key their strings by the id.
- **Keys come from a keymap, never from code** (KBD-09, KBD-11). `keymaps/cua.json` (Mirada's own
  keys), `cua.mac.json` (Cmd for Ctrl, with Finder's and Preview's keys: ⌘⌫ to trash, as a Mac's
  Delete is Backspace, which steps back; Return to rename, as F2 is a brightness key; ⌘L, ⌘I, ⌃⌘F)
  and `emacs.json` bind ids to `KeyChord` tokens, the shown one first; `keymap` in the settings
  picks one. `Keybindings.layered` is the one place the layers go on: the keymap, its `vertical`
  bindings while the strip stands down the side (`Command.vertical`, set by
  `ViewerWindow.stepAlong`: Up and Down step and Left and Right pan, and `chord()`, the floating
  arrows and the shortcuts window follow), then the user's (`keys.<id>` in the settings, blank to
  unbind, and `palette.keys`). A user key takes the same key from another command where both work.
  `CommandTest.everyShownChordRunsItsCommand` checks every key of every command in every keymap,
  both platforms and both axes, runs that command; `everyKeymapBindsCommandsToKeysOnce` that no key
  is bound twice. A token's modifiers may come in any order; one whose key is a character no key is
  named for (`C-?`) matches that character with or without Shift. The JSON is read with the update
  check's small reader (`update.Json`) the first time a key is pressed or a chord shown, which is
  after the first paint (`TitleBar.finish`), and registered as resources in the native metadata.
- **A menu's keys are shown, never registered** (`MenuChord`). JavaFX puts a `MenuButton`'s and
  a context menu's accelerators into the scene, where a bare one fires under the palette's text
  field and any other runs a second time. `MenuChord` is a `KeyCombination` that names the
  command's chord and never matches, so the key filter (`Keybindings.commandFor`) stays the only way in. Styled as Editora's
  `.menu-item-chord`: Inter 11px (`ui-font.css`) in its `-state-key` periwinkle, defined on `.root`
  in `mirada.css`.
- **A press outside a menu closes it and still lands** (`Popups`, Pauta MENU-07). Mirada's own
  right-click menus have auto-hide off (XWayland honours GTK's pointer grab only some of the time),
  so the window's scene closes them on any press, which then goes on: a click on a thumbnail shows
  that image, a right-click opens its own menu. `Popups.show` closes every other menu first, so one
  shows at a time (MENU-06). The Menu key and Shift+F10 open the selected thing's menu from the key
  filter (`Popups.isMenuKey`, MENU-09): the image's, under the strip's current cell or in the middle
  of the image; in Bookmarks and Notes the selected row's, under it, though the keys stay in the
  filter. The platform's own keyboard request (GTK and Windows send one, Headless none) is dropped
  (`Popups.menusByKey`), or a menu would open twice. `MenusFxTest` holds all three.
- **Every icon-only button is `Icons.button` or named by `Icons.name`** (ICON-21, KBD-17, TBAR-01):
  the tooltip and what a screen reader says set together, the tooltip built from the command's
  title and its chord now ("Rotate Right (R)", `Icons.tooltip`, TBAR-03), and rebuilt when the
  palette's keys or the strip's axis change (`ViewerWindow.relabelButtons`). The button wears its
  command's glyph from `Icons.forCommand`, the menus' (ICON-20): the zoom button the glyph of Actual
  Size or Zoom to Fit, whichever it would do; full screen's is the way out while in it, in the menus
  too (`Icons.setFullScreen`). A command without a fitting glyph gets one of its own (MENU-14):
  `MenusFxTest` opens the menus and fails on a row with none. Previous and Next are in the header's
  menu too, so the floating arrows are never the only way (TBAR-08).
- **Escape is ours, not the stage's.** JavaFX's full-screen exit key would leave full screen and
  then let the same press reach the key filter, which would close the window.
  `setFullScreenExitKeyCombination(NO_MATCH)` hands it to `Command.ESCAPE`: leave full screen if in
  it, otherwise close — on Linux only (`Command.escapeCloses`); macOS and Windows keep the window.
- **A scroll is a wheel's or a trackpad's** (`Scrolling`): macOS reports the fingers
  (`ScrollEvent.getTouchCount`), and those scrolls pan, or swipe once a gesture when there is nothing
  to pan; a wheel zooms. Windows and Linux touchpads report no fingers and so still zoom; Windows
  sends a pinch as Ctrl+wheel. Untested on a real Mac trackpad. A command that cannot run (`Command.unavailable`)
  is greyed with its reason in the palette and disabled in the context menu.
- **The command palette is a port of Termina's, which is Editora's, and the pure parts are verbatim.**
  `FuzzyMatch`, `PaletteKeys`, `TextInputKeys`, `KeyChord` and `MatchText` are Termina's files with
  the package renamed and `PaletteKeys`' localised legend replaced by plain English. A ranking or
  navigation change belongs in all three projects, or the palettes stop agreeing. One fix went the
  other way and is not in Termina yet: `TextInputKeys.actionFor` checks for a null chord, because a
  bare Ctrl or Shift press has none and `Map.ofEntries` throws on a null key.
- **The palette also finds images** (`Command.GO_TO_IMAGE`, `CommandPalette.showImages`): its rows
  are `Row`s, a command or an image of the folder, and file names rank by the same `FuzzyMatch`,
  ties in the folder's order (`orderedImages`). Ctrl+P opens it but does not close it: inside the
  palette Ctrl+P is the Emacs key for the row above, so only the palette's own keys close it from
  the keyboard; Escape closes either.
- **While the palette is up, the scene's key filter stands aside.** Filters run from the scene down,
  so `ViewerWindow.onKey` sees every key before the palette's card does; it returns early while the
  palette shows (or typing "r" would rotate the image) and handles only the palette's own keys, to
  close it, and not even those when the open palette moves or edits by them
  (`PaletteChords.keptByPalette`: a user who binds C-p keeps C-p as the row above in there). Escape
  is the palette's then, not the window's.
- **The palette's keys are the user's** (`PaletteChords`, `palette.keys` in the settings, as
  `KeyChord` tokens: `C-S-p M-x f1` by default, `Cmd-S-p` on a Mac): the user's layer over the
  keymap's `palette.show`, put on before any other user key, so nothing takes them. `onKey` asks them
  before any other key, the editor's too, in the viewer and in the Bookmarks window, which has a
  palette of its own. The first is the one shown (`Command.PALETTE.chord()`), the Mac menu's is the
  first with Cmd, and a setting with no readable key is the default. Parsed on the first key press,
  not at start-up.
- **The palette lists every command** (PAL-02), Escape, Command Palette, the Pan commands, the
  editor's tools and the Bookmarks window's included, greyed with the reason where they cannot run
  (`ViewerWindow.unavailable`: "The editor is not open.", or "Finish editing first" for what would
  change the image under the editor). Those that work where it opened lead its list
  (`Command.inPalette(Context)`).
- **The editor's and the Bookmarks window's own keys stay theirs where no command fits.** The arrows
  move and size the crop (`Editor.onKey`); the Bookmarks window's filter keeps Up, Down, Ctrl+P and
  Ctrl+N to move, Escape, and Delete to remove while the filter is empty. Everything else there is a
  command, so it has a key in the keymap and a row in the palette.

## Bookmarks and notes

- **Bookmarks and notes are Mirada's, never the file's** (`Bookmarks`, Editora's bookmarks and
  personal notes moved to pictures). One mark per path, image or folder: bookmarked, a note, or
  both; with neither it is dropped. Paths are kept absolute and normalised, as `FolderImages` lists
  them, so `indexOf` finds a mark in the open folder. The file is read after the first paint
  (`ViewerWindow.showMarks` does nothing before, so the first image never waits for it), and every
  change reads it again before writing, so a second window's marks survive. A rename in Mirada
  carries the mark (`Bookmarks.moved`); a file moved or renamed elsewhere loses it, and a mark
  whose file is gone is kept (an unplugged drive) but left out of Go to Bookmark. The badge over
  the image (`MarksBadge`) is built the first time a shown image has a mark. The Bookmarks and
  Notes window (`BookmarksWindow`, Editora's two tool windows in one) lists them by folder, the
  rules in `BookmarkList` (latest folder first, images by name, every word in name, folder or
  note); it is built when first asked for and reads the file again each time it is brought up. Its
  keys stay in the filter field, as the palette's do: the list is not focus-traversable.

## Platforms

- **One Mirada per config folder, and the check is one file test.** `App.main` asks
  `SingleInstance.handOff` before anything decodes: with no `instance.sock` in the config folder
  (or, for a folder whose path is too long for a socket, in the temp folder) it returns at once and
  loads no networking classes. Becoming the one that listens (`claimInBackground`) runs on a thread
  of its own: the folder's `instance.lock` (`InstanceLock`, byte 0 exclusive for the primary, byte 1
  shared by every other) decides who binds the socket, so two launches in one instant never take it
  from each other, and a crash leaves a socket nobody answers, which the next primary removes.
  Measured headless, interleaved with the commit before, under load: 650 against 660 ms to the first
  paint, within noise. **A thread waiting in `accept()` held the JVM's exit back by about 300 ms**;
  a shutdown hook closes the socket first. A measured start (`MIRADA_PERF`) or a tour (AOT training,
  the smoke tests) never hands off, so a Mirada you have open cannot swallow a training run; a dev run
  on your own config folder does, so use `--dev` or `--new-instance` beside your own Mirada. macOS's
  Finder and `open` never make a second process: their Apple Event reaches the running app
  (`MacOpenFiles`). `scripts/headless-run.sh` uses a throwaway config folder, so it is always alone,
  and `dev-run.sh` now hands Mirada's own options to the app, not the JVM.
- **The programs Mirada starts get the login shell's `PATH` after its own** (`ToolPath`, CLI-10): the
  shell (`$SHELL -l -i`) is asked once, the first time a program starts, never at start-up. Its own
  comes first so a launcher's or the headless runs' stubs still win; Java looks a program up on its
  own `PATH`, not the child's, so `ToolPath` resolves it to a full path too. Windows keeps its own.
- **`--help` and `--version` must `System.exit`.** Started as an `Application` (the jlink image, dev
  runs), the JDK's launcher starts the toolkit before `main`, whose thread keeps the process alive.
  The native image starts from `Launcher`, a plain class, and starts no toolkit for them.
- **`java.awt.headless` is decided in `App.main`, as the first statement**, before `HeadStart` can
  touch ImageIO: headless everywhere but macOS, where Move to Trash is AWT's `Desktop`
  (`Trash.headless`); AWT itself loads only when a file is first trashed. ImageIO and `BufferedImage` are `java.desktop` and need no display; tests are
  headless on every platform through surefire's `java.awt.headless`.
- **Move to Trash is three implementations.** Linux follows the freedesktop.org spec itself (info
  file first, created exclusively to reserve the name, then an atomic move) and leaves a file on
  another drive to `gio trash`; Windows calls `SHFileOperationW` through FFM with undo; macOS uses
  AWT's `Desktop`, AWT being headful there. Only Linux's is tested here (`TrashTest`, against a
  temporary trash). Only Linux's can be undone (`Trash.restore`, `Command.UNDO_TRASH`): Mirada
  wrote the info file, so it knows where the file went; it never overwrites a file that came since.
  A file sent to `gio trash` (another drive) cannot be, nor the Recycle Bin's or the Mac's, whose
  Trash needs Full Disk Access to read.
- **macOS does not pass a double-clicked file as an argument.** Linux (`Exec=… %f`) and Windows do.
  macOS sends an "open documents" Apple Event, which AppKit gives the application's delegate,
  JavaFX's `GlassApplication`. Since JavaFX 25 (JDK-8354631) that delegate answers
  `application:openURLs:`, which AppKit prefers to `application:openFiles:`, and only posts an
  `AWTEmbeddedEvent` notification with each URL, for an AWT embedded in JavaFX: glass's own
  open-files event (`Application.EventHandler.handleOpenFilesAction`) never fires, and AWT's
  `Desktop` handlers start too late for the event that launched the app. So 0.1.0 opened nothing from
  Finder (jpackage's launcher had hidden it before the native image replaced it), and so did a glass
  handler. `MacOpenFiles` observes the notification itself: an Objective-C class made at run time
  through FFM, its method an upcall, registered from `App.main` before `launch`; files that arrive
  before the window are held until `App.start` asks. A file named on the command line also arrives
  as an event and is dropped once (`besides`). The upcall, its lookup and the `objc_msgSend` shapes
  are in the native metadata by hand: the tracing agent never sees a Finder event. Proved on CI's
  Macs, not by hand: the smoke test given `Mirada.app` opens a fixture through `open -a`, then
  again in the running app. If JavaFX starts calling `openFiles:` again, check this is not doubled.
  **The JVM image (Intel) misses the launching file**: jpackage's launcher (`JLI_Launch`) starts
  Java on a second thread while the main thread waits without AppKit, and the event comes and goes
  in between. Files sent to a running Mirada arrive; the smoke test warns there and fails the
  native image.
- **The macOS menu bar is JavaFX's system `MenuBar`, the application menu is glass's.** `MacMenus`
  builds File, Image, View, Go and Help from `Command` after the first paint, unmanaged in the scene
  (it draws nothing there); the header's ☰ menu stays as well, as on the other platforms. No item
  has a key equivalent (Pauta MENU-02): the native menu would take the key before `onKey`, and a
  bare one while the palette's field has focus. Each chord is written into its item's title instead
  ("Rotate Right   R", `MacMenus.title`, MENU-15), as the native menu draws text only (and so no
  icons); a Cmd key reaches `Keybindings.commandFor` like any other. Items grey, and their titles are
  written again, only while their menu is open (`onShowing`, by `Command.unavailable`). JavaFX gives no way into the application menu (Hide, Quit), so `MacAppMenu`
  gets it from `com.sun.glass.ui.mac.MacApplication.getAppleMenu`, by reflection, since that
  package exists only in macOS's JavaFX: macOS's launchers pass `--add-exports
  javafx.graphics/com.sun.glass.ui.mac=com.mirada` (the pom's `glass.mac.option`, a harmless `-D`
  elsewhere, as the export would warn; `dev-run.sh` and `aot-train.sh` add it on Darwin, and the
  AOT cache needs training and launch to agree), and the native image has the method in its
  reflection metadata, added by hand. Untested on a Mac.
