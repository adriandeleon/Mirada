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
| [architecture.md](architecture.md) | The packages, the six decoders, where the native libraries are found |
| [conventions.md](conventions.md) | The priorities, rules as tested static methods, docs in the same PR, null annotations |
| [testing.md](testing.md) | Running the tests, what is tested where, the format matrix, the UI headless, CI |
| [gotchas.md](gotchas.md) | Things that will bite you, by part of the app |
| [release.md](release.md) | Tagging a release, what the Release workflow does, signing |
| [building-and-packaging.md](building-and-packaging.md) | The codec bundle, the native image that ships, the installers, start-up numbers |
| [decisions/](decisions/README.md) | Decision records: the native image, libjpeg-turbo, codecs from source, `.properties`, no TestFX yet |
| [reference/](reference/README.md) | History: older measurements and the native image experiment |
| [native-image.md](native-image.md) | The GraalVM native image experiment: why, how it was built, what it measured |
| [competitive-landscape.md](competitive-landscape.md) | Other image viewers, and what Mirada takes from each |
| [pauta-audit.md](pauta-audit.md) | Where Mirada stands against each rule of its Pauta edition, with evidence; regenerated, not edited |

The commands and the change checklist are in [AGENTS.md](../AGENTS.md); the short version for a
person is [CONTRIBUTING.md](../CONTRIBUTING.md); what is known to be missing is in
[TODO.md](../TODO.md).
