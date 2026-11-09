# To do

What is known to be missing, kept in the same pull request as the change that adds or settles it.

## Signing and distribution

- Developer ID and notarization on macOS (wired, waiting for the secrets).
- A code-signing certificate on Windows.
- A Homebrew cask (Homebrew takes only notarized apps).
- winget-pkgs has 0.3.0 under review (microsoft/winget-pkgs#471599), not yet merged.

## Saving an edit

- A TIFF's own EXIF when an edit of it is saved.
- A profile for a HEIF with nclx colours or a JPEG XL.
- A Brotli-compressed JPEG XL Exif box.

## Untested

- Trash on Windows and macOS is untested.
- HEIC grids are tested on an iPhone-style file, not a real iPhone photo.
- The app itself has not been run on Windows or macOS by hand; CI only tests there, and the macOS
  Finder double-click is tested only through `open -a` on CI.
- On an Intel Mac (the JVM image) a launch from Finder still opens an empty window.
