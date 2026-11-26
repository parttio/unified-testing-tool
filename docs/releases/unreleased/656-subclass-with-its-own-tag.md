---
section: Fixed
---
- **Menu bar items and subclasses with their own tag** ([#656](https://github.com/parttio/unified-testing-tool/issues/656),
  [#493](https://github.com/parttio/unified-testing-tool/issues/493)): on Playwright,
  `page.findComponent(DrawerToggle.class)` found the page's buttons, since `DrawerToggle` extends `Button`; a component
  class with a tag of its own is now found by that tag. `UnifiedMenuBar.getItems()` is the same on Playwright and
  Selenium when items overflow: every top-level item, never the "More options" button.
