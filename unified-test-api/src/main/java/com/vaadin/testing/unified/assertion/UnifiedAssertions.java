package com.vaadin.testing.unified.assertion;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.internal.grid.GridSupport;
import java.util.List;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedTabSheet;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedField;
import com.vaadin.testing.unified.component.UnifiedNumberField;
import com.vaadin.testing.unified.component.UnifiedTextField;
import java.time.LocalDate;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.UnifiedPageImpl;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.steps.Phrase;
import com.vaadin.testing.unified.internal.steps.Phrases;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import com.vaadin.testing.unified.internal.failures.FailureMessage;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Retrying assertions: each check retries until the timeout (10 s default), then fails with the
 * expected and actual values. On Browserless, UI.access tasks and signals are flushed between tries.
 */
public final class UnifiedAssertions {
    private UnifiedAssertions() {}

    public static PageAssert assertThat(UnifiedPage page) {
        if (!(page instanceof UnifiedPageImpl impl)) {
            throw new IllegalArgumentException("Not an injected UnifiedPage: " + page);
        }
        return new PageAssert(impl.run());
    }

    public static TextFieldAssert assertThat(UnifiedTextField field) { return new TextFieldAssert(field); }
    public static NumberFieldAssert assertThat(UnifiedNumberField field) { return new NumberFieldAssert(field); }
    public static DatePickerAssert assertThat(UnifiedDatePicker field) { return new DatePickerAssert(field); }
    public static CheckboxAssert assertThat(UnifiedCheckbox checkbox) { return new CheckboxAssert(checkbox); }
    public static SingleSelectAssert assertThat(UnifiedComboBox field) {
        return new SingleSelectAssert(field, field::getSelectedItem, field::getItems);
    }
    public static SingleSelectAssert assertThat(UnifiedRadioButtonGroup field) {
        return new SingleSelectAssert(field, field::getSelectedItem, field::getItems);
    }
    public static MultiSelectAssert assertThat(UnifiedMultiSelectComboBox field) { return new MultiSelectAssert(field); }
    public static TabSheetAssert assertThat(UnifiedTabSheet tabSheet) { return new TabSheetAssert(tabSheet); }
    public static TextAreaAssert assertThat(com.vaadin.testing.unified.component.UnifiedTextArea field) { return new TextAreaAssert(field); }
    public static IntegerFieldAssert assertThat(com.vaadin.testing.unified.component.UnifiedIntegerField field) { return new IntegerFieldAssert(field); }
    public static BigDecimalFieldAssert assertThat(com.vaadin.testing.unified.component.UnifiedBigDecimalField field) { return new BigDecimalFieldAssert(field); }
    public static DateTimePickerAssert assertThat(com.vaadin.testing.unified.component.UnifiedDateTimePicker field) { return new DateTimePickerAssert(field); }
    public static TabsAssert assertThat(com.vaadin.testing.unified.component.UnifiedTabs tabs) { return new TabsAssert(tabs); }
    public static DetailsAssert assertThat(com.vaadin.testing.unified.component.UnifiedDetails details) { return new DetailsAssert(details); }
    public static ButtonAssert assertThat(UnifiedButton button) { return new ButtonAssert(button); }
    public static NotificationAssert assertThat(UnifiedNotification notification) { return new NotificationAssert(notification); }
    public static DialogAssert assertThat(UnifiedDialog dialog) { return new DialogAssert(dialog); }
    public static GridAssert assertThat(UnifiedGrid grid) { return new GridAssert(grid); }
    public static GridRowAssert assertThat(UnifiedGrid.UnifiedGridRow row) { return new GridRowAssert(row); }
    public static GridColumnAssert assertThat(UnifiedGrid.UnifiedGridColumn column) { return new GridColumnAssert(column); }
    public static GenericAssert assertThat(UnifiedWrapper wrapper) { return new GenericAssert(wrapper); }

    // ---------------------------------------------------------------- shared retry

    /** Every retrying assertion ends here, and records one User Step: "see …" or "see no …". */
    static void check(TestRun run, Supplier<Phrase> step, Supplier<String> subject, String expectation,
            Object expected, Supplier<?> actual, Predicate<Object> ok) {
        run.steps().check(step, run.window(), () -> {
            // the distinct values seen while waiting, in order: "Found “Saving…”, then “Saved Alan Turing”"
            Set<String> seen = new LinkedHashSet<>();
            return run.pollFor(() -> {
                Object value = actual.get();
                String shown = shown(value);
                seen.remove(shown); // the latest last
                seen.add(shown);
                return ok.test(value) ? Boolean.TRUE : null;
            }, () -> FailureParts.of(subject.get() + " " + expectation + (expected == null ? "" : " " + shown(expected)),
                    seen.isEmpty() ? null : FailureMessage.sequence(List.copyOf(seen))));
        });
    }

