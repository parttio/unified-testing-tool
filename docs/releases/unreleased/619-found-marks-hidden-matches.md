---
section: Changed behaviour
---
- **Several matches name the hidden ones** ([#619](https://github.com/parttio/unified-testing-tool/issues/619)): when
  a lookup matches several components, the failure's `Found` line marks each listed match that is not visible
  `(hidden)`, as Near Misses and the Findable View already do: `Found 2: button “Cancel”, button “Cancel” (hidden)`.
  Add `.visible()` to single out the one the user sees. The same on every Testing Tool.
