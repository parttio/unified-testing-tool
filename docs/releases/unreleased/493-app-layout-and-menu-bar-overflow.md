---
section: New
---
- **App layout and menu bar overflow** ([#493](https://github.com/parttio/unified-testing-tool/issues/493)):
  `page.find(UnifiedAppLayout.class)` finds an `AppLayout`; `assertThat(layout).isOverlay()`, `isNotOverlay()`,
  `isDrawerOpened()`, `isDrawerClosed()`, and `openDrawer()`, `closeDrawer()`, `toggleDrawer()` with its DrawerToggle,
  as a user does. `assertThat(menuBar).hasOverflowItems("Share", "Print")` and `hasNoOverflowItems()` check which items
  a narrow menu bar has moved into "More options"; `clickItem(..)` on one of them fails, pointing to
  `unfoldingOverflow().clickItem(..)`, which opens that menu first ([#664](https://github.com/parttio/unified-testing-tool/issues/664)). Overlay and overflow are
  browser only. The Findable View lists the layout with `(overlay)` and `(drawer closed)`. See
  [App layout](../guide/components.md#app-layout).