    /** A value as a failure shows it: text quoted (“Saved”), lists in brackets, the rest as it prints. */
    static String shown(Object value) {
        if (value == null) {
            return "nothing";
        }
        if (value instanceof CharSequence text) {
            return "“" + Phrase.cut(text.toString()) + "”";
        }
        if (value instanceof java.util.Collection<?> values) {
            return "[" + String.join(", ", values.stream().map(UnifiedAssertions::shown).toList()) + "]";
        }
        return String.valueOf(value);
    }

    /** {@link #check} with the step worded from the expectation: "see text field “Name” with value “Alan”". */
    static void checkTarget(TestRun run, Supplier<StepTarget> target, String subject, String expectation,
            Object expected, Supplier<?> actual, Predicate<Object> ok) {
        check(run, () -> Phrases.forCheck(target.get(), expectation, expected), () -> {
            try {
                return target.get().renderWithScope();
            } catch (RuntimeException e) {
                return subject;
            }
        }, expectation, expected, actual, ok);
    }

    /** A match count, read as "2 matches" in a failure. */
    private record Matches(int count) {
        @Override
        public String toString() {
            return count + (count == 1 ? " match" : " matches");
        }
    }

    static Object matches(int count) {
        return new Matches(count);
    }

    static int count(Object matches) {
        return ((Matches) matches).count();
    }

    // ---------------------------------------------------------------- page

    public static final class PageAssert {
        private final TestRun run;

        PageAssert(TestRun run) { this.run = run; }

        public PageAssert isAt(Class<? extends Component> view) {
            check(run, () -> Phrases.page("see", "see " + Phrases.viewName(view)), () -> "the page", "to show",
                    view.getSimpleName(), () -> run.session().currentPath(), any -> run.session().isAt(view));
            return this;
        }

        public PageAssert hasPath(String path) {
            String expected = path.startsWith("/") ? path.substring(1) : path;
            check(run, () -> Phrase.template("see", "see path {value}", null, List.of(expected)), () -> "the page",
                    "to have path", expected, () -> run.session().currentPath(), expected::equals);
            return this;
        }

        /**
         * Nothing went wrong behind the user's back so far in this test: the application logged no error, and no
         * browser console of the test's windows got one. What was logged is in the failure, and under the User
         * Step during which it was logged in the reports.
         * <p>
         * Which lines count follows {@code vaadin.testing.user-steps.server-log}; with {@code off} only the
         * console is checked. Console errors are known on Testing Tools that can read the console.
         */
        public PageAssert hasNoErrors() {
            check(run, () -> Phrases.page("see no", "see no errors"), () -> "the application", "to have logged no errors",
                    null, () -> run.steps().errors().stream().map(com.vaadin.testing.unified.internal.steps
                            .StepRecorder.LogLine::text).toList(), logged -> ((List<?>) logged).isEmpty());
            return this;
        }

        public PageAssert hasQueryParameter(String name, String value) {
            check(run, () -> Phrase.template("see", "see query parameter “" + name + "” with {value}", null,
                            value == null ? List.of() : List.of(value)), () -> "query parameter “" + name + "”", "to be", value,
                    () -> run.session().queryParameter(name).orElse(null), v -> Objects.equals(v, value));
            return this;
        }
    }

    // ---------------------------------------------------------------- wrappers

    public abstract static class WrapperAssert<SELF extends WrapperAssert<SELF>> {
        final UnifiedWrapper wrapper;
        final LazyElement element;
        final TestRun run;

        WrapperAssert(UnifiedWrapper wrapper) {
            this.wrapper = wrapper;
            this.element = Wrappers.lazyElementOf(wrapper);
            this.run = element.run();
        }

        @SuppressWarnings("unchecked")
        SELF self() { return (SELF) this; }

        SELF check(String expectation, Object expected, Supplier<?> actual, Predicate<Object> ok) {
            UnifiedAssertions.checkTarget(run, () -> StepTarget.of(element), element.describe(), expectation, expected,
                    actual, ok);
            return self();
        }

