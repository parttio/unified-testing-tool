# 4. Locating components

Decided in [#7](https://github.com/parttio/unified-testing-tool/issues/7), [#12](https://github.com/parttio/unified-testing-tool/issues/12) and "What shape does a lookup take?" (#199); see [ADR 0001](../adr/0001-locator-filters-follow-component-type.md), [ADR 0002](../adr/0002-unified-wrapper-names-and-class-literal-lookups.md) and [ADR 0007](../adr/0007-wrappers-carry-their-filters.md). Templates: [#347](https://github.com/parttio/unified-testing-tool/issues/347), [ADR 0012](../adr/0012-lookups-enter-templates-explicitly.md). The style the lookups are written in: [12. Readable tests](12-readable-tests.md#lookups).

## Lookup shape

```java
page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
```

```mermaid
flowchart LR
    S["find(Wrapper.class)"] -->|"the Wrapper"| F["filters<br/>withLabel, withText, …"] -->|"a new Wrapper"| U["use<br/>action, read, assertThat"]
```

- `find(UnifiedX.class)` returns **the Wrapper itself**. There is no query type and no terminal: the Wrapper is the query.
- The Wrapper type acts as the role (like Playwright's `getByRole`). No role filter.
- Filters are methods of the Wrapper, declared as mixins (`UnifiedTextField extends CommonFilters<UnifiedTextField>, LabelFilter<UnifiedTextField>`, [6. Wrappers](06-wrappers.md#shape)), so each type offers **only the filters that make sense for it**. Wrong filters do not compile:

```java
page.find(UnifiedButton.class).withLabel("Save");   // compile error: Button has no label
page.find(UnifiedButton.class).withText("Save");    // OK
```

| Rule | Detail |
|---|---|
| Immutable | A filter returns a new Wrapper and never changes the one it was called on. A Wrapper can be declared once and narrowed in several ways. |
| Lazy | `find(..)` and the filters search nothing. A Wrapper never holds a component instance. |
| Exactly one, on every use | Every action, read and assertion finds the component again and waits up to the timeout for exactly one match ([5. Waiting](05-waiting.md)). No match or several fail with a `UnifiedAssertionError` naming the count and the Near Misses ([12. Readable tests, Failures](12-readable-tests.md#failures)). |

## Filters

Exact match by default (whitespace normalised); every text filter has a `…Containing` variant, which matches a case-insensitive substring.

| Filter | Matches | Mixin | Recommended |
|---|---|---|---|
| `withLabel` | `label` property, or the label of the FormLayout item holding the field (`addFormItem(field, "Name")`: the `<vaadin-form-item>`'s `slot="label"` child) | `LabelFilter` | yes |
| `withAccessibleName` | what a screen reader announces (see below) | `CommonFilters` (every type) | yes |
| `withText` | text content: `getText()`, the DOM text, the same on every Testing Tool | `TextFilter` | yes |
| `withPlaceholder` | `placeholder` | `PlaceholderFilter` | yes |
| `withTestId` | `data-testid` (`Component.setTestId`) | `CommonFilters` | fallback |
| `withId` | `id` | `CommonFilters` | fallback |
| `withClassName`, `withTheme`, `withAttribute` | CSS class, theme, attribute | `CommonFilters` | no (implementation details) |
| `withClassNames` | every one of several CSS classes; `withClassName` takes one class and rejects a name with spaces on every Testing Tool, as Flow's `addClassName` does | `CommonFilters` | no (implementation details) |
| `visible()` | visible components only (`isVisible()`) | `CommonFilters` | where visibility matters |
| `withProperty`, `where(Predicate<UnifiedElement>)` | building blocks for custom filters | `CommonFilters` | for Wrapper authors |
| type-specific, e.g. `withHeaderTitle` | declared by the Wrapper as a default method | that type | yes |

**Accessible name**: the browser's computation, as Dramafinder's `getAccessibleName()` reads it, the same on every Testing Tool. Decided in #641; see [ADR 0016](../adr/0016-accessible-name-follows-the-browser.md). `UnifiedElement.getAccessibleName()` reads the same value ([6. Wrappers](06-wrappers.md#unified-element)):

1. the texts of the `aria-labelledby` targets, every id, joined with a space
2. a non-blank `aria-label`
3. a field's own label, as the web component renders it on its input (a group, such as a radio group, on its host): `accessibleNameRef` (`setAriaLabelledBy`), else `accessibleName` (`setAriaLabel`), else `label`. A select adds the value it shows (the selected item or the placeholder). A placeholder is not a name
4. the own text of a component named by its content, only when it has neither `aria-labelledby` nor `aria-label`: a button, a tab, a menu item, a breadcrumb item, a badge, a notification, a link (`Anchor`, `RouterLink`), a native `<button>`, a heading. Slotted children, an icon or a tooltip, are not part of it

Each is read on the host, then on the field's own input. What a Vaadin web component renders as a name counts too: a dialog's header title, an avatar's name, a side nav's label, a card's title. Whitespace is normalised. Nothing applies: the name is `null`, so a `Div` or a `Span` showing text has none, and `withText(..)` finds it.

## What a lookup sees

The same on every Testing Tool:

| Rule | Detail |
|---|---|
| Hidden components | **included**: a component hidden after it was shown stays in a browser's page, so Browserless includes it too. Add `.visible()` for "the action is gone". A component that was never visible is in no browser's page (Flow doesn't send it), but Browserless finds it: `.visible()` gives the same answer everywhere. |
| Overlay hosts | a component whose content an overlay shows, and whose own element has no box (`Dialog`, `ConfirmDialog`, `Notification`): open ones only, and an open one is visible, so `visible()` and `isVisible()` agree. A closed one (the app's or the dev tools') is not part of the page. Each adapter registers its overlay hosts; another overlay component opts in with one registration |
| Shadow DOM | not searched by a lookup: neither Vaadin's components and the dev tools nor the app's own Templates. A test enters a Template explicitly ([Templates](#templates)) |
| Grid cell components | found page-wide for the rows the grid rendered, as a browser has them |
| No tag | a lookup for a component without a tag (`findComponent(Component.class)`, a `Composite`) fails at once on every Testing Tool: a browser can't find it |
| Classes that share a tag | Browserless finds a component by its class, a browser by its tag. When component classes share a tag, a browser lookup for one of them also checks the attribute that tells it apart, if the component renders one, so it finds what Browserless finds. A lookup for a supertype (`Component.class` with `tag = "a"`, as `UnifiedLink`) matches them all. Classes that render no such attribute match by tag in a browser: a lookup for one also finds the others ([Appendix: gaps](appendix-gaps.md#outside-the-testing-tools)) |

The classes that share a tag, and what tells them apart:

| Tag | Class | A browser lookup matches |
|---|---|---|
| `a` | `RouterLink` | `a[router-link]`: Flow puts a `router-link` attribute on a `RouterLink` |
| `a` | `Anchor` | `a:not([router-link])` |
| `a` | `Component` with `tag = "a"` (`UnifiedLink`) | every `a` |

Browser adapters put the filters CSS can express (id, test id, class, theme, attribute) into the selector and check the others in the page, for every candidate in one call.

**Guidance:** locate by what the user sees (type + label, accessible name or text). Use test id or id only when a component has no user-visible name.

## Checks and several matches

A lookup has no terminal. What used to need one is an assertion or `all()`:

| Intent | Write | Behaviour |
|---|---|---|
| use the one match | `find(..).withLabel("Name").setValue(..)` | waits for exactly one match |
| check it is there | `assertThat(wrapper).exists()` | retries until at least one matches |
| check it is gone | `assertThat(wrapper).doesNotExist()` | retries until none matches |
| check how many | `assertThat(wrapper).hasCount(n)` | retries until `n` match |
| loop over the matches | `for (var w : wrapper.all())` | lazy Wrappers by position: the `i`-th Wrapper is the `i`-th match whenever it is used |

There is no boolean `exists()`, no `first()` and no index lookup: when several components match, add a filter or a [scope](#scoping); when the order is the point, `all().get(i)` says so. `all()` is only for loops; counts are checked with `hasCount(n)`, which waits.

## Scoping

Every Wrapper that is a `Scope` (page, `UnifiedDialog`, `UnifiedContainer`, the plain HTML Wrappers, a `findComponent(..)` result) can start a lookup:

```java
UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Delete account?");
dialog.find(UnifiedButton.class).withText("Delete").click();

UnifiedContainer address = page.find(UnifiedContainer.class).withTestId("address-form");   // layouts
address.find(UnifiedTextField.class).withLabel("City").setValue("Turku");
```

A lookup inside a scope finds the scope again first, on every use: a missing scope fails with Near Misses for the scope.

It searches what the scope holds, not the scope itself: `dialog.find(UnifiedDialog.class)` finds a dialog inside the dialog, never the dialog. A part whose component is its content (a grid cell, a column header, `UnifiedDetails.content()`, `UnifiedTabSheet.content()`) finds that component, as a browser finds it in the cell or panel that holds it.

Components created by renderers (e.g. Grid cells) and the content of closed overlays are reached through their **owning Wrapper** on every Testing Tool ([7. Grid](07-grid.md)).

## Templates

Decided in #347; see [ADR 0012](../adr/0012-lookups-enter-templates-explicitly.md).

A **Template** is a component whose own content a test reaches only by entering it: an app's `LitTemplate`, whose `@Id` components live in its shadow root, or a web component that renders its content in the browser. No lookup enters a Template by itself. A test enters one Template at a time, by finding it as a Scope:

```java
page.findTemplate(StorefrontView.class)                    // no Wrapper of its own
    .find(UnifiedSearchBar.class)                          // a Wrapper with TemplateScope (6. Wrappers)
    .find(UnifiedButton.class).withId("new").click();
```

| Rule | Detail |
|---|---|
| Entering | `findTemplate(X.class)` on any Scope, for a Template without a Wrapper of its own (the tag from `@Tag`), or `find(..)` of a Wrapper that extends `TemplateScope` ([6. Wrappers](06-wrappers.md#templates)). There is no setting that makes lookups enter shadow roots. |
| What it searches | the Template's shadow root and its light DOM (slotted children, children the server added), not the shadow roots of nested Templates: each is entered with its own lookup. A slotted component is found once, where it is in the light DOM. |
| What is findable inside a `LitTemplate` | what the server knows, the same on every Testing Tool: the `@Id` components and what is under them. A browser takes the elements with an `id` in the shadow root, and what is under them; plain template HTML without an `id` is not found. |
| Portable lookups inside | `withId(..)`, and filters on state the server set. Text, labels and attributes written in the template's HTML are empty on the server: on Browserless such a filter doesn't match ([Appendix B](appendix-gaps.md#browserless-test)). |
| Ids | compared within the Template entered: the same id in two Templates is not ambiguous. |
| Client-rendered web components | entered the same way in a browser. Browserless can't see inside them: a test that enters one declares `@RequiresBrowser` ([9. Capabilities](09-capabilities.md#browser-only-features-in-the-unified-api)). |
| The Template's own text | `getText()` and `withText(..)` on a Template read its light DOM only, as on any component. |
| The Template's element *(unreleased)* | `findTemplate(X.class).element()` is the Template's host. Its Unified Element methods, `findDescendants(..)` and `findChildren(..)` among them, stay in the light DOM, as on any element: they don't enter the shadow root. Template HTML without an `@Id` (an element with no `id`, and nothing the server knows) can't be found on any Testing Tool, through a lookup or the element; give it an `@Id` to reach it. No Unified call will pierce the shadow root: a test for one browser reaches it through the Escape Hatch ([#617](https://github.com/parttio/unified-testing-tool/issues/617), `.out-of-scope/shadow-piercing-lookups.md`). |

A failed lookup that would have found its component inside a Template says so, with the lookup that enters it ([12. Readable tests, Failures](12-readable-tests.md#failures)).

## Components without a Wrapper

```java
UnifiedGenericComponent rating = page.findComponent(Rating.class).withLabel("Score");   // tag from @Tag
```

`findComponent(X.class)` returns a lazy `UnifiedGenericComponent` with the shared filters (`CommonFilters`, `LabelFilter`, `TextFilter`). It is a Scope.

## Escape Hatch only

CSS, XPath, ARIA role queries, predicates over a tool's own objects ([8. Escape Hatch](08-escape-hatch.md)).

## Requirements on Testing Tool adapters

| Adapter | Maps the Wrapper type to |
|---|---|
| Browserless | the Flow component class |
| Playwright | a locator from the web component tag |
| Selenium | the web component tag |

- Implement every filter in the table with the same meaning; exact match by default.
- Resolve a lookup on every use, and fail when zero or several components match, after waiting up to the timeout.
- Let the core list Near Misses: re-run a lookup without its user-facing filters, and read `label`, text, `placeholder`, `data-testid` and the accessible name of each candidate ([12. Readable tests](12-readable-tests.md#requirements-on-testing-tool-adapters)).
- TestBench before 25.4: implement `withTestId` with the `data-testid` attribute.
- Implement the `WITHOUT_ATTRIBUTE` filter: an element without the attribute. The core adds it, and an `ATTRIBUTE` filter without a value, to a lookup for a class that shares its tag ([What a lookup sees](#what-a-lookup-sees)).
- Search a Template's shadow root and light DOM when a lookup is scoped on it (`QuerySpec.inTemplate()`), and never a shadow root otherwise. Browserless: walk the Template's virtual children (its `@Id` components), which Browserless Test's own walk leaves out, and read an `@Id` binding's id as the element's `id`, as a browser does. List the Templates in a scope without entering them (`AdapterSession.findTemplates`), for failures and the Findable View.
