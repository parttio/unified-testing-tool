---
section: New
---
- **Reach in the Findable View and in failures** ([#494](https://github.com/parttio/unified-testing-tool/issues/494)):
  in a browser, the Findable View's header names the Viewport, and a line says when a user can't get to its component
  as it shows: `(clipped)`, `(behind the AppLayout drawer)`, `(behind button “Menu”)`, `(hidden in the AppLayout
  drawer)` or `(hidden in the MenuBar overflow)`. An action on a component something covers or clips fails with that
  in `Found`, the same on Playwright and Selenium: `Expected a reachable button with text “Save”`, `Found behind the
  AppLayout drawer`. See [Reports and failures](../guide/reports-and-failures.md).
