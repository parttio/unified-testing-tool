# Unreleased

What is on `main` and not in a release yet: the changes since [0.1.0-alpha4](0.1.0-alpha4.md). The documentation
site describes `main`, so everything below is in the guide and the specification but not in `0.1.0-alpha4`. This
page becomes the notes of the next alpha.

## New

- **The migration recipes give Failsafe its own evidence directory**
  ([#512](https://github.com/parttio/unified-testing-tool/issues/512)): every `maven-failsafe-plugin` in
  `<build><plugins>` and in each profile gets `vaadin.testing.evidence-dir` =
  `${project.build.directory}/failsafe-reports/unified-evidence`, unless it sets one already, so a build with
  Surefire and Failsafe no longer mixes both runs in one `summary.md`. The guide's CI examples upload
  `**/target/*-reports/unified-evidence/`.
- **The Migration Recipes enter Templates** ([#543](https://github.com/parttio/unified-testing-tool/issues/543)): the
  TestBench recipe turns `$(SearchBarElement.class)`, or `$("search-bar")`, into `page.findTemplate(SearchBar.class)`
  when a `LitTemplate` in the sources has that `@Tag`, and the lookups on its element into lookups inside it, with a
  Migration Marker on a text or label filter there. TestBench's `id("x")` becomes `withId("x")` everywhere. The
  Browserless recipe turns a Component Reference to a `LitTemplate`'s `@Id` field, such as
  `test(view.panel.search.newOrder).click()`, into `page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class)
  .find(UnifiedButton.class).withId("new").click()`.
- **Components inside a Template** ([#347](https://github.com/parttio/unified-testing-tool/issues/347),
  [ADR 0012](../adr/0012-lookups-enter-templates-explicitly.md)): a test enters a `LitTemplate` with
  `findTemplate(SearchBar.class)` on any Scope, or with a Wrapper that extends `TemplateScope`, and finds its `@Id`
  components inside, on every Testing Tool: `page.findTemplate(SearchBar.class).find(UnifiedButton.class).withId("new")`.
  No lookup enters a shadow root otherwise. On Browserless, an `@Id` component's id now reads as its `id`, as in a
  browser. A lookup that finds nothing names the Templates in which it would, with the lookup that enters them, and the
  Findable View lists each Template. Text written in the template's HTML is browser-only; a web component that renders
  its content in the browser needs `@RequiresBrowser`. The Adapter SPI gains `QuerySpec.inTemplate()`, which
  keeps its four-argument constructor, and `AdapterSession.findTemplates(..)`, which finds none by default.
- **One message of a chat** ([#515](https://github.com/parttio/unified-testing-tool/issues/515)):
  `UnifiedMessageList.message(i)` (0-based) and `message()` with `withUserName(..)`, `withText(..)`,
  `withTextContaining(..)` and `last()` find one message lazily, as `grid.row()` finds a row. Its checks,
  `exists()`, `doesNotExist()`, `hasCount(n)`, `hasText(..)`, `hasTextContaining(..)` and `hasUserName(..)`, wait
  for a reply that comes later and are User Steps:
  `see last message from “Assistant” with text containing “Pancakes” in message list`.
- **The Findable View counts a message list's messages**: `message list, 3 messages`, as it counts a grid's rows.
- **A screenshot `TestWatcher` that blocks a Dramafinder class says to remove it**
  ([#509](https://github.com/parttio/unified-testing-tool/issues/509)): when a copied base class keeps a class from
  migrating only through a `@RegisterExtension` field that calls `page.screenshot(..)` or `page.context().tracing()`,
  `FindTestClassesToMigrate` and the skip Migration Marker add
  `the Unified API writes failure evidence itself: remove it` to the reason.
- **The Dramafinder recipe converts more Playwright checks on raw locators**
  ([#510](https://github.com/parttio/unified-testing-tool/issues/510)): on a `locator("#x")` or `getByTestId("x")`
  it already turns into a lookup, `assertThat(locator)`'s `containsText(s)` becomes `hasTextContaining(s)`,
  `hasText(s)` `hasText(s)`, `hasAttribute("disabled", "")` `isDisabled()`, `not().hasAttribute("disabled", ..)`
  and `not().hasAttribute("aria-disabled", "true")` `isEnabled()`, `isAttached()` `exists()`, `hasClass("c")`
  `assertThat(w.withClassName("c")).exists()` and `not().isVisible()` `assertThat(w.visible()).doesNotExist()`.
- **The Dramafinder recipe converts a chat in a dock, `focus()` and a message by index**
  ([#511](https://github.com/parttio/unified-testing-tool/issues/511)): `MessageInputElement.get(page.getByTestId("dock"))`
  becomes `page.find(UnifiedContainer.class).withTestId("dock").find(UnifiedMessageInput.class)` (and the same for
  `MessageListElement`, and for `locator("#x")` / `locator("[data-testid=x]")`), `new MessageListElement(locator)`
  `page.find(UnifiedMessageList.class).withId(..)`, `input.focus()` `input.element().focus()`, and
  `assertThat(list.getMessage(i)).containsText(s)` / `hasText(s)` `assertThat(list.message(i)).hasTextContaining(s)`
  / `hasText(s)`. A message list read by index no longer keeps `assertMessageCount(n)` on it from converting.
- **The Dramafinder recipe opens a view by its route class after `getUrl()` too**
  ([#513](https://github.com/parttio/unified-testing-tool/issues/513)): `page.navigate(getUrl() + "/plan")` becomes
  `page.navigate(PlanView.class)`, and `page.navigate(getUrl() + "/plan?week=" + monday)` becomes
  `page.navigate(PlanView.class, QueryParameters.of("week", String.valueOf(monday)))`, when exactly one `@Route`
  among the migrated sources has the path, as `getView()` does since 0.1.0-alpha4. A renamed route then breaks at
  compile time, and the User Step reads `open Plan` instead of `open “plan”`. Any other URL, such as one with two
  query parameters, still becomes the path after the base URL.

## Changed behaviour

- **Failure evidence is in the test's own evidence directory** ([#514](https://github.com/parttio/unified-testing-tool/issues/514)):
  `<evidence dir>/<test class>/<test method>/failure.png` (browsers) or `failure.tree.txt` (Browserless), and
  `failure.view.txt`, next to `user-steps.txt`, instead of `<fully qualified class>.<method>.*` at the evidence
  directory's root. The failure's `Evidence` line names the directory and the files,
  `…/EditPersonTest/editsAPerson/  (failure.png, failure.view.txt)`, `user-steps.json`'s `failure` lists them as
  `evidence`, and `summary.json`'s per-scenario `evidence` paths become `EditPersonTest/editsAPerson/failure.png`.
  `UnifiedAssertionError.getEvidenceFiles()` returns the names. A script that read the root files reads the test's
  directory instead; a passing test leaves no `failure.*` there.
- **`user-steps.txt` shows the `Did you mean`** ([#518](https://github.com/parttio/unified-testing-tool/issues/518)):
  a failure with a suggestion gets one more line under its `✘` line, in the failure message's wording,
  `Did you mean  find(UnifiedButton.class).withAccessibleName("Previous Week")`. The CI log prints the same text.
  `Nearby` stays in the message, `user-steps.json` and the reports; the JSON is unchanged. *Reading reports and
  failures* now states exactly when a failure gets a `Did you mean`; the rules themselves are unchanged.

## Fixed

- **The Findable View names a container only when it narrows the alike down**
  ([#522](https://github.com/parttio/unified-testing-tool/issues/522)): `not unique: 7 alike` instead of
  `…; this one in container (test id “meal-row”)` when every row shares that test id; and
  `not unique: 3 alike; 2 in container (test id “billing”)` when the one container with that test id holds several.
- **The Findable View lists the same components on every Testing Tool**
  ([#517](https://github.com/parttio/unified-testing-tool/issues/517)): in a browser it no longer lists a
  component's internal parts, which the component renders itself and the application did not create, such as the
  text area inside a message input. Adapters report this as `ElementFacts.internalPart()`.
