# How other web stacks make UI tests readable

Research for [#194](https://github.com/parttio/unified-testing-tool/issues/194), part of the map [#193](https://github.com/parttio/unified-testing-tool/issues/193) "Make Unified UI Tests read like what the user does".

**Question.** How do the main web UI testing tools make tests read like what the user does, and what does each approach cost? Could each approach sit on top of the Unified API without breaking the guarantee that one Unified UI Test runs unchanged on Browserless, Playwright and Selenium?

**Scenario used in every sample:** fill the field labelled *City* with *Turku*, click *Save*, check that a notification *Saved* is shown.

**Sources.** Official documentation and first-party source code only. Several documentation hosts (testing-library.com, docs.cypress.io, selenide.org, serenity-bdd.github.io, robotframework.org, kasperskylab.github.io) could not be reached from the research session, so their pages were read from the repositories the sites are built from (for example `testing-library/testing-library-docs`, `cypress-io/cypress-documentation`, `serenity-bdd/serenity-bdd.github.io`, `robotframework/robotframework/doc/userguide`). Each citation gives the published URL and, where the claim comes from source code, the source file. Read on 2026-11-15.

## Contents

- [Baseline: the Unified API today](#baseline-the-unified-api-today)
- [Testing Library](#1-testing-library)
- [Playwright locators and codegen](#2-playwright-locators-and-codegen)
- [Cypress](#3-cypress)
- [Capybara](#4-capybara)
- [Selenide](#5-selenide)
- [Screenplay (Serenity BDD)](#6-screenplay-pattern-serenity-bdd)
- [Robot Framework](#7-robot-framework)
- [Kotlin/Android DSLs: Kaspresso and Compose testing](#8-kotlinandroid-test-dsls-kaspresso-kakao-and-compose-testing)
- [What the tools have in common](#what-the-tools-have-in-common)
- [Comparison table](#comparison-table)
- [Ideas worth prototyping here](#ideas-worth-prototyping-here)

## Baseline: the Unified API today

```java
page.find(UnifiedTextField.class).withLabel("City").single().setValue("Turku");
page.find(UnifiedButton.class).withText("Save").single().click();
assertThat(page.find(UnifiedNotification.class).single()).isOpen().hasText("Saved");
```

What it already has, which several tools below took years to arrive at:

- **Locate by what the user sees.** `withLabel`, `withText`, `withAccessibleName` are the recommended filters; test id and id are fallbacks; CSS, XPath and ARIA role queries are Escape Hatch only ([04-locating.md](../spec/04-locating.md)).
- **The wrapper type is the role.** `find(UnifiedButton.class)` plays the part of `getByRole('button')` ([04-locating.md](../spec/04-locating.md), [finding-components.md](../guide/finding-components.md)).
- **Filters checked at compile time.** `find(UnifiedButton.class).withLabel(..)` does not compile ([ADR 0001](../adr/0001-locator-filters-follow-component-type.md)).
- **Strict.** `single()` fails when zero or several components match ([04-locating.md](../spec/04-locating.md)).
- **Lazy and auto-waiting.** A wrapper re-finds its component on every use; actions wait (Auto-Wait, [GLOSSARY.md](../../GLOSSARY.md)).
- **Open to add-ons.** Wrappers are annotated interfaces shipped in the add-on's own jar ([ADR 0003](../adr/0003-wrappers-are-interfaces-shipped-in-the-addon-jar.md)), found by class literal so that no entry object has to know every type ([ADR 0002](../adr/0002-unified-wrapper-names-and-class-literal-lookups.md)).

What it reads like: the sentence is noun-first and ceremony-heavy. Each line says *find, type, filter, cardinality, action*: `find(UnifiedTextField.class)` and `.single()` carry no meaning a reader of the test cares about. A failure says `No component matches UnifiedTextField.withLabel(City).single()` ([`LazyElement.java`](../../unified-test-api/src/main/java/com/vaadin/testing/unified/internal/LazyElement.java)), in API terms, without listing what *was* there. There is no notion of a step, so a report can't say "Saved the city" or "filled City with Turku".

The constraints that any new style must respect (from the map [#193](https://github.com/parttio/unified-testing-tool/issues/193)): three-Testing-Tool guarantee and Auto-Wait not up for trade; add-ons can join; no import clashes; filters checked at compile time.

---

## 1. Testing Library

```js
const user = userEvent.setup()
render(<AddressForm />)

await user.type(screen.getByLabelText('City'), 'Turku')
await user.click(screen.getByRole('button', { name: 'Save' }))
expect(await screen.findByText('Saved')).toBeInTheDocument()
```

**What makes it readable.**

- One principle drives the whole API: "The more your tests resemble the way your software is used, the more confidence they can give you" ([guiding principles](https://testing-library.com/docs/guiding-principles)).
- A documented **priority order** of queries: `getByRole` ("your top preference for just about everything"), `getByLabelText` ("really good for form fields … users find elements using label text"), `getByPlaceholderText`, `getByText`, `getByDisplayValue`; then `getByAltText`, `getByTitle`; `getByTestId` last, "only recommended for cases where you can't match by role or text" ([queries/about, Priority](https://testing-library.com/docs/queries/about#priority)).
- Query prefixes encode cardinality and waiting in the verb: `getBy` throws on 0 or more than 1, `queryBy` returns `null` on 0, `findBy` retries (default 1000 ms) ([queries/about, Types of Queries](https://testing-library.com/docs/queries/about#types-of-queries)).
- `user-event` "allows you to describe a user interaction instead of a concrete event. It adds visibility and interactability checks along the way … the browser e.g. wouldn't let a user click a hidden element or type in a disabled text box" ([user-event intro](https://testing-library.com/docs/user-event/intro)).

**What it hides or costs.**

- *Debuggability* is good: when a `get`/`find` query fails, "the current state of the `container` (DOM) gets printed" ([debugging](https://testing-library.com/docs/dom-testing-library/api-debugging#automatic-logging)).
- *Performance*: `getByRole` "can be expensive (particularly with large DOM trees)"; the docs suggest `hidden: true` or falling back to `getByLabelText`/`getByText` ([byRole, Performance](https://testing-library.com/docs/queries/byrole#performance)).
- *Type safety*: roles are strings (`'button'`); nothing stops `getByRole('button', {name})` on a role with no name, and the returned element is an untyped `HTMLElement`. No per-type actions: `user.type` works on anything.
- *Ambiguity* is handled by strictness (`getBy` throws on more than one).
- *Extension*: new queries are plain functions over the DOM (`buildQueries`); there is no notion of a component type a third party can add.

**On the Unified API?** The *vocabulary* is already there: wrapper type = role, `withLabel` = `getByLabelText`, `withAccessibleName` = `{name}`, strict `single()` = `getBy`. A role-string API itself cannot be portable: Browserless has no accessibility tree, which is why ARIA role queries are Escape Hatch only ([04-locating.md](../spec/04-locating.md)). What can be borrowed: the written *priority order* (the spec has a "Recommended" column but no ranking), and the DOM dump on failure (as a list of candidates; see idea 3).

## 2. Playwright locators and codegen

```java
page.getByLabel("City").fill("Turku");
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
assertThat(page.getByText("Saved")).isVisible();
```

**What makes it readable.**

- `getBy…` methods named after what the user perceives; the docs "recommend prioritizing user-facing attributes and explicit contracts such as `getByRole`" and call CSS/XPath "not recommended" ([locators](https://playwright.dev/docs/locators#locating-elements), [CSS or XPath](https://playwright.dev/docs/locators#locate-by-css-or-xpath)).
- **No terminal.** A locator is "a way to find element(s) on the page at any moment"; it is the unit of auto-waiting and retry ([locators, Introduction](https://playwright.dev/docs/locators)). Actions wait until the element is visible, stable, receives events and is enabled ([actionability](https://playwright.dev/docs/actionability)).
- **Strict by default**: "all operations on locators that imply some target DOM element will throw an exception if more than one element matches"; `first()`, `last()`, `nth()` are "not recommended" ([locators, Strictness](https://playwright.dev/docs/locators#strictness)).
- **Codegen** records a user's clicks and "will look at your page and figure out the best locator, prioritizing role, text and test id locators. If the generator finds multiple elements matching the locator, it will improve the locator to make it resilient" ([codegen](https://playwright.dev/docs/codegen)). Assertions can be recorded too.
- **Report vocabulary**: `test.step("Log in", …)` "declares a test step that is shown in the report" ([test.step](https://playwright.dev/docs/api/class-test#test-step)); `locator.describe("Subscribe button")` (since 1.53) names a locator "in the trace viewer and reports" ([locator.describe](https://playwright.dev/docs/api/class-locator#locator-describe)).

**What it hides or costs.**

- *Type safety*: one `Locator` type for every element; `fill` on a button compiles and fails at run time. `GetByRoleOptions` in Java is verbose.
- *Ambiguity*: none silently (strictness), at the price of more specific locators.
- *Debuggability*: strong (trace viewer, Inspector, steps in the HTML report), but it's tooling around a browser; none of it exists in a JVM-only run.
- *Extension*: custom selector engines through `selectors.register` ([Selectors.register](https://playwright.dev/docs/api/class-selectors#selectors-register)), string-based; no typed component objects.

**On the Unified API?** The locator model is the closest relative of the Unified query: lazy, strict, auto-waiting. Two things are borrowable without touching portability: (a) **dropping the terminal** — `single()` is already lazy and fails on first use, so the query object could itself act as the lazy wrapper (idea 2); (b) **steps and descriptions in reports** (idea 3). A **recorder** that emits Unified API code is conceivable on the Playwright adapter: it would see Vaadin tags and labels and could emit `find(UnifiedTextField.class).withLabel("City")`; the generated test stays portable as long as it only emits Unified queries (idea 5).

## 3. Cypress

```js
cy.getByRole('textbox', { name: 'City' }).type('Turku')
cy.getByRole('button', { name: 'Save' }).click()
cy.contains('Saved').should('be.visible')
```

(With the Cypress Testing Library plugin: `cy.findByLabelText('City').type('Turku')`.)

**What makes it readable.**

- One chain per user step; queries and assertions "link up, retrying the entire chain together" ([retry-ability](https://docs.cypress.io/app/core-concepts/retry-ability)).
- `cy.contains(text)` reads like the user's eye scanning for text ([contains](https://docs.cypress.io/api/commands/contains)).
- A new built-in `cy.getByRole(role, {name})` finds elements "the way assistive technology does, and fails if the role or text is changed" ([getByRole](https://docs.cypress.io/api/commands/getbyrole)). The docs "strongly endorse" Testing Library's practices "where … you want to fail a test if a specific piece of content or accessible role is not present" ([best practices, Cypress and Testing Library](https://docs.cypress.io/app/core-concepts/best-practices#Cypress-and-Testing-Library)).
- Custom commands let a team grow its own verbs (`cy.login()`) ([custom commands](https://docs.cypress.io/api/cypress-api/custom-commands)).

**What it hides or costs.**

- *Mixed guidance*: the main "Selecting Elements" best practice is **`data-*` attributes**, and text only "if the content of the element changed would you want the test to fail?" ([best practices, Selecting Elements](https://docs.cypress.io/app/core-concepts/best-practices#Selecting-Elements)). User-facing locators are opt-in, not the default.
- *Ambiguity*: `cy.contains('Hello')` yields the "first el on the page containing 'Hello'", and "DOM elements can contain *more* than the desired text and still match" ([contains](https://docs.cypress.io/api/commands/contains)). No label query built in.
- *Type safety*: custom commands are declared by augmenting the global `Cypress.Chainable` interface by hand ([best practices, Selecting Elements](https://docs.cypress.io/app/core-concepts/best-practices#Selecting-Elements)); the docs themselves warn "Don't make everything a custom command" and "Don't overcomplicate things" ([custom commands, Best Practices](https://docs.cypress.io/api/cypress-api/custom-commands#Best-Practices)).
- *Debuggability*: the command log and time-travel snapshots are good, but browser-only.

**On the Unified API?** Little to add beyond what Testing Library and Playwright already show. The lesson is a warning: once readable verbs are user-defined commands, every team's tests read differently and lose types. If the Unified API adds verbs, it should ship them, typed, rather than leave each team to invent them.

## 4. Capybara

```ruby
fill_in 'City', with: 'Turku'
click_on 'Save'
expect(page).to have_content 'Saved'
```

**What makes it readable.**

- **Action-first verbs, no lookup step.** The verb names the user's action and takes the label the user reads: `fill_in('First Name', with: 'John')`, `click_button('Save')`, `click_on` "clicks on either links or buttons", `choose`, `check`, `select('Option', from: 'Select Box')` ([README, Interacting with forms](https://github.com/teamcapybara/capybara#interacting-with-forms), [Clicking links and buttons](https://github.com/teamcapybara/capybara#clicking-links-and-buttons)).
- **Each verb knows which element kinds it can act on.** `fill_in` uses the `:fillable_field` selector (inputs other than submit, image, radio, checkbox, hidden, file; and textareas) ([`fillable_field.rb`](https://github.com/teamcapybara/capybara/blob/master/lib/capybara/selector/definition/fillable_field.rb)); `click_on` is an alias of `click_link_or_button` ([`actions.rb`](https://github.com/teamcapybara/capybara/blob/master/lib/capybara/node/actions.rb)).
- Matchers like `have_content` wait and retry up to `default_max_wait_time` ([README, Asynchronous JavaScript](https://github.com/teamcapybara/capybara#asynchronous-javascript-ajax-and-friends)).
- **The same DSL drives several drivers**, including the in-process `:rack_test` driver ("fast but limited: it does not support JavaScript") and Selenium ([README, Drivers](https://github.com/teamcapybara/capybara#drivers)). This is the oldest precedent for the Unified API's Browserless-plus-browsers model.

**What it hides or costs.**

- *Ambiguity, by design*: one string means several things. "The field can be found via its name, id, test_id attribute, placeholder, or label text"; buttons "by their id, name, test_id attribute, value, or title" ([`actions.rb`](https://github.com/teamcapybara/capybara/blob/master/lib/capybara/node/actions.rb)). Substring matching is on by default: `click_link("Password") # also matches "Password confirmation"`; the default `Capybara.match = :smart` tries exact first, then partial ([README, Matching](https://github.com/teamcapybara/capybara#matching)). A reader can't tell from `fill_in 'City'` whether *City* is a label or a `name` attribute.
- *Type safety*: none (Ruby); the verb set is closed for element kinds — a custom widget either looks like an `<input>` or needs a custom selector.
- *Extension*: `Capybara.add_selector(:name) { xpath {…} }` and `modify_selector` ([README, XPath, CSS and selectors](https://github.com/teamcapybara/capybara#xpath-css-and-selectors)): powerful but string/XPath-based; `fill_in` always searches with `:fillable_field`, so a custom widget joins the verb only by modifying that shared selector.

**On the Unified API?** Yes, as a thin layer: `page.fillIn("City", "Turku")` can resolve to "the one component, of any wrapper type whose query has `withLabel` and whose wrapper accepts a string value, labelled *City*" and then call the typed wrapper. All three adapters already implement `withLabel` with the same meaning, so nothing tool-specific is needed. What must *not* be copied: the multi-attribute fallback (label *or* name *or* id), which ADR 0001 already rejected for `withCaption`, and substring-by-default. Keep exact, label-only, strict (idea 1).

## 5. Selenide

```java
open("/address");
$(byLabel("City")).setValue("Turku");
$(byRole("button", "Save")).click();
$(byText("Saved")).shouldBe(visible);
```

**What makes it readable.**

- "A concise fluent API, natural language assertions" ([README](https://github.com/selenide/selenide#what-is-selenide)): `shouldBe(visible)`, `shouldHave(text("…"))`.
- `$` is **lazy** and waits: conditions are re-checked until `Configuration.timeout`, default 4000 ms ([`Selenide.java`](https://github.com/selenide/selenide/blob/main/statics/src/main/java/com/codeborne/selenide/Selenide.java), [`Configuration.java`](https://github.com/selenide/selenide/blob/main/src/main/java/com/codeborne/selenide/Configuration.java)).
- User-facing selectors were added over time: `byLabel`, `byPlaceholder` and `byTestId` (7.12.0), `byRole(role, accessibleName)` (7.17.0) ([`Selectors.java`](https://github.com/selenide/selenide/blob/main/src/main/java/com/codeborne/selenide/Selectors.java)).

**What it hides or costs.**

- *Ambiguity*: `$(…)` "locates the **first** element matching" ([`Selenide.java`](https://github.com/selenide/selenide/blob/main/statics/src/main/java/com/codeborne/selenide/Selenide.java)). A second *City* field is silently ignored.
- *Accessible name* is "computed pragmatically" (aria-labelledby, aria-label, label, alt, text, title), not by the browser ([`Selectors.java`, byRole](https://github.com/selenide/selenide/blob/main/src/main/java/com/codeborne/selenide/Selectors.java)): the same trade-off the Unified spec makes ("Rare edge cases can differ from the browser").
- *Type safety*: every element is a `SelenideElement`; `setValue` on a button compiles. Typed widgets exist only as user-written page objects (`Container` marker interface, [`Container.java`](https://github.com/selenide/selenide/blob/main/src/main/java/com/codeborne/selenide/Container.java)).
- *Imports*: static imports (`$`, `byLabel`, `visible`) are what make it short; this is the cost ADR 0002 avoided for type-token constants.

**On the Unified API?** The shape `$(byLabel("City"))` is "specs as values", which ADR 0002 rejected because the static factories would live on the wrapper type and clash with Flow names. Factories on a separate class (`Unified.textField("City")`) avoid that clash but still need static imports and a second home for every add-on wrapper. The useful lesson is the reverse: Selenide shows what happens without strictness and without typed elements, and both are properties the Unified API should keep.

## 6. Screenplay pattern (Serenity BDD)

```java
@CastMember
Actor toby;

@Test
void savesCity() {
    toby.attemptsTo(
        Enter.theValue("Turku").into(InputField.withLabel("City")),
        Click.on(Button.withText("Save")),
        Ensure.that(Target.the("notification").locatedBy("vaadin-notification-card"))
              .text().isEqualTo("Saved"));
}
```

**What makes it readable.**

- The model is a story: "*actors* who interact with an application … to perform *tasks* that help them achieve their goals. Actors have *abilities* … Actors can also answer *questions*" ([Screenplay fundamentals](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals)).
- **Tasks** group interactions into domain steps, with a report description: `Task.where("{0} logs in as " + username, …)` "will appear in the reports whenever the task is executed", or `@Step("{0} adds an item to the list")` ([fundamentals, Grouping interactions into tasks](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals#grouping-interactions-into-tasks)). A test then reads `toby.attemptsTo(Login.as("standard_user", "secret_sauce"))`.
- **Questions** produce readable failures: `Question.about("the number of todo items")` "will produce a more meaningful error message" ([fundamentals, Questions](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals#questions---querying-the-state-of-the-system)).
- Ready-made UI targets read like the user: `InputField.withLabel(..)`, `Button.withText(..)`, `Button.withAriaLabel(..)` ([`InputField.java`](https://github.com/serenity-bdd/serenity-core/blob/main/serenity-screenplay-webdriver/src/main/java/net/serenitybdd/screenplay/ui/InputField.java), [`Button.java`](https://github.com/serenity-bdd/serenity-core/blob/main/serenity-screenplay-webdriver/src/main/java/net/serenitybdd/screenplay/ui/Button.java)); `Target.the("…")` gives every element a name for the report.
- Works with Cucumber, so Gherkin steps can call tasks ([fundamentals, Screenplay and Cucumber](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals#screenplay-and-cucumber)).

**What it hides or costs.**

- *Ceremony*: for a three-step scenario Screenplay is longer than plain calls; it pays off only when tasks are reused. The docs' own first example, `Enter.theValue(..).into("#user-name")`, is "hard to see … at a glance" until wrapped in a task ([fundamentals](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals#grouping-interactions-into-tasks)).
- *Type safety*: `Enter`, `Click` accept any `Target`; nothing ties `Enter` to a field.
- *Debuggability*: excellent reports (every task and interaction is a named step), but stack traces pass through the performable machinery.
- *Extension*: anyone can write a `Performable` or `Question`, so third parties extend freely.

**On the Unified API?** Yes, entirely: tasks and questions are plain Java over whatever API is underneath, so a Screenplay-style layer over Unified wrappers stays portable. The Unified API even has the actor concept already: `page.openAsUser("bob", "USER")` gives each user their own Unified Page ([11-users.md](../spec/11-users.md)). But a full Screenplay layer is a framework of its own; the parts with the best value-for-cost are **named steps in reports** and **readable question descriptions** (idea 3), plus a lightweight way to group steps into tasks (idea 4).

## 7. Robot Framework

```robotframework
*** Test Cases ***
Saving the city shows a confirmation
    Fill Text    label=City    Turku
    Click    role=button[name="Save"]
    Wait For Elements State    text=Saved    visible
```

Or, in behaviour-driven style with user keywords:

```robotframework
*** Test Cases ***
Saving the city shows a confirmation
    When the user fills City with Turku
    And the user clicks Save
    Then a notification Saved is shown

*** Keywords ***
the user fills ${label} with ${value}
    Fill Text    label=${label}    ${value}
```

**What makes it readable.**

- Tests are tables of keywords, readable "even for people without any Robot Framework experience" when arguments are embedded in keyword names ([User Guide, Embedding arguments into keyword name](https://robotframework.org/robotframework/latest/RobotFrameworkUserGuide.html#embedding-arguments-into-keyword-name)).
- A **behaviour-driven style** with `Given/When/Then/And/But` prefixes for "executable requirements … that also non-technical project stakeholders must understand" ([User Guide, Behavior-driven style](https://robotframework.org/robotframework/latest/RobotFrameworkUserGuide.html#behavior-driven-style)).
- The Browser library (Playwright-based) offers `label=` "as Playwright's `getByLabel`" and `role=button[name="Login"]`, and runs in **strict mode** by default: "a selector matching more than one element fails the keyword" ([Browser library, Finding elements](https://marketsquare.github.io/robotframework-browser/Browser.html#Finding%20elements); source [`browser.py`](https://github.com/MarketSquare/robotframework-browser/blob/main/Browser/browser.py)).

**What it hides or costs.**

- *Locator semantics differ per library.* SeleniumLibrary's default strategy matches `id` and `name`, and `Click Button` matches "`id`, `name`, and `value`"; there is no label strategy ([SeleniumLibrary, Default locator strategy](https://robotframework.org/SeleniumLibrary/SeleniumLibrary.html#Default%20locator%20strategy); source [`__init__.py`](https://github.com/robotframework/SeleniumLibrary/blob/master/src/SeleniumLibrary/__init__.py), [`element.py`](https://github.com/robotframework/SeleniumLibrary/blob/master/src/SeleniumLibrary/keywords/element.py)). The same `.robot` file does not run on both libraries.
- *Type safety*: none; arguments are strings, errors surface at run time. IDE support is plugin-dependent.
- *A second language*: tests live outside Java, so the application's view classes, routes, enums and test data are not reachable as code.
- *Extension*: easy (any Python or Java keyword library), but every extension is another vocabulary.

**On the Unified API?** Technically possible (a keyword library that calls Unified wrappers), and it would stay portable because every keyword would be implemented once on the Unified API. It doesn't fit the map's constraints well: the test author leaves Java, compile-time filters are lost, and the map lists BDD as "a candidate, not a given". Robot's lasting lesson is the **embedded-argument keyword** as the unit a non-developer reads, which the "steps in user terms" idea gives without leaving Java.

## 8. Kotlin/Android test DSLs: Kaspresso (Kakao) and Compose testing

**Kaspresso / Kakao** (Android Views; Kakao matchers are Espresso matchers, normally by resource id):

```kotlin
class AddressScreen : Screen<AddressScreen>() {
    val city  = KEditText { withId(R.id.city) }
    val save  = KButton { withText("Save") }
    val saved = KTextView { withText("Saved") }
}

@Test
fun savesCity() = run {
    step("Save the city") {
        AddressScreen {
            city { replaceText("Turku") }
            save { click() }
            saved { isVisible() }
        }
    }
}
```

**Compose testing** (semantics tree):

```kotlin
composeTestRule.onNode(hasText("City") and hasSetTextAction()).performTextInput("Turku")
composeTestRule.onNodeWithText("Save").performClick()
composeTestRule.onNodeWithText("Saved").assertIsDisplayed()
```

**What makes it readable.**

- *Kakao*: a `Screen` declares typed views (`KEditText`, `KButton`, `KCheckbox`, …), and Kotlin receiver lambdas (`city { replaceText(..) }`) make the test a nested outline of screen, element, action ([Kakao README](https://github.com/KakaoCup/Kakao#how-to-use-it)). Kaspresso's README shows the same test in raw Espresso next to Kakao to make the point ([Kaspresso README, Readability](https://github.com/KasperskyLab/Kaspresso#readability)).
- *Kaspresso* adds `step("…") { }` and `scenario(..)` "that allows you to read your test more easily" and to "see the relation between the test and the corresponding test case", plus "detailed and readable" logs and Allure reports ([Kaspresso README](https://github.com/KasperskyLab/Kaspresso#readability)).
- **One DSL, several engines**: "Since Kakao and Kautomator provide almost identical APIs, you don't have to care about what is under the hood of your tests, either Espresso or UI Automator", tests can run "on the JVM environment" with Robolectric, and Compose screens use "the same" DSL ([Kaspresso README](https://github.com/KasperskyLab/Kaspresso)). This is the closest modern analogue to the Unified API's three-Testing-Tool guarantee.
- *Compose*: finders work on the **semantics tree**, "primarily used for accessibility", so tests locate by what the UI exposes to users ([Compose semantics in tests](https://developer.android.com/develop/ui/compose/testing/semantics)). Tests are "synchronized by default … waiting until the UI tree is idle" ([synchronization](https://developer.android.com/develop/ui/compose/testing/synchronization)). A single-node finder will "throw an AssertionError if none or more than one element is found" ([`SemanticsNodeInteractionsProvider`](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/SemanticsNodeInteractionsProvider)).

**What it hides or costs.**

- *Kakao*: elements are declared up front in a Screen, usually by id, so the test body reads well but the *label* the user sees lives in another file. Custom views need a `KBaseView` subclass implementing action/assertion interfaces ([Kakao README, Custom KView](https://github.com/KakaoCup/Kakao#custom-kview)): typed and open to third parties, but one more class per widget.
- *Compose*: matchers are untyped with respect to the element (any node, any `perform…`); merged vs unmerged semantics trees surprise people, which is why `printToLog()` and `useUnmergedTree` exist ([Compose testing APIs](https://developer.android.com/develop/ui/compose/testing/apis)). Finding a text field "by label" is `hasText(..) and hasSetTextAction()`, an idiom rather than a named query.
- *Kotlin*: the receiver-lambda style needs Kotlin; in Java the same nesting becomes `city(f -> f.replaceText(..))`, which reads worse than a plain call.

**On the Unified API?** A Kotlin extension module could offer `page { textField("City") { setValue("Turku") } }` on top of Unified wrappers and remain portable, but Vaadin Flow tests are overwhelmingly Java, so it would serve a minority. What transfers to Java: Kaspresso's `step("…")` blocks (idea 4) and the proof that one typed DSL over several engines, including a JVM-only one, is viable.

---

## What the tools have in common

1. **Everyone converged on "locate by what the user perceives"** — Testing Library's priority list, Playwright's `getBy…`, Cypress's new `getByRole`, Selenide's `byLabel`/`byRole` (added in 7.x), Robot Browser's `label=`/`role=`, Serenity's `InputField.withLabel`, Compose's semantics tree. The Unified API is already on that side. Its readability gap is **sentence shape**, not locator semantics.
2. **Two layers, never one.** Every stack separates a *locator vocabulary* (how to name an element) from an *intention layer* (how to name a step: `test.step`, Screenplay tasks, Robot user keywords, Kaspresso `step`, Cypress custom commands). The Unified API has only the first.
3. **Strictness splits the field.** Strict: Testing Library `getBy`, Playwright, Robot Browser, Compose, Unified. First-match: Selenide `$`, Cypress `contains`, Capybara with `:first`/`:prefer_exact`. Capybara's default `:smart` sits in between. Every tool that is strict calls out first-match as a source of wrong-element bugs; the Unified API should stay strict whatever sugar it adds.
4. **Action-first verbs (Capybara, Robot) read best and type worst.** `fill_in 'City'` hides *what kind* of thing City is and lets one string mean label, name or id. Noun-first typed calls (Unified, Kakao) type best and read worst. A layer that reads action-first but resolves through typed wrappers, exact and strict, has not been built by any of these tools; it fits the Unified API because the wrapper types and their filter sets already exist.
5. **Readable failures matter as much as readable tests.** Testing Library prints the DOM, Compose prints the semantics tree, Serenity names targets and questions, Playwright shows steps in traces. The Unified API names the query in API terms and saves a screenshot or component tree file ([09-capabilities.md](../spec/09-capabilities.md#failure-evidence)), but does not list near misses.
6. **Multi-engine DSLs exist and work**: Capybara (rack_test / Selenium) and Kaspresso (Espresso / UI Automator / Robolectric) both put one readable DSL over an in-process engine and real-device or real-browser engines. Both keep the DSL to what every engine supports — the same rule as the Unified API's Escape Hatch.

## Comparison table

| Approach | Reads as | Locate by user-facing text | Strict | Element typed | Third-party components join by | Failure output | Fits on Unified API without breaking the three-tool guarantee |
|---|---|---|---|---|---|---|---|
| **Unified API today** | `find(Type).withLabel(..).single().act()` | yes (label, text, accessible name) | yes | yes, filters checked at compile time | wrapper interface in the add-on jar (ADR 0003) | query description + screenshot / tree file | — |
| Testing Library + user-event | `user.type(getByLabelText('City'), ..)` | yes, documented priority | `getBy` yes | no | plain query functions | DOM dump | vocabulary already there; role strings can't be portable (Escape Hatch only) |
| Playwright locators | `getByLabel('City').fill(..)` | yes | yes | no (one `Locator`) | string selector engines | trace, steps, `describe()` | **drop terminal**, steps, `describe`; recorder emitting Unified code |
| Playwright codegen | recorded code | yes, picks best locator | yes | no | — | — | as a Unified recorder on the Playwright adapter |
| Cypress | `cy.getByRole(..).type(..)` | opt-in (default advice: `data-*`) | no (`contains` = first) | no | untyped custom commands | command log | nothing new; warns against untyped team verbs |
| Capybara | `fill_in 'City', with: ..` | yes, but also name/id/placeholder | `:smart` | no | XPath/CSS selectors | element not found / ambiguous | **action-first verbs**, if label-only, exact, strict |
| Selenide | `$(byLabel('City')).setValue(..)` | yes (since 7.x) | no (first match) | no | page objects | condition + screenshot | specs-as-values clash (ADR 0002); not recommended |
| Screenplay (Serenity) | `actor.attemptsTo(Enter.theValue(..).into(..))` | yes (`InputField.withLabel`) | depends on WebDriver | no | `Performable`, `Question` | named steps in report | yes as a layer; take **named steps / tasks**, not the whole pattern |
| Robot Framework | `Fill Text  label=City  Turku` / `When the user fills City with Turku` | Browser lib yes; SeleniumLibrary no | Browser lib yes | no | keyword libraries | keyword log | possible as a keyword library; leaves Java, loses types |
| Kaspresso / Kakao | `city { replaceText(..) }` in `step("..")` | usually by id | Espresso: yes ([`AmbiguousViewMatcherException`](https://developer.android.com/training/testing/espresso/basics)) | yes (`KEditText`) | `KBaseView` subclass | step logs, Allure | Kotlin-only DSL; take **`step()`** |
| Compose testing | `onNodeWithText('Save').performClick()` | yes (semantics) | yes | no | semantics matchers | `printToLog()` tree | take **tree/candidate dump** on failure |

## Ideas worth prototyping here

Ranked by value for cost. Each keeps the three-Testing-Tool guarantee because it is built only from existing Unified queries, wrappers and Unified Elements; none needs adapter-specific code.

### 1. Action-first user verbs on every Scope (Capybara, made strict and typed)

```java
page.fillIn("City", "Turku");
page.clickOn("Save");
assertThat(page).showsNotification("Saved");
```

- `fillIn(label, value)` finds **exactly one** component, among wrapper types that have `withLabel` and take a typed value, whose label is exactly *label*; then calls its `setValue`. `clickOn(text)` does the same over clickable wrappers by text or accessible name. Ambiguous or missing → the same strict failure as `single()`, naming every candidate type it tried.
- Exact, label-only, no name/id fallback (ADR 0001's rejection of `withCaption` stands).
- Add-ons join by declaring a capability on their wrapper interface (for example, implementing a `FillableByText` or `Clickable` marker), so the verb set stays open (ADR 0003).
- Typed calls remain for anything that isn't a string value; the verbs are sugar, not a replacement.
- Cost: compile-time typing is lost *for these calls* (a typo in the label fails at run time, as today); searching several wrapper types per verb costs a round trip per type on browsers unless adapters batch it. Prototype on `CheckoutTest` and `EditPersonUnifiedTest` to see how much of a real test the verbs cover.

### 2. The query is the lazy wrapper (Playwright locators, no terminal)

```java
page.find(UnifiedTextField.class).withLabel("City").setValue("Turku");
var save = page.find(UnifiedButton.class).withText("Save");
save.click();
```

- `single()` is already lazy and fails on first use ([04-locating.md](../spec/04-locating.md)), so a query that *is* the wrapper (the query type extends the wrapper interface; both are interfaces under ADR 0003) changes nothing at run time and drops one token from every line. `all()` and `exists()` stay as terminals.
- Keeps compile-time filters (ADR 0001) and class-literal lookup (ADR 0002).
- Cost: query type and wrapper type merge, so the wrapper's methods and the filters share one completion list; a filter called after an action would need to be disallowed or documented. Needs an upgrade recipe in `unified-test-rewrite` (remove `.single()`), which is mechanical.

### 3. Steps and failures in user terms (Playwright steps, Serenity targets, Testing Library DOM dump)

- Every wrapper action and assertion logs a step generated from its query: `fill text field "City" with "Turku"`, `click button "Save"`, `notification has text "Saved"`. The text comes from the wrapper type and its filters, which `describe()` already composes, so it is identical on every Testing Tool.
- `single()` failures list **near misses** in the same terms: `No text field labelled "City". Text fields on the page: "Street", "Postal code", "Town"`, like Testing Library's DOM dump and Compose's `printToLog`, but filtered to the wanted wrapper type so the message stays short.
- An optional `describe("..")` (Playwright 1.53) names a query for the report.
- Directly addresses the map's open "Failure output and living documentation" area; cheap; benefits every style, including today's.

### 4. Named steps and reusable tasks in plain Java (Kaspresso `step`, Screenplay tasks without the framework)

```java
step("Save the city", () -> {
    page.fillIn("City", "Turku");
    page.clickOn("Save");
});
```

- A `step(name, body)` that nests the generated action steps (idea 3) under a human name, reported to the JUnit/Surefire output (and, later, to Allure if wanted).
- Reusable tasks are then ordinary Java methods annotated or wrapped with a step name, not a new object model. Multi-user scenarios already have their actors: `openAsUser` returns a Unified Page per user.
- This gives non-developers a readable outline without Gherkin; if BDD is chosen later, Cucumber step definitions would call the same tasks.
- Cost: one more concept; risk of tests that are all prose and no checks, which Cypress's own docs warn about for custom commands.

### 5. A recorder that emits Unified API code (Playwright codegen), lower priority

- Run on the Playwright adapter: record clicks and typing, map each target to the nearest Vaadin tag with a Core Set or add-on wrapper, and emit `find(Wrapper.class).withLabel/withText(..)` (or the verbs of idea 1), preferring filters in the spec's recommended order and making them unique the way Playwright's generator does.
- Output is portable by construction (it only emits Unified API calls), so recorded tests run on Browserless too.
- Cost: largest of the five (inspector UI, mapping DOM to wrappers, keeping up with Dramafinder); worth a spike only after ideas 1–3 settle what the emitted code should look like.

Not recommended for prototyping now: a Selenide-style static selector API (reopens ADR 0002's import-clash concern for little gain over idea 2), Robot Framework or a Kotlin-only DSL (both leave the Java test author behind), a full Screenplay framework (ideas 3 and 4 take its useful parts).
