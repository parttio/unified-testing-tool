---
name: write-unified-ui-tests
description: Write, change, fix or review Unified UI Tests (Vaadin tests that take a UnifiedPage and use Unified* Wrappers), including reading a UnifiedAssertionError when one fails.
---
<!-- unified-test-skill: write-unified-ui-tests ${project.version} -->

# Write Unified UI Tests

A Unified UI Test is a JUnit test written only against the Unified API, so it runs on every Testing Tool:
Browserless (the default), Playwright and Selenium. It reads as the scenario a user goes through, and every run
reports that scenario as User Steps (`fill text field “Name” with “Alan M. Turing”`). Write it so that a reviewer
reading the diff, and anyone reading a failure, sees what the user does. The rules are
[spec chapter 12](https://parttio.github.io/unified-testing-tool/spec/12-readable-tests/); the
[developer guide](https://parttio.github.io/unified-testing-tool/guide/) shows each component.

## Steps

1. **Read the view.** Open the view class the scenario is about, and the components it builds. Note for each
   component the test touches what the user sees: its label, its text, its header title, or its test id. When the
   view calls `getTranslation("key")`, look the key up in `src/main/resources/vaadin-i18n/translations*.properties`.
   Done when every lookup you are about to write names something you saw in the view's code. To check what is
   there at run time, print `page.describe()` from a test after navigating: each line is a component with the
   lookup that finds it, the same on every Testing Tool. Delete the print before you report.
2. **Write the test** in the shape below. Read the Wrapper's methods before calling one: every Wrapper's filters,
   actions, reads and checks are listed in `META-INF/unified/api-reference.md` inside the `unified-test-api` jar
   (the same page is [on the site](https://parttio.github.io/unified-testing-tool/api-reference.md)); the sources
   and Javadoc have the details. Done when it compiles: `mvn test-compile`.
3. **Prove it** (see below). Done when it passes on every Testing Tool it allows, the style rules pass, and
   `user-steps.txt` tells the scenario you were asked for.
4. **Report** the test's `@DisplayName`, its runs, and its `user-steps.txt`.

## Shape of a test

```java
@UnifiedSpringBootTest                          // @UnifiedUITest without Spring Boot
class EditPersonTest {

    @Test
    @DisplayName("Renaming a person in the editor updates the grid")
    void renamesAPerson(UnifiedPage page) {
        // given
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);

        // when
        renamePerson(page, "Alan Turing", "Alan M. Turing");

        // then
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(people.row().withCellText("Name", "Alan M. Turing")).hasCellText("Email", "alan@example.com");
    }

    private static void renamePerson(UnifiedPage page, String name, String newName) {
        page.step("Rename " + name, () -> {
            page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
            page.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
            page.find(UnifiedButton.class).withText("Save").click();
        });
    }
}
```

- **`@DisplayName`** on every test: the scenario in one sentence, in the user's words. It heads the reports.
- **`// given`, `// when`, `// then`** comments mark the scenario's shape. Leave out the ones with nothing under them.
- **User Task**: a method whose whole body is one `page.step("<user work>", () -> …)`, named after what the user
  does ("Rename Alan Turing", "Fill the billing address"). Extract one when a piece of user work takes several
  User Steps or repeats. Keep it a `private static` method of the test class until a second test class needs it;
  then move it to a `<View>Tasks` class of `static` methods that take the `UnifiedPage`
  (`PeopleTasks.renamePerson(page, ..)`), and call that from both.
- **Wrappers are lazy**: declare one before a step (`UnifiedGrid people = page.find(UnifiedGrid.class);`) and use
  it inside the step and after it.
- **Secrets**: type a password or token through `page.secret(..)`:
  `.setValue(page.secret(apiKey))`. The reports then show `••••`.
- **Page objects** are optional and hold structure only: methods returning Wrappers
  (`UnifiedTextField name() { return page.find(UnifiedTextField.class).withLabel("Name"); }`). User work goes in
  User Tasks, checks in the test.
- **One Spring context**: with Spring Boot, the test classes extend one shared base class that holds
  `@UnifiedSpringBootTest`, the profile (`@ActiveProfiles`) and any `@ServiceConnection` container field. A class
  with its own `@MockitoBean`, `@Import`, properties or container field gets a Spring context of its own, and each
  context starts the application's server, on Browserless too. Add one only when the test needs that setup. Test data
  is committed, not rolled back (the browser Testing Tools refuse `@Transactional`): each test cleans up every table
  it writes to.
- **Without Spring Boot**: mark the class `@UnifiedUITest`. Browserless builds the views in the test JVM; a browser
  Testing Tool opens the application at `vaadin.testing.base-url`, on a server the build starts or a deployed copy
  (an EAR on an application server, a container), or a test class's `@BaseUrl`. A suite whose application can't run
  in the test JVM is browser-only: each class declares `@RequiresBrowser` or `@RequiresTestingTool`, so Browserless
  skips it. The application's data outlives the test there too: each test leaves behind nothing the next one sees.

## Canonical forms

| Intent | Write |
|---|---|
| a component | `page.find(UnifiedTextField.class).withLabel("Name")` — the Wrapper itself, every use finds exactly one |
| by what the user sees | `withLabel(..)` for fields, `withText(..)` for buttons and text, `withHeaderTitle(..)` for dialogs, `withAccessibleName(..)` for icon buttons, `withPlaceholder(..)` for fields without a label |
| by what the user can't see | `withTestId(..)`, `withId(..)`: only when no visible name tells it apart |
| inside a container or dialog | `dialog.find(UnifiedTextField.class).withLabel("Email")`, `page.find(UnifiedContainer.class).withTestId("billing").find(..)` |
| a component without a Wrapper | `page.findComponent(MyComponent.class)` |
| inside a `LitTemplate` (its `@Id` fields) | `page.findTemplate(SearchBar.class).find(UnifiedButton.class).withId("new")`: no lookup enters a template by itself; enter one at a time, find by the `@Id` value, as text the template's HTML writes is empty on Browserless |
| a key press | `page.pressKey("Enter")`, sent to the focused component |
| a grid row | `grid.row().withCellText("Name", "Alan Turing")`; `grid.row(0)` by index; cell components: `row.cell("Actions").find(UnifiedButton.class)` |
| a check | `assertThat(wrapper).hasText(..)`, `.hasValue(..)`, `.isOpen()`, `.isDisabled()`, `assertThat(row).hasCellText(..)`, `assertThat(page).isAt(View.class)` |
| a chat's messages | any message: `assertThat(chat).hasMessageContaining("Got it")`; one message: `assertThat(chat.message(2)).hasTextContaining(..)` (index from 0), `assertThat(chat.message().withUserName("Assistant").last()).hasTextContaining(..)`, `assertThat(chat.message().withTextContaining("secret")).doesNotExist()` |
| a menu's items | `assertThat(menu).hasItems("File", "Help")`, `.hasItems(List.of("File", "Export"), "PDF", "CSV")`, `.hasItem("File", "Print")`, `.hasItemEnabled(..)`, `.hasItemDisabled(..)` |
| present, absent, how many | `assertThat(wrapper).exists()`, `.doesNotExist()`, `.hasCount(n)`, also on grid rows |
| gone from the user's view | `assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist()`: lookups and `getText()` include hidden components, on every Testing Tool |
| a checkbox | `check()`, `uncheck()`, `setChecked(..)`: `element().click()` toggles it on Browserless only |
| a route the user may not open | `page.navigate(AdminView.class)` doesn't throw; check where it lands: `assertThat(page).isAt(LoginView.class)` |
| a condition no assertion states | `page.waitUntil(() -> ..)`: a last resort, it shows as a gap in the reports |
| every match, to act on each | `for (UnifiedButton b : page.find(UnifiedButton.class).withText("Done").all()) { .. }` |

Wrappers are the `Unified*` interfaces in `com.vaadin.testing.unified.component`; `assertThat` is
`import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;`. Its assertions
retry until the timeout, so a check waits for the server's answer by itself. Check what the user sees with these
assertions; a plain JUnit `assertEquals` on a Wrapper's getter reads once, without waiting, and shows in the reports
as a gap. `all()` reads now, without waiting: use it only to loop, and check presence or absence with `exists()`,
`doesNotExist()`, `hasCount(n)`, `isClosed()` or `isHidden()`. When a lookup matches several components, add the
filter that names the one meant (often a container) rather than picking one by position.

- `isVisible()` waits for exactly one match, and fails on none or several: check absence with `doesNotExist()`, or
  `isHidden()` for a component still on the page.
- Text filters match the DOM text, as the view's code writes it, not as CSS `text-transform` shows it.
- A row lookup scrolls the grid, so an Escape Hatch read by row position after it may hit another row: read the row
  through its Wrapper.

A suite migrated by hand keeps its old checks, which pass without User Steps. Convert each:

| Raw check | Unified assertion |
|---|---|
| `assertTrue(locator.isVisible())` | `assertThat(wrapper).isVisible()`; a text that can show twice: `assertThat(page.find(UnifiedSpan.class).withText("Saved").visible()).exists()` |
| `assertFalse(locator.isVisible())` | `assertThat(wrapper.visible()).doesNotExist()` |
| `assertTrue(page.textContent("body").contains("Saved"))` | a check on the component that shows it: `assertThat(notification).hasTextContaining("Saved")` |
| `assertEquals("Alice", locator.inputValue())` | `assertThat(field).hasValue("Alice")` |
| `assertEquals(3, rows.count())` | `assertThat(grid).hasRowCount(3)`, or `assertThat(wrapper).hasCount(3)` |
| a pause (`waitForTimeout`, `Thread.sleep`) before a check | nothing: the check waits. The pause makes it pass by luck, hiding a race the waiting check exposes |

## Prove it

Run the test class on every Testing Tool it allows: Browserless and Playwright, or only the ones its
`@RequiresBrowser` / `@RequiresTestingTool` names (add `-pl <module>` in a multi-module build):

```
mvn test -Dtest=EditPersonTest
mvn test -Dtest=EditPersonTest -Dvaadin.testing.tool=playwright
```

Run it with the plugin that runs the suite's tests. Under Failsafe (a server the build starts, or a deployed copy),
that is `mvn verify -Dit.test=EditPersonIT -Dvaadin.testing.tool=playwright`, with
`-Dvaadin.testing.base-url=<the application's URL>` unless the build sets it. The first run on Playwright downloads
Chromium, about 280 MB.

Then run the style rules: `mvn test -Dtest=UnifiedStyleTest`. When the project has no such test, add one: the test
dependency `org.vaadin.addons:unified-test-archunit` (its version comes from `unified-test-bom`) and

```java
class UnifiedStyleTest {
    @Test
    @DisplayName("Every Unified UI Test follows the style rules")
    void followsTheUnifiedStyle() {
        UnifiedStyleRules.ALL.check(new ClassFileImporter().importPackages("com.example"));
    }
}
```

Last, read `<evidence-dir>/<TestClass>/<testMethod>/user-steps.txt`. `<evidence-dir>` is the
`vaadin.testing.evidence-dir` setting: `target/surefire-reports/unified-evidence` under Surefire and Failsafe alike,
unless the plugin's configuration sets another (a Failsafe build often sets
`target/failsafe-reports/unified-evidence`). Each line is one User Step. Read it as the person who asked for the scenario would: every action they described appears, in their
order, on the components they named, and every outcome they expect is a `see` line. A step you meant but don't see
is a test that does less than asked: fix the test.

## Read a failure

Every Unified failure is a `UnifiedAssertionError`, with these lines (lines with nothing to say are left out):

```
UnifiedAssertionError: Renaming a person in the editor updates the grid
  Failed at     User Step 3 of 5: fill text field “E-mail” with “alan@bletchley.uk”
  In step       Rename Alan Turing
  Expected      exactly one text field labelled “E-mail”
  Found         none
  Nearby        text field “Email”, text field “Name”
  Did you mean  find(UnifiedTextField.class).withLabel("Email")
  Waited        10 s (vaadin.testing.timeout)
  Tool          playwright (chromium)
  Code          EditPersonTest.java:14 (renamesAPerson) ← EditPersonTest.java:27 (renamePerson)
  Evidence      target/surefire-reports/unified-evidence/EditPersonTest/renamesAPerson/  (failure.png, failure.view.txt)
```

`Nearby` lists the Near Misses, closest first; `Found` is `none`, `2: …` (several matches: add a filter),
`1, but disabled` (the user can't use it yet: do the step that enables it), or the values an assertion saw while
waiting. `Evidence` is the test's directory and the files in it: `failure.png`, a screenshot (browser), or
`failure.tree.txt`, the component tree (Browserless), and on every Testing Tool `failure.view.txt`: each component
the page held at the failure, with the lookup that finds it.

By default that is all a run keeps beyond `user-steps.txt`. Two opt-in settings keep more, for passing tests too
(see Configuration):
- `-Dvaadin.testing.user-steps.snapshots=true`: after every User Step, a screenshot (Playwright, Selenium) or the
  component tree (Browserless) in the test's evidence directory, under `steps/`, linked from each step in the HTML
  report.
- `-Dvaadin.testing.playwright.trace=on-failure` (or `on`): Playwright's trace, grouped by User Step, as
  `trace.zip` in the test's evidence directory. Open it at https://trace.playwright.dev.

Decide who is wrong before changing anything:
- **The test is wrong** when the view's code shows what the Near Miss shows (the label is "Email", the test guessed
  "E-mail"): apply `Did you mean`, or write the lookup from the view.
- **The application is wrong** when the scenario you were given says the user should see something else (the
  notification, a saved value, a row): stop and report the failure's `Expected` and `Found` to the person who asked,
  with the view code involved. Change the expectation only when they confirm the new behaviour is intended.

## Configuration

Every setting is a JUnit configuration parameter (`src/test/resources/junit-platform.properties`, or Surefire's
`configurationParameters`) or a system property (`-D`). Unset means the default.

| Setting | Default |
|---|---|
| `vaadin.testing.tool` | `browserless` (or `playwright`, `selenium`) |
| `vaadin.testing.base-url` | `http://localhost:8080`; with Spring Boot, the Spring port; a test class's `@BaseUrl` wins over both. Without Spring Boot, set it to the deployed application |
| `vaadin.testing.timeout` | `10s`: how long a lookup or assertion waits (`500ms`, `1m`) |
| `vaadin.testing.viewport` | `1280x720`: in a browser, the exact Viewport of every test without `@Viewport` (`phone:375x667`) |
| `vaadin.testing.layout-checks` | `off`: `on` measures the layout after each navigation, reload and `setViewport` and puts Layout Findings on that step |
| `vaadin.testing.layout-findings` | `report`: `fail` fails the step that measured a Layout Finding |
| `vaadin.testing.layout-checks.screenshots` | `off`: `on` keeps a Viewport screenshot per measurement |
| `vaadin.testing.evidence-dir` | `target/surefire-reports/unified-evidence`, also under Failsafe; read the build's value before looking for evidence |
| `vaadin.testing.user-steps.snapshots` | `false`; `true`: a screenshot or component tree after every User Step, in `steps/` |
| `vaadin.testing.user-steps.log` | `failed`: print the User Steps of failed tests to the CI log; `all` or `none` |
| `vaadin.testing.user-steps.server-log` | `warn`: the application's warnings and errors under the User Step that logged them; `error` or `off` |
| `vaadin.testing.user-steps.ignored-errors` | unset; one Java regex: `hasNoErrors()` doesn't count an error line it finds (a known console or server line); the line still shows |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` to the GitHub job summary when `GITHUB_STEP_SUMMARY` is set |
| `vaadin.testing.user-steps.changed-since` | in a GitHub pull request, `origin/$GITHUB_BASE_REF`; else unset. The git ref "Changed in this PR" compares with |
| `vaadin.testing.user-steps.baseline-dir` | unset; the base branch's evidence directory: changed scenarios show a diff of their User Steps against it |
| `vaadin.testing.user-steps.compare-dir` | unset; the evidence directory of a run on another Testing Tool: the run summary says which scenarios differ |
| `vaadin.testing.browserless.view-packages` | with Spring, the `@SpringBootApplication`'s packages and the test's; else the test's package |
| `vaadin.testing.browserless.route-errors-to-session` | `false`; `true` sends a listener's exception to the session's `ErrorHandler`, as in a browser |
| `vaadin.testing.playwright.browser` | `chromium` (or `firefox`, `webkit`) |
| `vaadin.testing.playwright.headless` | `true` |
| `vaadin.testing.playwright.executable` | Playwright's own browser; a path to a browser skips its download |
| `vaadin.testing.playwright.trace` | `off`; `on` or `on-failure`: keep Playwright's trace as `trace.zip` |
| `vaadin.testing.selenium.browser` | `chrome` (or `firefox`) |
| `vaadin.testing.selenium.headless` | `true` |
| `vaadin.testing.selenium.executable` | the installed browser |
| `vaadin.testing.selenium.reuse-browser` | `true`: one browser per run, a fresh tab without cookies or storage per test |

## Review a test

When asked to review a Unified UI Test that you didn't write, or the tests of a pull request, report what you find;
change nothing unless asked. A test you wrote yourself gets the same review before you report it.

1. **The scenario.** Read what the test was written for: the request, the ticket, or else its `@DisplayName`. Then
   read its `user-steps.txt`; when there is none, run the test first, on Browserless when it allows it. Compare them as in "Prove it":
   every action, in order, on the component named, and every outcome a `see` line after the action that causes it.
   Flag a `@DisplayName` that says something the steps don't do, and a test that ends on an action with no check.
2. **The code**, against the canonical forms. Flag:
   - a lookup by test id, id, class name or CSS where the user reads a label, a text or a name;
   - `all().get(..)`, or any pick by position;
   - a getter with `assertEquals`, `assertTrue` or AssertJ where `assertThat(wrapper)` has the check;
   - `Thread.sleep(..)`, a Testing Tool's own wait outside code that declares that Testing Tool, or a
     `withTimeout(..)` or run timeout raised without a comment that says what is slow;
   - an Escape Hatch without `@RequiresTestingTool` and a comment saying why, including one in a `…Tasks` class or
     a page object, which the style rules don't follow;
   - the same user work copied into several tests instead of one User Task.
3. **The runs.** Run it on every Testing Tool it allows, and the style rules, as in "Prove it". Run it a second
   time before trusting a test that passed once.
4. **A test changed to make it pass.** Read what it checked before: `git diff`, or the run summary's User Steps
   diff against the base run. Flag a removed `see` line, a weaker check (`hasText` to `hasTextContaining`, a value
   check dropped), an added sleep or longer timeout, and a change to the view or service that makes the application
   fit the test. A changed lookup is right only when the view changed on purpose in the same change.

Report each finding with its file and line, what is wrong, and the canonical form to use instead; then the
scenario's actions and outcomes that have no User Step. The same checklist, for people, is in the
[guide](https://parttio.github.io/unified-testing-tool/guide/reviewing-tests/).

## The Escape Hatch

`unwrap(..)` leaves the Unified API for one Testing Tool's own object (Playwright `Page`, Browserless
`BrowserlessUIContext`, a Flow component). Use it only for what the Unified API cannot say (network mocking,
arbitrary JavaScript, server-side state). The test then declares the Testing Tool, and a one-line comment says why:

```java
@Test
@DisplayName("Offline users see the retry banner")
@RequiresTestingTool(TestingTool.PLAYWRIGHT)
void showsTheRetryBanner(UnifiedPage page) {
    // Escape Hatch: the Unified API can't take the browser offline
    page.unwrap(Page.class).context().setOffline(true);
    ...
}
```

Escape Hatch code gets none of the Unified API's waiting, so it waits with its Testing Tool's own API: TestBench's
`waitForFirst()` and `waitUntil(..)`; on Playwright, Dramafinder's `AutoWait.waitForVaadinIdle(Page)` for the server
and `Locator.waitFor()` for an element. The style rules accept that wait where the test, its class, or every test
reaching the helper declares that Testing Tool; `Thread.sleep(..)` and another Testing Tool's waits still fail.

An Escape Hatch action that reaches the server (a Playwright `Locator.click()`) returns before the server answers.
The next Unified action waits for that answer before acting, on every Testing Tool (on Playwright for at most 5 s,
then it acts anyway). Escape Hatch code that comes next gets no such wait, nor does an action after a longer answer:
make it wait, in this order of preference:

1. **Check the state the server produces**, on every Testing Tool: `assertThat(name).hasValue("Alan Turing");`
   before reading the page through the Escape Hatch. The assertion retries until the answer is there.
2. **Wait for the server in the Escape Hatch** when the answer has nothing to check, in a test with
   `@RequiresTestingTool(TestingTool.PLAYWRIGHT)`:
   `AutoWait.waitForVaadinIdle(page.unwrap(Page.class));` (`org.vaadin.addons.dramafinder.AutoWait`).

For an extra check that only some Testing Tools can make, use `tryUnwrap(..)`; the test keeps running everywhere:
`name.tryUnwrap(TextField.class).ifPresent(field -> assertEquals("Name", field.getLabel()));`.
