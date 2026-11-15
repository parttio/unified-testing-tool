# PROTOTYPE: readable styles for Unified UI Tests

Throwaway. Branch `prototype/readable-styles`, never merged. For the ticket "Prototype: the same three tests
in each candidate style" (#198) on the map "Make Unified UI Tests read like what the user does" (#193).

**Question:** what do the same tests look like in each candidate style, and which reads best for the
reviewer of an agent-written test?

```
mvn -o compile                                   # every style compiles against its own stub API
python3 -I score/score.py src/main/java/proto    # intent share per style, baseline token rules
```

| Package | Style |
|---|---|
| `a_today` | Today's API, copied from the demo tests |
| `b_lazy` | No `single()`: the query is the lazy Wrapper, "exactly one" checked on use (Playwright locator) |
| `c_named` | B + `find(UnifiedTextField.class, "City")` by visible name, `rowWhere(column, text)` |
| `d_specs` | B + specs as values: `find(textField("City"))` from a `Components` holder class |
| `e_verbs` | C + action verbs on every scope: `fillIn("City", "Turku")`, `clickOn("Save")`, `assertThat(page).showsNotification(..)` |
| `e_verbs` `ScenariosWithSteps` | F: E + optional `step("Rename Alan Turing", () -> …)` groups |

Tests: `CheckoutTest.picksTheFieldThroughItsContainer`, `EditPersonUnifiedTest.editsAPersonFromTheGrid`,
`GridTest.findsARowByWhatItShows` and `GridTest.combinesCellFilters`. The grid tests inline the `grid(page, id)`
helper, so every style starts from the same statements. B–F carry a `@DisplayName` sentence (decided in
"Who reads a Unified UI Test, and in what form?"); annotations are not scored.

## The same test in each style

`EditPersonUnifiedTest.editsAPersonFromTheGrid`:

```java
// A, today
page.navigate(PeopleView.class);
UnifiedGrid people = page.find(UnifiedGrid.class).single();
people.rows().withCellText("Name", "Alan Turing").single().click();
page.find(UnifiedTextField.class).withLabel("Name").single().setValue("Alan M. Turing");
page.find(UnifiedButton.class).withText("Save").single().click();
assertThat(page.find(UnifiedNotification.class).single()).hasText("Saved Alan M. Turing");
assertThat(people.rows().withCellText("Name", "Alan M. Turing").single()).hasCellText("Email", "alan@example.com");

// B, lazy
UnifiedGrid people = page.find(UnifiedGrid.class);
people.rows().withCellText("Name", "Alan Turing").click();
page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
page.find(UnifiedButton.class).withText("Save").click();
assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");

// C, by visible name
people.rowWhere("Name", "Alan Turing").click();
page.find(UnifiedTextField.class, "Name").setValue("Alan M. Turing");
page.find(UnifiedButton.class, "Save").click();

// D, specs as values
UnifiedGrid people = page.find(grid());
people.find(row("Name", "Alan Turing")).click();
page.find(textField("Name")).setValue("Alan M. Turing");
page.find(button("Save")).click();
assertThat(page.find(notification())).hasText("Saved Alan M. Turing");

// E, action verbs (lookups as C)
people.rowWhere("Name", "Alan Turing").click();
page.fillIn("Name", "Alan M. Turing");
page.clickOn("Save");
assertThat(page).showsNotification("Saved Alan M. Turing");

// F, E + a named group
step("Rename Alan Turing", () -> {
    people.rowWhere("Name", "Alan Turing").click();
    page.fillIn("Name", "Alan M. Turing");
    page.clickOn("Save");
});
```

## Scores

Intent share with the baseline's token rules (`score/score.py`); words = identifiers and literals.

| Test | A today | B lazy | C named | D specs | E verbs | F steps |
|---|---|---|---|---|---|---|
| Checkout | 36% (24/66) | 40% (24/59) | 43% (24/55) | 50% (24/48) | 47% (19/40) | 47% (20/42) |
| Edit person | 39% (21/53) | 44% (21/47) | 48% (21/43) | 51% (21/41) | 52% (18/34) | 52% (19/36) |
| Grid: row by what it shows | 44% (15/34) | 46% (15/32) | 48% (15/31) | 50% (15/30) | 48% (15/31) | – |
| Grid: combined filters | 41% (17/41) | 44% (17/38) | 45% (17/37) | 43% (17/39) | 45% (17/37) | – |
| **All four** | **39%** (77/194) | **43%** (77/176) | **46%** (77/166) | **48%** (77/158) | **48%** (69/142) | |
| **Plumbing words** | 117 | 99 | 89 | 81 | **73** | |

