package proto.e_verbs;

/**
 * PROTOTYPE. Style E: style C plus action-first verbs on every Scope (page, dialog, container) for the top
 * intents, and screen-level assertions. Each verb is the canonical form of one intent:
 *   fillIn(label, value)  -> the one fillable field with that label (text, number, date, combo by text, ...)
 *   clickOn(name)         -> the one clickable with that text or accessible name (button, link, menu item)
 *   assertThat(scope).showsNotification(text) / showsField(label, value) / showsText(text)
 * Strict: several matches fail, as find(..) does. Anything else (grid rows, scoping, unusual filters) uses
 * the style C lookups. An add-on Wrapper joins the verbs by implementing Fillable or Clickable.
 */
interface Findable {}
interface Named extends Findable {}
/** Can be the target of fillIn(label, value). */
interface Fillable extends Named {}
/** Can be the target of clickOn(name). */
interface Clickable extends Named {}

interface Scope {
    <W extends Findable> W find(Class<W> type);
    <W extends Named> W find(Class<W> type, String name);
    void fillIn(String label, String value);
    void clickOn(String name);
}

interface UnifiedPage extends Scope { void navigate(Class<?> view); }

interface UnifiedTextField extends Fillable {
    UnifiedTextField withLabel(String label);
    void setValue(String value);
}

interface UnifiedButton extends Clickable {
    UnifiedButton withText(String text);
    void click();
}

interface UnifiedNotification extends Findable {}

interface UnifiedContainer extends Scope, Findable {
    UnifiedContainer withTestId(String id);
    UnifiedContainer withId(String id);
}

interface UnifiedGrid extends Findable {
    UnifiedGrid withTestId(String id);
    Rows rows();
    Rows rowWhere(String column, String text);
    interface Rows {
        Rows withCellText(String column, String text);
        Rows withCellTextContaining(String column, String text);
        void click();
        int getIndex();
    }
}

final class Assertions {
    private Assertions() {}
    interface ScopeAssert {
        ScopeAssert showsNotification(String text);
        ScopeAssert showsField(String label, String value);
        ScopeAssert showsText(String text);
    }
    interface RowsAssert { RowsAssert hasCellText(String column, String text); RowsAssert hasCount(int n); RowsAssert doesNotExist(); }
    static ScopeAssert assertThat(Scope scope) { return null; }
    static RowsAssert assertThat(UnifiedGrid.Rows r) { return null; }
}

/** Style F: a named group of User Steps; shows in the User Step report and in failures. */
final class Steps {
    private Steps() {}
    static void step(String name, Runnable userSteps) { userSteps.run(); }
}
