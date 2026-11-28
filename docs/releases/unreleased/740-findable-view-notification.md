---
section: Fixed
---
- **An open notification in the Findable View** ([#740](https://github.com/parttio/unified-testing-tool/issues/740)):
  on Playwright and Selenium, `describe()` left out an open notification, as if its card were an internal part of
  another component. It is now listed with its text, as on Browserless:
  `notification “3 rows imported”  page.find(UnifiedNotification.class)…`. A component's real internal parts, such
  as the text area inside a message input, are still left out ([spec](../spec/12-readable-tests.md)).
