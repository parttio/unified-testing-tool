# Unreleased

What is on `main` and not in a release yet: the changes since [0.1.0-alpha4](0.1.0-alpha4.md). The documentation
site describes `main`, so everything below is in the guide and the specification but not in `0.1.0-alpha4`. This
page becomes the notes of the next alpha.

## New

- **The Findable View lists plain HTML the user can tell is clickable**
  ([#523](https://github.com/parttio/unified-testing-tool/issues/523)): a `div`, `span`, `article` and the other
  plain HTML Wrappers' components with a `role` such as `button` or `tab`, or `tabindex` 0 or more:
  `span “Week of 2026-11-16” (role button)  page.find(UnifiedSpan.class).withText("Week of 2026-11-16")`. A click
  listener alone doesn't count. **Adapter SPI:** an `ATTRIBUTE` `Filter` with a `null` value matches an element that
  has the attribute; a custom adapter that applies `ATTRIBUTE` filters itself handles it.
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

- **A Spring Boot app's TestBench tests start the app themselves** ([#528](https://github.com/parttio/unified-testing-tool/issues/528),
  [ADR 0013](../adr/0013-migrated-spring-boot-tests-start-the-app.md)): in a module with a `@SpringBootApplication`,
  the TestBench recipe turns a test class without `@SpringBootTest`, which ran against a server the build started,
  into a `@UnifiedSpringBootTest` instead of a `@UnifiedUITest`, with a Migration Marker saying the build's
  `spring-boot:start`/`stop` executions can go (the recipe leaves the pom's executions alone). The new option
  `springBootTest: false` keeps `@UnifiedUITest`, with a Migration Marker. In a project with Spring Security, the
  build gets `spring-boot-starter-security-test` (`spring-security-test` on a Spring Boot without it), and, by
  default, a statement calling a login element (`LoginOverlayElement`, `LoginFormElement` or a page object extending
  one) gets a Migration Marker suggesting `@WithMockUser`. `FindTestClassesToMigrate` names the annotation such a
  class gets in a new `Annotation` column.
- **`UnifiedDatePicker.setValue(..)` sets a picker whose calendar is open, though its field is hidden**
  ([#516](https://github.com/parttio/unified-testing-tool/issues/516)): such as a week picker an application opens
  from a badge of its own. Auto-Wait waits for the field to be visible and enabled, or for the calendar to be open.
  With the field hidden, the date is set as a pick in the calendar sets it and the calendar closes; a visible field
  is set as before, in as many round trips. A hidden picker whose calendar is closed still fails as hidden.
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

- **`selectTab` on a tab that sends the user to another page passes**
  ([#554](https://github.com/parttio/unified-testing-tool/issues/554)): after the click, `selectTab` reads the
  selected tab back. When the tab's listener had left the page (`Page.setLocation(..)`, as a "Logout" tab does), that
  read failed with the Testing Tool's own error, such as Playwright's `TimeoutError`, and the report had no User Step
  for it. Tabs the lookup no longer finds now have nothing to check, as a selection field the app replaced
  ([#377](https://github.com/parttio/unified-testing-tool/issues/377)); Playwright sees they're gone at once,
  instead of after its 10 s timeout. A tab that doesn't take, on tabs still on the
  page, fails as a `UnifiedAssertionError`: Expected the tab selected in the tabs, Found the selected one, the
  Testing Tool's error as the cause. Browserless doesn't follow `Page.setLocation(..)`: such a test needs
  `@RequiresBrowser` (*Known limitations by Testing Tool*).
- **Selenium opens and recognises a route with an optional parameter or an alias**
  ([#530](https://github.com/parttio/unified-testing-tool/issues/530)): `page.navigate(StorefrontView.class)` on
  `@Route("storefront/:orderID?")` opens `storefront`, as on the other Testing Tools, not `storefront/:orderID`.
  `assertThat(page).isAt(view)` matches the route's template and each `@RouteAlias` of the view on Selenium and
  Playwright: a required `:x` matches one segment, an optional `:x?` zero or one, a wildcard `:x*` the rest.
  `navigate(view)` on a route with a required parameter fails with a `UnifiedAssertionError` naming the
  parameter, on every Testing Tool, instead of a server error.
- **The style rules accept the waits migrated TestBench code keeps**
  ([#529](https://github.com/parttio/unified-testing-tool/issues/529)): `UnifiedStyleRules.NO_RAW_WAITS` allows a
  Testing Tool's own wait API, such as `waitForFirst()` or `TestBenchEscapeHatch.waitUntil(..)`, in code that
  declares `@RequiresTestingTool` for that Testing Tool: the test method, its class or an enclosing class, or a
  helper that only such tests reach. Escape Hatch code gets none of the Unified API's waiting. `Thread.sleep(..)`,
  Awaitility, another Testing Tool's waits, and waits in a helper a test without the annotation also reaches still
  fail; for a wait in code that declares another Testing Tool, the message names the one it belongs to.
- **Uses of custom TestBench page objects are marked and run on Selenium only**
  ([#527](https://github.com/parttio/unified-testing-tool/issues/527)): a statement calling an application's own
  `TestBenchElement` subclass, such as `loginView.login(..)` on a `LoginViewElement extends LoginOverlayElement`,
  gets the Migration Marker `TestBench page object: LoginViewElement (custom element extending LoginOverlayElement):
  write a Wrapper or convert the page object`. A test reaching one, itself or through a helper of its class hierarchy,
  gets `@RequiresTestingTool(SELENIUM)`, as does a test calling only an inherited helper that goes through the Escape
  Hatch. `FindTestClassesToMigrate` lists the page objects and custom elements of each TestBench class, each
  `converts with includePageObjects` or `stays TestBench (Escape Hatch)` with the reason, in a new `Page objects`
  column, and a class using one that stays TestBench is `migrates partly`.

- **The Findable View names a container only when it narrows the alike down**
  ([#522](https://github.com/parttio/unified-testing-tool/issues/522)): `not unique: 7 alike` instead of
  `…; this one in container (test id “meal-row”)` when every row shares that test id; and
  `not unique: 3 alike; 2 in container (test id “billing”)` when the one container with that test id holds several.
- **The Findable View lists the same components on every Testing Tool**
  ([#517](https://github.com/parttio/unified-testing-tool/issues/517)): in a browser it no longer lists a
  component's internal parts, which the component renders itself and the application did not create, such as the
  text area inside a message input. Adapters report this as `ElementFacts.internalPart()`.
