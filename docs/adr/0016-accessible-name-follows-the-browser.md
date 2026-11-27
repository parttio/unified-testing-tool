---
status: accepted
---

# The accessible name follows the browser's order on every Testing Tool; Playwright reads it from Dramafinder

`withAccessibleName(..)` and `UnifiedElement.getAccessibleName()` used an order of the Unified API's own: `aria-label`, the `accessibleName` property, the `aria-labelledby` target's text, `label`, and else the element's text, for any component. Browsers, and Dramafinder's `getAccessibleName()` since 2.2.1, read it differently: `aria-labelledby` (every id) before `aria-label`, then a field's label, and the text only for a component named by its content. So the Playwright adapter couldn't use Dramafinder's read without the three Testing Tools disagreeing ([parttio/dramafinder#302](https://github.com/parttio/dramafinder/issues/302), #448). Decided in #641:

- **One computation, the browser's, on every Testing Tool:** the texts of the `aria-labelledby` targets, joined with a space; else a non-blank `aria-label`; else a field's own label (`accessibleNameRef`, `accessibleName` or `label`, as the web component renders it on its input or host). A component named by its content (a button, a tab, a menu item, a breadcrumb item, a badge, a notification, a link, a native button, a heading) falls back to its own text, without slotted icons and tooltips. Nothing applies: `null`. Whitespace is normalised. [4. Locating](../spec/04-locating.md#filters) states the rules.
- **Playwright reads Dramafinder's `AccessibleNames`**, the computation behind `VaadinElement.getAccessibleName()` and its overrides. The `withAccessibleName` filter runs `BrowserQueries.ACCESSIBLE_NAME` in the page, a copy of the same scripts, as on Selenium: Dramafinder's own filter matches each `aria-labelledby` target on its own, so it would disagree with the read for several ids.
- **Browserless computes the same from the server-side elements**, with the ARIA attributes Vaadin's web components render in the browser: a field's label on its input, a group's on its host, a select's value, a dialog's header title, an avatar's name, a side nav's label, a card's title.

## Considered Options

- **Keep the Unified API's order:** the Playwright adapter can't use Dramafinder's read, and the name a test finds differs from what assistive technology announces when both `aria-label` and `aria-labelledby` are set.
- **Switch on Playwright only:** one code path there, but a test that finds a component by name on one Testing Tool fails on another.
- **Dramafinder's `withAccessibleName` filter on Playwright:** no copy of the script, but it matches a component named by two `aria-labelledby` targets by either target's text, never by the name `getAccessibleName()` reads.

## Consequences

- A `withAccessibleName("…")` lookup that matched a `Div`, a `Span` or another component not named by its content by its text no longer matches it: it becomes `withText("…")`.
- Where both `aria-label` and `aria-labelledby` are set, the name is the `aria-labelledby` text.
- A component named only by what renders in its shadow DOM (a Grid's `setAriaLabel(..)`) has no name, as Dramafinder reads it.
- The Findable View, Near Misses and Step Targets show no accessible name for an unnamed component; they name it by its text, label, test id or id, as before.
