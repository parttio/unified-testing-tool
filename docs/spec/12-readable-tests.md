# 12. Readable tests and reports

Decided on the map [Make Unified UI Tests read like what the user does](https://github.com/parttio/unified-testing-tool/issues/193); lookups in [ADR 0007](../adr/0007-wrappers-carry-their-filters.md). Terms: **User Step**, **User Task** and **Near Miss** in [GLOSSARY.md](../../GLOSSARY.md). Research in [`docs/research/`](../../docs/research/).

Unified UI Tests are mostly written by AI agents. They are read, in this order, by the reviewer of the diff, by whoever reads a failure (in CI or in an agent's conversation), by the developer who maintains them, and by non-developers. This chapter makes the test code read as the scenario, and lets every run report what the user did in user terms.

Three rules shape it:

- **One obvious way per intent.** Each common intent has one canonical form. A longer form exists only for what the short one can't express.
- **More API is not better.** Readability comes from the structure of the test code and from the reports, not from extra lookup forms or verbs.
- **Java only.** No Gherkin and no external DSL ([BDD research](../../docs/research/bdd-for-unified-ui-tests.md)). The guide may mention JGiven as an option.

## Lookups

[ADR 0007](../adr/0007-wrappers-carry-their-filters.md) replaces the query-and-terminal shape of [4. Locating](04-locating.md):

```java
page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
assertThat(users.row().withCellTextContaining("Email", "user11")).hasCount(11);
```

| Rule | Detail |
|---|---|
| The Wrapper is the query | `find(UnifiedX.class)` returns the Wrapper. Filters are Wrapper methods, specific to its type (ADR 0001), declared as mixins (`LabelFilter<UnifiedTextField>`). There is no query type and no `single()`. |
| Immutable | A filter returns a new Wrapper and never changes the one it was called on. |
| Exactly one, on every use | Every action, read and assertion finds the component again and waits up to the timeout for exactly one match. 0 or several matches fail with Near Misses (see [Failures](#failures)). A Wrapper never holds a component instance. |
| Checks are assertions | `assertThat(x).exists()`, `.doesNotExist()`, `.hasCount(n)`, which retry like every assertion. There is no boolean `exists()`. |
| `all()` | Only to loop over matches. It returns lazy Wrappers by position. |
| Grid rows | `grid.row().withCellText("Name", "Alan Turing")`, `grid.row(n)`. Row actions are Wrapper actions and get Auto-Wait. |
| `findComponent(X.class)` | Returns a lazy `UnifiedGenericComponent` with the shared filters. |

## Structuring a scenario

A Unified UI Test is plain JUnit. The Unified API does not impose a structure. The guide recommends this one:

| Element | Purpose | Reaches the reports |
|---|---|---|
| `@DisplayName("…")` or `@DisplayNameGeneration` | the scenario in one sentence | yes, as the scenario name |
| `// given`, `// when`, `// then` comments | the shape of the scenario, for the reviewer of the diff | no |
| **User Task**: a method whose body is one `page.step("…", () -> …)` | one named piece of user work, named after what the user does ("Edit Alan Turing") | yes, as a heading over its User Steps |
| Lean page object (optional) | the lookups of one view, as methods returning Wrappers. No actions, no assertions | no |

Where User Tasks live: a private method in the test class, until a second test class needs it. It then moves to a `…Tasks` class of static methods that take the `UnifiedPage` (`PeopleTasks.editPerson(page, …)`). Page objects are optional and hold structure only. TestBench page objects migrated by the Migration Recipes keep working: their lookup methods are structure, and their action methods are User Tasks in all but name ([page objects research](../../docs/research/page-objects-and-tasks.md)).

```java
@Test
@DisplayName("Editing a person in the dialog updates the grid")
void editsAPersonInTheDialog(UnifiedPage page) {
    // given
    page.navigate(PeopleView.class);

    // when
    editPerson(page, "Alan Turing", "Alan M. Turing", "alan@bletchley.uk", "Admin");

    // then
    assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
    assertThat(page.find(UnifiedGrid.class).row().withCellText("Name", "Alan M. Turing"))
            .hasCellText("Email", "alan@bletchley.uk")
            .hasCellText("Role", "Admin");
}

private static void editPerson(UnifiedPage page, String name, String newName, String email, String role) {
    page.step("Edit " + name, () -> {
        page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
        dialog.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
        dialog.find(UnifiedTextField.class).withLabel("Email").setValue(email);
        dialog.find(UnifiedComboBox.class).withLabel("Role").selectItem(role);
        dialog.find(UnifiedButton.class).withText("Save").click();
    });
}
```

## `page.step(..)`

```java
void step(String name, Runnable userSteps);   // on UnifiedPage
```

- **On the page, not static.** It needs no static import and doesn't clash with `Allure.step`. It records into the test's run, so it works across `openWindow` and `openAsUser`.
- **`Runnable` only.** Wrappers are lazy, so a test declares them before the step and uses them inside and after it.
- **Nesting:** steps nest without limit. Reports indent them, and a failure names the chain (`In step  Edit Alan Turing › …`).
- **Failure location:** a failure inside a step points first at the line that called the step, then at the line inside it.
- **Naming:** step names describe user work. They are not given/when/then keywords.

## User Steps

Every Wrapper action and every retrying assertion records one User Step, on every Testing Tool, whether or not the test uses `page.step(..)`.

**Wording:** `<verb> <component kind> “<name>” [with “<value>”] [in <scope>]`

- **Verbs** come from a fixed table. Assertions read **see** or **see no**.

  | Verb | Wrapper actions |
  |---|---|
  | open | `navigate` |
  | click, double-click | `click`, `doubleClick` |
  | fill, clear | `setValue`, `clear` |
  | choose | `selectItem` on single-select fields |
  | check, uncheck | `setChecked` |
  | select, deselect | selection in grids and multi-select fields |
  | sort, expand, upload, press | `sort`, `toggleDetails`/`expand`, `upload`, `pressKey` |
  | see / see no | retrying assertions, `doesNotExist` |
- **Component kind:** the Wrapper type in plain words (`UnifiedComboBox` → “combo box”).
- **Name:** the main filter (label, text or accessible name). Other filters go in brackets: `(test id “billing”)`.
- **Scope** names the container: `in dialog “Edit person”`.
- **Add-on Wrappers** get their kind from the class name and a default verb per method, and can override both.
- **Secret values:** values typed into a password field are always shown as `••••`, and a test can mark other values as secret. Values longer than 80 characters are cut with `…`.
- **Calls the recorder can't see** (Escape Hatch, opaque `where(..)`/`waitUntil(..)`, plain JUnit assertions) appear as one gap line, `… 3 calls outside the Unified API (Escape Hatch)`. They are never silently left out.

## Reports

Recording is always on (in memory). One Testing Tool per run, named in every header.

| Output | Where | When | For |
|---|---|---|---|
| CI log | stdout | failed tests by default | whoever reads CI |
| `user-steps.txt` | evidence directory, per test | every test | agents, people |
| `user-steps.json` | evidence directory, per test | every test | the later outputs and tools. Versioned: `"schemaVersion": 1` |
| `summary.md` | evidence directory, per run | every run | the reviewer |
| GitHub job summary | `summary.md` appended when `GITHUB_STEP_SUMMARY` is set | every run in GitHub Actions | the reviewer |

**`summary.md`**, in order:

1. **Header:** Testing Tool and counts.
2. **Changed in this PR:** scenarios with any User Step or User Task called from a file changed against the base branch (`GITHUB_BASE_REF`, or `vaadin.testing.user-steps.changed-since`), steps unfolded, marked new or changed. A change in a shared `…Tasks` class marks every scenario using it. Left out outside a PR.
3. **Failed scenarios:** `@DisplayName`, the failed User Step, Near Misses, evidence path.
4. **Passing scenarios:** one line each, User Steps folded in `<details>`.

Later outputs, built from the same `user-steps.json`: an HTML run report with per-step evidence, living documentation per view, and a step-level diff against the base branch's last run.

## Failures

Every Unified failure throws `UnifiedAssertionError extends AssertionError`, so JUnit reports a failure, not an error. Its message uses fixed labels in a fixed order, and lines with nothing to say are left out. The same parts are available as fields.

```
UnifiedAssertionError: Editing a person in the dialog updates the grid
  Failed at     User Step 5 of 10: fill text field “Email” with “alan@bletchley.uk”
  In step       Edit Alan Turing
  Expected      exactly one text field labelled “Email” in dialog “Edit person”
  Found         none
  Nearby        text field “Name”, text field “E-mail”, combo box “Role”
  Did you mean  find(UnifiedTextField.class).withLabel("E-mail")
  Waited        10 s (vaadin.testing.timeout)
  Tool          playwright (chromium)
  Code          EditPersonTest.java:13 (editsAPersonInTheDialog) ← EditPersonTest.java:24 (editPerson)
  Evidence      target/surefire-reports/unified-evidence/EditPersonTest/editsAPersonInTheDialog/
```

- **Near Misses** are computed on failure only:
  - components of the same Wrapper type in the same scope, showing the property the failed filter matched on: at most 5, closest first (same text ignoring case, then contains, then edit distance);
  - then up to 3 components of another kind with the exact name wanted;
  - hidden components marked `(hidden)`;
  - if the scope itself is missing, Near Misses for the scope instead.
- **Did you mean** appears only when one Near Miss is clearly best: it differs only in case, spacing or punctuation; or it is within a small edit distance with no equal rival; or it has the exact name but is another kind. It is written from `find(..)` on, in ADR 0007 style.
- **Other `Found` lines:**
  - several matches (`Found 2: …`, each with its scope, no suggestion);
  - an assertion timeout (the distinct values seen while waiting: `Found “Saving…”, then “Saved Alan Turing”`);
  - not usable (`Found 1, but disabled`, or hidden, read-only, covered by an open dialog).

  The message is the same on every Testing Tool.
- **`UnifiedElement.getAccessibleName()`** reads the accessible name (4. Locating's order), for Near Misses and for tests.

## Style rules

An opt-in module, `unified-test-archunit` (test scope, in the BOM), checks the style in a plain `@Test`:

```java
class UnifiedStyleTest {
    @Test
    void followsTheUnifiedStyle() {
        UnifiedStyleRules.ALL.check(new ClassFileImporter().importPackages("com.example"));
    }
}
```

| Rule | Fails when |
|---|---|
| Scenario sentence | a `@Test` in a Unified UI Test has no `@DisplayName` and its class no `@DisplayNameGeneration` |
| No raw waits | `Thread.sleep(..)` or a Testing Tool's own wait API is called |
| Testing Tool code needs its Capability | a Unified UI Test class calls an Escape Hatch or depends on Playwright, TestBench or Browserless packages, directly or through its own helpers, without `@RequiresTestingTool` on the class or test method |

Suites with existing violations can wrap the rules in ArchUnit's `FreezingArchRule`. Everything bytecode can't check reliably stays in the guide: `all()` only in loops, no plain JUnit assertions on what the user sees, shared User Tasks rather than copies.

## The agent skill

`write-unified-ui-tests` is the one place agents learn this chapter. It ships in a jar, matching the API version. The recipe `AddUnifiedTestingSkill` (run by every Migration Recipe, or alone) writes it to `.claude/skills/write-unified-ui-tests/SKILL.md`, replaces an older stamped copy, leaves an unstamped (hand-edited) copy alone, and adds a pointer section to `AGENTS.md`.

The skill covers:
- the canonical forms;
- reading the view class (and its translation keys) before writing a lookup;
- `@DisplayName`, comments and User Tasks;
- proving the test on Browserless then Playwright, plus `UnifiedStyleTest` and `user-steps.txt`;
- reading a failure;
- when the Escape Hatch is acceptable.

A second `skill-eval` suite scores it: prose scenarios in, tests out, judged on compiling, passing on two Testing Tools, the style rules, intent share and no Escape Hatch. `resolve-migration-markers` defers to it for style.

## Moving to this chapter

A clean break, since the API is alpha: no deprecation release and no Upgrade Recipe. The release that implements ADR 0007 also moves the Migration Recipes and their golden pairs, the demo tests, the custom Wrappers, `resolve-migration-markers`, the guide and chapters 4–9. The Migration Recipes' `first()` rule is amended in [ADR 0004](../adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md).

### Earlier chapters this amends

| Chapter | Change |
|---|---|
| [4. Locating](04-locating.md) | query shape, terminals, `exists()` and `all()` as in [Lookups](#lookups) |
| [5. Waiting](05-waiting.md) | counts and absence as retrying assertions; failures as in [Failures](#failures) |
| [6. Wrappers](06-wrappers.md) | Wrappers declare filter mixins instead of `Findable<Q>` and a nested query; `getAccessibleName()` on the Unified Element |
| [7. Grid](07-grid.md) | `row()` replaces `rows()`/`RowQuery`; row and column actions get Auto-Wait |
| [8. Escape Hatch](08-escape-hatch.md) | Escape Hatch calls show as gap lines in reports; the style rule on `@RequiresTestingTool` |
| [9. Capabilities](09-capabilities.md) | the same style rule |

## Settings

| Setting | Default |
|---|---|
| `vaadin.testing.user-steps.log` | `failed` (or `all`, `none`) |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` when `GITHUB_STEP_SUMMARY` is set |
| `vaadin.testing.user-steps.changed-since` | in a PR, `origin/$GITHUB_BASE_REF`; otherwise unset |

## Requirements on Testing Tool adapters

- Let the core list Near Misses: re-run a lookup without its user-facing filters, and read `label`, text, `placeholder`, `data-testid` and the accessible name of each candidate.
- Expose the accessible name through the Unified Element.
- Give grid row and column actions the same Auto-Wait as every other action.
- Provide failure evidence for the failed User Step: a screenshot in a browser, the component tree on Browserless.
