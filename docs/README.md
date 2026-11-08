# Mirada's documentation

This folder is for **contributors**: how Mirada is built, measured and checked. The user
documentation (getting started, the keyboard, Settings, the FAQ) lives on the website,
[mirada-project.dev/docs](https://mirada-project.dev/docs/), from the
[mirada-website](https://github.com/adriandeleon/mirada-website) repository; this folder never
carries a second copy.

**When these documents and the code disagree, the code wins**, and the document is fixed in the same
change.

| Document | What it covers |
|---|---|
| [pauta-audit.md](pauta-audit.md) | Where Mirada stands against each rule of its Pauta edition, with evidence; regenerated, not edited |
| [native-image.md](native-image.md) | The GraalVM native image that ships: why, how it is built, what it measured |
| [competitive-landscape.md](competitive-landscape.md) | Other image viewers, and what Mirada takes from each |

[AGENTS.md](../AGENTS.md) holds the commands, the architecture and the gotchas until they move here
(Pauta's REPO-07 asks for `architecture.md`, `conventions.md`, `testing.md`, `gotchas.md`,
`release.md`, `building-and-packaging.md` and `decisions/`).
