---
section: Fixed
---
- **`selectTab` follows the link of a tab that holds one**
  ([#531](https://github.com/parttio/unified-testing-tool/issues/531)): in a `Tabs` used as a navigation menu, as
  Bakery's `MainView` builds it, `selectTab("Users")` selected the tab and the view stayed the same, where a user's
  click lands on the tab's `RouterLink` and navigates. `selectTab` on a tab whose only `<a>` is a `RouterLink` or an
  `Anchor` now follows it as `UnifiedLink.click()` does: a click on the link in a browser; on Browserless, the tab is
  selected, then the link's route opened, and a link out of the application fails asking for `@RequiresBrowser`. A
  selection that navigated isn't read back, and the User Steps read `select tab “Users” in tabs`, then
  `open “users”`. `UnifiedTabSheet.selectTab(..)` does the same. **Adapter SPI:** `TabSheetAccess.link(label)`, the
  tab's link; its default, empty, keeps a custom adapter selecting the tab as before.
