---
section: Changed behaviour
---
- **Raw checks to Unified assertions** ([#702](https://github.com/parttio/unified-testing-tool/issues/702)): the
  [common mistakes](../guide/common-mistakes.md#habits-from-other-testing-tools) table, and the `write-unified-ui-tests`
  skill, now map raw Playwright checks (`assertTrue(locator.isVisible())`, `textContent("body").contains(..)`,
  `inputValue()`, `rows.count()`, a fixed pause) to Unified assertions, so a suite migrated by hand gets them
  converted. The skill also says that `isVisible()` needs exactly one match, that text filters match the DOM text
  rather than CSS `text-transform`, and that a row lookup scrolls the grid under a later Escape Hatch read by position.
