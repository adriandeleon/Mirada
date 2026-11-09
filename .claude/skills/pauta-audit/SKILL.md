---
name: pauta-audit
description: >-
  Audits this JavaFX app against the Pauta edition it implements (the adl desktop apps guidelines,
  pauta-project.dev): checks every rule (KBD-07, WIN-03, …) by code, test or by hand, and writes
  docs/pauta-audit.md with pass, fail, n.a. or manual and file:line evidence for each rule, failed MUSTs first.
  Use when asked to "audit against Pauta", "check Pauta compliance", "which Pauta rules does this app fail",
  "is this app Pauta compliant", or to audit one Pauta page or rule prefix (for example "audit KBD" or
  "audit the keyboard rules"). Not for writing new features from Pauta (read the rule's page for that).
argument-hint: "[prefix | page | rule ID …] [edition=<x.y.z>] [print]"
---

# Pauta audit

Audit this app against the Pauta edition it says it implements, one row per rule, and write the result to
`docs/pauta-audit.md`. Pauta is at https://pauta-project.dev/; each edition lives at
`https://pauta-project.dev/<edition>/`. Editora (`../Editora`) is its reference implementation.

## Arguments

All optional, in any order:

- **Scope.** A rule ID prefix (`KBD`, `win`), a page slug (`keyboard`, `window-shell`, `about` for `templates/about`), a page title
  (`Keyboard first`) or single IDs (`KBD-07 WIN-03`). Several are allowed; a rule is in scope if it matches
  any. No scope means every rule in the edition.
- **`edition=<x.y.z>`** overrides the edition the app names (step 1).
- **`print`** prints the report in the reply instead of writing `docs/pauta-audit.md`.

## 1. Find the edition

In this order, and stop at the first that names one:

1. The `pauta.version` property in `pom.xml`: `grep -n '<pauta.version>' pom.xml`.
2. The README's "Built to … Pauta …, edition X.Y.Z" line: `grep -n -i 'built to' README.md`.

If neither names an edition, **ask the user which edition to audit against**, and offer the latest from
https://pauta-project.dev/versions.json. If the two disagree, use `pom.xml` and list the mismatch as a
finding (the README line is stale).

## 2. Load the edition's rules and procedure

Fetch into `target/pauta/<edition>/` (ignored by Git, wiped by `mvn clean`; never commit these files):

```bash
mkdir -p target/pauta/$EDITION
curl -fsSL -o target/pauta/$EDITION/rules.json https://pauta-project.dev/$EDITION/rules.json
curl -fsSL -o target/pauta/$EDITION/llms.txt   https://pauta-project.dev/$EDITION/llms.txt
```

**Offline, or the edition is not published yet:** use the guidelines checkout at
`../adl-desktop-apps-guidelines`. Take `dist/<edition>/rules.json` and `dist/<edition>/llms.txt` if they
exist; otherwise run `npm run build` there first (it builds that checkout's own edition and the archived
ones). Building writes `dist/` in that checkout: if another session or person is working in it, ask first.

**The edition has no `rules.json`:** the editions published before rules had IDs (the 1.x editions) don't
serve one. Say so, and offer to audit against the oldest edition that does, or the latest (list them from
`versions.json`). Then label the report with both editions, "the app names 1.1.0; audited against 2.0.0",
and treat a failed rule that the app's own edition didn't have as **upgrade work**, not as a violation:
the edition's `changelog/` page says what each edition added or made stronger.

`rules.json` holds the edition's `levels` and `checks` (what each level and check kind means) and
`rules[]`, each with `id`, `level` (MUST, SHOULD, MAY), `page`, `pageTitle`, `section`, `url`, `text`,
`why`, `check.kind` (`code`, `test` or `by hand`), `check.text` and `reference`. The rules in scope:

```bash
jq -r --arg p "KBD" '.rules[]
  | select((.id | ascii_upcase | startswith(($p | ascii_upcase) + "-")) or .page == $p or (.page | split("/") | last) == $p)
  | [.id, .level, .check.kind, .check.text] | @tsv' target/pauta/$EDITION/rules.json
```

**Read `llms.txt` and follow its "Auditing an app" procedure.** It is the authority on how that edition
judges a rule; this skill adds where the report goes and what happens after. Where the two disagree on
how to judge a rule, the edition's `llms.txt` wins.

## 3. Judge each rule

Read the whole rule (`text`, `why`, `check`) before judging it. Results are **pass**, **fail**, **n.a.**
or **manual**, and every result has evidence:

- **n.a.** A rule applies to every app unless its text names the apps it covers ("An app with user
  documents…", "An app with a tray icon…", "An app that edits text…"). Decide from the code whether this
  app is one of them, and cite why it isn't (for example, "no tray: no `SystemTray`/`TrayIcon` in `src/`").
  A rule is also n.a. when its subject is a part the app doesn't have and no rule requires (a toolbar,
  tabs, tool panels, a find bar, icon-only buttons): the rules about how a toolbar behaves don't apply to
  an app without one. Name the missing part in the evidence. But a part that a rule *requires* (the
  Command Palette, a status bar, About) is a fail on that rule when it is missing, and the rules about
  how it behaves fail with it: a missing MUST feature is never n.a.
- **`code`.** Read or search the source as `check.text` says (it names files, classes, properties or
  style classes as Editora spells them; find this app's equivalent). Pass only if everything the check
  names is there. Evidence is the `file:line` that satisfies it; for a fail, the closest place it should
  be, and what is missing.
- **`test`.** The check names a test as Editora calls it. Find that test or this app's equivalent in
  `src/test/`, read it to confirm it asserts what the rule says (a test with the right name that checks
  something else is a fail), and confirm `./mvnw verify` runs it: not `@Disabled`, not excluded by the
  Surefire configuration in `pom.xml` (`excludes`, `excludedGroups`, `groups`). FX tests tagged
  `@Tag("fx")` run in `verify` unless the pom excludes them. Evidence is the test's `file:line`.
- **`by hand`.** Source alone can't settle it. Report **manual**, and in the evidence say what to look at
  in the running app (from `check.text`) and the `file:line` of the code involved, if any. When the source
  shows the feature isn't there at all, it is a **fail**, not manual.

Partly met is a **fail**: say which part is missing. Don't guess: when the source doesn't settle a `code`
or `test` rule, say what you searched for and mark it fail, with "not found" in the evidence.

Cite rules as "KBD-07 (Pauta <edition>)". Paths in evidence are relative to the repository root, as
`path/File.java:123`, so they are clickable.

**Large audits.** A whole edition is hundreds of rules. If you can start subagents, split the rules in
scope by page and give each subagent its pages, the path to `rules.json` and this section; ask each for its
rows in the report's table format, and merge them. Otherwise work page by page and write the rows as you
go.

## 4. Write the report

Write `docs/pauta-audit.md` (or print it, with `print`). Replace any earlier report: it is a snapshot,
regenerated rather than edited by hand.

```markdown
# Pauta audit

<App name> at <short commit SHA>, audited against [Pauta <edition>](https://pauta-project.dev/<edition>/)
on <YYYY-MM-DD>. Scope: <every rule | the prefixes or pages given>. Rules from <URL or local path>.

|        | Pass | Fail | Manual | N.a. |
|--------|-----:|-----:|-------:|-----:|
| MUST   |      |      |        |      |
| SHOULD |      |      |        |      |
| MAY    |      |      |        |      |

A failed MUST blocks a release.

## Failed MUSTs

| ID | Level | Result | Evidence | Rule |
|----|-------|--------|----------|------|
| KBD-07 | MUST | fail | `src/main/java/…/ui/Palette.java:42`: opens in a `javafx.stage.Popup` | https://pauta-project.dev/2.0.0/keyboard/#kbd-07 |

## Other failures

(SHOULD, then MAY.)

## Manual checks

## Passed and not applicable

(In rule order.)
```

Every rule in scope has exactly one row; omit a section that would be empty. Keep each evidence cell to one
line, and escape `|` inside cells as `\|`.

## 5. Offer fixes

After the report, list the failed MUSTs in the reply (ID, one line each) and **offer to fix them**; don't
start without a yes. For each fix:

- Copy from the rule's `reference`, which names files in Editora (`../Editora`) or one of the edition's
  templates; adapt names to this app rather than inventing a new design.
- Follow `AGENTS.md`'s change checklist: a command for each action, strings in all six catalogs, tests,
  docs and a `CHANGELOG.md` entry, and `./mvnw verify` passing.
- Re-run the audit for the fixed IDs only (`/pauta-audit KBD-07 WIN-03`) and update their rows.

Failed SHOULDs are worth fixing too; offer them separately, after the MUSTs.
