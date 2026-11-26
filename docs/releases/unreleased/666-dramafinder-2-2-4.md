---
section: Changed behaviour
---
- **Dramafinder 2.2.4** ([#666](https://github.com/parttio/unified-testing-tool/issues/666)): `unified-test-bom`
  manages Dramafinder 2.2.4 (was 2.2.3). On Playwright, `UnifiedMenuBar` reads its items, and the items folded into
  "More options", through Dramafinder's `MenuBarElement`, and `unfoldingOverflow()` opens that menu with Dramafinder's
  ([parttio/dramafinder#334](https://github.com/parttio/dramafinder/issues/334)); the adapter's own menu bar script is
  gone. Nothing changes in what the tests see. A project's own Dramafinder code left behind the Escape Hatch now gets
  `MenuBarElement.getItemLabels()` without "More options".
