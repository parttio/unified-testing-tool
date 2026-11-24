---
section: Fixed
---
- **An open `ConfirmDialog` is visible on every Testing Tool**
  ([#556](https://github.com/parttio/unified-testing-tool/issues/556)): `page.findComponent(ConfirmDialog.class).visible()`
  found the open confirm dialog on Selenium and none on Playwright, whose visibility check looked at the host's box,
  which a confirm dialog doesn't have. Overlay hosts (`Dialog`, `ConfirmDialog`, `Notification`) now follow one rule
  on every Testing Tool: open ones only, and an open one is visible ([4. Locating](../spec/04-locating.md#what-a-lookup-sees)).
  A closed `Dialog` that stays attached is no longer found on Playwright either, as on Selenium and Browserless.
