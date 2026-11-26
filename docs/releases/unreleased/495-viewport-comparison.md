---
section: New
---
- **Viewport Comparison** ([#495](https://github.com/parttio/unified-testing-tool/issues/495)): run the same tests
  once per Viewport (`vaadin.testing.viewport`) with the layout checks on, then `CompareRuns` compares each run with
  the widest, measurement point by measurement point. It writes `viewport-comparison.html` (a matrix of scenarios
  against Viewports, with each cell's screenshots side by side, its Layout Findings and the components it loses), a
  `.json` for agents and a Markdown summary for the GitHub job summary. Components hidden in the AppLayout drawer or
  the MenuBar overflow are explained; the others need review. It reports and fails nothing. `CompareRuns --out <dir>`
  sets where. See [A weekly responsive run](../guide/ci-setup.md#a-weekly-responsive-run-comparing-viewports).
