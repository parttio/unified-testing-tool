---
section: Fixed
---
- **A notification built from components** ([#690](https://github.com/parttio/unified-testing-tool/issues/690)):
  on Browserless, `UnifiedNotification.getText()` and `withText(..)` on a notification made with
  `new Notification(component..)` or `add(..)` threw a `NullPointerException`. They now read the text of its
  components, as a browser's `textContent` joins it: a `Span("Saved")` and a `Button("Undo")` read `SavedUndo`,
  the same as on Playwright and Selenium. A notification made with `Notification.show(String)` reads as before
  ([spec](../spec/06-wrappers.md)).
