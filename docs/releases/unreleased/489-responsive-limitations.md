---
section: Known limitations
---
- **Responsive testing needs a real browser** ([ADR 0011](../adr/0011-responsive-testing-viewports-layout-findings-viewport-comparison.md)):
  Browserless lays nothing out, so a test with `@Viewport` is skipped there, the layout assertions and
  `isOverlay()`, `hasOverflowItems(..)` ask for `@RequiresBrowser`, and the layout checks measure nothing.
- **Selenium on Firefox needs Firefox 132 or later** for an exact Viewport
  ([#490](https://github.com/parttio/unified-testing-tool/issues/490)); an older one skips the tests that need it.
- **Only the Viewport is emulated**, not the device: `screen.width`, touch input and the device pixel ratio are the
  browser's own.
- **The Viewport Comparison reports and fails nothing**, and matches measurement points by order until two runs take
  different User Steps ([#495](https://github.com/parttio/unified-testing-tool/issues/495)).
- **A Dramafinder test whose copied `SpringPlaywrightIT` opens its pages at a size is not migrated**, as for any
  `setupTest()` override; its Migration Marker names the `@Viewport` to keep
  ([#496](https://github.com/parttio/unified-testing-tool/issues/496)).
