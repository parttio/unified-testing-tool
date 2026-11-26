---
section: New
---
- **Viewport on Selenium with Firefox** ([#490](https://github.com/parttio/unified-testing-tool/issues/490)):
  Selenium sets an exact Viewport on Firefox 132 or later, as on Chrome, through WebDriver BiDi
  (`browsingContext.setViewport`), per tab. An older Firefox refuses with its version, so a test that needs its
  Viewport is skipped and the others run with a warning. CI runs the Viewport and layout tests on Firefox too.
