---
section: Migration
---
- **Viewport and window sizes** ([#496](https://github.com/parttio/unified-testing-tool/issues/496)): the TestBench and
  Dramafinder Migration Recipes convert resizes. The size a test starts at, a `resizeViewPortTo(w, h)` or
  `setViewportSize(w, h)` first in a `@BeforeEach` or a test, becomes `@Viewport(width = w, height = h)`, and a later
  one `page.setViewport(w, h)`; `window().getSize()` and `viewportSize()` read `page.viewport()`. A window size,
  `maximize()`, a driver's `--window-size` and a copied `SpringPlaywrightIT` that opens its pages at a size get a
  Migration Marker. See [the mapping](../spec/appendix-mapping.md#viewport-and-window-sizes-the-migration-recipes-convert).
