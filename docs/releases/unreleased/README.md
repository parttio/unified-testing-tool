# Release notes of the next release

One file per change, so that pull requests adding a note don't conflict. The documentation build
(`scripts/build-docs.sh`) assembles them into the site's [Unreleased](../unreleased.md) page, and cutting a release
turns them into `docs/releases/<version>.md` (`scripts/release_notes.py release <version>`, see `CONTRIBUTING.md`).

Name the file `<issue>-<slug>.md`, such as `564-unified-login.md`. It holds a `section`, one of `Breaking changes`,
`New`, `Changed behaviour`, `Faster`, `New settings`, `Fixed`, `Migration` and `Known limitations`, and the note as
one bullet:

```markdown
---
section: New
---
- **Login** ([#564](https://github.com/parttio/unified-testing-tool/issues/564)): `page.find(UnifiedLogin.class)`
  finds Flow's `LoginOverlay` (while open) or `LoginForm`; ...
```

Write links as from `docs/releases/`, where the assembled page lives: `../adr/0012-….md`, `../spec/04-locating.md`.
A change that adds to a note already here edits that note instead. `scripts/release_notes.py check` checks the
files; the Docs workflow builds the page with `mkdocs --strict`, which fails on a broken link.
