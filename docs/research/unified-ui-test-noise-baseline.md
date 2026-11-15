# Noise baseline for Unified UI Tests

Research for #196, part of map #193 ("Make Unified UI Tests read like what the user does").

**Question:** in today's demo Unified UI Tests, which constructs take up most of a test without stating
what the user does, how often does each appear, and what share of a test is intent versus plumbing? This
is the baseline future prototypes are measured against.

**Source:** the code itself, at commit `f42bb61`, in `unified-test-demo/src/test/java/demo/`. Every number
below comes from a static scan of that directory (method at the end); file and method names are given so
each number can be checked by reading the test.

## Short answer

- A typical statement names the component three times over: the Wrapper type, a filter and a terminal.
  `X.find(UnifiedY.class).withZ("…").single()` appears **261 times in 128 test methods** (2.0 per test).
  It is in 37% of all statements and holds **40% of all word tokens**.
- Only **40% of word tokens state intent**: the verb, the role, the label, the value or the expectation.
  That is 1.5 plumbing tokens for every intent token. Counting punctuation too, it is **17%**. The golden
  `*MigratedTest`s score lower (37%) than the hand-written Unified tests (41%).
- The biggest single noise source is **`assertThat(page.find(..)..single())`**: 78 statements, or 49%
  of all 159 `assertThat` statements.
- **Each component is either looked up twice or held in a variable:** 61 statements only declare a local
  for a found component (`UnifiedNumberField height = page.find(..).single();`), and 17 lookups repeat a
  lookup made earlier in the same test. 19% of the Unified statements (25% of the migrated ones) state no
  user intent at all.
- Grid access (`rows()`, `row(n)`, `withCellText`, `column`, `cell`), container scoping and the Escape
  Hatch are **concentrated**, not spread out. Grid calls appear in 17 of 128 tests, scoped searches in 15,
  and Escape Hatch use in 14 (6 of the 19 golden migrated tests, 32%).
- The ten most frequent user intents: open a view (109), fill a field (54), click a button (43), expect a
  notification (36), expect a field value (35), expect a text on the page (25), expect grid content (17),
  act on a grid row (16), choose an option (15) and expect a selection (14). The first four make up most
  of every scenario test.

## Corpus

| Group | Files | Test methods | Statements |
|---|---|---|---|
| Unified tests (hand-written, no `Migrated` in the name) | 27 | 109 | 548 |
| Golden migrated tests (`*Migrated*Test`) | 15 | 19 | 128 |
| Support code: helpers in test classes, page objects, base classes | 11 | 19 helper methods | 27 |

Left out: the originals the golden tests are migrated from (`*TestBenchTest`, `*BrowserlessTest`,
`*DramafinderTest`, `ProfileBrowserless{Leftovers,NarrowContext,PageObject}Test`, `ProfileBrowserlessPage`,
`ProfileTestBenchPage`, `AbstractProfileTestBench`, `RatingElement`). Also left out are the two
launcher-only harness methods (`FailureEvidenceTest.writesEvidenceOnFailure`,
`TransactionalTest.failsOnABrowserTestingTool`), which drive no page. Their inner tests
(`Failing.fails`, `TransactionalUiTest.usesTheDatabase`) are counted.

Caveat: many hand-written Unified tests are **API capability tests**, not user stories (`GridTest`,
`TreeTest`, `AddonTest`, `DashboardTest`, the escape-hatch tests). They lean on `assertEquals` over
values the API reads (`getCellText`, `all().size()`, `getMessage()`), which inflates "expect a raw value"
below. For a prototype comparison, the **scenario subset** at the end is the fairer yardstick.

## Noise constructs and their counts

All tests (128 methods; Unified / migrated in brackets where they differ).

### 1. `find(Unified*.class)` + filter + terminal chains

| Measure | Count |
|---|---|
| `find(..)` / `findComponent(..)` chains | **261** (231 `find`, 30 `findComponent`) [Unified 210, migrated 51] |
| Terminal `single()` / `all()` / `exists()` | 240 / 16 / 4 (`single()` 92%) |
| Statements containing a chain | 253 of 676 (37%) |
| Word tokens inside `find … terminal` | 1,739 of 4,363 (40%) |
| Chains per test | 2.0 mean (Unified 1.9, migrated 2.7) |
| Filters | `withLabel` 94, `withText` 73, `withId` 16, `withTestId` 10, `withClassName` 5, `withItem` 4, `withAccessibleName` 4, `withTab` 3, `withTextContaining` 3, `withPlaceholder` 2, `withHeaderTitle` 2, others 4 |
| Types looked up | `UnifiedButton` 57, `UnifiedTextField` 51, `UnifiedNotification` 31, `Span` 18 (via `findComponent`), `UnifiedComboBox` 12, `UnifiedContainer` 10, … |

