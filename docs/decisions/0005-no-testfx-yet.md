# 0005. No TestFX yet: plain JUnit, JavaFX's Headless platform and our own driver

Accepted, 2026-11-08. Pauta 4.0.1's PLAT-11 asks for TestFX; this record says why Mirada has none yet.

## Context

Most of Mirada's behaviour is rules extracted into static methods with plain tests (zoom stops,
which key does what, sort order, the edit's arithmetic). The tests that need JavaFX need it to make
an `Image` or a `Node`, not to click through a window. JavaFX's own Headless glass platform, with
software rendering, runs those without a display, and `javafx.scene.robot.Robot` drives the real
window when a person or an agent wants to see a flow (`scripts/headless-run.sh`, `HeadlessDriver`).

## Decision

No TestFX dependency for now. Surefire sets `glass.platform=Headless` and `prism.order=sw`; a test
that needs the toolkit starts it through `FxToolkit` and is tagged `@Tag("fx")`. UI flows are looked
at through the headless driver, not asserted in surefire.

## Consequences

- One dependency fewer, and no Monocle or xvfb.
- PLAT-11 (Pauta 4.0.1) fails until TestFX comes in; so do the Pauta rules that want FX tests of
  windows' controls (QUAL-13), which are easier to write with it.
- A UI regression is caught by a rule's test or by looking, not by a test that clicks. Revisit when
  the first window-level test is wanted ([testing.md](../testing.md)).
