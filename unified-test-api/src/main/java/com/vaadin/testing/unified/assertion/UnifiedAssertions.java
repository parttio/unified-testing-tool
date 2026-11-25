package com.vaadin.testing.unified.assertion;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedLogin;
import com.vaadin.testing.unified.component.UnifiedOverlayDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.internal.grid.GridSupport;
import com.vaadin.testing.unified.internal.messages.MessageSupport;
import com.vaadin.testing.unified.component.UnifiedMessageList;
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
import com.vaadin.testing.unified.internal.LayoutMeasurement;
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
    public static EmailFieldAssert assertThat(com.vaadin.testing.unified.component.UnifiedEmailField field) { return new EmailFieldAssert(field); }
    /** Its values read {@code “••••”} in a failure, as they do in the User Steps. */
    public static PasswordFieldAssert assertThat(com.vaadin.testing.unified.component.UnifiedPasswordField field) { return new PasswordFieldAssert(field); }
    public static IntegerFieldAssert assertThat(com.vaadin.testing.unified.component.UnifiedIntegerField field) { return new IntegerFieldAssert(field); }
    public static BigDecimalFieldAssert assertThat(com.vaadin.testing.unified.component.UnifiedBigDecimalField field) { return new BigDecimalFieldAssert(field); }
    public static DateTimePickerAssert assertThat(com.vaadin.testing.unified.component.UnifiedDateTimePicker field) { return new DateTimePickerAssert(field); }
    public static TabsAssert assertThat(com.vaadin.testing.unified.component.UnifiedTabs tabs) { return new TabsAssert(tabs); }
    public static DetailsAssert assertThat(com.vaadin.testing.unified.component.UnifiedDetails details) { return new DetailsAssert(details); }
    public static MenuBarAssert assertThat(com.vaadin.testing.unified.component.UnifiedMenuBar menuBar) { return new MenuBarAssert(menuBar); }
    public static SideNavAssert assertThat(com.vaadin.testing.unified.component.UnifiedSideNav sideNav) { return new SideNavAssert(sideNav); }
    public static SideNavItemAssert assertThat(com.vaadin.testing.unified.component.UnifiedSideNavItem item) { return new SideNavItemAssert(item); }
    public static ButtonAssert assertThat(UnifiedButton button) { return new ButtonAssert(button); }
    public static LinkAssert assertThat(com.vaadin.testing.unified.component.UnifiedLink link) { return new LinkAssert(link); }
    public static NotificationAssert assertThat(UnifiedNotification notification) { return new NotificationAssert(notification); }
    public static DialogAssert assertThat(UnifiedDialog dialog) { return new DialogAssert(dialog); }
    public static ConfirmDialogAssert assertThat(UnifiedConfirmDialog dialog) { return new ConfirmDialogAssert(dialog); }
    public static LoginAssert assertThat(UnifiedLogin login) { return new LoginAssert(login); }
    public static GridAssert assertThat(UnifiedGrid grid) { return new GridAssert(grid); }
    public static GridRowAssert assertThat(UnifiedGrid.UnifiedGridRow row) { return new GridRowAssert(row); }
    public static GridColumnAssert assertThat(UnifiedGrid.UnifiedGridColumn column) { return new GridColumnAssert(column); }
    public static MessageListAssert assertThat(com.vaadin.testing.unified.component.UnifiedMessageList list) { return new MessageListAssert(list); }
    public static MessageAssert assertThat(com.vaadin.testing.unified.component.UnifiedMessageList.UnifiedMessage message) { return new MessageAssert(message); }
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

    /** A component's Reach as a failure shows it: {@code after scroll}, {@code clipped by div#box}. */
    record ReachShown(LayoutMeasurement.Reach reach) {
        @Override
        public String toString() {
            return reach.reach() + (reach.by() == null ? "" : " by " + reach.by());
        }
    }

    /** Layout Findings as a failure shows them: one after the other, or none. */
    record LayoutFindings(List<LayoutMeasurement.Finding> findings) {
        boolean none() {
            return findings.isEmpty();
        }

        @Override
        public String toString() {
            return findings.isEmpty() ? "no Layout Findings"
                    : String.join("; ", findings.stream().map(LayoutMeasurement.Finding::text).toList());
        }
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

        /**
         * Nothing on the page scrolls sideways at the current Viewport: neither the page itself nor a container in it,
         * such as AppLayout's content area. Grid, Tabs, TabSheet and VirtualList scroll sideways by design and don't
         * count (ADR 0011). Retries until the timeout. Needs {@link com.vaadin.testing.unified.RequiresBrowser}.
         */
        public PageAssert hasNoHorizontalScroll() {
            run.requireBrowser("hasNoHorizontalScroll");
            check(run, () -> Phrases.page("see", "see no sideways scroll"), () -> "the page",
                    "to have no sideways scroll", null,
                    () -> new LayoutFindings(LayoutMeasurement.measure(run, null).findings("scroll")),
                    v -> ((LayoutFindings) v).none());
            return this;
        }

        /**
         * No Layout Finding on the page at the current Viewport: nothing scrolls sideways
         * ({@link #hasNoHorizontalScroll()}), no component is clipped out of reach, and no component a user acts on
         * overlaps another (ADR 0011). Retries until the timeout. Needs
         * {@link com.vaadin.testing.unified.RequiresBrowser}.
         */
        public PageAssert hasNoLayoutFindings() {
            run.requireBrowser("hasNoLayoutFindings");
            check(run, () -> Phrases.page("see", "see no Layout Findings"), () -> "the page",
                    "to have no Layout Findings", null,
                    () -> new LayoutFindings(LayoutMeasurement.measure(run, null).findings()),
                    v -> ((LayoutFindings) v).none());
            return this;
        }

        public PageAssert hasPath(String path) {
            String expected = path.startsWith("/") ? path.substring(1) : path;
            check(run, () -> Phrase.template("see", "see path {value}", null, List.of(expected)), () -> "the page",
                    "to have path", expected, () -> run.session().currentPath(), expected::equals);
            return this;
        }

        /**
         * The page's path matches this regular expression as a whole, for a path the test only partly knows, such
         * as {@code "products/\\d+"} after opening an item whose id it doesn't know. The path is the one
         * {@link #hasPath(String)} compares: no leading {@code /}, no query string. Retries until the timeout.
         *
         * @throws java.util.regex.PatternSyntaxException at once, if {@code regex} is not a regular expression
         */
        public PageAssert hasPathMatching(String regex) {
            return hasPathMatching(java.util.regex.Pattern.compile(regex));
        }

        /**
         * {@link #hasPathMatching(String)} with a compiled pattern, e.g. one with
         * {@link java.util.regex.Pattern#CASE_INSENSITIVE}. The User Step shows the pattern's text.
         */
        public PageAssert hasPathMatching(java.util.regex.Pattern pattern) {
            java.util.regex.Pattern relative = pattern.pattern().startsWith("/")
                    ? java.util.regex.Pattern.compile(pattern.pattern().substring(1), pattern.flags()) : pattern;
            String regex = relative.pattern();
            check(run, () -> Phrase.template("see", "see path matching {value}", null, List.of(regex)),
                    () -> "the page", "to have path matching", regex, () -> run.session().currentPath(),
                    v -> v != null && relative.matcher(withoutLeadingSlash(v.toString())).matches());
            return this;
        }

        private static String withoutLeadingSlash(String path) {
            return path.startsWith("/") ? path.substring(1) : path;
        }

        /**
         * The page's title is exactly this: what the browser shows on the tab, set by the view's
         * {@code @PageTitle}, {@code HasDynamicTitle} or {@code Page.setTitle(..)}. On Browserless, the title the
         * UI last set.
         */
        public PageAssert hasTitle(String title) {
            check(run, () -> Phrase.template("see", "see title {value}", null, List.of(title)), () -> "the page",
                    "to have title", title, () -> run.session().title(), v -> Objects.equals(v, title));
            return this;
        }

        /**
         * Nothing went wrong behind the user's back so far in this test: the application logged no error, and no
         * browser console of the test's windows got one. What was logged is in the failure, and under the User
         * Step during which it was logged in the reports. It doesn't wait: put it after the check that shows the
         * action is over.
         * <p>
         * Which lines count follows {@code vaadin.testing.user-steps.server-log}; with {@code off} only the
         * console is checked. Console errors are known on Testing Tools that can read the console.
         */
        public PageAssert hasNoErrors() {
            // checked once: an error that was logged doesn't go away by waiting
            run.steps().check(() -> Phrases.page("see no", "see no errors"), run.window(), () -> {
                List<String> errors = run.steps().errors().stream()
                        .map(com.vaadin.testing.unified.internal.steps.StepRecorder.LogLine::text).toList();
                if (!errors.isEmpty()) {
                    throw run.failure(com.vaadin.testing.unified.internal.failures.FailureParts.of(
                            "no error in the application's log or a browser's console",
                            errors.size() + ": " + String.join("; ", errors)), null);
                }
                return null;
            });
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

        /**
         * {@link #check} on what a user typed: on a password field its text never reaches the failure, as it never
         * reaches the User Steps, and reads {@code “••••”}, expected and found.
         */
        SELF checkTyped(String expectation, Object expected, Supplier<?> actual, Predicate<Object> ok) {
            if (!secret()) {
                return check(expectation, expected, actual, ok);
            }
            return check(expectation, Hidden.of(expected), () -> Hidden.of(actual.get()), v -> ok.test(Hidden.reveal(v)));
        }

        /** Whether what is typed into the component is hidden in the reports (a password field). */
        private boolean secret() {
            try {
                return StepTarget.of(element).secret();
            } catch (RuntimeException e) {
                return false;
            }
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

        /**
         * On screen at the current Viewport without scrolling, whether or not something covers it (ADR 0011). Retries
         * until the timeout. Needs {@link com.vaadin.testing.unified.RequiresBrowser}.
         */
        public SELF isInViewport() {
            run.requireBrowser("isInViewport");
            return check("to be in the viewport", null, this::reach,
                    v -> Set.of("visible", "behind").contains(((ReachShown) v).reach().reach()));
        }

        /**
         * Not cut off: neither a box with hidden overflow nor the page keeps a user from it, though they may have to
         * scroll to it (ADR 0011). Retries until the timeout. Needs {@link com.vaadin.testing.unified.RequiresBrowser}.
         */
        public SELF isNotClipped() {
            run.requireBrowser("isNotClipped");
            return check("not to be clipped", null, this::reach,
                    v -> !Set.of("clipped", "hidden").contains(((ReachShown) v).reach().reach()));
        }

        private ReachShown reach() {
            return new ReachShown(LayoutMeasurement.measure(run, element.concrete()).subject());
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

    /** A text, as a failure shows a secret one: {@code “••••”}. Empty text stays as it is: it hides nothing. */
    private record Hidden(CharSequence text) {
        static Object of(Object value) {
            return value instanceof CharSequence text && !text.isEmpty() ? new Hidden(text) : value;
        }

        static Object reveal(Object value) {
            return value instanceof Hidden hidden ? hidden.text : value;
        }

        @Override
        public String toString() {
            return "“" + com.vaadin.testing.unified.internal.steps.Phrase.MASK + "”";
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

    /** Value checks shared by the text fields ({@link com.vaadin.testing.unified.component.UnifiedTextInput}). */
    public abstract static class TextValueAssert<SELF extends TextValueAssert<SELF>> extends FieldAssert<SELF> {
        private final com.vaadin.testing.unified.component.UnifiedTextInput input;

        TextValueAssert(com.vaadin.testing.unified.component.UnifiedTextInput field) { super(field); this.input = field; }

        public SELF hasValue(String expected) {
            return checkTyped("to have value", expected, input::getValue, v -> Objects.equals(v, expected));
        }

        public SELF isEmpty() {
            return checkTyped("to be empty", null, input::getValue, v -> v == null || v.toString().isEmpty());
        }
    }

    public static final class TextFieldAssert extends TextValueAssert<TextFieldAssert> {
        TextFieldAssert(UnifiedTextField field) { super(field); }
    }

    public static final class EmailFieldAssert extends TextValueAssert<EmailFieldAssert> {
        EmailFieldAssert(com.vaadin.testing.unified.component.UnifiedEmailField field) { super(field); }
    }

    public static final class PasswordFieldAssert extends TextValueAssert<PasswordFieldAssert> {
        PasswordFieldAssert(com.vaadin.testing.unified.component.UnifiedPasswordField field) { super(field); }
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

    public static final class LinkAssert extends WrapperAssert<LinkAssert> {
        private final com.vaadin.testing.unified.component.UnifiedLink link;

        LinkAssert(com.vaadin.testing.unified.component.UnifiedLink link) { super(link); this.link = link; }

        /** The {@code href} as the application set it ({@code UnifiedLink.getHref()}), exactly. */
        public LinkAssert hasHref(String expected) {
            return check("to have href", expected, link::getHref, v -> Objects.equals(v, expected));
        }
    }

    /** The checks of every {@link UnifiedOverlayDialog}: open or closed, and its header. */
    public abstract static class OverlayDialogAssert<SELF extends OverlayDialogAssert<SELF>> extends WrapperAssert<SELF> {
        private final UnifiedOverlayDialog<?> dialog;

        OverlayDialogAssert(UnifiedOverlayDialog<?> dialog) { super(dialog); this.dialog = dialog; }

        public SELF isOpen() {
            return check("to be open", null, dialog::isOpen, Boolean.TRUE::equals);
        }

        /** Passes when the dialog is closed or no longer on the page (Flow removes closed dialogs). */
        public SELF isClosed() {
            return check("to be closed", null,
                    () -> element.tryConcrete().map(e -> Boolean.TRUE.equals(e.getPropertyBoolean("opened"))).orElse(false),
                    Boolean.FALSE::equals);
        }

        public SELF hasHeaderTitle(String expected) {
            return check("to have header title", expected, dialog::getHeaderTitle, v -> java.util.Objects.equals(v, expected));
        }
    }

    public static final class DialogAssert extends OverlayDialogAssert<DialogAssert> {
        DialogAssert(UnifiedDialog dialog) { super(dialog); }
    }

    /** {@code hasText(..)} and {@code hasTextContaining(..)} check the message ({@link UnifiedConfirmDialog#getText()}). */
    public static final class ConfirmDialogAssert extends OverlayDialogAssert<ConfirmDialogAssert> {
        private final UnifiedConfirmDialog dialog;

        ConfirmDialogAssert(UnifiedConfirmDialog dialog) { super(dialog); this.dialog = dialog; }

        @Override
        public ConfirmDialogAssert hasText(String expected) {
            return check("to have text", expected, dialog::getText, v -> Objects.equals(v, expected));
        }

        @Override
        public ConfirmDialogAssert hasTextContaining(String part) {
            return check("to have text containing", part, dialog::getText, v -> v != null && v.toString().contains(part));
        }
    }

    /** A login overlay or form: open or closed, its error state and its title. */
    public static final class LoginAssert extends WrapperAssert<LoginAssert> {
        private final UnifiedLogin login;

        LoginAssert(UnifiedLogin login) { super(login); this.login = login; }

        /** A {@code LoginOverlay} is open; a {@code LoginForm} is on the page. */
        public LoginAssert isOpen() {
            return check("to be open", null, login::isOpen, Boolean.TRUE::equals);
        }

        /** Passes when the overlay is closed or no longer on the page. */
        public LoginAssert isClosed() {
            return check("to be closed", null,
                    () -> element.tryConcrete().map(e -> Boolean.TRUE.equals(e.getPropertyBoolean("opened"))).orElse(false),
                    Boolean.FALSE::equals);
        }

        /** Shows its error message: the application called {@code setError(true)}, e.g. for a wrong password. */
        public LoginAssert hasError() {
            return check("to have an error", null, login::isError, Boolean.TRUE::equals);
        }

        public LoginAssert hasNoError() {
            return check("not to have an error", null, login::isError, Boolean.FALSE::equals);
        }

        public LoginAssert hasTitle(String expected) {
            return check("to have title", expected, login::getTitle, v -> Objects.equals(v, expected));
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

        /** A column with this header text, among others: for a column the application adds while the test runs. */
        public GridAssert hasColumn(String header) {
            return check("to have column", header, grid::getColumnHeaders, v -> ((List<?>) v).contains(header));
        }

        /** No column with this header text, e.g. after the application removed it. */
        public GridAssert doesNotHaveColumn(String header) {
            return check("not to have column", header, grid::getColumnHeaders, v -> !((List<?>) v).contains(header));
        }

        /** Exactly these column headers, in display order ({@link UnifiedGrid#getColumnHeaders()}). */
        public GridAssert hasColumnHeaders(String... expected) {
            List<String> list = List.of(expected);
            return check("to have column headers", list, grid::getColumnHeaders, list::equals);
        }
    }

    public static final class GridRowAssert {
        private final UnifiedGrid.UnifiedGridRow row;
        private final TestRun run;

        GridRowAssert(UnifiedGrid.UnifiedGridRow row) { this.row = row; this.run = GridSupport.runOf(row.grid()); }

        private GridRowAssert check(String expectation, Supplier<?> actual, Predicate<Object> ok) {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), GridSupport.codeOf(row), expectation, null,
                    actual, ok);
            return this;
        }

        /** At least one row matches. */
        public GridRowAssert exists() {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), GridSupport.codeOf(row),
                    "to exist", null,
                    () -> matches(GridSupport.countNow(row)), v -> count(v) > 0);
            return this;
        }

        /** No row matches, e.g. after it was deleted. */
        public GridRowAssert doesNotExist() {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), GridSupport.codeOf(row),
                    "not to exist", null,
                    () -> matches(GridSupport.countNow(row)), v -> count(v) == 0);
            return this;
        }

        /** Exactly this many rows match. */
        public GridRowAssert hasCount(int expected) {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(row), GridSupport.codeOf(row),
                    "to have count", expected,
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
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(column), GridSupport.codeOf(column),
                    "to be sorted",
                    "ASCENDING", column::getSortDirection,
                    v -> v == com.vaadin.flow.data.provider.SortDirection.ASCENDING);
            return this;
        }

        public GridColumnAssert isSortedDescending() {
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(column), GridSupport.codeOf(column),
                    "to be sorted",
                    "DESCENDING", column::getSortDirection,
                    v -> v == com.vaadin.flow.data.provider.SortDirection.DESCENDING);
            return this;
        }

        public GridColumnAssert hasCellTexts(String... expected) {
            List<String> list = List.of(expected);
            UnifiedAssertions.checkTarget(run, () -> GridSupport.targetOf(column), GridSupport.codeOf(column),
                    "to have cell texts",
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

    public static final class TextAreaAssert extends TextValueAssert<TextAreaAssert> {
        TextAreaAssert(com.vaadin.testing.unified.component.UnifiedTextArea field) { super(field); }
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

    /** Retrying checks of a side nav's top-level items. */
    public static final class SideNavAssert extends WrapperAssert<SideNavAssert> {
        private final com.vaadin.testing.unified.component.UnifiedSideNav sideNav;

        SideNavAssert(com.vaadin.testing.unified.component.UnifiedSideNav sideNav) { super(sideNav); this.sideNav = sideNav; }

        /** Exactly these top-level items, by their labels, in this order. */
        public SideNavAssert hasItems(String... expected) {
            List<String> list = List.of(expected);
            return check("to have items", list, sideNav::getItems, list::equals);
        }
    }

    /** Retrying checks of a side nav item: the page the user is on, its path, its child items. */
    public static final class SideNavItemAssert extends WrapperAssert<SideNavItemAssert> {
        private final com.vaadin.testing.unified.component.UnifiedSideNavItem item;

        SideNavItemAssert(com.vaadin.testing.unified.component.UnifiedSideNavItem item) { super(item); this.item = item; }

        /** It is the page the user is on ({@code UnifiedSideNavItem.isCurrent()}). */
        public SideNavItemAssert isCurrent() { return check("to be current", null, item::isCurrent, Boolean.TRUE::equals); }
        public SideNavItemAssert isNotCurrent() { return check("not to be current", null, item::isCurrent, Boolean.FALSE::equals); }

        /** Its child items are shown. */
        public SideNavItemAssert isExpanded() { return check("to be expanded", null, item::isExpanded, Boolean.TRUE::equals); }
        public SideNavItemAssert isCollapsed() { return check("to be collapsed", null, item::isExpanded, Boolean.FALSE::equals); }

        /** The path as the application set it ({@code UnifiedSideNavItem.getPath()}), exactly. */
        public SideNavItemAssert hasPath(String expected) {
            return check("to have path", expected, item::getPath, v -> Objects.equals(v, expected));
        }

        /** Exactly these child items, by their labels, in this order. */
        public SideNavItemAssert hasItems(String... expected) {
            List<String> list = List.of(expected);
            return check("to have items", list, item::getItems, list::equals);
        }
    }

    /** Retrying checks of a menu bar's items, named by their Item Names from the top level down. */
    public static final class MenuBarAssert extends WrapperAssert<MenuBarAssert> {
        private final com.vaadin.testing.unified.component.UnifiedMenuBar menuBar;

        MenuBarAssert(com.vaadin.testing.unified.component.UnifiedMenuBar menuBar) { super(menuBar); this.menuBar = menuBar; }

        /** Exactly these top-level items, in this order. */
        public MenuBarAssert hasItems(String... expected) {
            List<String> list = List.of(expected);
            return check("to have items", list, menuBar::getItems, list::equals);
        }

        /**
         * Exactly these items, in this order, in the sub-menu at this path:
         * {@code hasItems(List.of("File", "Export"), "PDF", "CSV")}. Opens the menus along the path in a browser.
         */
        public MenuBarAssert hasItems(List<String> path, String... expected) {
            List<String> list = List.of(expected);
            return check("to have items under " + shown(String.join(" › ", path)), list, () -> itemsUnder(path),
                    list::equals);
        }

        /** The menu offers the item at this path: {@code hasItem("File", "Export", "CSV")}. Opens its menus in a browser. */
        public MenuBarAssert hasItem(String topLevel, String... nested) {
            List<String> path = path(topLevel, nested);
            String item = path.get(path.size() - 1);
            return check("to have item", String.join(" › ", path), () -> itemsUnder(path.subList(0, path.size() - 1)),
                    v -> ((List<?>) v).contains(item));
        }

        /** The user can click the item at this path. Opens the menus along it in a browser. */
        public MenuBarAssert hasItemEnabled(String topLevel, String... nested) {
            return check("to have enabled item", String.join(" › ", path(topLevel, nested)),
                    () -> menuBar.isItemEnabled(topLevel, nested), Boolean.TRUE::equals);
        }

        /** The item at this path is shown but can't be clicked. Opens the menus along it in a browser. */
        public MenuBarAssert hasItemDisabled(String topLevel, String... nested) {
            return check("to have disabled item", String.join(" › ", path(topLevel, nested)),
                    () -> menuBar.isItemEnabled(topLevel, nested), Boolean.FALSE::equals);
        }

        private static List<String> path(String topLevel, String... nested) {
            List<String> path = new java.util.ArrayList<>();
            path.add(topLevel);
            path.addAll(List.of(nested));
            return List.copyOf(path);
        }

        private List<String> itemsUnder(List<String> path) {
            return path.isEmpty() ? menuBar.getItems()
                    : menuBar.getItems(path.get(0), path.subList(1, path.size()).toArray(String[]::new));
        }
    }

    /** Retrying checks of a chat's messages: a reply the application adds later is waited for. */
    public static final class MessageListAssert extends WrapperAssert<MessageListAssert> {
        private final com.vaadin.testing.unified.component.UnifiedMessageList list;

        MessageListAssert(com.vaadin.testing.unified.component.UnifiedMessageList list) { super(list); this.list = list; }

        public MessageListAssert hasMessageCount(int expected) {
            return check("to have message count", expected, list::getMessageCount, v -> Objects.equals(v, expected));
        }

        public MessageListAssert isEmpty() {
            return check("to have no messages", null, list::getMessageCount, v -> Objects.equals(v, 0));
        }

        /** Exactly these message texts, in this order. */
        public MessageListAssert hasMessageTexts(String... expected) {
            List<String> texts = List.of(expected);
            return check("to have messages", texts, list::getMessageTexts, texts::equals);
        }

        /** Some message's text contains this part. */
        public MessageListAssert hasMessageContaining(String part) {
            return check("to have a message containing", part, list::getMessageTexts,
                    v -> ((List<?>) v).stream().anyMatch(text -> text != null && text.toString().contains(part)));
        }
    }

    /**
     * Retrying checks of one message of a chat, or of the messages a lookup matches: a reply the application adds
     * later is waited for.
     */
    public static final class MessageAssert {
        private final com.vaadin.testing.unified.component.UnifiedMessageList.UnifiedMessage message;
        private final TestRun run;

        MessageAssert(com.vaadin.testing.unified.component.UnifiedMessageList.UnifiedMessage message) {
            this.message = message;
            this.run = MessageSupport.runOf(message.list());
        }

        private MessageAssert check(String expectation, Object expected, Supplier<?> actual, Predicate<Object> ok) {
            UnifiedAssertions.checkTarget(run, () -> MessageSupport.targetOf(message), message.toString(), expectation,
                    expected, actual, ok);
            return this;
        }

        /** At least one message matches. */
        public MessageAssert exists() {
            return check("to exist", null, () -> matches(MessageSupport.countNow(message)), v -> count(v) > 0);
        }

        /** No message matches, e.g. a prompt the application must not echo. */
        public MessageAssert doesNotExist() {
            return check("not to exist", null, () -> matches(MessageSupport.countNow(message)), v -> count(v) == 0);
        }

        /** Exactly this many messages match. */
        public MessageAssert hasCount(int expected) {
            return check("to have count", expected, () -> MessageSupport.countNow(message),
                    v -> Objects.equals(v, expected));
        }

        /** The one matching message's text, exactly. */
        public MessageAssert hasText(String expected) {
            return check("to have text", expected,
                    () -> MessageSupport.readNow(message, UnifiedMessageList.Message::text),
                    v -> Objects.equals(v, expected));
        }

        /** The one matching message's text contains this part. */
        public MessageAssert hasTextContaining(String part) {
            return check("to have text containing", part,
                    () -> MessageSupport.readNow(message, UnifiedMessageList.Message::text),
                    v -> v instanceof String text && text.contains(part));
        }

        /** The one matching message is from this user name, exactly. */
        public MessageAssert hasUserName(String expected) {
            return check("to have user name", expected,
                    () -> MessageSupport.readNow(message, UnifiedMessageList.Message::userName),
                    v -> Objects.equals(v, expected));
        }
    }
}
