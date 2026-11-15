package proto.c_named;

/**
 * PROTOTYPE. Style C: style B plus a shortcut for the most common lookup, "by what the user calls it".
 * find(type, name) only compiles for Wrappers that have a visible name (Named): a field's label, a button's
 * text or accessible name. A notification or a grid has none, so find(UnifiedNotification.class, "x") does
 * not compile. Grid rows get rowWhere(column, text) for the most common row lookup.
 */
interface Findable {}

/** A Wrapper the user can name: label for fields, text (else accessible name) for buttons. */
interface Named extends Findable {}

interface Scope {
    <W extends Findable> W find(Class<W> type);
    <W extends Named> W find(Class<W> type, String name);
}

interface UnifiedPage extends Scope { void navigate(Class<?> view); }

interface UnifiedTextField extends Named {
    UnifiedTextField withLabel(String label);
    void setValue(String value);
}

interface UnifiedButton extends Named {
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
    /** Shortcut for rows().withCellText(column, text). */
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
    interface TextAssert { TextAssert hasText(String t); }
    interface ValueAssert { ValueAssert hasValue(String v); }
    interface RowsAssert { RowsAssert hasCellText(String column, String text); RowsAssert hasCount(int n); RowsAssert doesNotExist(); }
    static TextAssert assertThat(UnifiedNotification n) { return null; }
    static ValueAssert assertThat(UnifiedTextField f) { return null; }
    static RowsAssert assertThat(UnifiedGrid.Rows r) { return null; }
}
