---
section: New settings
---
- **`vaadin.testing.viewport`** ([#489](https://github.com/parttio/unified-testing-tool/issues/489)): the Viewport
  of every test without `@Viewport`, `[name:]WIDTHxHEIGHT` such as `phone:375x667`; `1280x720` by default. A run
  whose Testing Tool can't set it exactly skips its tests with the reason, so a run at a size never compares the
  wrong one. See [Configuration](../guide/configuration.md).
