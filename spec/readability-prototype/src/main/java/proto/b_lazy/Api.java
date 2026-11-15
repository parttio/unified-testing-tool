package proto.b_lazy;

/**
 * PROTOTYPE. Style B: the query is the Wrapper. A Wrapper is lazy and re-finds its component on every use
 * (as today); "exactly one match" is checked when it is used, like a Playwright locator. Filters return the
 * same Wrapper type, so they stay checked at compile time (ADR 0001). find(..) needs no query type.
 */
interface Scope { <W extends Findable> W find(Class<W> type); }

interface Findable {}

interface UnifiedPage extends Scope { void navigate(Class<?> view); }

interface UnifiedTextField extends Findable {
    UnifiedTextField withLabel(String label);
    void setValue(String value);
}

interface UnifiedButton extends Findable {
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
    /** Rows matching the filters; acting on it needs exactly one. */
    interface Rows {
        Rows withCellText(String column, String text);
        Rows withCellTextContaining(String column, String text);
        void click();
        int getIndex();
    }
}

final class Assertions {
    private Assertions() {}
    interface TextAssert { TextAssert hasText(String t); }
    interface ValueAssert { ValueAssert hasValue(String v); }
    interface RowsAssert { RowsAssert hasCellText(String column, String text); RowsAssert hasCount(int n); RowsAssert doesNotExist(); }
    static TextAssert assertThat(UnifiedNotification n) { return null; }
    static ValueAssert assertThat(UnifiedTextField f) { return null; }
    static RowsAssert assertThat(UnifiedGrid.Rows r) { return null; }
}

interface UnifiedComboBox extends Findable {
    UnifiedComboBox withLabel(String label);
    void selectItem(String item);
}

interface UnifiedDialog extends Scope, Findable {
    UnifiedDialog withHeaderTitle(String title);
}

final class DialogAssertions {
    private DialogAssertions() {}
    interface DialogAssert { DialogAssert isClosed(); }
    static DialogAssert assertThat(UnifiedDialog d) { return null; }
}

/** One static method: a named group of User Steps in the report. */
final class Steps {
    private Steps() {}
    static void step(String name, Runnable userSteps) { userSteps.run(); }
}