Intent share undersells E: `fillIn("City", ..)` drops the role noun ("text field"), which the baseline rule
counts as intent, so E has fewer intent words and the fewest words overall (−27% against today). Plumbing
words per style is the fairer number for E.

### Read-aloud check (one line each)

| Style | "Fill Name with Alan M. Turing" read aloud | Passes? |
|---|---|---|
| A | page find unified text field class with label Name single set value Alan M. Turing | no |
| B | page find unified text field class with label Name set value Alan M. Turing | no |
| C | page find unified text field class Name set value Alan M. Turing | borderline |
| D | page find text field Name set value Alan M. Turing | yes |
| E | page fill in Name Alan M. Turing | yes |

## User Step report

The same in every style: it is built from what each Wrapper did at run time, not from how the test was
written. E's `fillIn("Name", ..)` still reports "text field", because the resolved component is known.

```
✔ Editing a person from the grid saves the new name                    Playwright, 2.1 s
   1  open People
   2  click row where Name is "Alan Turing" in grid
   3  fill text field "Name" with "Alan M. Turing"
   4  click button "Save"
   5  see notification "Saved Alan M. Turing"
   6  see row where Name is "Alan M. Turing" with Email "alan@example.com"
```

F groups the same User Steps under the step name:

```
   1  open People
   2  Rename Alan Turing
      2.1  click row where Name is "Alan Turing" in grid
      2.2  fill text field "Name" with "Alan M. Turing"
      2.3  click button "Save"
   3  see notification "Saved Alan M. Turing"
```

## A failure message

The billing field is now labelled "Town". Same frame in every style; only the suggested call differs.

```
✘ Billing and shipping each keep their own City                        Playwright
  User Step 2 of 7: fill text field "City" with "Turku"  (in container test id "billing")
  No text field labelled "City" there after 10 s.
  Text fields there: "Street", "Town", "Postal code"
  Did you mean:  <suggestion>
  Evidence: target/surefire-reports/unified-evidence/CheckoutTest/picksTheFieldThroughItsContainer/
```

| Style | Suggestion |
|---|---|
| A | `billing.find(UnifiedTextField.class).withLabel("Town").single()` |
| B | `billing.find(UnifiedTextField.class).withLabel("Town")` |
| C | `billing.find(UnifiedTextField.class, "Town")` |
| D | `billing.find(textField("Town"))` |
| E | `billing.fillIn("Town", "Turku")`; also lists other fillable kinds there: `combo box "Country"` |

E has a failure the others don't: an ambiguous verb.

```
  User Step 3 of 7: click "Save"
  2 clickable components named "Save": button "Save" (footer), link "Save" (menu)
  Narrow it:  page.find(UnifiedButton.class, "Save").click()   or scope it to a container
```

## What the prototype shows

- **B is the base for everything else.** Every later style assumes lazy Wrappers without `single()`. It
  removes 18 words (9%) on its own, and an upgrade recipe can drop `.single()` mechanically. Costs: the
  query type doubles as the Wrapper (`Rows` acts as one row on `click()`), and counts and absence move into
  assertions (`hasCount(11)`, `doesNotExist()`), which reads better than `all().size()`.
- **C and D compete for the lookup.** D reads aloud best ("find text field Name") and keeps per-type filters
  (`textField("City").withPlaceholder(..)`), but reopens ADR 0002, needs 5–7 static imports per test and
  an add-on holder class (`RatingComponents.rating(..)`). C keeps class literals and ADR 0002, but
  `find(type, name)` next to `withLabel(name)` is two ways to say one thing unless one goes.
- **E is the biggest win and a layer, not a lookup.** Fewest words, reads aloud almost verbatim, and the
  report and failures restore what the code drops (the component kind). It needs C or D underneath for grid
  rows, scoping and unusual filters, and it needs decisions: what counts as fillable or clickable, how
  non-text values are passed (numbers, dates, combo items), and that the long form is used only where the
  verb can't do it (one obvious way).
- **F helps long flows only.** Two words per group plus a lambda; it changes the report more than the
  code. Optional, as decided.

## Open questions this hands to the next tickets

For "What shape does a lookup take?": B as the base (yes/no); C or D on top; if C, does `withLabel` stay.
For "Is there a readable layer above the lookups, and which one?": E's verb set, the fillable and clickable
rules, value types, and whether F's `step(..)` ships.
