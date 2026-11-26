---
section: New
---
- **Layout assertions** ([#491](https://github.com/parttio/unified-testing-tool/issues/491)): in a browser,
  `assertThat(page).hasNoHorizontalScroll()` (neither the page nor a container in it, such as AppLayout's content area,
  scrolls sideways; Grid, Tabs, TabSheet and VirtualList scroll by design), `assertThat(page).hasNoLayoutFindings()`
  (nothing scrolls sideways, no component is clipped out of reach, no component a user acts on overlaps another),
  `assertThat(wrapper).isInViewport()` and `isNotClipped()`. Each measures the page in one round trip with the same
  script on Playwright and Selenium, waits for AppLayout's drawer to finish sliding, and retries until the timeout.
  `page.checkLayout()` measures at any moment, as a User Step. See [Layout](../guide/assertions-and-waiting.md#layout).
