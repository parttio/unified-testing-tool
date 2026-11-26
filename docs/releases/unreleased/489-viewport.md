---
section: New
---
- **Exact Viewports** ([#489](https://github.com/parttio/unified-testing-tool/issues/489),
  [ADR 0011](../adr/0011-responsive-testing-viewports-layout-findings-viewport-comparison.md)): in a browser, every
  test runs at an exact Viewport, 1280 × 720 by default. `@Viewport(width = 375, height = 667, name = "phone")` on a
  test or its class sets the size it starts at, and implies `@RequiresBrowser`. `page.setViewport(768, 1024)` changes
  it during the test, as often as needed, and waits until the application has heard of the new size; `page.viewport()`
  gives the current one. Windows from `openWindow()` and `openAsUser()` start at the test's Viewport. Playwright sets
  it with `setViewportSize`, Selenium with WebDriver BiDi `browsingContext.setViewport` on Chrome and Firefox 132 or
  later ([#490](https://github.com/parttio/unified-testing-tool/issues/490)). The reports name the Viewport each test ran at.
  See [Viewports](../guide/configuration.md#viewports).
