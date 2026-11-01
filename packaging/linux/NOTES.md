# The Linux menu entry

`Mirada.desktop` replaces the one jpackage generates. Termina's `packaging/linux/NOTES.md` explains
the traps in full; the ones that apply here:

- **The name must match the launcher exactly**: jpackage looks for `Mirada.desktop` and silently
  ignores `mirada.desktop`.
- **Only some `APPLICATION_*` tokens are substituted**: `APPLICATION_NAME`,
  `APPLICATION_DESCRIPTION`, `APPLICATION_LAUNCHER` and `APPLICATION_ICON`. `APPLICATION_MIMETYPES`
  and `APPLICATION_CATEGORIES` are not, and would ship literally, so `MimeType` and `Categories` are
  written out. `MimeType` must list the same types as the file associations in
  `scripts/build-installers.sh`, or the menu entry and the registered handler disagree.
- **`Exec` ends in `%f`**: the desktop passes the double-clicked file as Mirada's argument.
- **`StartupWMClass=com.mirada.App`** is the class JavaFX gives the window, so the dock matches the
  running window to its launcher instead of showing a second, generic icon.
- **The deb and rpm bundlers ignore `--icon`** and regenerate `lib/Mirada.png` from their own
  resources unless `Mirada.png` is in the resource directory, which `build-installers.sh` stages
  from `branding/`.

`scripts/check-linux-packages.sh` asserts all of this against the built packages, because every
mistake above produces a package that installs fine and looks wrong.
