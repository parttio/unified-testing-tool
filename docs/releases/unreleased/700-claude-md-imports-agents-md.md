---
section: Changed behaviour
---
- **`AddUnifiedTestingSkill` also creates `CLAUDE.md`** ([#700](https://github.com/parttio/unified-testing-tool/issues/700)):
  in a project with neither `AGENTS.md` nor `CLAUDE.md`, the recipe still creates `AGENTS.md` with the pointer to the
  `write-unified-ui-tests` skill, and now also a `CLAUDE.md` holding only `@AGENTS.md`. Claude Code reads `CLAUDE.md`,
  not `AGENTS.md`, so it now sees the pointer. A project that has either file, or both, is handled as before.