Most chains have the same form, `page.find(UnifiedButton.class).withText("Save").single()`. Of its 8
word tokens, 3 carry intent (`Button`, `"Save"`, and the verb that follows). The rest is `page`, `find`,
`Unified…`, `class`, `withText` and `single`. As `docs/spec/04-locating.md` ("Terminals") says,
`single()` has no meaning for the user; it only states "exactly one match".

### 2. `assertThat(page.find(..).single())`

**78 statements** (Unified 62, migrated 16), which is 49% of all 159 `assertThat(..)` statements. The
most common form is `assertThat(page.find(UnifiedNotification.class).single()).hasText("…")`
(20 times, counting the line-wrapped variant). For the same intent, a second form appears 7 times:
`assertThat(page.find(UnifiedNotification.class).withText("…").single()).isOpen()`.

### 3. The same component named twice: repeated lookups and holding variables

| Measure | Count |
|---|---|
| Repeated lookups (an identical receiver + type + filters chain a second time in one test) | **17** redundant lookups in 13 tests [Unified 15/11, migrated 2/2] |
| Declaration-only statements (`UnifiedX x = …find(..)..single();` with no action) | **61** [Unified 50, migrated 11] |
| Statements that state no user intent at all | Unified 106 of 548 (19%), migrated 32 of 128 (25%) |

The two are the same cost paid in different ways: a test either repeats the chain or declares a
variable. `ExpenseTest.fillsTheValueFields` repeats all four field lookups: fill, then
`assertThat(page.find(...same chain...).single()).hasValue(..)`. `PreferencesTest.savesTypedValues`
declares three locals instead. The most frequent no-intent statements are declarations:
`UnifiedGrid users = grid(page, "…")` (8), `UnifiedNumberField height = page.find(…)` (5),
`UnifiedDialog dialog = openDialog(page)` (4), `UnifiedTextField name = …` (4). In migrated tests they
are Escape Hatch setup, `TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page)` (3), and
`UnifiedGrid people = page.find(UnifiedGrid.class).single()` (2).

### 4. Grid row and cell access

| Call | Count |
|---|---|
| `rows()` | 17 |
| `withCellText(..)` / `withCellTextContaining(..)` | 13 / 3 |
| `row(n)` | 11 |
| `column(..)` / `columnWithId(..)` | 8 / 1 |
| `hasCellText` / `hasCellTexts` | 6 / 6 |
| `getIndex()` / `getCellText(..)` | 5 / 4 |
| `cell(..)` / `header()` | 1 / 1 |

Grid calls appear in 17 tests, 14 of them in `GridTest` and `GridInteractionTest`. The noise is in the
migrated grid tests. `EditPersonMigratedTest` and `EditPersonBrowserlessMigratedTest` keep a row
**index** (`int row = …getIndex(); people.row(row).getCellText(0)`), where `EditPersonUnifiedTest` finds
the row again by what it shows. In the Unified form, finding a row is
`people.rows().withCellText("Name", "Alan Turing").single()`: 4 plumbing tokens around 2 literals.

### 5. Container scoping

**25 scoped searches** (a `find` whose receiver is not a Unified Page) in 15 tests. Receivers: a dialog
(`dialog.find`, 6), a `Scope` local (`content.find`, 4, in `AccountTest`), a `UnifiedContainer` local
(`billing`, `shipping`, `buttons`; 4), `tabs.content()`, `advanced.content()`, `…single().find(…)` chained straight off another lookup (`CheckoutTest.containersNest`,
`ExpenseTest.searchesInPlainHtmlContainers`), and grid parts (`cell("Actions")`, `column(..).header()`,
`row.details()`). Scoping costs a lookup of its own, usually by test id
(`page.find(UnifiedContainer.class).withTestId("billing").single()`), and a variable to hold it.

### 6. Escape Hatch use

| Measure | Count |
|---|---|
| `unwrap(..)` / `tryUnwrap(..)` | 6 / 1 |
| `BrowserlessEscapeHatch.of` / `TestBenchEscapeHatch.of` | 9 |
| Statements calling a Testing Tool API directly (`browserless.$(..)`, `testBench.$(..)`, `GridElement.get(..)`, …) | 28 |
| Tests with any Escape Hatch use | **14 of 128** (Unified 8 of 109, all of them tests that demonstrate the hatch; migrated 6 of 19 = 32%) |
| `.element()` (Unified Element: `pressKey`, `dispatchEvent`, `setValueAsUser`, `getVisibleText`, RouterLink `click`) | 11 in 10 tests |
| Migration Markers (`// TODO unified:`) in migrated files | 31 in 13 files. Most common: "was waitForFirst(); single() fails…" 9, Dramafinder Escape Hatch 7, TestBench Escape Hatch 3 |

