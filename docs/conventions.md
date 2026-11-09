# Conventions

How code is written here. The operational rules (worktrees, the change checklist) are in
[AGENTS.md](../AGENTS.md).

## Priorities

In this order, when they pull against each other:

1. **Speed.** Cold start, stepping to the next image, and decoding. A feature that costs either is
   not worth it.
2. **Keyboard navigation.** Everything reachable without the mouse.
3. **A command palette**, so every command can be found and run by name.

## Conventions

- **Extract the decision, then test it.** Anything with a rule in it — sort order, which key does
  what, zoom stops, where an image may be panned to — is a static method with its own tests (`Zoom`,
  `Keybindings.layered`, `NaturalOrder`, `FolderImages.select`, `Exif.summary`).
- **Fast first.** Startup and stepping are the product. Prefetch neighbours after the current image
  is up, never before it; no per-pulse work.

## Docs in the same PR

A change that affects behaviour updates its docs in the same pull request, never as a follow-up: an
entry under `## [Unreleased]` in `CHANGELOG.md` (Keep a Changelog) opening with a bold one-line
summary for users, the guides in `docs/` it touches, `AGENTS.md` where it is touched, and the
website's user docs (`../mirada-website`) when users see it. When documentation and code disagree,
the code wins, and the documentation is fixed in the same change.

## Null annotations

JSpecify annotations, checked by NullAway in the main compile — Editora's setup, the same rules as
Termina and Torrenta. `mvn compile` fails on any null that can reach a type not marked nullable.

- **Every package is `@NullMarked`**, through its `package-info.java`; `NullMarkedPackagesTest` fails
  for a package without one, and if NullAway leaves the pom.
- **Mark what really may be null** (`private @Nullable Image image`), and prefer a shape that cannot
  be null when the null is an accident of the code.
- **NullAway does not follow a boolean that holds a null check**, nor a getter called twice: read it
  into a local and check inline.
- **JavaFX returns null more often than it looks:** `Node.getScene()`, `Scene.getWindow()`,
  `Path.getFileName()`, `Path.getParent()`, `FileChooser.showOpenDialog`.
- **Plumbing.** Only NullAway runs, only on the main compile. `.mvn/jvm.config` exports the javac
  internals Error Prone needs. JSpecify is `provided`, read through `requires static org.jspecify`.
