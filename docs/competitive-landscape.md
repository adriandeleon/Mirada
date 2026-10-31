# Competitive landscape

Researched October 2026, from each project's own pages, docs, release notes and issue trackers. What
the popular viewers on Linux, Windows and macOS do, what their users keep asking for, and which of
those gaps are worth Mirada's time. Cells marked **?** could not be confirmed from an official page;
the per-viewer notes say which pages were thin.

**The short answer:** the folder-stepping core Mirada already has is table stakes everywhere except
macOS, where the built-in viewers do not do it at all. What users complain about most, on every
platform, is not missing editing features but the basics done slowly or half-way: no preloading,
one fixed sort order, zoom that resets between images, no rename, and modern formats behind plugins
or paywalls. Mirada should win on those before it adds anything else.

## The field

| Viewer | Platforms | Licence / price | What it is |
|---|---|---|---|
| **GNOME Image Viewer** (Loupe) 51 | Linux | GPL-3, free | GNOME's default since 45. Minimal, GPU-rendered, sandboxed decoders (glycin) |
| **Gwenview** 26.08 | Linux | GPL-2, free | KDE's default. Viewer plus thumbnail browser, compare, annotation |
| **gThumb** 4.0 | Linux | GPL-2, free | Organiser: catalogs, tags, editing. Just ported to GTK4 |
| **nomacs** 3.22 | Linux, Windows, macOS | GPL-3, free | Power viewer; synced compare across windows. Asking for maintainers |
| **qView** 7.1 | Linux, Windows, macOS | GPL-3, free | Minimal and fast; preloading. The closest in spirit to Mirada |
| **imv / feh** | Linux | MIT, free | Keyboard-only, scriptable, for tiling-WM users |
| **Microsoft Photos** | Windows | free | Windows 11's default; gallery, AI edits. Redesign rolling out |
| **IrfanView** 4.76 | Windows | free personal; $18 commercial | Fast, tiny, plugin formats, batch everything. Dated UI |
| **XnView MP** 1.12 | Windows, macOS, Linux | free personal; €29 business | 500+ formats, browser, batch, light asset management |
| **FastStone** 8.5 | Windows | free personal; $34.95 commercial | Fast, keyboard-driven full screen, strong editing. Dated UI |
| **ImageGlass** 10 | Windows, macOS, Linux (new in 10) | GPL-3 free; Pro $14.90 | Modern UI, 90+ formats, preloading |
| **Preview** | macOS | free (bundled) | The default. Opens files, not folders |
| **Quick Look** | macOS | free (bundled) | Finder's Space-bar preview: the "look and close" baseline |
| **Xee³** 3.5.4 | macOS | ~$3.99 | Folder and archive browsing. Intel-only since 2021; stops launching when Rosetta goes (macOS 28) |
| **ApolloOne** 5.1 | macOS | $36 to $100, or yearly | Pro culling tool: RAW, HDR, ratings, compare |
| **Phiewer** 7.1 | macOS | free lite; PRO $7.99 | Folder browser with grid and compare views |

## Feature matrix

✓ has it · ◐ partly, or with a catch · – does not · ? unconfirmed

