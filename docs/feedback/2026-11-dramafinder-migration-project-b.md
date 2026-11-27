# Field feedback: migrating a Dramafinder suite (Project B)

Feedback collected after migrating a real app's UI tests from **Dramafinder on Playwright** to the Unified API.
It's about the tool: writing, running and debugging tests with it. It records what went well and what didn't, to steer
the project and to name its advantages.

| | |
|---|---|
| Unified API version | **0.1.0-alpha4**, on Browserless and Playwright |
| Project | **Project B** (anonymised): a Spring Boot app built on the Vaadin AI components |
| Before | 12 Dramafinder 1.2.0 ITs on Playwright sharing a base class, plus 1 `SpringBrowserlessTest` |
| After | The same 74 UI tests plus one new regression test, on the Unified API, run on both Testing Tools |
| Fixed since | Most findings below were fixed in [0.1.0-alpha5](../releases/0.1.0-alpha5.md); see [Status of the findings](#status-of-the-findings) |

Live-LLM tests weren't converted and aren't counted. The project's own migration log and findings list (G1 to G15) stay
in that project; the G-numbers are kept so the findings can be traced back.

## In short

| | Better with Unified | Worse or missing |
|---|---|---|
| **Writing** | Component Wrappers instead of DOM locators; Auto-Wait; navigation by view class; one-line checks for title, column, query parameter, enabled/disabled | About 10% more lines; a few checks had no API yet (date-picker overlay, message position, scroll, negative text); CSS checks need a browser |
| **Running** | 71 of 75 tests also run without a browser, 4× faster per test; the whole build is faster although it runs the suite twice | Browserless has no CSS, so it can pass what a user couldn't do. Playwright has to run as well, which needs a second Failsafe execution |
| **Debugging** | User Steps with server log lines, cross-tool comparison, Findable View, `Did you mean` and its auto-fix, `hasNoErrors()` | No screenshot on Browserless; some evidence was hard to find or undocumented (G8, G14, G15) |

**Verdict from the project:** Unified made the tests easier to write and read, faster to run and much easier to debug.
The gaps were small and specific; each was worked around in a few lines, and 2 Escape Hatch statements remain in 1 test.

## Writing tests

### What got easier

**Components, not DOM.** The old tests found elements with Playwright locators and checked them with DOM assertions. The
new tests find Vaadin components by type and check them by meaning:

| In the 11 migrated ITs | Dramafinder | Unified |
|---|---|---|
| Raw locators (`locator(`, `getByTestId`, `getByText`, …) | 161 | 1 (in the Escape Hatch) |
| Dramafinder element lookups (`…Element.get(..)`) | 22 | 0 |
| JavaScript (`evaluate(..)`, `waitForFunction`) | 14 | 2 (scroll position) |
| Explicit waits (`waitFor…`, `waitForTimeout`) | 18 | 5 `page.waitUntil(..)`, all for state outside the UI (database rows, computed CSS) |
| Lookups by `withAccessibleName` / `withText` / `withId` / `withTestId` | 0 | 53 |

A typical test, week navigation:

```java
// Dramafinder / Playwright
assertThat(page.locator("#app-week-prev")).not().hasAttribute("disabled", "");
assertThat(page.locator("#app-week-prev")).not().hasAttribute("aria-disabled", "true");
assertThat(page.locator("#app-week-next")).hasAttribute("disabled", "");
page.locator("#app-week-prev").click();
assertThat(page.locator("#app-week-badge")).containsText(expectedBadge);   // waits for navigation
String url = page.url();
Assertions.assertThat(url).contains("week=");
Assertions.assertThat(url).contains(middlePlan.getWeekStartDate().toString());

// Unified
page.navigate(PlanView.class);
assertThat(prevWeek(page)).isVisible().isEnabled();     // found by its aria-label "Previous week"
assertThat(nextWeek(page)).isVisible().isDisabled();
prevWeek(page).click();
assertThat(weekBadge(page)).hasTextContaining(badgeFor(middlePlan));
assertThat(page).hasQueryParameter("week", middlePlan.getWeekStartDate().toString());
```

- **Auto-Wait replaced hand-made waits.** A `waitForTimeout(150)` polling loop that waited for a row count to settle,
  because it flaked, is gone. So are comments like "wait for the badge first, so navigation has completed": every
  check retries.
- **Checks state what they mean.** `isEnabled()` replaces a pair of `disabled` / `aria-disabled` attribute checks.
  `hasTitle`, `hasColumn` / `doesNotHaveColumn`, `hasQueryParameter` and `isAt(View.class)` replace locator, text and
  URL-string checks.
- **Navigation is checked by the compiler.** `navigate(PlanView.class, QueryParameters.of("week", ..))` replaces
  `"plan?week=" + monday`; `page.reload()` replaces re-navigating to a path.
- **The tests also check accessibility.** Buttons are found by the `aria-label` a screen reader announces ("Undo
  Friday", "Revert … to defaults"), so a missing or wrong label now fails a test. It also replaced runtime-built ids.
- **Some checks became stronger:**
  - "absent" checks now check first that rows exist, so they can't pass on a list that hasn't rendered yet;
  - a pin test picks its button by a data attribute rather than "the first one".
- **The framework writes the failure evidence.** The old base class had a 30-line `TestWatcher` for failure
  screenshots; the base class is deleted.
- **Tests read better.** All 75 tests have a `@DisplayName` and the User Steps name every action, which the style rules
  (`UnifiedStyleTest`) enforce.

### What cost more, or was missing

**More lines.** `src/test` went from 1,126 to 1,256 lines over the 15 changed files (+12%):

- **Copied setup.** The Migration Recipe removed the base class and copied its 3 annotations, a mock field and its
  `@BeforeEach` into each of the 11 IT classes, about 9 lines each. That's the recipe's choice, not a limitation:
  `@UnifiedSpringBootTest` is `@Inherited`, so a project base class would still work.
- **A `@DisplayName` on each of the 75 tests**, which the style rules require.
- **An explicit `page.navigate(..)` in each test**, where Dramafinder navigated implicitly through `getView()`. One more
  line per test, but each test now says where it starts.

**API gaps.** Each needed a workaround; none blocked a test.

| Need | Workaround | Finding |
|---|---|---|
| Pick a day in a date picker whose text field is hidden by CSS | `picker.element().setProperty("value", ..)` in a named `page.step(..)` | G11 |
| "Message *n* contains X" in a chat | `hasMessageCount(n)`, then AssertJ on `getMessageTexts().get(i)`; these reads don't show in the User Steps | G10 |
| "Text is not X" | AssertJ on `getText()` | |
| A CSS class check | `withClassName(..)` + `exists()`: a filter rather than a check on the component | |
| Read or set the scroll position | Playwright JavaScript under `@RequiresTestingTool(PLAYWRIGHT)`: the only Escape Hatch left | |
| Computed CSS (`max-height`, `display`) | `hasCssValue` / `getCssValue` under `@RequiresBrowser`; skipped on Browserless | |
| Charts, project components (a KPI card) | `UnifiedContainer.withTestId(..)`; there's no Wrapper | |
| Assert that a navigation *doesn't* happen | The old `waitForTimeout(500)` + URL check became an `isAt` + query-parameter check, slightly weaker | |

**Learning and docs.** There's new vocabulary (Wrappers, filters, User Steps, Testing Tools). The Findable View
(`page.describe()`) and the bundled skill help. But the API reference and skill didn't list the `vaadin.testing.*`
settings, so an agent concluded screenshots come only on failure (G15). The migration skill can't be invoked by an agent
(G1).

## Running tests

| `./mvnw -Pit verify` (unit tests + UI ITs) | Wall clock | IT class time | Tools |
|---|---|---|---|
| Dramafinder baseline | 83 s | 67 s | Playwright |
| Unified, Playwright only (after the recipe) | 51 s | | Playwright |
| Unified, final | **59–61 s** | **42 s for both** | Browserless (73 scenarios, 6.7 s) + Playwright (28.9 s) |

- **Browserless runs most of the suite without a browser.** 71 of the 75 UI tests run in the JVM: no Chromium, no server
  port. About 0.1 s each against 0.4 s on Playwright. The Browserless UI test also runs in plain `./mvnw test`.
- **Both tools take less time than one used to.** Even Playwright alone dropped from 83 s to 51 s; the cause wasn't
  investigated.
- **The cost of two tools:**
  - a second Failsafe execution with its own evidence directory, copied from the guide (G6);
  - Chromium is still downloaded for the Playwright run;
  - 4 tests can't run on Browserless (1 needs CSS, 1 scroll JavaScript, 2 are repository-level and take no page).
- **Browserless alone isn't enough.** It has no CSS, so it can act on fields a user can't see. The date-picker test
  passed on Browserless and failed on Playwright (G12). Running both tools is what makes the Browserless speed safe to
  rely on.

## Debugging failures

| | Dramafinder | Unified |
|---|---|---|
| On failure | JUnit stack trace + a full-page screenshot (from the hand-written `TestWatcher`) | `UnifiedAssertionError` with Expected / Found / Nearby, `user-steps.txt` / `.json`, `summary.md`, `report.html`, screenshot (browser) or component tree (Browserless) |
| Server errors | In the console log, mixed with everything else | Printed under the User Step that caused them |
| Cross-tool | n/a | "Compared with Browserless" names tests that end differently, and the step where the two runs part |
| A renamed label | Fix the locator by hand | `Did you mean …`, and `ApplyDidYouMean` fixes it in one run |
| Silent server errors | Not checked | `assertThat(page).hasNoErrors()` |

This is where Unified helped most during the migration:

- **A latent app bug, found and explained in one read.** A view rebuilt its AI orchestrator whenever `beforeEnter` fired
  again on the same view instance. In a browser a full page load hides this; Browserless's same-route navigation
  exposed it. The test failed with "Expected exactly one message input, found none", and the cause (the LLM provider
  "already in use by another orchestrator") was printed under User Step 1. The Dramafinder suite never hit it. The fix
  is in the app, and `hasNoErrors()` now guards it (G9).
- **A Browserless-only pass, flagged on the first run:** the date-picker test above (G12).
- **Nondeterministic test data, exposed.** The comparison showed one test clicking a different list item on each tool,
  because the app shuffles its data. A pass/fail report never shows that.

What was still weaker:

- **No screenshot on Browserless.** You get a component tree instead.
- **The Findable View was written outside the test's evidence directory**, and the failure didn't name it (G8).
- **`Did you mean` was missing from `user-steps.txt`**, the file the skill says to read (G14).
- **Per-step screenshots and traces were opt-in and undocumented** for agents (G15).

## Where Unified was incomplete or less efficient

1. **More code:** +12% lines, mostly the recipe copying the base-class setup into every class. A project base class can
   win most of it back.
2. **Missing checks:** no date-picker overlay pick (G11), no positional message check (G10), no negative text check, no
   scroll position. Some workarounds leave the User Steps (AssertJ reads, `element().setProperty`).
3. **No Wrapper for some components:** Charts, project components, plain `Span`/`Div` in the Findable View (G13). These
   fall back to `UnifiedContainer` + test id.
4. **Browserless can give false passes** on anything CSS hides, by design. It's only safe together with a browser run.
5. **One check got slightly weaker:** "no navigation happens" no longer waits 500 ms.
6. **Docs for agents lagged the jars:** settings missing from the skill (G15), guides at a release tag a version behind
   (G2), migration skill user-only (G1).
7. **Two tools mean more build config**, though it's copy-paste from the guide.

None of these made a test slower or flakier. Two old flakiness sources are gone: the `waitForTimeout` loop and a
single-read persistence race, now a retried `page.waitUntil`.

## Status of the findings

All findings were filed and closed. Where they were fixed:

| Finding | Status |
|---|---|
| G1 Migration skill can't be invoked by an agent | Documented as user-only; not changed |
| G2 Guides at a release tag give the previous release's coordinates | Fixed |
| G6 Surefire and Failsafe share one evidence directory by default | Fixed in [0.1.0-alpha5](../releases/0.1.0-alpha5.md) |
| G8 Findable View written outside the evidence directory | Fixed in [0.1.0-alpha5](../releases/0.1.0-alpha5.md) |
| G10 No positional or per-sender message check | Fixed in [0.1.0-alpha5](../releases/0.1.0-alpha5.md) |
| G11, G12 Date picker whose field is hidden: no overlay pick, and a Browserless-only pass | Fixed in [0.1.0-alpha5](../releases/0.1.0-alpha5.md) |
| G13 Plain HTML components missing from the Findable View | Fixed in [0.1.0-alpha5](../releases/0.1.0-alpha5.md) |
| G14 `Did you mean` missing from `user-steps.txt` | Fixed in [0.1.0-alpha5](../releases/0.1.0-alpha5.md) |
| G15 `vaadin.testing.*` settings missing from the skill and API reference | Fixed |

Still open as ideas: a negative text check, a scroll-position check, and a check that a navigation doesn't happen.

## Advantages to highlight

- Tests find components by meaning and accessible name, which also checks accessibility for free.
- Auto-Wait removes hand-made waits and the flakiness they caused.
- Most of the suite runs without a browser, and the whole build got faster even while running on two tools.
- The cross-tool comparison catches Browserless-only passes and nondeterministic test data.
- Server errors appear under the User Step that caused them, which explained a latent app bug in one read.
- `Did you mean` and `ApplyDidYouMean` repair a renamed label automatically.
