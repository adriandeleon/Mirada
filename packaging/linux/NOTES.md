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

## The deb installs nothing at configure time but a link

jpackage's own `postinst` registers the menu entry with `xdg-desktop-menu install`, and that fails
with "No writable system menu directory found" on any system without `/etc/xdg/menus` — a server,
a container, GitHub's Ubuntu runner — which makes dpkg mark the whole package as failed. It does
this whenever file associations are given, `--linux-shortcut` or not.

So the deb carries `postinst` and `prerm` of its own: jpackage's templates (its tokens kept) without
the desktop commands, adding and removing the `/usr/bin/mirada` link. `build-installers.sh` then
rebuilds the finished deb with the menu entry as a file the package owns,
`/usr/share/applications/mirada.desktop`; dpkg's triggers (desktop-file-utils) refresh the menu and
MIME caches, and removal deletes it like any other file. The rpm keeps jpackage's spec: a failing
`%post` there is a warning, not a failed install.

CI and the release install the built deb with `apt` on a clean runner and smoke-test the installed
copy; `check-linux-packages.sh` fails if the `postinst` calls `xdg-desktop-menu` again.