        /** At least one component matches the lookup (for a Wrapper from {@code all()}: one at its position). */
        public SELF exists() {
            int needed = Math.max(element.index(), 0) + 1;
            return check("to exist", null, () -> matches(element.countNow()), v -> count(v) >= needed);
        }

        /** No component matches the lookup, e.g. after it was removed. */
        public SELF doesNotExist() {
            int needed = Math.max(element.index(), 0) + 1;
            return check("not to exist", null, () -> matches(element.countNow()), v -> count(v) < needed);
        }

        /** Exactly this many components match the lookup. */
        public SELF hasCount(int expected) {
            return check("to have count", expected, element::countNow, v -> Objects.equals(v, expected));
        }

        public SELF isVisible() { return check("to be visible", null, wrapper::isVisible, Boolean.TRUE::equals); }
        public SELF isHidden() { return check("to be hidden", null, wrapper::isVisible, Boolean.FALSE::equals); }
        public SELF isEnabled() { return check("to be enabled", null, wrapper::isEnabled, Boolean.TRUE::equals); }
        public SELF isDisabled() { return check("to be disabled", null, wrapper::isEnabled, Boolean.FALSE::equals); }

        /** The element's text ({@link com.vaadin.testing.unified.UnifiedElement#getText()}), exactly. */
        public SELF hasText(String expected) {
            return check("to have text", expected, element::getText, v -> Objects.equals(v, expected));
        }

        public SELF hasTextContaining(String part) {
            return check("to have text containing", part, element::getText,
                    v -> v != null && v.toString().contains(part));
        }

        public SELF hasProperty(String name, Object value) {
            String expected = String.valueOf(value);
            return check("to have property " + name, expected, () -> element.getProperty(name), expected::equals);
        }

        /** Needs {@code @RequiresBrowser}. */
        public SELF hasCssValue(String property, String value) {
            run.requireBrowser("hasCssValue");
            return check("to have CSS " + property, value, () -> element.getCssValue(property),
                    value::equals);
        }
    }

    public static final class GenericAssert extends WrapperAssert<GenericAssert> {
        GenericAssert(UnifiedWrapper wrapper) { super(wrapper); }
    }

    /** Label and validation checks shared by input fields. */
    public abstract static class FieldAssert<SELF extends FieldAssert<SELF>> extends WrapperAssert<SELF> {
        final UnifiedField field;

        FieldAssert(UnifiedField field) { super(field); this.field = field; }

        public SELF isValid() { return check("to be valid", null, field::isInvalid, Boolean.FALSE::equals); }
        public SELF isInvalid() { return check("to be invalid", null, field::isInvalid, Boolean.TRUE::equals); }

        public SELF hasErrorMessage(String expected) {
            return check("to have error message", expected, field::getErrorMessage, v -> Objects.equals(v, expected));
        }

        public SELF isRequired() { return check("to be required", null, field::isRequired, Boolean.TRUE::equals); }

        public SELF hasLabel(String expected) {
            return check("to have label", expected, field::getLabel, v -> Objects.equals(v, expected));
        }
    }

    public static final class TextFieldAssert extends FieldAssert<TextFieldAssert> {
        private final UnifiedTextField textField;

        TextFieldAssert(UnifiedTextField field) { super(field); this.textField = field; }

        public TextFieldAssert hasValue(String expected) {
            return check("to have value", expected, textField::getValue, v -> Objects.equals(v, expected));
        }

        public TextFieldAssert isEmpty() {
            return check("to be empty", null, textField::getValue, v -> v == null || v.toString().isEmpty());
        }
    }

    public static final class NumberFieldAssert extends FieldAssert<NumberFieldAssert> {
        private final UnifiedNumberField numberField;

        NumberFieldAssert(UnifiedNumberField field) { super(field); this.numberField = field; }

        public NumberFieldAssert hasValue(double expected) {
            return check("to have value", expected, numberField::getValue,
                    v -> v != null && Double.compare((Double) v, expected) == 0);
        }

        public NumberFieldAssert isEmpty() {
            return check("to be empty", null, numberField::getValue, Objects::isNull);
        }
    }

    public static final class DatePickerAssert extends FieldAssert<DatePickerAssert> {
        private final UnifiedDatePicker datePicker;

        DatePickerAssert(UnifiedDatePicker field) { super(field); this.datePicker = field; }

        public DatePickerAssert hasValue(LocalDate expected) {
            return check("to have value", expected, datePicker::getValue, v -> Objects.equals(v, expected));
        }

