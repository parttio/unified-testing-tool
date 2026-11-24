---
section: Changed behaviour
---
- **A grid row or column as text** ([#620](https://github.com/parttio/unified-testing-tool/issues/620)): a row's
  and a column's `toString()` read as the target of their User Steps, `row where Name is “Unique cake” in grid`
  or `column “Name” in grid`, instead of code (`row().withCellText("Name", "Unique cake")`), so that
  `page.step("Edit " + row, ..)` names the User Task in the reports' words. It asks the grid nothing. Failure
  messages and `grid.describe()` still show the code that finds the row.
