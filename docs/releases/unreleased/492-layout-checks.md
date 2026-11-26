---
section: New settings
---
- **Automatic layout checks** ([#492](https://github.com/parttio/unified-testing-tool/issues/492)):
  `vaadin.testing.layout-checks=on` measures the layout after every navigation, reload and `setViewport`, in a
  browser, and shows each **Layout Finding** in amber under the User Step that measured it, in `report.html` and
  `user-steps.json`. `vaadin.testing.layout-findings=fail` makes that step fail; `report` (the default) only reports.
  `vaadin.testing.layout-checks.screenshots=on` keeps a screenshot per measurement, with the finding's box on it. Off
  by default: nothing is measured, no extra round trip. See [Layout](../guide/assertions-and-waiting.md#layout).
