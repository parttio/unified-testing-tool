# A Core Set Wrapper for Crud

The Unified API has no `UnifiedCrud`, and the Core Set will not get one.

## Why this is out of scope

The Core Set has two rules (spec, *Wrappers*): every Core Set component works the same on all three Testing Tools, and it contains **no commercial components** (Grid Pro, Charts, Spreadsheet, Board, Crud). Crud is a commercial component, so it falls outside the Core Set by definition, not because the work is hard.

Bakery's migration (#524) also showed that a Crud Wrapper couldn't behave the same on every Testing Tool anyway. Its Save, Cancel, Delete and Discard buttons get their text from client-side i18n. On Browserless they have no name (`describe()` shows `button (no name)` four times), while on Playwright and Selenium they read `Save`, `Cancel`, `Delete` and `Discard`. So even `withText("Save")` can't be the same lookup on all three tools.

Clicking those buttons doesn't work on Browserless either. The `vaadin-crud` web component handles Save, Cancel and Delete on the client: it listens for the clicks in the browser and sends its own `save`, `cancel` and `delete` events to the server. Browserless has no browser, so a click on one of these buttons does nothing: it raises no error, but the Crud's save, cancel and delete listeners never run. The Discard confirmation (and the delete confirmation) is client-side too, so it never opens. Bakery's alpha5 migration (#602) ran into this.

## What to do instead

Write a **project Wrapper** for Crud, as Bakery did. Its `UnifiedCrud` opens a row's editor through the `vaadin-crud-edit` descendants and reads `editorOpened`, on Playwright and Selenium. To click one row's pencil, find it in that row's cell with a Wrapper bound to its tag, `row.cell(0).find(UnifiedCrudEdit.class).click()` with `@UnifiedComponent(value = Component.class, tag = "vaadin-crud-edit")` (#608, [ADR 0015](../docs/adr/0015-renderer-markup-is-found-by-tag-wrappers-row-click-avoids-it.md)). A project Wrapper can't make Save, Cancel, Delete or Discard work on Browserless by clicking, because the server never hears the click. So a Crud test that clicks these buttons declares `@RequiresBrowser`, as Bakery's do. A Crud test can also keep the TestBench Escape Hatch (`CrudElement`), with `@RequiresTestingTool(SELENIUM)`.

Whether a Wrapper could fire the Crud's server-side events another way on Browserless, for example with `UnifiedElement.dispatchEvent("save")` on the crud, hasn't been tried.

The generic parts Crud is built from are still in scope: a ConfirmDialog Wrapper (its discard and delete confirmations), and treating overlay hosts as visible when their overlay is open. Those are tracked under #532.

## Prior requests

- #532: "Core Set: EmailField, PasswordField, ConfirmDialog, Crud and LoginOverlay have no Wrapper" (the Crud item; the issue's other items stay open)
- #608: "Grid: reach LitRenderer content in a cell (headerless column, renderer functions on Browserless)" (its Crud buttons finding, split out as #615; the grid cell work stays in #608)
