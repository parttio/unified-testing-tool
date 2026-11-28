---
section: Fixed
---
- **`AddUnifiedTestingSkill` no longer skips silently** ([#698](https://github.com/parttio/unified-testing-tool/issues/698)):
  when the build root has no build file, `AGENTS.md` or `CLAUDE.md` (the parent POM is in a subdirectory), the recipe
  still installs nothing, but now warns on each module's build file below the root, saying so and how to install
  the skill. Installing it into a nested module is [#705](https://github.com/parttio/unified-testing-tool/issues/705).
