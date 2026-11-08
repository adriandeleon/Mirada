# Architecture

How Mirada is put together: its packages, its decoders and where their native libraries come from.
How it is built and shipped is in [building-and-packaging.md](building-and-packaging.md), what will
bite you in each part in [gotchas.md](gotchas.md), and why the larger choices were made in
[decisions/](decisions/README.md).

## Packages

| Package | What |
|---|---|
| `com.mirada.folder` | The "document": the images in one folder, in the chosen order (`Sorting`: natural name order, date or size), and which one is showing (`FolderImages`, `NaturalOrder`, `ImageFormats`) |
| `com.mirada.image` | Loading: decoded images cached by path (`ImageCache`), EXIF orientation and the camera line (`Exif`, `Orientation`), the sidebar's camera card and every metadata tag (`ExifDetails`), the strip's thumbnails (`Thumbnails`) |
| `com.mirada.edit` | The editor's rules and pixel work, none of it UI: the edit and its order (`Edit`: turn, flip, crop), the crop as fractions (`Crop`) and as a box being dragged (`CropBox`), the shapes (`AspectRatio`), turns and mirrors on ARGB ints (`Raster`), undo and redo (`History`), the EXIF, colour profile and XMP a saved edit keeps (`ExifBlock`, `IccProfile`, `Xmp`, all reading through `Containers`), and writing JPEG, PNG, TIFF or BMP (`Encoder`) |
| `com.mirada.os` | Platform glue: the files Finder opens Mirada with (`MacOpenFiles`) and About and Settings in its application menu (`MacAppMenu`); moving a file to the desktop's trash, and the one rule for `java.awt.headless` (`Trash`); showing a file in Finder, File Explorer or the freedesktop file manager (`Reveal`) |
| `com.mirada.settings` | `settings.properties` in `~/.mirada`, `~/Library/Application Support/Mirada` on macOS or `%APPDATA%\Mirada` on Windows, moved there once from `~/.mirada` (`MIRADA_CONFIG_DIR` moves it): theme, what opens at start, how long the floating controls stay; clamped on read, written through on every change. Beside it, `bookmarks.properties`: the user's bookmarks and notes on images and folders (`Bookmarks`), read after the first paint, re-read before every change so two windows add to each other's |
| `com.mirada.ui` | The window (`ViewerWindow`), laid out as GNOME Image Viewer's: the header bar (`TitleBar`), the zoom/pan canvas (`ImageViewport`, `Zoom`, `Scrolling`) with floating controls over it (`OnScreenControls`), the properties sidebar (`InfoPanel`, `ImageDetails`) and the thumbnail strip (`ThumbnailStrip`, along the bottom or down the left, pictures or names, as the settings say); every bookmark and note by folder (`BookmarksWindow`, `BookmarkList`); the editor (`Editor`, `CropView`); every command with its id, name, description, group and where its keys work (`Command`), the keys from a bundled keymap (`Keymap`, `keymaps/cua.json`, `cua.mac.json`, `emacs.json`) with the user's own on top (`Keybindings`), the command palette (`CommandPalette`, one in the viewer and one in the Bookmarks window), the macOS menu bar (`MacMenus`), `AboutWindow`, `SettingsWindow`, `ShortcutsWindow`, the rename rules (`Renaming`), the theme and, on macOS and Windows, the system's accent colour (`Theme`, `Accent`), the symbolic icons, each command's included (`Icons`) |

## Decoders

Six decoders (`ImageFormats.Decoder`, by extension). JavaFX's own `Image` decodes BMP, GIF
(animated) and PNG in its background thread, and the JPEGs libjpeg-turbo declines; a decoder with
a `fallback()` hands what it declines, or cannot load for, to that one. The rest run on
two decoder threads in `ImageCache`, into premultiplied ARGB ints (`ArgbPixels`) that become a
`WritableImage` without a copy (`PixelBuffer`):

| Formats | Decoder | How |
|---|---|---|
| JPEG | `TurboJpegDecoder` | libjpeg-turbo's TurboJPEG, through FFM; JavaFX for CMYK, YCCK and 12-bit |
| WebP (still and animated) | `WebpDecoder` | libwebp's animation decoder, through FFM; ImageIO for WebP without libwebp |
| TIFF, PSD | `ImageIoDecoder` | ImageIO with the TwelveMonkeys plugins, pure Java |
| HEIC, AVIF | `HeifDecoder` | libheif (with libde265 and dav1d), through FFM |
| JPEG XL | `JxlDecoder` | libjxl, through FFM, on every core via `libjxl_threads` |

The native libraries are found by `NativeLibraries`: first in the folder named by the
`mirada.codecs` system property, then the system's search path, then Homebrew's. Runs and tests set
it to `target/codecs` (`codecs.dir` in the pom); the installers will set it to their own copy.

A missing library is not fatal: its files still list, and open with a message
naming the library. EXIF is metadata-extractor. The libraries come from the codec bundle, built from source
([building-and-packaging.md](building-and-packaging.md#the-codec-bundle),
[decision 0003](decisions/0003-codecs-built-from-source.md)).
