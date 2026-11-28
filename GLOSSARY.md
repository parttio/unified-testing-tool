# Unified Testing

One API for writing Vaadin Flow tests that can run on any of Vaadin's existing testing tools.

## Language

**Unified API**:
The single Java API that Vaadin Flow tests are written against, independent of which Testing Tool runs them.
_Avoid_: Testing tool, framework, facade

**Testing Tool**:
One of the existing tools a test written against the Unified API can run on: Dramafinder (Playwright), Browserless, or TestBench Selenium.
_Avoid_: Engine, backend, driver, runner

**Test Runner**:
The framework that discovers and runs Unified UI Tests and reports their results, such as JUnit; separate from the Testing Tool that drives the application.
_Avoid_: Test framework, engine, Testing Tool

**Escape Hatch**:
A deliberate way out of the Unified API to call a specific Testing Tool directly, for behaviour the tools don't share.
_Avoid_: Workaround, native access

**Core Set**:
The Vaadin components the Unified API spec designs in full, one per interaction pattern; every other component follows a documented pattern derived from them.
_Avoid_: Supported components, MVP components

**Auto-Wait**:
The Unified API's guarantee that an action waits, before acting, for its component to be usable and for a server round-trip already under way to finish, and after acting, for the round-trip it started, on every Testing Tool.
_Avoid_: Implicit wait, waitForVaadin

**Unified UI Test**:
A test written only against the Unified API, so it runs on whichever Testing Tool the test run selects.
_Avoid_: Vaadin test, browser test, integration test

**Direct Test**:
A UI test written against a Testing Tool's own API (Browserless Test, Dramafinder, TestBench) rather than the Unified API; the counterpart a Unified UI Test is compared with.
_Avoid_: Plain test, native test, raw test

**Performance Pair**:
A Unified UI Test and a Direct Test of the same scenario on one Testing Tool, held to the same user actions and checks, so their time and round trips can be compared.
_Avoid_: Benchmark Pair, twin, golden pair

**Capability**:
Something a Unified UI Test can require from the Testing Tool running it, such as a real browser; a test whose Capability is missing is skipped.
_Avoid_: Feature flag, tag

**Wrapper**:
The Unified API's object for one component of a given type, named with the `Unified` prefix, which re-finds its component, by its type and filters, on every use.
_Avoid_: Element, tester, locator, page object

**Unified Page**:
The Unified API's view of one browser tab or window showing the application: what a Unified UI Test receives, navigates and searches in.
_Avoid_: UI, window, driver, browser

**Template**:
A component whose own content a test reaches only by entering it, one Template at a time: an app's LitTemplate on every Testing Tool, or a client-rendered web component on browser Testing Tools only.
_Avoid_: Shadow host, web component, LitTemplate, app template

**Application's packages**:
The packages the Unified API takes as the application's own, read from the test the same way on every Testing Tool: the test class's, the `@SpringBootApplication`'s, the configured view packages and those of the routes the test opened, with their subpackages. A Template's class is looked up in them by its tag (ADR 0018).
_Avoid_: classpath, scan packages, base packages

**Base URL**:
The address of the running application that a browser Testing Tool opens for a test; relative paths, the current path and route checks are resolved against it. Each test has one, chosen per test class or for the whole run (ADR 0021).
_Avoid_: App URL, server URL, root URL

**Viewport**:
The visible area of a Unified Page, as width × height in CSS pixels, which the application lays itself out for; a test runs at one unless it changes it.
_Avoid_: Window size, screen size, resolution, breakpoint

**Unified Element**:
The portable, low-level handle to one component (properties, attributes, text, user actions) that every Testing Tool supports and on which Wrappers are written.
_Avoid_: Raw element, WebElement, locator

**Item Name**:
What a test calls one item of a component with several (a tab, a menu item): its visible text, or its accessible name when it shows no text, the same on every Testing Tool.
_Avoid_: Label, caption, item text

**Migration Recipe**:
An OpenRewrite recipe that rewrites a Testing Tool's existing tests into Unified UI Tests, converting what maps and leaving a Migration Marker where it can't.
_Avoid_: Converter, codemod, migration script

**Migration Marker**:
The `// TODO unified:` comment a Migration Recipe leaves on code it couldn't convert, usually kept running through the Escape Hatch. In a `pom.xml` it is an XML comment, `<!-- TODO unified: ... -->`, on what the build must change (an old Vaadin, a Failsafe profile that tested a server or browser outside the test).
_Avoid_: TODO, FIXME, warning

**Component Reference**:
A test's direct handle on a component's Java object, such as a field of the view, which only Browserless has; a Unified UI Test locates components instead.
_Avoid_: Field access, component instance

**User Step**:
One action or check a Unified UI Test performs, stated the way a user would describe it ("fill text field 'City' with 'Turku'"), as the Unified API reports it.
_Avoid_: Step method, log line, trace, keyword

**User Task**:
A named piece of user work in a Unified UI Test ("edit Alan Turing's name"), shown in the reports as one heading over the User Steps it performs.
_Avoid_: Task, step method, keyword, flow, journey, page object

**Step Target**:
The component a User Step acts on or checks, marked in its snapshot and failure evidence on every Testing Tool: its line in a Browserless tree, a spotlighted box over a browser screenshot.
_Avoid_: Highlighted element, focus, subject

**Step Change**:
A component that appeared, vanished or changed state during one action User Step, as seen by comparing the Findable View before and after it; one that no later check reads is an unchecked Step Change.
_Avoid_: Effect, side effect, diff, delta

**Secret Value**:
A value of the test that its reports show as `••••` wherever it occurs: a sign-in password, unless the test declares it not secret, or a value the test marks as secret. Exceptions: the password inside its own sign-in's user name, and the scenario name the author wrote.
_Avoid_: Sensitive data, credential, redacted value

**Base Run**:
The base branch's last run of the same Unified UI Tests on the same Testing Tool, whose User Steps a pull request's changed scenarios are compared with.
_Avoid_: Baseline, golden run, reference run

**Replay**:
Running a failed scenario again, from its evidence, with every kind of evidence on, and stopping just before the failing User Step so a person or an agent can look at the page, run the step, and try lookups; for a person in a visible browser, on Browserless with the JVM held open.
_Avoid_: Rerun, recording, debug mode, time travel

**Findable View**:
A text view of what a Unified UI Test can find in a page or a container: one line per component, with its kind, name and state and the lookup that singles it out, written on failure on every Testing Tool and returned by `describe()`.
_Avoid_: Snapshot, ARIA snapshot, component tree, page dump

**Near Miss**:
A component a failed lookup almost matched (the same kind with a similar name, or another kind with the exact name), listed in the failure so the reader can see what the test should have asked for.
_Avoid_: Candidate, suggestion, fuzzy match

**Layout Finding**:
A layout problem seen on a Unified Page at its current Viewport (it scrolls sideways, a component is clipped out of reach, or interactive components overlap), reported on the User Step where it was seen.
_Avoid_: Violation, layout error, responsive finding, warning

**Viewport Comparison**:
The comparison of runs of the same Unified UI Tests on one Testing Tool at different Viewports, User Step by User Step: which components the widest run shows and another lacks, which Layout Findings appear at which size, and where the runs part.
_Avoid_: Responsive check, responsive diff, breakpoint test

**Reach**:
Whether a user can get to a component on a Unified Page at its current Viewport: visible (perhaps after scrolling), clipped out of reach, or behind another component; a hidden component has none.
_Avoid_: Visibility, in view, accessible
