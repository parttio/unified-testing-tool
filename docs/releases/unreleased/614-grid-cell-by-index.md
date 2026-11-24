---
section: New
---
- **Grid cell by index** ([#614](https://github.com/parttio/unified-testing-tool/issues/614)):
  `row.cell(2)` is the Scope of the row's cell in the column at index 2 among the visible columns, as
  `getCellText(2)` counts them, for components in a column without header text, such as an action button:
  `row.cell(2).find(UnifiedButton.class).withText("Edit").click()`. The User Steps name it `cell at index 2`.
