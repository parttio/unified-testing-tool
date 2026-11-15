package proto.a_today;

import java.util.List;

/** PROTOTYPE stub of today's API shape (unified-test-api 0.1.0-alpha2). */
interface Findable<Q> {}

interface Query<W> { W single(); List<W> all(); boolean exists(); }

interface Scope { <Q> Q find(Class<? extends Findable<Q>> type); }

interface UnifiedPage extends Scope { void navigate(Class<?> view); }

interface UnifiedTextField extends Findable<UnifiedTextField.Q> {
    void setValue(String value);
    interface Q extends Query<UnifiedTextField> { Q withLabel(String label); }
}

interface UnifiedButton extends Findable<UnifiedButton.Q> {
    void click();
    interface Q extends Query<UnifiedButton> { Q withText(String text); }
}

interface UnifiedNotification extends Findable<UnifiedNotification.Q> {
    interface Q extends Query<UnifiedNotification> {}
}

interface UnifiedContainer extends Scope, Findable<UnifiedContainer.Q> {
    interface Q extends Query<UnifiedContainer> { Q withTestId(String id); Q withId(String id); }
}

interface UnifiedGrid extends Findable<UnifiedGrid.Q> {
    RowQuery rows();
    interface Q extends Query<UnifiedGrid> { Q withTestId(String id); }
    interface Row { void click(); int getIndex(); }
    interface RowQuery extends Query<Row> {
        RowQuery withCellText(String column, String text);
        RowQuery withCellTextContaining(String column, String text);
    }
}

final class Assertions {
    private Assertions() {}
    interface TextAssert { TextAssert hasText(String t); }
    interface ValueAssert { ValueAssert hasValue(String v); }
    interface RowAssert { RowAssert hasCellText(String column, String text); }
    static TextAssert assertThat(UnifiedNotification n) { return null; }
    static ValueAssert assertThat(UnifiedTextField f) { return null; }
    static RowAssert assertThat(UnifiedGrid.Row r) { return null; }
}
