---
section: Fixed
---
- **Text set as HTML** ([#691](https://github.com/parttio/unified-testing-tool/issues/691)): on Browserless, text
  in an `innerHTML` property (Flow's `Html`, `setProperty("innerHTML", ..)`, a `Span` with markup) was invisible:
  `getText()` read it as empty, `withText(..)` didn't find it, and the Findable View showed `(no name)`. It now reads
  as a browser's `textContent` holds it, markup left out: `new Html("<div><b>Total</b> 42</div>")` reads `Total 42`,
  the same as on Playwright and Selenium. The elements inside the markup are still not components, so a lookup
  doesn't find them ([spec](../spec/06-wrappers.md), [gaps](../spec/appendix-gaps.md#browserless-test)).
