---
section: Changed behaviour
---
- **Presence checks without raw waits** ([#704](https://github.com/parttio/unified-testing-tool/issues/704)): the
  [common mistakes](../guide/common-mistakes.md#waiting-with-a-sleep) page shows how a pause before a check that
  doesn't wait makes a test pass by luck, hiding a race, and what replaces it. The `all()` documentation and the
  `write-unified-ui-tests` skill say that `all()` reads now, and point presence and absence checks to `exists()`,
  `doesNotExist()`, `hasCount(n)`, `isClosed()` and `isHidden()`.
