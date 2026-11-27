# Reaching a Template's plain HTML (shadow-piercing lookups)

The Unified API has no way to find HTML written in a `LitTemplate` without an `@Id`, and it will not get one: not a variant of `findDescendants` that enters open shadow roots, and not a lookup that finds id-less template HTML in a browser.

Bakery's dashboard shows why someone asks. Its Lit template writes four `<vaadin-board-row>` elements, with no `@Id` and no Flow component behind them. `page.findTemplate(DashboardView.class).element().findDescendants("vaadin-board-row")` returns nothing on every Testing Tool, because a Template's element is its host and `findDescendants` stays in the light DOM (#609).

## Why this is out of scope

[ADR 0012](../docs/adr/0012-lookups-enter-templates-explicitly.md) decided that **only what the server knows is findable inside a LitTemplate**, on every Testing Tool. HTML written in a template has no server-side element, so Browserless can never find it. Browsers deliberately keep only what has an `id`, or sits under something with one, so that a lookup inside a Template finds the same components on all three tools. A method that pierces shadow roots would give up that parity for a structural call, not for something like CSS or layout that only a browser has.

It would also be a second raw-element path. [ADR 0015](../docs/adr/0015-renderer-markup-is-found-by-tag-wrappers-row-click-avoids-it.md) already turned down `element()` plus `findDescendants(tag)` for grid renderer markup, because it has no Auto-Wait, no "exactly one" and no readable User Step. #608's tag-Wrapper answer doesn't carry over either. There, Browserless knows that a column's renderer draws browser-only markup and fails at once. Inside a Template it has no such signal, so a lookup would come back empty, and `doesNotExist()` could pass by mistake.

The request came from a commercial component, too. `vaadin-board-row` is Board, which is outside the Core Set (see [crud-wrapper.md](crud-wrapper.md)).

## What to do instead

- **Give it an `@Id`** (and an `id` in the template), then find it inside the Template with `withId(..)`. This works on every Testing Tool. If no Wrapper fits it, `UnifiedContainer` or a project Wrapper bound to its tag does.
- **Use the Escape Hatch**, in a test for that one Testing Tool. On Playwright, the Template's `Locator` enters open shadow roots with plain CSS: `template.unwrap(Locator.class).locator("vaadin-board-row")`. On Selenium, TestBench's `$` on an element searches its shadow root: `template.unwrap(TestBenchElement.class).$("vaadin-board-row").all()`. The guide's [Templates](../docs/guide/finding-components.md#templates) section shows both.

## Prior requests

- #617: "A findDescendants that enters a Template's open shadow root in a browser" (split from #609, found in Bakery's alpha5 migration, #602)
