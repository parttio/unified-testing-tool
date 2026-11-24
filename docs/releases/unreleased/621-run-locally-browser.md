---
section: Fixed
---
- **`getRunLocallyBrowser()` in migrated TestBench tests** ([#621](https://github.com/parttio/unified-testing-tool/issues/621)):
  `TestBenchEscapeHatch` has `getRunLocallyBrowser()` and `getRunLocallyBrowserVersion()`, so a TestBench test
  calling them, such as `if (getRunLocallyBrowser() == null)`, compiles after the migration. The browser is the one
  `vaadin.testing.selenium.browser` sets (`CHROME` or `FIREFOX`), or `null` without the setting, as for a TestBench
  test without `@RunLocally`; the version is always `""`, any version.
