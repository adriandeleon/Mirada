# 0004. Settings and bookmarks in `.properties` files

Accepted.

## Context

Mirada keeps a handful of flat settings (theme, panels at start, sort order, slideshow pace, the
palette's keys, the window's place, recent folders) and the user's bookmarks and notes. Reading them
must cost start-up nothing it can avoid, the files should be easy to mend by hand, and Mirada has
no JSON or YAML library to bring in for them.

## Decision

`settings.properties` and `bookmarks.properties` in the platform's config folder, read and written
with `java.util.Properties`, Termina's way: a plain file meant to be hand-edited, every value clamped
on read as well as on write, every change written through at once (the Settings window has no OK
button). Bookmarks are read after the first paint and read again before every change, so two
windows add to each other's.

## Consequences

- No new dependency, and nothing to parse but key-value lines.
- No nesting: a list is numbered keys (`recent.0` to `recent.9`), a mark one key per path.
- A file that will not decode is moved aside to `.corrupt.bak` and the defaults load; one that
  cannot be read is never written over (`ConfigFile`).
