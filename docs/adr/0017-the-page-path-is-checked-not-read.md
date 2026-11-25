---
status: accepted
---

# The page's path is checked with a retrying regex assertion, not read

A test that opens a Crud row expects the item's id in the URL (`products/42`), but doesn't know the id. `hasPath(..)` compares exactly, `isAt(view)` matches the route template (`products` and `products/42` both pass), and `UnifiedPage` has no way to read the path, so Bakery's migration dropped the check (#610). Decided in #610:

- **`assertThat(page).hasPathMatching(regex)`**: the path `hasPath` compares (no leading `/`, no query string) must match the regular expression as a whole. It retries until the timeout, like every other `PageAssert` check, and its User Step reads `see path matching “products/\d+”`. A leading `/` in the pattern is ignored, as in `hasPath`.
- **`String` and `java.util.regex.Pattern` overloads.** The `String` is the common case and matches the other checks' parameters. The `Pattern` is there for flags (`CASE_INSENSITIVE`). It is the first public method that takes a `Pattern`. The User Step shows the pattern's text, not its flags.
- **No path getter on `UnifiedPage`.** Spec [5. Waiting](../spec/05-waiting.md#assertions-and-getters) keeps getters for values a test uses, and assertions for checks.

## Considered Options

- **`hasPathStartingWith(prefix)`**, like `hasTextContaining(..)`: easier to read, but `products/` also passes on `products/abc/edit`, and the test can't say "a number follows". A regex covers a prefix too (`products/.+`).
- **A `currentPath()` read** used in `assertEquals` or AssertJ: it doesn't retry, so in a browser it is flaky right after the click that navigates, and its failure has no evidence. The scenario would still count as checked (a value the test read counts as a check in the run summary), but the report would show a read, not a `see` step.
- **`String` only:** one method, but flags would have to be written inline (`(?i)`).

## Consequences

- A test that needs the path as a value, to build another URL from it, still uses the Escape Hatch.
- Regex metacharacters in a path need escaping (`.`, `?`, `+`).
- `isAt(view, RouteParameters)` is not part of this decision. It can be added later without changing `hasPathMatching`.
