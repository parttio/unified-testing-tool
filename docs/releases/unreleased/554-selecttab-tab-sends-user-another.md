---
section: Fixed
---
- **`selectTab` on a tab that sends the user to another page passes**
  ([#554](https://github.com/parttio/unified-testing-tool/issues/554)): after the click, `selectTab` reads the
  selected tab back. When the tab's listener had left the page (`Page.setLocation(..)`, as a "Logout" tab does), that
  read failed with the Testing Tool's own error, such as Playwright's `TimeoutError`, and the report had no User Step
  for it. Tabs the lookup no longer finds now have nothing to check, as a selection field the app replaced
  ([#377](https://github.com/parttio/unified-testing-tool/issues/377)); Playwright sees they're gone at once,
  instead of after its 10 s timeout. A tab that doesn't take, on tabs still on the
  page, fails as a `UnifiedAssertionError`: Expected the tab selected in the tabs, Found the selected one, the
  Testing Tool's error as the cause. Browserless doesn't follow `Page.setLocation(..)`: such a test needs
  `@RequiresBrowser` (*Known limitations by Testing Tool*).