In the hand-written suite the Escape Hatch is close to absent except where it is the subject of the test.
It weighs most in the migrated tests, where each use also carries a Migration Marker line
(`EditPersonDramafinderMigratedTest`: 7 markers for 7 statements).

### 7. `findComponent(FlowClass.class)` for text on the page

30 `findComponent` chains, all in Unified tests, 18 of them `Span`. They are mostly how a test says "the
page shows X": `assertThat(page.findComponent(Span.class).withId("status").single()).hasText("…")`
(6 times as written, plus `ExpenseTest`'s `assertStatus` helper, 4 calls). The user never sees a `Span`
or an id.

## Intent vs plumbing ratio

### Method

1. **Unit:** statements of `@Test` methods that take a `UnifiedPage`. A statement is the text between
   `;` at bracket depth 0, with comments removed.
2. **Tokens:** Java word tokens, meaning identifiers, string literals and number literals. Punctuation is
   left out of the headline figure and reported separately.
3. **An intent token** is one of these. Everything else is plumbing.
   - a string or number literal (label, button text, value, expected text)
   - an action verb (`click`, `setValue`, `selectItem(s)`, `check`, `selectTab`, `clickItem`, `upload`,
     `pressKey`, `navigate`, `sort`, `select`, `toggleDetails`, …)
   - an expectation: any `is…`/`has…` predicate (`hasText`, `isOpen`), `assertEquals/True/False/Null/Same`
     and `get…` reads of what the user sees
   - the role noun, counted once per statement: the class before `.class`, such as `UnifiedButton`, so
     that "the Save *button*" counts
   - a view class in `navigate(XView.class)`
   - a helper whose name already says the intent (`assertNotification`, `assertStatus`, `openDialog`)

   Plumbing includes `page`, `find`, `findComponent`, `single`, `all`, `class`, filter names
   (`withLabel`, `withText`, …), `assertThat`, local variable names and types, `rows`, `row`,
   `element`, `content`, `unwrap` and Escape Hatch receivers.
4. **Ratio:** intent share = intent / (intent + plumbing), summed per test and per group.

Worked example, `EditPersonUnifiedTest.editsAPersonFromTheGrid` (intent / plumbing tokens):

```
2/2  page.navigate(PeopleView.class)
1/6  UnifiedGrid people = page.find(UnifiedGrid.class).single()
3/4  people.rows().withCellText("Name", "Alan Turing").single().click()
4/5  page.find(UnifiedTextField.class).withLabel("Name").single().setValue("Alan M. Turing")
3/5  page.find(UnifiedButton.class).withText("Save").single().click()
3/5  assertThat(page.find(UnifiedNotification.class).single()).hasText("Saved Alan M. Turing")
5/5  assertThat(people.rows().withCellText("Name", "Alan M. Turing").single()).hasCellText("Email", "alan@example.com")
= 21 intent / 32 plumbing = 40%
```

### Results

| Group | Intent tokens | Plumbing tokens | Intent share | Plumbing per intent token | Per-test median (min–max) |
|---|---|---|---|---|---|
| Unified tests | 1,457 | 2,114 | **41%** | 1.45 | 41% (30–69%) |
| Golden migrated tests | 293 | 499 | **37%** | 1.70 | 38% (28–42%) |
| All tests | 1,750 | 2,613 | **40%** | 1.49 | 40% (28–69%) |
| Scenario subset (15 user-flow tests, below) | 338 | 525 | **39%** | 1.55 | – |

How sensitive the figures are to the method:

- Counting punctuation as plumbing gives an intent share of **17%** (Unified, 1,457 of 8,516 tokens) and
  **16%** (migrated).
- Counting the filter name (`withLabel`, `withText`, …) as intent, on the grounds that it says *how* the
  user identifies the component, adds 220 tokens and gives **45%** overall.
- Measured in characters, string literals (the labels and values themselves) are **16%** of the
  non-whitespace characters in both groups.

The highest shares are tests that are mostly `navigate` + `assertThat(page).isAt(..)`
(`NavigationTest.navigatesByPathWithQuery`, 69%) or that read grid cells off an already-held grid
(`GridTest.readsCountHeadersAndCells`, 57%). The lowest are migrated tests with Escape Hatch leftovers
(`ProfileBrowserlessLeftoversMigratedTest.savesTheProfile`, 29%) and scoped-container tests
(`CheckoutTest.containersNest`, 30%; `ReviewTest.findsReplacedButtonsRightAfterAClick`, 31%).

**Scenario subset** (tests that read as a user flow, for comparing prototypes):
`EditPersonUnifiedTest.editsAPersonFromTheGrid`, `ProfileViewTest.savesName`,
`CheckoutTest.picksTheFieldThroughItsContainer`, `DialogTest.searchesInsideTheDialog`,
`OrderTest.selectsByVisibleLabel`, `PreferencesTest.savesTypedValues`, `ExpenseTest.fillsTheValueFields`,
`MockedServiceTest.usesTheMock`, `AccountTest.switchesTabsAndWorksInside`, `ReportTest.findsOnlyTheOpenDialog`,
`ContainersMigratedTest` (both tests), `FieldsMigratedTest` (both tests) and `ProfileMigratedTest.savesTheProfile`.
Together: 127 statements, 72 find chains, 19 declaration-only lines, 21 `assertThat(find..single())`,
6 repeated lookups, and an intent share of 39%.

## The 10 most frequent user intents

Counted per statement over all 128 test methods. A statement can carry more than one intent; helper calls
count by what the helper does. "Typical line" is the most frequent form after replacing literals with `"…"`.

| # | Intent | Count | Typical current line (times this exact form appears) | Intent / plumbing tokens of the typical line |
|---|---|---|---|---|
| 1 | Open a view | 109 | `page.navigate(ProfileView.class);` (22 for ProfileView; one form for all views) | 2 / 2 |
| 2 | Fill (or clear) a field by its label | 54 | `page.find(UnifiedTextField.class).withLabel("Name").single().setValue("Alice");` (18). Otherwise via a declared local, `height.setValue(170.0);` | 4 / 5 |
| 3 | Click a button by its text | 43 | `page.find(UnifiedButton.class).withText("Save").single().click();` (34). Scoped: `dialog.find(UnifiedButton.class).withText("Save").single().click();` (4) | 3 / 5 |
| 4 | Expect a notification | 36 | `assertThat(page.find(UnifiedNotification.class).single()).hasText("Saved Alice");` (20) / `….withText("…").single()).isOpen();` (7) / helper `assertNotification(page, "…")` (6) | 3 / 5 |
| 5 | Expect a field's value / checked state | 35 | `assertThat(page.find(UnifiedTextField.class).withLabel("Name").single()).hasValue("Alice");` (6). Or on a held local, `assertThat(newsletter).isChecked();` | 4 / 6 |
| 6 | Expect a text shown on the page | 25 | `assertThat(page.findComponent(Span.class).withId("status").single()).hasText("Approved");` (6) / helper `assertStatus(page, "…")` (4) | 3 / 6 |
| 7 | Expect grid content (row count, a row's cells, a column) | 17 | `assertThat(users.row(0)).hasCellText("Name", "User 1");` / `assertThat(people.rows().withCellText("Name", "…").single()).hasCellText("Email", "…");` | 5 / 5 |
| 8 | Act on a grid row (click, select, sort, details) | 16 | `people.rows().withCellText("Name", "Alan Turing").single().click();` / `users.row(1).click();` | 3 / 4 |
| 9 | Choose (or clear) an option in a select field | 15 | `page.find(UnifiedComboBox.class).withLabel("Country").single().selectItem("Germany");`. Usually via a declared local: `country.selectItem("Germany");` | 4 / 5 |
| 10 | Expect a select field's selection or items | 14 | `assertThat(size).hasSelectedItem("Large");` (needs an earlier declaration line) | 2 / 2 + declaration |

Next in line: expect to be on a view (`assertThat(page).isAt(ProfileView.class)`, 13), select a tab or
menu item (`tabs.selectTab("Security")`, 11), and expect a dialog open or closed
(`assertThat(dialog).isClosed()`, 11).

Outside the ranking is the catch-all **"expect a raw value"**: 92 `assertEquals`/`assertTrue`/`assertNull`
statements, mostly in capability tests. They break down as reading a value (`getValue`, `getItems`,
`getSelectedTab`; 38), counting matches (`assertEquals(1, …all().size())`; 25), tool or Escape Hatch
state (10), grid reads (`getCellText`, `getIndex`; 8), presence (`exists()`; 6) and error messages from
`assertThrows` (5). The counts of matches (25) and `exists()` (6) are a user intent in disguise: "there
is one / there is no X".

## Reproducing

The scripts are not in the repo. They are a small Python lexer-and-regex scanner and an aggregator.
`noise.py` splits each method into statements, then counts the chains, filters, terminals, scoped
receivers, repeated chain keys (receiver + `find`/`findComponent` + type + filter text), declaration-only
statements, grid calls, Escape Hatch calls, intents (a rule per verb or assertion subject type) and the
token classes above. `agg.py` sums the counts per group. Known limits:

- It is regex-based, not a Java parser.
- A repeated lookup counts only when the text is identical. The same component reached through
  different filters or receivers is not caught, so 17 is a lower bound.
- Intent classification goes by the last call of a statement and the declared type of a local, so a few
  statements are approximate (page-object calls are counted as their own bucket, 6).
- Statement counts include lambda bodies inside `assertThrows`.

Rerunning both scripts on a later commit gives numbers that compare directly with these.
