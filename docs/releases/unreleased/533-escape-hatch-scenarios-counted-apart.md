---
section: Changed behaviour
---
- **Escape Hatch scenarios are counted apart under "Scenarios without a check"**
  ([#533](https://github.com/parttio/unified-testing-tool/issues/533)): after a TestBench migration, a scenario that
  checks with JUnit or AssertJ assertions on Escape Hatch reads looked unchecked, and such scenarios filled the list.
  A passing scenario without a check that recorded an Escape Hatch gap is no longer listed: one line after the list
  counts them, `13 more end in Escape Hatch code, whose checks the report can't see: …`, and points to resolving
  their Migration Markers or moving their checks to `assertThat(..)`. `report.html` shows them after that line with
  an *Escape Hatch* badge instead of *no check*; `summary.json` adds `escapeHatch` next to `unchecked`, whose meaning
  is unchanged.
