# A Core Set Wrapper for Crud

The Unified API has no `UnifiedCrud`, and the Core Set will not get one.

## Why this is out of scope

The Core Set has two rules (spec, *Wrappers*): every Core Set component works the same on all three Testing Tools, and it contains **no commercial components** (Grid Pro, Charts, Spreadsheet, Board, Crud). Crud is a commercial component, so it falls outside the Core Set by definition, not because the work is hard.

Bakery's migration (#524) also showed that a Crud Wrapper couldn't behave the same on every Testing Tool anyway. Its Save, Cancel, Delete and Discard buttons get their text from client-side i18n. On Browserless they have no name (`describe()` shows `button (no name)` four times), while on Playwright and Selenium they read `Save`, `Cancel`, `Delete` and `Discard`. So even `withText("Save")` can't be the same lookup on all three tools.

## What to do instead

Write a **project Wrapper** for Crud, as Bakery did. Its `UnifiedCrud` opens a row's editor through the `vaadin-crud-edit` descendants and reads `editorOpened`, on Playwright and Selenium, and its tests declare `@RequiresBrowser`. A Crud test can also keep the TestBench Escape Hatch (`CrudElement`), with `@RequiresTestingTool(SELENIUM)`.

The generic parts Crud is built from are still in scope: a ConfirmDialog Wrapper (its discard and delete confirmations), and treating overlay hosts as visible when their overlay is open. Those are tracked under #532.

## Prior requests

- #532: "Core Set: EmailField, PasswordField, ConfirmDialog, Crud and LoginOverlay have no Wrapper" (the Crud item; the issue's other items stay open)
