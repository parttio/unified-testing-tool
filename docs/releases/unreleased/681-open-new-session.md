---
section: New
---
- **A second user without Spring Security** ([#681](https://github.com/parttio/unified-testing-tool/issues/681)):
  `page.openNewSession()` opens a window in a session of its own with nobody signed in, on every Testing Tool (a new
  Browserless user, a new Playwright browser context, a new Selenium browser). The test signs in through the
  application's own login with `UnifiedLogin`, as User Steps. `openAsUser(..)` still needs Spring Security, and its
  error now points here. The adapter SPI's `AdapterSession` gains `openNewSession()`
  ([ADR 0021](../adr/0021-a-second-user-signs-in-through-the-app-open-new-session.md),
  [guide](../guide/navigation-and-users.md#a-second-user-without-spring-security)).