        public DatePickerAssert isEmpty() {
            return check("to be empty", null, datePicker::getValue, Objects::isNull);
        }
    }

    /** A ComboBox or RadioButtonGroup. */
    public static final class SingleSelectAssert extends FieldAssert<SingleSelectAssert> {
        private final Supplier<String> selected;
        private final Supplier<List<String>> items;

        SingleSelectAssert(UnifiedField field, Supplier<String> selected, Supplier<List<String>> items) {
            super(field);
            this.selected = selected;
            this.items = items;
        }

        public SingleSelectAssert hasSelectedItem(String expected) {
            return check("to have selected item", expected, selected, v -> Objects.equals(v, expected));
        }

        public SingleSelectAssert hasNoSelectedItem() {
            return check("to have no selected item", null, selected, Objects::isNull);
        }

        /** Exactly these items, in this order. */
        public SingleSelectAssert hasItems(String... expected) {
            List<String> list = List.of(expected);
            return check("to have items", list, items, list::equals);
        }
    }

    public static final class MultiSelectAssert extends FieldAssert<MultiSelectAssert> {
        private final UnifiedMultiSelectComboBox multi;

        MultiSelectAssert(UnifiedMultiSelectComboBox field) { super(field); this.multi = field; }

        /** Exactly these items are selected, in any order. */
        @SuppressWarnings("unchecked")
        public MultiSelectAssert hasSelectedItems(String... expected) {
            // in the test's order: the User Step reads the same in every run, which Set.of doesn't promise
            java.util.Set<String> set = new java.util.LinkedHashSet<>(List.of(expected));
            return check("to have selected items", set, multi::getSelectedItems,
                    v -> v != null && ((List<String>) v).size() == set.size() && set.containsAll((List<String>) v));
        }

        public MultiSelectAssert hasNoSelectedItems() {
            return check("to have no selected items", List.of(), multi::getSelectedItems, v -> ((List<?>) v).isEmpty());
        }

        /** Exactly these items, in this order. */
        public MultiSelectAssert hasItems(String... expected) {
            List<String> list = List.of(expected);
            return check("to have items", list, multi::getItems, list::equals);
        }
    }

    public static final class TabSheetAssert extends WrapperAssert<TabSheetAssert> {
        private final UnifiedTabSheet tabSheet;

        TabSheetAssert(UnifiedTabSheet tabSheet) { super(tabSheet); this.tabSheet = tabSheet; }

        public TabSheetAssert hasSelectedTab(String expected) {
            return check("to have selected tab", expected, tabSheet::getSelectedTab, v -> Objects.equals(v, expected));
        }

        /** Exactly these tabs, in this order. */
        public TabSheetAssert hasTabs(String... expected) {
            List<String> list = List.of(expected);
            return check("to have tabs", list, tabSheet::getTabs, list::equals);
        }
    }

    public static final class CheckboxAssert extends WrapperAssert<CheckboxAssert> {
        private final UnifiedCheckbox checkbox;

        CheckboxAssert(UnifiedCheckbox checkbox) { super(checkbox); this.checkbox = checkbox; }

        public CheckboxAssert isChecked() { return check("to be checked", null, checkbox::isChecked, Boolean.TRUE::equals); }
        public CheckboxAssert isNotChecked() { return check("not to be checked", null, checkbox::isChecked, Boolean.FALSE::equals); }

        public CheckboxAssert isIndeterminate() {
            return check("to be indeterminate", null, checkbox::isIndeterminate, Boolean.TRUE::equals);
        }

        public CheckboxAssert hasLabel(String expected) {
            return check("to have label", expected, checkbox::getLabel, v -> Objects.equals(v, expected));
        }
    }

    public static final class ButtonAssert extends WrapperAssert<ButtonAssert> {
        private final UnifiedButton button;

        ButtonAssert(UnifiedButton button) { super(button); this.button = button; }

        @Override
        public ButtonAssert hasText(String expected) {
            return check("to have text", expected, button::getText, v -> Objects.equals(v, expected));
        }
    }

    public static final class DialogAssert extends WrapperAssert<DialogAssert> {
        private final UnifiedDialog dialog;

        DialogAssert(UnifiedDialog dialog) { super(dialog); this.dialog = dialog; }

        public DialogAssert isOpen() {
            return check("to be open", null, dialog::isOpen, Boolean.TRUE::equals);
        }

