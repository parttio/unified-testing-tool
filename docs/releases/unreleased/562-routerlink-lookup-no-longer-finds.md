---
section: Fixed
---
- **A `RouterLink` lookup no longer finds `Anchor`s in a browser**
  ([#562](https://github.com/parttio/unified-testing-tool/issues/562)): both render `<a>`, so on Playwright and
  Selenium `findComponent(RouterLink.class)` also found the anchors, and Bakery counted 4 router links where
  Browserless counted 3. A browser lookup for a class that shares its tag now also checks the attribute that tells it
  apart: `a[router-link]` for a `RouterLink`, `a:not([router-link])` for an `Anchor`. Counts, `exists()`,
  `hasCount(..)`, Near Misses and `describe()` agree on every Testing Tool. `UnifiedLink` still finds both. The
  classes that share a tag with nothing to tell them apart are listed in the
  [gaps](../spec/appendix-gaps.md#outside-the-testing-tools). **Adapter SPI:** a `WITHOUT_ATTRIBUTE` `Filter`, for an
  element without the attribute; a custom adapter that applies filters itself handles it.
