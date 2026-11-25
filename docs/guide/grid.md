# Grid

Rows are found by **what they show**, like any other component. Spec: [7. Grid](../spec/07-grid.md).

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:gridRow"
```

## Finding rows

To see which lookup finds a row, print `grid.describe()`: the grid's columns and first 20 rows, each with the lookup on the grid that finds it (`row().withCellText("Name", "User 1")`, or `row(i)` when no column's text is its own), the same on every Testing Tool.

`row()` returns a lazy row, and its filters narrow it down, the way `find(..)` and its filters do for components. Each filter returns a new row. Every use of the row finds it again and waits until exactly one row matches:

| Filter | Matches |
|---|---|
| `withCellText("Name", "Alice")` | the cell under the column headed *Name* is *Alice* |
| `withCellText("Alice")` | any cell is *Alice*; reads every column, so slower on large lazy Grids |
| `withCellTextContaining("Email", "@example")` | part of the cell text, ignoring case |
| `selected()` | selected rows |

```java
--8<-- "unified-test-demo/src/test/java/demo/GridTest.java:helpers"

--8<-- "unified-test-demo/src/test/java/demo/GridTest.java:findsARowByWhatItShows"
```

Row access scrolls the row into view and waits for its data, so a row far down a lazy Grid is found like the first one.

Filters add up:

```java
--8<-- "unified-test-demo/src/test/java/demo/GridTest.java:combinesCellFilters"
```

`row(i)` reaches a row by index, for tests where the order is the point (after sorting, for example).

Whether a row is there, and how many match, are retrying assertions: `assertThat(row).exists()`, `doesNotExist()`, `hasCount(n)`. To loop over the matching rows, `row().withCellText(..).all()` returns them by index, read now.

Row actions (`click()`, `select()`, `toggleDetails()`) are actions like any other: they wait for the grid and the row (Auto-Wait), and show in the reports as `click row where Name is “Alan Turing” in grid`.

A row's `toString()` *(unreleased)* reads the same way, and a column too (`column “Name” in grid`), so a [User Task](structuring-a-scenario.md#user-tasks) named after a row reads as the reports do. Reading it as text asks the grid nothing:

```java
--8<-- "unified-test-demo/src/test/java/demo/GridTest.java:namesAUserTaskAfterARow"
```

## Reading and checking rows

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:gridCells"
```

## Components in cells

`row.cell("Actions")` is a [Scope](finding-components.md#scopes):

```java
--8<-- "unified-test-demo/src/test/java/demo/GridInteractionTest.java:helpers"

--8<-- "unified-test-demo/src/test/java/demo/GridInteractionTest.java:clicksAComponentInACell"
```

`row.cell(1)` *(unreleased)* finds the cell by its index among the visible columns, as `getCellText(1)` reads it: for a column without header text, such as an action column. The reports name it `cell at index 1`. Prefer the header: an index breaks when columns are reordered.

```java
--8<-- "unified-test-demo/src/test/java/demo/GridInteractionTest.java:clicksAComponentInACellByIndex"
```

### Markup a renderer draws *(unreleased)*

A `LitRenderer` draws plain markup, not Flow components: Crud's `vaadin-crud-edit` pencil, a clickable card. Find it in the cell with a [custom Wrapper](custom-wrappers.md) bound to its tag only, `@UnifiedComponent(value = Component.class, tag = "demo-edit")`, with a `click()`:

```java
--8<-- "unified-test-demo/src/test/java/demo/GridInteractionTest.java:clicksRendererMarkupInACell"
```

That markup only exists in a browser, so the test declares `@RequiresBrowser`. On Browserless, a lookup in such a cell fails and says so, rather than finding nothing. To run the renderer's function there instead (`cardClick`, say), use Browserless Test's `GridTester` through the [Escape Hatch](tool-specific.md#the-escape-hatch); it isn't a user action, so the Unified API has no call for it ([ADR 0015](../adr/0015-renderer-markup-is-found-by-tag-wrappers-row-click-avoids-it.md)):

```java
--8<-- "unified-test-demo/src/test/java/demo/GridInteractionTest.java:runsRendererFunctionOnBrowserless"
```

## Columns

Find a column by its header text; `columnWithId("owner")` (`Column.setId`) or `column(1)` for a column without header text.

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:gridColumns"
```

`column.header()` is a Scope, for filter fields in the header:

```java
--8<-- "unified-test-demo/src/test/java/demo/GridInteractionTest.java:filtersThroughAHeaderField"
```

A column's key (`Column.setKey`) stays on the server, so no browser can find a column by it.

When the application adds or removes columns while the test runs, check the headers with a retrying assertion: `hasColumn("Archived on")`, `doesNotHaveColumn(..)`, or `hasColumnHeaders(..)` for all of them in display order. `getColumnHeaders()` reads them now.

```java
--8<-- "unified-test-demo/src/test/java/demo/GridTest.java:waitsForAColumn"
```

## Selection, clicks and details

```java
--8<-- "unified-test-demo/src/test/java/demo/GridInteractionTest.java:selectsRows"
```

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:gridRowActions"
```

The multi-select checkbox column is not a column: column indexes count only the application's columns.

`row.click()` clicks a spot of the row free of interactive content (a link, a button, a renderer's card), so it selects the row or opens its details rather than pressing what is in a cell. When such content covers the whole row, it fails, the same on Playwright and Selenium *(unreleased)*, and names `row.cell(..).find(..).click()` for clicking that content. Browserless sees no layout and clicks the row.

## The item behind a row

A test sees what the user sees: rendered text. To read the Java item behind a row, use the [Escape Hatch](tool-specific.md) on Browserless.
