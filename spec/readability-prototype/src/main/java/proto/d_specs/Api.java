package proto.d_specs;

/**
 * PROTOTYPE. Style D: what to find is a value, built by static factories on a holder class (Components),
 * not on the Wrapper type, so nothing clashes with Flow's TextField (the reason ADR 0002 rejected specs).
 * Each spec type has only its component's filters (ADR 0001). An add-on ships its own holder
 * (RatingComponents.rating("Stars")), so add-ons can join. find(spec) returns the lazy Wrapper (as style B).
 */
interface Spec<W> {}

interface Scope { <W> W find(Spec<W> spec); }

interface UnifiedPage extends Scope { void navigate(Class<?> view); }

interface UnifiedTextField { void setValue(String value); }
interface UnifiedButton { void click(); }
interface UnifiedNotification {}
interface UnifiedContainer extends Scope {}
interface UnifiedGrid extends Scope {}
/** Rows matching a row spec; acting on it needs exactly one. */
interface UnifiedRows { void click(); int getIndex(); }

interface TextFieldSpec extends Spec<UnifiedTextField> { TextFieldSpec withPlaceholder(String p); }
interface ButtonSpec extends Spec<UnifiedButton> { ButtonSpec withAccessibleName(String n); }
interface NotificationSpec extends Spec<UnifiedNotification> {}
interface ContainerSpec extends Spec<UnifiedContainer> { ContainerSpec withTestId(String id); ContainerSpec withId(String id); }
interface GridSpec extends Spec<UnifiedGrid> { GridSpec withTestId(String id); }
interface RowSpec extends Spec<UnifiedRows> {
    RowSpec withCellText(String column, String text);
    RowSpec withCellTextContaining(String column, String text);
}

/** The holder: import static proto.d_specs.Components.*; */
final class Components {
    private Components() {}
    /** By label. */
    static TextFieldSpec textField(String label) { return null; }
    /** By text, else accessible name. */
    static ButtonSpec button(String text) { return null; }
    static NotificationSpec notification() { return null; }
    static ContainerSpec container() { return null; }
    static GridSpec grid() { return null; }
    static RowSpec row(String column, String text) { return null; }
    static RowSpec rows() { return null; }
}

final class Assertions {
    private Assertions() {}
    interface TextAssert { TextAssert hasText(String t); }
    interface ValueAssert { ValueAssert hasValue(String v); }
    interface RowsAssert { RowsAssert hasCellText(String column, String text); RowsAssert hasCount(int n); RowsAssert doesNotExist(); }
    static TextAssert assertThat(UnifiedNotification n) { return null; }
    static ValueAssert assertThat(UnifiedTextField f) { return null; }
    static RowsAssert assertThat(UnifiedRows r) { return null; }
}