        /** Passes when the dialog is closed or no longer on the page (Flow removes closed dialogs). */
        public DialogAssert isClosed() {
            return check("to be closed", null,
                    () -> element.tryConcrete().map(e -> Boolean.TRUE.equals(e.getPropertyBoolean("opened"))).orElse(false),
                    Boolean.FALSE::equals);
        }

        public DialogAssert hasHeaderTitle(String expected) {
            return check("to have header title", expected, dialog::getHeaderTitle, v -> java.util.Objects.equals(v, expected));
        }
    }

    public static final class GridAssert extends WrapperAssert<GridAssert> {
        private final UnifiedGrid grid;

        GridAssert(UnifiedGrid grid) { super(grid); this.grid = grid; }

        public GridAssert hasRowCount(int expected) {
            return check("to have row count", expected, grid::getRowCount, v -> Objects.equals(v, expected));
        }

        public GridAssert isEmpty() {
            return check("to be empty", null, grid::getRowCount, v -> Objects.equals(v, 0));
        }
    }

    public static final class GridRowAssert {
        private final UnifiedGrid.UnifiedGridRow row;
        private final TestRun run;

        GridRowAssert(UnifiedGrid.UnifiedGridRow row) { this.row = row; this.run = GridSupport.runOf(row.grid()); }

