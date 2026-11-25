---
section: Fixed
---
- **Actions wait for a round trip already under way, on Playwright** ([#604](https://github.com/parttio/unified-testing-tool/issues/604)):
  after an Escape Hatch click (a Playwright `Locator.click()` returns before the server answers), the next Unified
  action acted at once, and the server's late answer could overwrite what it typed. Auto-Wait now also waits, before
  acting, until Flow is idle, for at most 5 s, then acts anyway, so an app that stays busy (`@Poll`, a long request)
  can still be driven. It shares the call that checks the component is usable, so the round trips per action of the
  Performance Pairs don't change.
  TestBench and Browserless already behaved this way. See [5. Waiting](../spec/05-waiting.md#auto-wait) and
  [ADR 0014](../adr/0014-playwright-actions-wait-for-flow-idle-before-acting.md).
