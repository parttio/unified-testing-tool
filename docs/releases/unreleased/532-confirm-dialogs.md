---
section: New
---
- **Confirm dialogs** ([#532](https://github.com/parttio/unified-testing-tool/issues/532)):
  `page.find(UnifiedConfirmDialog.class)` finds Flow's `ConfirmDialog`, by `withHeaderTitle(..)` or
  `withTextContaining(..)`. `confirm()`, `reject()` and `cancel()` press the button of that role, whatever its text,
  on every Testing Tool, where a test found the button by a text that i18n or the web component's defaults chose;
  pressing a button the dialog doesn't show fails, listing the buttons it has. The User Steps read
  `confirm “Delete order?”`. `getText()`, `getConfirmText()`, `getRejectText()` and `getCancelText()` read what the
  user sees; `isOpen()`, `close()` and the header filter and checks are `UnifiedDialog`'s, now shared through
  `UnifiedOverlayDialog`. The TestBench recipe turns `$(ConfirmDialogElement.class)` and its
  `getConfirmButton().click()` into `page.find(UnifiedConfirmDialog.class)` and `confirm()`; the Dramafinder recipe
  converts `ConfirmDialogElement`. **Adapter SPI:** `ConfirmDialogAccess`.