| | Steps the folder | WebP · AVIF · HEIC · JXL | RAW | ICC colour | Preloads | Thumbnails | Compare | Crop | Trash | Rename | EXIF panel | Gestures |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **Mirada 0.1** | ✓ | ✓ (native libraries) | – | – | ✓ | – | – | – | – | – | ◐ status line | – |
| Loupe | ✓ | ✓ | ✓ | ◐ display profile in doubt | ? | – | – | ✓ | ✓ | – (#1 request) | ✓ | ✓ |
| Gwenview | ✓ | ✓ | ✓ | ? | ◐ top bug, 200+ votes | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ◐ Wayland |
| gThumb | ✓ (arrows new in 4.0) | ✓ | ✓ | ◐ buggy | ? | ✓ | ? | ✓ | ✓ | ✓ | ✓ | ✓ |
| nomacs | ✓ | ✓ | ✓ | ✓ | ? | ✓ | ✓ | ✓ | ? | ? | ✓ | ? |
| qView | ✓ | ✓ | ✓ | ✓ | ✓ | – | – | – | ✓ | ✓ | ? | ✓ |
| Photos | ✓ | ◐ HEIC needs a $0.99 codec | ? | ? | ? | ? | ? | ✓ | ✓ | ? | ✓ map, histogram | ? |
| IrfanView | ✓ | ◐ plugins | ◐ plugin | ✓ | ? | ✓ | ? | ✓ | ? | ✓ | ✓ | ? |
| XnView MP | ✓ | ✓ | ✓ | ✓ | ? | ✓ | ✓ up to 4 | ✓ | ? | ✓ | ✓ | ? |
| FastStone | ✓ | ✓ (no animated WebP) | ✓ | ✓ with monitor profile | ? | ✓ | ✓ up to 4 | ✓ | ? | ✓ | ✓ | ✓ |
| ImageGlass | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | ? | ✓ | ? | ? | ◐ separate tool | ✓ |
| Preview | – | ◐ | ✓ | ✓ | ? | ◐ opened files only | – | ✓ | ? | ? | ✓ map | ✓ |
| Quick Look | – selected files only | ◐ | ✓ | ? | ? | ◐ index sheet | – | – | – | – | – | ✓ |
| Xee³ | ✓ and archives | ? | ? | ? | ? | ? | ? | – | ? | ? | ? | ? |
| ApolloOne | ✓ | ◐ JXL, WebP export only | ✓ | ✓ HDR | ? | ✓ | ✓ synced | ✓ | ? | ✓ | ✓ | ? |
| Phiewer | ✓ | ◐ no JXL | ✓ | ? | ? | ✓ | ✓ up to 9 | ◐ PRO | – hard delete | ✓ | ✓ | ? |

Lossless JPEG rotation is in IrfanView, XnView MP, FastStone and gThumb; Loupe rotates by rewriting
the EXIF orientation tag rather than the pixels. Animated GIF plays almost everywhere; Preview shows
the frames instead.

## What each platform's users expect

- **Linux.** Instant arrow-key stepping, fit/100 %, full screen, trash, rotate, and every modern
  format are universal. The most-voted gaps are a choice of sort order (or following the file
  manager's), zoom kept across images, rename, and preloading: Gwenview's two preload bugs have over
  200 votes between them, and a standing Loupe complaint is that it starts slower than the viewer it
  replaced. Touchpad gestures and ICC handling are expected on modern desktops.
- **Windows.** Users judge on speed first; IrfanView and FastStone are loved for it despite their
  UIs, and Microsoft now advertises Photos' startup times. Formats without paid codecs are expected,
  and gaps get called out (FastStone's animated WebP, Photos' HEVC extension). Lossless JPEG
  transforms, 2–4 image compare, batch convert and ratings are what the power viewers compete on.
  The common complaint about the fast ones is a dated interface.
- **macOS.** Neither Preview nor Quick Look steps through a folder from one opened file, which is
  why Xee and Phiewer exist — and Xee is abandoned and Intel-only. Mac users expect the system's
  formats (HEIC, RAW), colour management, and native conventions: ⌥⌘0 actual size, ⌥⌘9 zoom to
  fit, ⌃⌘F full screen, ⌘⌫ to the Trash (never a hard delete), ⌘I for info, a real menu bar, pinch
  and swipe, and dark mode followed automatically.

## Where Mirada is ahead or level

- **Folder stepping in natural order, with preloading and no flicker.** Level with qView, ahead of
  Gwenview (whose users have voted for this for years) and of everything built into macOS.
- **EXIF orientation, including the mirrored cases.** Level.
- **Zoom about the pointer, crisp pixels above 200 %.** qView users are still asking for the
  second.
- **Cross-platform from one codebase**, which only nomacs, qView, XnView MP and ImageGlass 10 offer.

## Where it is behind

- **Formats.** JPEG, PNG, GIF and BMP only. Every viewer above opens WebP, and most open AVIF, HEIC
  and JPEG XL. This is the single biggest gap.
- **File operations.** No trash, rename, copy or move.
- **Colour management.** JavaFX does not apply embedded ICC profiles, so wide-gamut photos (Display
  P3 from every recent iPhone) show desaturated on any display. Conversion has to happen at decode.
- **Gestures.** None. JavaFX reports pinch and swipe on macOS and Windows touch; Linux touchpad
  support needs checking.
- **macOS conventions.** No menu bar, no ⌥⌘0 / ⌥⌘9 / ⌘⌫, no Open With or Share.
- **No info panel, filmstrip or slideshow.**

## Where this leaves Mirada

Ordered by what users complain about most, weighed against cost.

**Next: the basics, done better than anyone.**

1. **Modern formats.** WebP and TIFF through TwelveMonkeys (pure Java); HEIC, AVIF and JPEG XL need
   native decoders (libheif, libavif, libjxl through FFM), which means per-platform packaging.
   Animated WebP too, since FastStone is criticised for lacking it.
2. **Trash, rename, copy and move.** Delete goes to the system trash, always. F2 renames in place.
3. **Sort order:** name (natural), date modified, date taken; remembered.
4. **Keep zoom across images**, as an option. A top request for Loupe and IrfanView alike.
5. **Platform keys and menus:** the macOS shortcuts above, a menu bar that becomes the system menu
   bar on macOS, Open With.
6. **Measured cold start.** What Loupe is criticised for and Photos now advertises. AppCDS or
   AOT as Termina does, with a number in CI.

**Then: what makes it pleasant to live with.**

7. **Colour management:** convert embedded profiles to sRGB at decode (`java.awt.color` can do the
   conversion). Display profiles are out of reach from JavaFX.
8. **Info panel:** EXIF, histogram, a map link for GPS.
9. **Filmstrip**, toggled, and **slideshow**.
10. **Gestures:** pinch to zoom, swipe to step, where JavaFX delivers them.
11. **Lossless rotation saved to the file**, the Loupe way: rewrite the orientation tag.
12. **Crop**, saved as a copy.

**Worth considering later, because they are differentiators:**

- **Side-by-side compare with synced zoom** (Gwenview, nomacs, ApolloOne). Popular with
  photographers, and cheap on top of the existing viewport.
- **Browsing inside archives** (Xee's headline). Shared work with the Reader's CBZ support.
- **Star ratings in XMP sidecars**, for culling without a catalogue.

**Not doing:** catalogues and libraries (gThumb, Photos), AI editing (Photos), batch conversion
(IrfanView, XnView), annotation, plugin systems. Each pulls toward a different product than "look,
then go".

## Sources

Linux: [Loupe on apps.gnome.org](https://apps.gnome.org/Loupe/) ·
[Loupe NEWS](https://gitlab.gnome.org/GNOME/loupe/-/raw/main/NEWS) ·
[glycin README](https://gitlab.gnome.org/GNOME/glycin/-/raw/main/README.md) ·
[Loupe issue #367, display profile](https://gitlab.gnome.org/GNOME/loupe/-/issues/367) ·
[Gwenview handbook](https://docs.kde.org/stable_kf6/en/gwenview/gwenview/index.html) ·
[KImageFormats README](https://invent.kde.org/frameworks/kimageformats/-/raw/master/README.md) ·
[gThumb NEWS](https://gitlab.gnome.org/GNOME/gthumb/-/raw/master/NEWS) ·
[nomacs](https://nomacs.org/) · [qView](https://github.com/jurplel/qView) ·
[feh](https://feh.finalrewind.org/) · [imv man page](https://man.archlinux.org/man/imv.1.en)

Windows: [New Photos app on Windows 11](https://blogs.windows.com/windowsexperience/2026/10/15/meet-the-new-photos-app-on-windows-11/) ·
[Photos release notes](https://learn.microsoft.com/en-us/windows-insider/release-notes/apps/photos) ·
[HEIC support in Photos](https://support.microsoft.com/help/4494024) ·
[IrfanView formats](https://www.irfanview.com/main_formats.htm) ·
[XnView MP](https://www.xnview.com/en/xnviewmp/) ·
[FastStone Image Viewer](https://www.faststone.org/FSViewerDetail.htm) ·
[ImageGlass features](https://imageglass.org/docs/features) ·
[ImageGlass formats](https://imageglass.org/docs/supported-formats)

macOS: [Preview user guide](https://support.apple.com/guide/preview/welcome/mac) ·
[Preview keyboard shortcuts](https://support.apple.com/guide/preview/keyboard-shortcuts-cpprvw0003/mac) ·
[Quick Look](https://support.apple.com/guide/mac-help/mh14119/mac) ·
[Xee](https://theunarchiver.com/xee) · [Xee on Homebrew](https://formulae.brew.sh/cask/xee) ·
[ApolloOne on the Mac App Store](https://apps.apple.com/us/app/apolloone-photo-video-viewer/id1044484672) ·
[Phiewer](https://phiewer.com/)
