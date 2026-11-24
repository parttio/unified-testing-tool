# Unreleased

What is on `main` and not in a release yet: the changes since [0.1.0-alpha4](0.1.0-alpha4.md). The documentation
site describes `main`, so everything below is in the guide and the specification but not in `0.1.0-alpha4`. This
page becomes the notes of the next alpha.

## New

- **Login** ([#564](https://github.com/parttio/unified-testing-tool/issues/564)): `page.find(UnifiedLogin.class)`
  finds Flow's `LoginOverlay` (while open) or `LoginForm`; `withTitle(..)` filters by the overlay's title.
  `login(username, password)` signs in: in a browser it types into the form's fields and presses submit, which also
  posts a form with an `action` (Spring Security's `/login`); on Browserless it fires the component's `LoginEvent`,
  and a component with an `action` fails, asking for `@RequiresBrowser` or `@WithMockUser`
  ([#582](https://github.com/parttio/unified-testing-tool/issues/582)). The User Step reads
  `sign in as “barista@vaadin.com” in login`; the password is a secret of the run, never shown in a step, report or
  failure. `forgotPassword()`, `isOpen()`, `isError()`, `getTitle()`, `getDescription()`, and the checks `isOpen`,
  `isClosed`, `hasError`, `hasNoError`, `hasTitle`. The TestBench recipe converts `LoginOverlayElement` and
  `LoginFormElement` lookups (`isOpened()`, `getTitle()`, `forgotPassword()`), and its login marker names
  `login(..)`; the Dramafinder recipe converts `LoginFormElement` and `LoginOverlayElement`, `login(..)` included.
  `@UnifiedComponent(tag = ..)` takes several comma-separated tags. **Adapter SPI:** `LoginAccess`.

- **Confirm dialogs** ([#532](https://github.com/parttio/unified-testing-tool/issues/532)):
  `page.find(UnifiedConfirmDialog.class)` finds Flow's `ConfirmDialog`, by `withHeaderTitle(..)` or
  `withTextContaining(..)`. `confirm()`, `reject()` and `cancel()` press the button of that role, whatever its text,
  on every Testing Tool, where a test found the button by a text that i18n or the web component's defaults chose;
  pressing a button the dialog doesn't show fails, listing the buttons it has. The User Steps read
  `confirm “Delete order?”`. `getText()`, `getConfirmText()`, `getRejectText()` and `getCancelText()` read what the
  user sees; `isOpen()`, `close()` and the header filter and checks are `UnifiedDialog`'s, now shared through
  `UnifiedOverlayDialog`. The TestBench recipe turns `$(ConfirmDialogElement.class)` and its
  `getConfirmButton().click()` into `page.find(UnifiedConfirmDialog.class)` and `confirm()`; the Dramafinder recipe
  converts `ConfirmDialogElement`. **Adapter SPI:** `ConfirmDialogAccess`.

- **Email and password fields** ([#555](https://github.com/parttio/unified-testing-tool/issues/555)):
  `page.find(UnifiedEmailField.class)` and `page.find(UnifiedPasswordField.class)` find Flow's `EmailField` and
  `PasswordField`, with a text field's filters, actions, getters and checks on every Testing Tool, where a project
  wrote its own Wrapper for each. Both share the new `UnifiedTextInput` with `UnifiedTextField` and
  `UnifiedTextArea`. What is typed into a password field reads `••••` in the User Steps, as before, and now its
  values read `“••••”` in a failed check too. The TestBench, Dramafinder and Browserless recipes convert
  `EmailFieldElement`, `PasswordFieldElement`, `EmailField` and `PasswordField` lookups to the new Wrappers.

- **Links** ([#561](https://github.com/parttio/unified-testing-tool/issues/561)): `page.find(UnifiedLink.class)` finds a
  `RouterLink`, an `Anchor` or any other `<a>`, by text, accessible name or `withHref(..)`. `click()` follows the link
  as a user's click does on every Testing Tool, where `findComponent(RouterLink.class).element().click()` followed it
  in a browser only: on Browserless, a link to a route of the application navigates to it, and one that leaves the
  application or opens another window fails asking for `@RequiresBrowser`. The User Steps read `click link “Users”`,
  then `open “users”`. `getHref()` and `assertThat(link).hasHref(..)` read the `href` as the application set it. The
  TestBench recipe turns `$(AnchorElement.class)` into `page.find(UnifiedLink.class)`. **Adapter SPI:** `LinkAccess`,
  which an adapter whose click doesn't follow a link provides; a lookup on Browserless for a class without a `@Tag` of
  its own and an explicit `@UnifiedComponent(tag = ..)` now also matches the tag, as a browser does.

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
- **Escape Hatch scenarios are counted apart under "Scenarios without a check"**
  ([#533](https://github.com/parttio/unified-testing-tool/issues/533)): after a TestBench migration, a scenario that
  checks with JUnit or AssertJ assertions on Escape Hatch reads looked unchecked, and such scenarios filled the list.
  A passing scenario without a check that recorded an Escape Hatch gap is no longer listed: one line after the list
  counts them, `13 more end in Escape Hatch code, whose checks the report can't see: …`, and points to resolving
  their Migration Markers or moving their checks to `assertThat(..)`. `report.html` shows them after that line with
  an *Escape Hatch* badge instead of *no check*; `summary.json` adds `escapeHatch` next to `unchecked`, whose meaning
  is unchanged.
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

- **An open `ConfirmDialog` is visible on every Testing Tool**
  ([#556](https://github.com/parttio/unified-testing-tool/issues/556)): `page.findComponent(ConfirmDialog.class).visible()`
  found the open confirm dialog on Selenium and none on Playwright, whose visibility check looked at the host's box,
  which a confirm dialog doesn't have. Overlay hosts (`Dialog`, `ConfirmDialog`, `Notification`) now follow one rule
  on every Testing Tool: open ones only, and an open one is visible ([4. Locating](../spec/04-locating.md#what-a-lookup-sees)).
  A closed `Dialog` that stays attached is no longer found on Playwright either, as on Selenium and Browserless.
- **`selectTab` follows the link of a tab that holds one**
  ([#531](https://github.com/parttio/unified-testing-tool/issues/531)): in a `Tabs` used as a navigation menu, as
  Bakery's `MainView` builds it, `selectTab("Users")` selected the tab and the view stayed the same, where a user's
  click lands on the tab's `RouterLink` and navigates. `selectTab` on a tab whose only `<a>` is a `RouterLink` or an
  `Anchor` now follows it as `UnifiedLink.click()` does: a click on the link in a browser; on Browserless, the tab is
  selected, then the link's route opened, and a link out of the application fails asking for `@RequiresBrowser`. A
  selection that navigated isn't read back, and the User Steps read `select tab “Users” in tabs`, then
  `open “users”`. `UnifiedTabSheet.selectTab(..)` does the same. **Adapter SPI:** `TabSheetAccess.link(label)`, the
  tab's link; its default, empty, keeps a custom adapter selecting the tab as before.
- **A `RouterLink` lookup no longer finds `Anchor`s in a browser**
  ([#562](https://github.com/parttio/unified-testing-tool/issues/562)): both render `<a>`, so on Playwright and
  Selenium `findComponent(RouterLink.class)` also found the anchors, and Bakery counted 4 router links where
  Browserless counted 3. A browser lookup for a class that shares its tag now also checks the attribute that tells it
  apart: `a[router-link]` for a `RouterLink`, `a:not([router-link])` for an `Anchor`. Counts, `exists()`,
  `hasCount(..)`, Near Misses and `describe()` agree on every Testing Tool. `UnifiedLink` still finds both. The
  classes that share a tag with nothing to tell them apart are listed in the
  [gaps](../spec/appendix-gaps.md#outside-the-testing-tools). **Adapter SPI:** a `WITHOUT_ATTRIBUTE` `Filter`, for an
  element without the attribute; a custom adapter that applies filters itself handles it.
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
