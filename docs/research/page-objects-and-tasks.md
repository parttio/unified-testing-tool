Research for [#216](https://github.com/parttio/unified-testing-tool/issues/216), part of map [#193](https://github.com/parttio/unified-testing-tool/issues/193) "Make Unified UI Tests read like what the user does".

# Page objects and Tasks: opposite or complementary?

It feeds the last question of [#200](https://github.com/parttio/unified-testing-tool/issues/200): does the guide name a **Task** term, and does it steer shared helpers away from page objects, which TestBench recommends?
Written 2026-11-16 against `main` at `cf6da4d`.

**Question.** What does a page object encapsulate, and what does a Task encapsulate (UI structure or user intent)? How do the two combine in practice, and what should the Unified API guide and glossary say?

Labels used below: **[fact]** is what a source says, with a link. **[opinion]** is this note's judgement.

## Answer in short

- **Complementary, not opposite.** Every source that has both ideas puts them in **two layers**. A page object (or component object) knows **where things are** on one view. A task knows **what the user gets done**, and it may cross several views. Even Screenplay, sold as "beyond page objects", keeps a structure-only class named `LoginPage` that holds its Targets, and puts Tasks on top of it. Serenity/JS calls this layout a "Lean Page Object".
- **What the critics attack is the *fat* page object:** one class per page that holds locators, actions, multi-view workflows and sometimes assertions. They do not attack the idea of keeping structure in one place.
- **In the Unified API the bottom layer already exists.** Wrappers are the "page component objects" of Selenium and the "element classes" of TestBench. TestBench's own docs say its component element classes "can also be considered page objects". The lookups are also already in user terms (`withLabel("Name")`). So a view-level page object adds less here than it does over raw selectors.
- **Recommendation [opinion]:** name **Task** in the glossary and guide as the intent layer: a method over the Unified API whose body is one named `page.step(..)`. It stays in the test class, and moves to a `…Tasks` class of static methods once a second test class needs it. Present a page object as **optional** and **structure only**: a class of lookups for one view that return Wrappers, with no user actions and no assertions. Use it when a view's lookups are long, scoped or repeated. Migrated TestBench page objects stay valid, and their action methods are Tasks in all but name.

## Links to read

1. [Martin Fowler, *PageObject* (bliki, 2013)](https://martinfowler.com/bliki/PageObject.html). The origin of the term. A page object wraps a page *or a fragment* in an application-specific API, and should be built for "significant elements" rather than whole pages ("panel objects").
2. [Selenium docs, *Page object models*](https://www.selenium.dev/documentation/test_practices/encouraged/page_object_models/). The most complete statement of the rules: "services", no assertions, Page Component Objects, returning the next page object.
3. [Selenium docs, *Domain specific language*](https://www.selenium.dev/documentation/test_practices/encouraged/domain_specific_language/). Selenium's *separate* page for the intent layer: "create a new account", not "click on the third column".
4. [Vaadin TestBench, *Tests with Page Objects*](https://vaadin.com/docs/latest/flow/testing/end-to-end/page-objects). What TestBench actually recommends. In TestBench a page object *is* an element class, including the component elements.
5. [Serenity/JS handbook, *Organising page elements*](https://serenity-js.org/handbook/web-testing/page-objects-pattern/). The clearest primary source for "structure-only page object plus tasks as standalone functions", with a trade-off table.
6. [Serenity BDD, *Screenplay Fundamentals*](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals). Actors, Abilities, Tasks, Interactions, Questions, and how a Task groups interactions under a report name.
7. [Marcano, Palmer, Smart, Molak, *Page Objects Refactored: SOLID Steps to the Screenplay/Journey Pattern* (DZone, 2016)](https://dzone.com/articles/page-objects-refactored-solid-steps-to-the-screenp). The case against fat page objects (Single Responsibility). The companion InfoQ piece is [*Beyond Page Objects*](https://www.infoq.com/articles/Beyond-Page-Objects-Test-Automation-Serenity-Screenplay/).
8. [Playwright, *Page object models*](https://playwright.dev/docs/pom) together with [`test.step` → Decorator](https://playwright.dev/docs/api/class-test#test-step). The modern mainstream view, plus the exact combination proposed here: a page object method that is reported as a named step.
9. [Testing Library, *Guiding Principles*](https://testing-library.com/docs/guiding-principles). "The more your tests resemble the way your software is used…". This is why user-facing lookups weaken the locator-hiding argument for page objects.
10. [Cypress docs, *Custom Commands → Best Practices*](https://docs.cypress.io/api/cypress-api/custom-commands#Best-Practices). Write a plain function in the spec first, and share it only when every test needs it. This is the "Task stays in the test class until shared" rule. The opposing view is [Bahmutov, *Stop using Page Objects and Start using App Actions*](https://www.cypress.io/blog/stop-using-page-objects-and-start-using-app-actions).

**How the sources were read.** In this session the network proxy blocked martinfowler.com, selenium.dev, vaadin.com, testing-library.com, serenity-bdd.github.io, serenity-js.org, dzone.com, infoq.com and cypress.io. The docs were read from their GitHub sources instead, at these revisions:
[SeleniumHQ/seleniumhq.github.io `trunk`](https://github.com/SeleniumHQ/seleniumhq.github.io/blob/trunk/website_and_docs/content/documentation/test_practices/encouraged/page_object_models.en.md),
[vaadin/docs `f0394e7`](https://github.com/vaadin/docs/blob/main/articles/flow/testing/end-to-end/page-objects.adoc) (2026-11-13),
[microsoft/playwright `main`](https://github.com/microsoft/playwright/blob/main/docs/src/pom.md),
[serenity-bdd/serenity-bdd.github.io `3cc5f92`](https://github.com/serenity-bdd/serenity-bdd.github.io) (2026-11-03),
[serenity-js/serenity-js.org `a20b4f6`](https://github.com/serenity-js/serenity-js.org/blob/main/src/docs/handbook/web-testing/page-objects-pattern.mdx) (2026-10-11),
[testing-library/testing-library-docs `main`](https://github.com/testing-library/testing-library-docs/blob/main/docs/guiding-principles.mdx) and
[cypress-io/cypress-documentation `main`](https://github.com/cypress-io/cypress-documentation/blob/main/docs/api/cypress-api/custom-commands.mdx).
**Three sources were not read in full:** Fowler's bliki, the DZone/InfoQ articles and Bahmutov's post. They are not on GitHub. Claims about them below come from search-result excerpts, from how other primary docs cite them, and from memory of the texts, and they are marked **[unverified]**. Fetch them to confirm before quoting.

---

## 1. What each pattern encapsulates

### Page object: UI structure, offered as "services"

- **Fowler [unverified, from memory and search excerpt].** A page object wraps an HTML page or fragment in an application-specific API, so tests manipulate the page "without digging around in the HTML". Its rule of thumb is that it "should allow a software client to do anything and see anything that a human can", while it "hides the underlying widgetry". Despite the name it should be built for the *significant elements* of a page, not for each page. He calls these panel objects; Selenium cites that footnote ([Selenium POM, Page Component Objects](https://www.selenium.dev/documentation/test_practices/encouraged/page_object_models/#page-component-objects)). He favours no assertions inside page objects. What it encapsulates is **structure** (the widgetry), exposed through methods named after what a human can do on that page.
- **Selenium [fact].** "A page object is an object-oriented class that serves as an interface to a page of your AUT". The benefit is that "if the UI changes for the page, the tests themselves don't need to change". Its public methods are "the **services** that the page or component offers", and the page object "should be the only thing that has a deep knowledge of the structure of the HTML". "Page objects themselves should never make verifications or assertions". The only exception is a check, at construction, that the page loaded. Methods may "return another Page Object … This means we can effectively model the user's journey". Page Component Objects "represent discrete chunks of the page and can be included in page objects" ([Selenium POM](https://www.selenium.dev/documentation/test_practices/encouraged/page_object_models/), Overview, Assertions, Page Component Objects, Implementation Notes, Summary).
- **Selenium keeps intent on a separate page [fact].** Its *Domain specific language* page says tests should "speak for, and represent the user", using "ubiquitous language". "Rather than referring to 'load data into this table' or 'click on the third column' it should be possible to use language such as 'create a new account'". In Selenium a DSL is "usually represented by methods" ([Selenium DSL](https://www.selenium.dev/documentation/test_practices/encouraged/domain_specific_language/)). So Selenium itself has two layers.
- **Vaadin TestBench [fact].** A page object "hides the implementation details from the test methods": "which elements or components are used, how they may be found". It "offers high-level methods representing actions that a real user could perform on the page". The objects "shouldn't represent the whole page but rather a smaller part of it". Then: "a *page object* actually encapsulates a DOM element and is also sometimes called a *TestBench Element class*", and "All Vaadin component integrations for TestBench can also be considered *page objects* even though they only provide a high-level API for a single component". It recommends returning the next view's page object ("Chaining Page Objects") ([TestBench page objects](https://vaadin.com/docs/latest/flow/testing/end-to-end/page-objects)). *Creating tests* motivates it like this: "have only the high-level logic in the test itself -- your manager should be able to read and understand the test method -- and extract the `ElementQuery` parts to a separate Page Object class" ([TestBench creating tests](https://vaadin.com/docs/latest/flow/testing/end-to-end/creating-tests)).
- **Playwright [fact].** "A page object represents a part of your web application". Page objects "simplify authoring by creating a higher-level API which suits your application and simplify maintenance by capturing element selectors in one place". Its example `PlaywrightDevPage` holds `Locator` fields plus `goto()`, `getStarted()` and `pageObjectModel()`. Unlike Selenium's rule, `getStarted()` contains an `expect(..)` ([Playwright POM](https://playwright.dev/docs/pom)).

So a page object encapsulates **structure** (locators, which component is where, how one view relates to the next). The better-written ones expose that structure through **verbs a user would use** (Selenium's "services", TestBench's "actions that a real user could perform"). That second half is where it overlaps with a Task.

### Task: user intent, named for the report

- **Serenity BDD Screenplay [fact].** "We model *actors* who interact with an application … to perform *tasks* that help them achieve their goals. Actors have *abilities* … Actors can also answer *questions* about the state of the system". A Task groups "interactions into reusable sequences that represent higher level domain concepts". `Task.where("{0} logs in as …", …)` gives it a name that appears in the report ([Screenplay Fundamentals](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals), "Grouping interactions into tasks"). Interactions are the low-level actions (`Enter`, `Click`). Questions read state for assertions.
- **Serenity/JS [fact].** Tasks "associate business meaning with sequences of activities", and can be "parameterised and reused across different test scenarios" ([Screenplay Pattern](https://serenity-js.org/handbook/design/screenplay-pattern/), Tasks). The pattern's history goes from Marcano's 2007 "Roles, Goals, Tasks, Actions" model, through "A journey beyond the page object pattern" (2013), to the 2016 DZone/InfoQ articles (same page, history section).
- **Marcano et al. [unverified, from search excerpts].** Page objects tend to take on several responsibilities, which violates the Single Responsibility Principle. They grow unwieldy, and it becomes unclear where a change belongs. The Screenplay/Journey pattern applies SOLID by splitting them into small objects and centring the test on what the user does, not on how the page is built ([DZone](https://dzone.com/articles/page-objects-refactored-solid-steps-to-the-screenp), [InfoQ](https://www.infoq.com/articles/Beyond-Page-Objects-Test-Automation-Serenity-Screenplay/)).

A Task encapsulates **intent**: one goal the user reaches ("edit Alan Turing's name"), told in domain words, possibly across several views. It does not care where the components are; it delegates that to the layer below.

### Testing Library and Cypress: two more angles

- **Testing Library [fact].** "The more your tests resemble the way your software is used, the more confidence they can give you" ([Guiding Principles](https://testing-library.com/docs/guiding-principles), quoting Kent C. Dodds). It has no page object concept. Queries by role, label and text make the locator itself read like the user. **[opinion]** This weakens the classic page object argument ("capture selectors in one place") whenever lookups are already in user terms. That is the Unified API's case: `withLabel("Name")`, `withText("Save")`, `row().withCellText("Name", "Alan Turing")` ([ADR 0007](../adr/0007-wrappers-carry-their-filters.md)).
- **Cypress docs [fact].** "It's often more efficient to write a function for repeatable behavior that's specific to only a single spec file". "Can this be written as a function? The answer is usually yes". Don't wrap trivial calls (`cy.clickButton(selector)`): "Understandability and debuggability should be prioritized above all else" ([Custom Commands, Best Practices](https://docs.cypress.io/api/cypress-api/custom-commands#Best-Practices)).
- **Bahmutov [unverified, from the title and search excerpts].** On the Cypress blog (2019) he argues for *app actions* (calling the app's own model from the test) instead of page objects. Its applicability here is limited [opinion]: a Unified UI Test must run the same on Browserless, Playwright and Selenium, and should act as the user does. It does share the point that page object classes are often more structure than a test needs.

## 2. How they combine in practice

Each of the four combinations below appears in a primary source.

### a. Tasks built on page objects (both layers, page object fat)

- **Serenity BDD, "Page Objects with a step library" [fact].** `@Step("Enter shipping address") enterShippingAddress(..)` calls `checkoutPage.enterAddress(..)`. "The Step Library handles Serenity reporting while the Page Object handles page interactions" ([Serenity Playwright page objects](https://github.com/serenity-bdd/serenity-bdd.github.io/blob/main/docs/playwright/page-objects.md), "Integrating with Step Libraries"). Serenity's AI guide lists this style as "as valid as Screenplay" ([page-object idioms](https://github.com/serenity-bdd/serenity-bdd.github.io/blob/main/docs/ai/page-object-idioms.md)).
- **Vaadin TestBench BDD [fact].** The JBehave `CalculatorSteps` (`@When("I push $buttons")`) calls `KeypadElement.calculate(..)`. In other words, the intent layer sits on a TestBench page object ([TestBench BDD](https://vaadin.com/docs/latest/flow/testing/end-to-end/bdd)).
- **Playwright [fact].** A `@step` decorator on `LoginPage.login()` makes "each call to the decorated method … show up as a step in the report", boxed so that failures point at the call site ([`test.step`, Decorator and Boxing](https://playwright.dev/docs/api/class-test#test-step)). Here the page object method *is* the task.

### b. Page objects slimmed to structure, tasks hold the intent

- **Serenity/JS "Lean Page Objects" [fact].** "A Lean Page Object groups related element definitions in a static class. Unlike traditional Page Objects, Lean Page Objects contain no actions or assertions — they describe structure only … with tasks expressed as standalone functions that reference page elements statically". The listed trade-off: "Organised, but behaviour lives outside the page object". "You can mix approaches in the same project. Start with helper functions, refactor to Lean Page Objects when you notice duplication…" ([Organising page elements](https://serenity-js.org/handbook/web-testing/page-objects-pattern/)).
- **Serenity BDD Screenplay [fact].** Its best practice is "Use Target Constants", in a class named `LoginPage` that holds only `Target`s. Tasks reference them ([Screenplay Playwright, Best Practices](https://github.com/serenity-bdd/serenity-bdd.github.io/blob/main/docs/screenplay/screenplay-playwright.md)). The WebDriver page also suggests storing a Target "in a separate Page Component class" ([Screenplay WebDriver](https://serenity-bdd.github.io/docs/screenplay/screenplay_webdriver)).

So the pattern that critiques page objects ends up with a structure-only page class plus tasks. **This is the strongest evidence for "complementary"**, provided the page object stays thin.

### c. Page objects slimmed to component objects

- **Selenium's Page Component Objects, Fowler's panel objects, TestBench's element classes, and Serenity/JS "interaction objects"** ("what can a user observe (Questions) and what can they do (Tasks)?" on one component such as `TodoItem`) [fact, sources above].
- **In the Unified API this layer is the Wrapper [fact]:** one object per component type, with filters and user actions (`GLOSSARY.md`, [ADR 0003](../adr/0003-wrappers-are-interfaces-shipped-in-the-addon-jar.md), [ADR 0007](../adr/0007-wrappers-carry-their-filters.md), [custom wrappers guide](../guide/custom-wrappers.md)). A project-specific composite (a `MetricCard`) gets its own Wrapper, which is what Selenium calls a page component object.

### d. Screenplay without page objects

Screenplay can also define Targets inline or use UI factories such as `InputField.withLabel(..)` and `Button.withText(..)`, with no page class at all ([prior research, Screenplay](readable-web-ui-tests.md)). In that case the only layers are interactions (≈ Wrapper calls) and tasks. Serenity BDD's own guide also says "don't mix Page Objects and Screenplay in one test" ([Serenity AI overview](https://github.com/serenity-bdd/serenity-bdd.github.io/blob/main/docs/ai/overview.md)) [fact]. That rule is about not mixing two *frameworks* (`@Steps` libraries and `Actor.attemptsTo`). It is not about keeping structure apart from intent, which its own Screenplay docs do [opinion].

## 3. Trade-offs

| | Fat page object (structure + actions + flows) | Structure-only page object ("lean") | Task method (intent) |
|---|---|---|---|
| **Encapsulates** | where things are and what you can do on one view | where things are on one view | one user goal, possibly across views |
| **God-object risk** | high: grows with every test's need; Marcano et al.'s SRP critique [unverified] | low: only nouns | low per task; a `…Tasks` class can grow, but it splits by goal, not by view |
| **Duplication** | removes locator and action duplication | removes lookup duplication | removes workflow duplication; lookups may still repeat if there is no page object |
| **Cross-view work** ("log in, then edit person") | awkward: belongs to no page, or is chained through returned page objects (Selenium, TestBench) | n/a | natural: a task calls whatever views it needs |
| **Discoverability** | good: `profile.` autocomplete lists what you can do | good for lookups | weaker for static methods (`PeopleTasks.` or a static import), unless one class per feature area |
| **Readability of the test** | good if methods are named in user terms (TestBench, Serenity: "method names should describe the user intent"); poor when they leak structure (`saveButton().click()`) | a test still reads as lookups and actions, unless tasks sit on top | best: the test is a list of goals, and each one is a named step in the report |
| **Reporting** | none by default; needs `@Step` (Serenity) or a decorator (Playwright) | none | the `page.step(..)` name is the User Step heading |
| **Assertions** | sources disagree: Selenium says never (except "page loaded"), Playwright's example asserts | none | a task may wait for its own outcome; the test asserts |
| **Reuse across tests** | yes, but the object usually needs construction and state | yes | yes; static methods taking the `UnifiedPage` reuse naturally across users (`editPerson(bob, ..)` with `bob = page.openAsUser(..)`) |
| **Cost when the UI changes** | one place | one place | one place per task; with user-facing lookups many changes (moved fields, new layout) need no edit at all |

**[opinion] Extra points for the Unified API:**

- **The locator argument is weaker here.** Page objects exist mainly to hide selectors. Unified lookups are by label, text and cell text, which read like the user (Testing Library's principle). What stays fragile is the *visible text* itself. When the label "Name" becomes "Full name", every test that says `withLabel("Name")` changes, with or without tasks. A Task or a structure-only page object that owns the lookup brings that back to one place, and this is the real remaining case for a view-level page object.
- **The demo's migrated page objects show the drift.** [`ProfileMigratedPage`](../../unified-test-demo/src/test/java/demo/ProfileMigratedPage.java) mixes an intent method (`fill(name, email)`) with a structure method (`saveButton()`), so the test still says `profile.saveButton().click()`. [`ProfileBrowserlessMigratedPage`](../../unified-test-demo/src/test/java/demo/ProfileBrowserlessMigratedPage.java) has `open()`, `save(name, email)` and `notification()`: two tasks and one lookup on one class. This is typical of real suites, and fine. The guide only needs to say which half is which.
- **Chaining is not needed.** TestBench and Selenium return the next page object because each one holds its root element or driver. A Unified Task takes the `UnifiedPage` and every lookup is lazy and re-found ([ADR 0007](../adr/0007-wrappers-carry-their-filters.md)), so there is nothing to hand on from view to view.
- **Agents are the main authors ([#197](https://github.com/parttio/unified-testing-tool/issues/197)).** One obvious place for intent (a Task) is easier to generate and to review than a choice between "page object method or helper method". A mandatory page object layer would add a class per view, which is more API surface in the user's code for little gain ("More API is not better", map #193).

## 4. Recommendation for the guide and the glossary [opinion]

1. **Terms: complementary, at different layers.**
   - **Wrapper**: one component (exists).
   - **Task**: one user goal, named, reported (new).
   - **Page object**: optional, the lookups of one view (described in the guide, not added to the glossary).

   Say so in the guide in one sentence: "A page object says *where* things are on a view; a Task says *what the user gets done*. Wrappers already do the component part, so most suites need only Tasks."
2. **Task, the default for shared intent.** A method over the Unified API, named with a verb phrase in domain words. Its body is wrapped in `page.step("Edit Alan Turing", () -> …)` so that it is one User Step heading in the report. It stays a private method in the test class, and moves to a `…Tasks` class of static methods (`PeopleTasks.editPerson(page, "Alan Turing", …)`) when a second test class needs it. This follows the Cypress rule "first ask: can this be written as a function?" Tasks may wait for their own outcome. The test makes the assertions it is about.
3. **Page object, optional, structure only.** Add one when a view's lookups are long (scoped inside a dialog or layout, a `selector`), repeated across Tasks, or tied to visible text that changes often. It is a class over a `UnifiedPage` whose methods are nouns that return Wrappers (`name()`, `email()`, `save()` returning `UnifiedButton`, `people()` returning `UnifiedGrid`). It has no user actions and no assertions. This is Serenity/JS's Lean Page Object and Screenplay's Targets class. Tasks then use it. It is also the same shape as the map's open item **generated view models** (`ProfileViewModel.name()`), so if those are generated later, they *are* this page object, and the guide needs no second term.
4. **TestBench page objects and migration.** Do not tell Vaadin users that page objects are wrong. TestBench recommends them, and Fowler, Selenium and Playwright document them. The guide should say: a migrated page object keeps working. Its lookup methods are the structure layer. Its action methods (`fill`, `save`, `login`) are Tasks, and wrapping their bodies in `page.step(..)` puts them in the User Step report. Consider having the agent-skill step of [ADR 0004](../adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md) do that wrap. The Migration Recipes should not.
5. **Glossary.**
   - Add **Task**:
     > **Task**:
     > A named piece of user work in a Unified UI Test ("edit Alan Turing's name"), written as a method over the Unified API whose body runs as one `page.step(..)`, so the User Step report shows it as a heading over the User Steps it performs.
     > _Avoid_: Step method, keyword, flow, action, page object
   - **Keep "page object" in Wrapper's _Avoid_.** In TestBench's own vocabulary a component element class *is* a page object, so a Vaadin reader may call a Wrapper that. The glossary should steer them to "Wrapper" for one component. Don't add **Page Object** as a glossary term: it is optional and a plain-Java convention, not a Unified API concept. Define it in the guide with the one-line "structure only, returns Wrappers" rule. If the map later adopts generated view models, add a term then (for example **View Model**), with "page object" under its _Avoid_.
6. **Example for the guide** (ADR 0007 style; `page.step(..)` is the API proposed in #200 and does not exist yet):

```java
// In the test class until a second test class needs it; then PeopleTasks.editPerson(..)
static void editPerson(UnifiedPage page, String person, String newName) {
    page.step("Edit " + person, () -> {
        page.find(UnifiedGrid.class).row().withCellText("Name", person).click();
        page.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
        page.find(UnifiedButton.class).withText("Save").click();
    });
}

@Test
void renamesAPerson(UnifiedPage page) {
    page.navigate(PeopleView.class);
    editPerson(page, "Alan Turing", "Alan M. Turing");
    assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
}
```

The optional page object for the same view would hold only `grid()`, `name()` and `save()` returning Wrappers, and `editPerson` would call them instead of the three lookups.

## Open points

- Confirm the [unverified] Fowler, DZone/InfoQ and Bahmutov claims against the full texts (blocked in this session).
- Whether `page.step(..)` should be boxed, like Playwright's `box: true`, so that a failure inside a Task reports the Task's call site first. That belongs to #200.
- Whether the opt-in style rules (map #193, "Style rules") should flag page object methods that perform actions without a `step(..)`. Probably not, since that is too opinionated for migrated suites.