        private GridRowAssert check(String expectation, Supplier<?> actual, Predicate<Object> ok) {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), row.toString(), expectation, null, actual,
                    ok);
            return this;
        }

        /** At least one row matches. */
        public GridRowAssert exists() {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), row.toString(), "to exist", null,
                    () -> matches(GridSupport.countNow(row)), v -> count(v) > 0);
            return this;
        }

        /** No row matches, e.g. after it was deleted. */
        public GridRowAssert doesNotExist() {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), row.toString(), "not to exist", null,
                    () -> matches(GridSupport.countNow(row)), v -> count(v) == 0);
            return this;
        }

        /** Exactly this many rows match. */
        public GridRowAssert hasCount(int expected) {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), row.toString(), "to have count", expected,
                    () -> GridSupport.countNow(row), v -> Objects.equals(v, expected));
            return this;
        }

        public GridRowAssert isSelected() { return check("to be selected", row::isSelected, Boolean.TRUE::equals); }
        public GridRowAssert isNotSelected() { return check("not to be selected", row::isSelected, Boolean.FALSE::equals); }
        public GridRowAssert hasDetailsOpen() { return check("to have details open", row::isDetailsOpen, Boolean.TRUE::equals); }
        public GridRowAssert hasDetailsClosed() { return check("to have details closed", row::isDetailsOpen, Boolean.FALSE::equals); }

        public GridRowAssert hasCellText(String columnHeader, String expected) {
            UnifiedAssertions.check(run,
                    () -> Phrase.of("see", GridSupport.targetOf(row), List.of(expected)).withValueLabel("with " + columnHeader),
                    () -> "cell “" + columnHeader + "” of " + GridSupport.targetOf(row).renderWithScope(), "to have text", expected,
                    () -> row.getCellText(columnHeader), v -> Objects.equals(v, expected));
            return this;
        }
    }

    public static final class GridColumnAssert {
        private final UnifiedGrid.UnifiedGridColumn column;
        private final TestRun run;

        GridColumnAssert(UnifiedGrid.UnifiedGridColumn column) {
            this.column = column;
            this.run = GridSupport.runOf(column.grid());
        }

        public GridColumnAssert isSortedAscending() {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(column), column.toString(), "to be sorted",
                    "ASCENDING", column::getSortDirection,
                    v -> v == com.vaadin.flow.data.provider.SortDirection.ASCENDING);
            return this;
        }

        public GridColumnAssert isSortedDescending() {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(column), column.toString(), "to be sorted",
                    "DESCENDING", column::getSortDirection,
                    v -> v == com.vaadin.flow.data.provider.SortDirection.DESCENDING);
            return this;
        }

        public GridColumnAssert hasCellTexts(String... expected) {
            List<String> list = List.of(expected);
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(column), column.toString(), "to have cell texts",
                    list, column::getCellTexts, v -> Objects.equals(v, list));
            return this;
        }
    }

    public static final class NotificationAssert extends WrapperAssert<NotificationAssert> {
        private final UnifiedNotification notification;

        NotificationAssert(UnifiedNotification notification) { super(notification); this.notification = notification; }

        public NotificationAssert isOpen() {
            return check("to be open", null, notification::isOpen, Boolean.TRUE::equals);
        }

        @Override
        public NotificationAssert hasText(String expected) {
            return check("to have text", expected, notification::getText, v -> Objects.equals(v, expected));
        }

        @Override
        public NotificationAssert hasTextContaining(String part) {
            return check("to have text containing", part, notification::getText, v -> v != null && v.toString().contains(part));
        }
    }

    public static final class TextAreaAssert extends FieldAssert<TextAreaAssert> {
        private final com.vaadin.testing.unified.component.UnifiedTextArea textArea;

        TextAreaAssert(com.vaadin.testing.unified.component.UnifiedTextArea field) { super(field); this.textArea = field; }

        public TextAreaAssert hasValue(String expected) {
            return check("to have value", expected, textArea::getValue, v -> Objects.equals(v, expected));
        }

        public TextAreaAssert isEmpty() {
            return check("to be empty", null, textArea::getValue, v -> v == null || v.toString().isEmpty());
        }
    }

    public static final class IntegerFieldAssert extends FieldAssert<IntegerFieldAssert> {
        private final com.vaadin.testing.unified.component.UnifiedIntegerField integerField;

        IntegerFieldAssert(com.vaadin.testing.unified.component.UnifiedIntegerField field) { super(field); this.integerField = field; }

        public IntegerFieldAssert hasValue(int expected) {
            return check("to have value", expected, integerField::getValue, v -> Objects.equals(v, expected));
        }

        public IntegerFieldAssert isEmpty() {
            return check("to be empty", null, integerField::getValue, Objects::isNull);
        }
    }

    public static final class BigDecimalFieldAssert extends FieldAssert<BigDecimalFieldAssert> {
        private final com.vaadin.testing.unified.component.UnifiedBigDecimalField decimalField;

        BigDecimalFieldAssert(com.vaadin.testing.unified.component.UnifiedBigDecimalField field) { super(field); this.decimalField = field; }

        /** Numerically equal: {@code 54.2} and {@code 54.20} match. */
        public BigDecimalFieldAssert hasValue(java.math.BigDecimal expected) {
            return check("to have value", expected, decimalField::getValue,
                    v -> v != null && ((java.math.BigDecimal) v).compareTo(expected) == 0);
        }

        public BigDecimalFieldAssert isEmpty() {
            return check("to be empty", null, decimalField::getValue, Objects::isNull);
        }
    }

    public static final class DateTimePickerAssert extends FieldAssert<DateTimePickerAssert> {
        private final com.vaadin.testing.unified.component.UnifiedDateTimePicker picker;

        DateTimePickerAssert(com.vaadin.testing.unified.component.UnifiedDateTimePicker field) { super(field); this.picker = field; }

        public DateTimePickerAssert hasValue(java.time.LocalDateTime expected) {
            return check("to have value", expected, picker::getValue, v -> Objects.equals(v, expected));
        }

        public DateTimePickerAssert isEmpty() {
            return check("to be empty", null, picker::getValue, Objects::isNull);
        }
    }

    public static final class TabsAssert extends WrapperAssert<TabsAssert> {
        private final com.vaadin.testing.unified.component.UnifiedTabs tabs;

        TabsAssert(com.vaadin.testing.unified.component.UnifiedTabs tabs) { super(tabs); this.tabs = tabs; }

        public TabsAssert hasSelectedTab(String expected) {
            return check("to have selected tab", expected, tabs::getSelectedTab, v -> Objects.equals(v, expected));
        }

        /** Exactly these tabs, in this order. */
        public TabsAssert hasTabs(String... expected) {
            List<String> list = List.of(expected);
            return check("to have tabs", list, tabs::getTabs, list::equals);
        }
    }

    public static final class DetailsAssert extends WrapperAssert<DetailsAssert> {
        private final com.vaadin.testing.unified.component.UnifiedDetails details;

        DetailsAssert(com.vaadin.testing.unified.component.UnifiedDetails details) { super(details); this.details = details; }

        public DetailsAssert isOpen() { return check("to be open", null, details::isOpen, Boolean.TRUE::equals); }
        public DetailsAssert isClosed() { return check("to be closed", null, details::isOpen, Boolean.FALSE::equals); }

        public DetailsAssert hasSummaryText(String expected) {
            return check("to have summary text", expected, details::getSummaryText, v -> Objects.equals(v, expected));
        }
    }
}
