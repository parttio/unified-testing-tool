---
section: Changed behaviour
---
- **On Selenium, clicking a component something covers fails** ([#494](https://github.com/parttio/unified-testing-tool/issues/494)):
  TestBench clicks with a script, which reached a button under the AppLayout drawer or a dialog's backdrop, where a
  user couldn't. The click now checks what is under its point first and fails, as on Playwright, saying what covers
  the component. One round trip fewer per click as well.
