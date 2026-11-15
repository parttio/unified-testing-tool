---
status: accepted
---

# Wrappers carry their filters; lookups have no terminal

`page.find(UnifiedTextField.class)` returns the Wrapper itself, and its filters are methods on the Wrapper: `page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice")`. This amends [ADR 0002](0002-unified-wrapper-names-and-class-literal-lookups.md): the `Unified` prefix and class-literal lookups stay, while the per-type query (`Findable<Q>`, `TextFieldQuery`, ...) and the terminals `single()` and `exists()` go. `single()` was already lazy and checked nothing until the Wrapper was used, so it added words without behaviour. Removing it was the only readability change in the prototype that made the API smaller, as it folds 33 query types into their Wrappers. Decided in "What shape does a lookup take?" (#199), after the prototype in #198 compared it with `find(type, name)`, specs as values and action verbs.

## Decisions

- **Filters return a new Wrapper** and never change the one they are called on. Wrappers are immutable.
- **Filters stay per type at compile time** ([ADR 0001](0001-locator-filters-follow-component-type.md)), as generic mixins on the Wrapper (`CommonFilters<UnifiedTextField>`, `LabelFilter<UnifiedTextField>`). The runtime implements them the way it implements default methods ([ADR 0003](0003-wrappers-are-interfaces-shipped-in-the-addon-jar.md)), so an add-on author lists mixins and writes no filter code.
- **"Exactly one" is checked on every use.** Each action, read or assertion finds the component again and waits up to the Auto-Wait timeout for exactly one match. It fails with the count and the near misses. A Wrapper never holds a component instance.
- **Checks are retrying assertions, never booleans:** `assertThat(x).exists()`, `.doesNotExist()`, `.hasCount(n)`. The boolean `exists()` goes. `all()` stays, only for iterating, and returns lazy Wrappers by position.
- **Grid rows follow the same pattern, in the singular:** `people.row().withCellText("Name", "Alan Turing").click()`. `row(n)` takes an index; `rows()` and `RowQuery` go.
- `findComponent(X.class)` returns a lazy `UnifiedGenericComponent` with the shared filters. No page-level text assertion is added.

## Considered Options

- **`find(type, name)` shortcut** (`find(UnifiedTextField.class, "Name")`): seven fewer words in the prototype, but two ways to say "labelled Name".
- **Specs as values** (`find(textField("Name"))`): read best aloud, but a second hierarchy of spec types and static factories, about doubling the API.
- **Action verbs** (`page.fillIn("Name", ..)`, `page.clickOn("Save")`): fewest words, but they hide the component kind and move complexity into rules nobody sees in the code.

## Consequences

- Each Wrapper's autocomplete lists about a dozen `with…` filters next to its actions.
- Every existing Unified UI Test, the Migration Recipes and their golden pairs, and add-on Wrappers must move. How is decided in "How do existing tests, recipes and add-on Wrappers move to style B?" (#212).
- Grid row actions become Wrapper actions and so get Auto-Wait, which they lack today (#214).
