package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/**
 * Which Wrapper replaces which TestBench element ({@code docs/spec/appendix-mapping.md}), and the TestBench
 * calls the Wrapper has with the same name and meaning. A lookup is converted only when every call made on it
 * is one of these; otherwise it stays TestBench, behind the Escape Hatch.
 */
final class Wrappers {

    /** A Wrapper and the TestBench calls it shares: query filters, and methods on the element found. */
    record Wrapper(String fqn, Set<String> filters, Set<String> methods, Map<String, String> renames,
            Conversion conversion) {

        Wrapper(String fqn, Set<String> filters, Set<String> methods) {
            this(fqn, filters, methods, Map.of(), (call, cursor) -> false);
        }

        Wrapper(String fqn, Set<String> filters, Set<String> methods, Conversion conversion) {
            this(fqn, filters, methods, Map.of(), conversion);
        }

        Wrapper(String fqn, Set<String> filters, Set<String> methods, Map<String, String> renames) {
            this(fqn, filters, methods, renames, (call, cursor) -> false);
        }

        /** Whether the Wrapper has the method: same name, or a TestBench name it renames, or the new name. */
        boolean has(String method) {
            return methods.contains(method) || renames.containsKey(method) || renames.containsValue(method);
        }

        /** Whether a call on the element is one the Wrapper has, by name or through a recipe's conversion. */
        boolean supports(J.MethodInvocation call, Cursor callCursor) {
            return has(call.getSimpleName()) || conversion.converts(call, callCursor);
        }

        String simpleName() {
            return fqn.substring(fqn.lastIndexOf('.') + 1);
        }
    }

    /** A TestBench call a recipe converts to a differently named Wrapper call, such as {@code getCell(..)}. */
    interface Conversion {
        boolean converts(J.MethodInvocation call, Cursor callCursor);
    }

    /** Button filters TestBench has and the Unified API spells withText (ADR 0001). */
    static final Map<String, String> BUTTON_FILTER_RENAMES = Map.of("withLabel", "withText", "withCaption", "withText");
    static final String BUTTON_ELEMENT = "com.vaadin.flow.component.button.testbench.ButtonElement";

    /**
     * TestBench query terminals; the conversion drops them, as the Wrapper is the lookup (ADR 0007), and turns
     * {@code id("x")} into the filter {@code withId("x")}.
     */
    static final Set<String> TERMINALS = Set.of("single", "first", "waitForFirst", "id");

    static final String UNIFIED_TEMPLATE = "com.vaadin.testing.unified.component.UnifiedTemplate";

    private static final Set<String> COMMON_FILTERS = Set.of("withId", "withClassName", "withTheme");

    private static final Map<String, String> SINGLE_SELECT_RENAMES =
            Map.of("selectByText", "selectItem", "getSelectedText", "getSelectedItem", "getOptions", "getItems");

    private static final Map<String, Wrapper> BY_ELEMENT = Map.ofEntries(
            Map.entry("com.vaadin.flow.component.textfield.testbench.TextFieldElement", new Wrapper("com.vaadin.testing.unified.component.UnifiedTextField",
                    with(COMMON_FILTERS, "withLabel"),
                    Set.of("setValue", "getValue", "clear", "getLabel", "getPlaceholder", "getHelperText",
                            "getErrorMessage", "isEnabled"))),
            Map.entry("com.vaadin.flow.component.button.testbench.ButtonElement", new Wrapper("com.vaadin.testing.unified.component.UnifiedButton",
                    // withLabel and withCaption become withText (ADR 0001), see FILTER_RENAMES
                    with(COMMON_FILTERS, "withText", "withLabel", "withCaption"),
                    Set.of("click", "getText", "isEnabled"))),
            // a RouterLink or an Anchor: TestBench finds both as AnchorElement (<a>), as UnifiedLink does
            Map.entry("com.vaadin.flow.component.html.testbench.AnchorElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedLink",
                    with(COMMON_FILTERS, "withText"),
                    Set.of("click", "getText", "isEnabled"))),
            Map.entry("com.vaadin.flow.component.notification.testbench.NotificationElement", new Wrapper("com.vaadin.testing.unified.component.UnifiedNotification",
                    with(COMMON_FILTERS, "withText"),
                    Set.of("getText", "isOpen"))),
            Map.entry("com.vaadin.flow.component.checkbox.testbench.CheckboxElement", new Wrapper("com.vaadin.testing.unified.component.UnifiedCheckbox",
                    with(COMMON_FILTERS, "withLabel"),
                    Set.of("setChecked", "isChecked", "getLabel", "isEnabled"))),
            Map.entry("com.vaadin.flow.component.combobox.testbench.ComboBoxElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedComboBox",
                    with(COMMON_FILTERS, "withLabel", "withPlaceholder"),
                    Set.of("clear", "getLabel", "getPlaceholder", "isEnabled"),
                    SINGLE_SELECT_RENAMES)),
            Map.entry("com.vaadin.flow.component.radiobutton.testbench.RadioButtonGroupElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedRadioButtonGroup",
                    with(COMMON_FILTERS, "withLabel"),
                    Set.of("getLabel", "isEnabled"),
                    SINGLE_SELECT_RENAMES)),
            Map.entry("com.vaadin.flow.component.combobox.testbench.MultiSelectComboBoxElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox",
                    with(COMMON_FILTERS, "withLabel", "withPlaceholder"),
                    Set.of("getLabel", "getPlaceholder", "isEnabled"),
                    Map.of("selectByText", "selectItems", "deselectByText", "deselectItems", "deselectAll", "clear",
                            "getSelectedTexts", "getSelectedItems", "getOptions", "getItems"))),
            Map.entry("com.vaadin.flow.component.datepicker.testbench.DatePickerElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedDatePicker",
                    with(COMMON_FILTERS, "withLabel", "withPlaceholder"),
                    Set.of("clear", "getLabel", "getPlaceholder", "getHelperText", "getErrorMessage", "isEnabled"),
                    Map.of("setDate", "setValue", "getDate", "getValue", "setInputValue", "typeText"))),
            Map.entry("com.vaadin.flow.component.textfield.testbench.NumberFieldElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedNumberField",
                    with(COMMON_FILTERS, "withLabel", "withPlaceholder"),
                    Set.of("clear", "getLabel", "getPlaceholder", "getHelperText", "getErrorMessage", "isEnabled"),
                    NumberFields::converts)),
            Map.entry("com.vaadin.flow.component.dialog.testbench.DialogElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedDialog",
                    COMMON_FILTERS,
                    // find is what a $(..) inside the dialog becomes
                    Set.of("isOpen", "getHeaderTitle", "find"),
                    Chains::scopedLookupConverts)),
            Map.entry("com.vaadin.flow.component.tabs.testbench.TabSheetElement", new Wrapper(
                    TabSheets.TAB_SHEET,
                    COMMON_FILTERS,
                    // the Unified names TabSheets and the renames write
                    Set.of("selectTab", "getSelectedTab", "isEnabled"),
                    Map.of("getTabLabels", "getTabs", "getContent", "content"),
                    TabSheets::converts)),
            Map.entry("com.vaadin.flow.component.grid.testbench.GridElement", new Wrapper("com.vaadin.testing.unified.component.UnifiedGrid",
                    COMMON_FILTERS,
                    // row is what GridCells writes for getCell(..)
                    Set.of("getRowCount", "row"),
                    GridCells::converts)),
            Map.entry("com.vaadin.flow.component.messages.testbench.MessageInputElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedMessageInput",
                    COMMON_FILTERS,
                    Set.of("isEnabled"),
                    Map.of("submit", "send"))),
            Map.entry("com.vaadin.flow.component.messages.testbench.MessageListElement", new Wrapper(
                    "com.vaadin.testing.unified.component.UnifiedMessageList",
                    COMMON_FILTERS,
                    // getMessageCount is what TestBenchRenamesToUnified writes for getMessageElements().size()
                    Set.of("getMessageCount"),
                    Wrappers::isMessageCount)));

    private Wrappers() {
    }

    /**
     * Whether a converted {@code withClassName(..)} call needs the Unified {@code withClassNames(..)}: TestBench's
     * and Browserless's take several class names (varargs), the Unified one takes one, as Flow's
     * {@code addClassName} does, and rejects a name with spaces (#259). So: two names or more, an array, or a
     * single literal with a space.
     */
    static boolean needsClassNames(J.MethodInvocation call) {
        if (!call.getSimpleName().equals("withClassName")) {
            return false;
        }
        List<Expression> arguments = call.getArguments().stream().filter(a -> !(a instanceof J.Empty)).toList();
        if (arguments.size() > 1) {
            return true;
        }
        if (arguments.size() != 1) {
            return false;
        }
        Expression argument = arguments.get(0);
        return argument.getType() instanceof JavaType.Array
                || argument instanceof J.Literal literal && literal.getValue() instanceof String s
                        && s.strip().matches("(?s).*\\s.*");
    }

    /** The call, and the calls of the chain before it, with {@code withClassName} renamed where it needs to be. */
    static J.MethodInvocation withClassNames(J.MethodInvocation call) {
        J.MethodInvocation m = call;
        if (m.getSelect() instanceof J.MethodInvocation before) {
            m = m.withSelect(withClassNames(before));
        }
        return needsClassNames(m) ? m.withName(m.getName().withSimpleName("withClassNames")) : m;
    }

    /** {@code list.getMessageElements().size()}: the message count, {@code getMessageCount()}. */
    static boolean isMessageCount(J.MethodInvocation call, Cursor callCursor) {
        return call.getSimpleName().equals("getMessageElements") && call.getArguments().stream().allMatch(a -> a instanceof J.Empty)
                && callCursor.getParentTreeCursor().getValue() instanceof J.MethodInvocation size
                && size.getSimpleName().equals("size") && size.getSelect() == call;
    }

    /**
     * What a Template's element converts to (spec 04, "Templates"): {@code findTemplate(SearchBar.class)}, a Scope
     * whose {@code find(..)} is what a {@code $(..)} on the element becomes.
     */
    static final Wrapper TEMPLATE = new Wrapper(UNIFIED_TEMPLATE, COMMON_FILTERS, Set.of("find"),
            Chains::scopedLookupConverts);

    /**
     * The Wrapper for the element, or {@code null} when it has none yet: {@link #TEMPLATE} for a page object whose
     * {@code @Element} tag is a {@code LitTemplate}'s, while a visit that reads them runs ({@link LitTemplates}).
     */
    static Wrapper forElement(String elementFqn) {
        Wrapper wrapper = BY_ELEMENT.get(elementFqn);
        return wrapper != null ? wrapper : LitTemplates.ofElement(elementFqn) != null ? TEMPLATE : null;
    }

    /** The {@code LitTemplate} class a {@code $(..)} finds the element of, or {@code null}. */
    static String templateOf(J.MethodInvocation dollar) {
        if (!dollar.getSimpleName().equals("$") || dollar.getArguments().size() != 1) {
            return null;
        }
        Expression arg = dollar.getArguments().get(0);
        if (arg instanceof J.Literal literal && literal.getValue() instanceof String tag) {
            return LitTemplates.ofTag(tag);
        }
        return arg instanceof J.FieldAccess classLiteral
                && classLiteral.getTarget().getType() instanceof JavaType.FullyQualified element
                ? LitTemplates.ofElement(element.getFullyQualifiedName()) : null;
    }

    /**
     * The lookup without its terminal, as the Wrapper is the lookup (ADR 0007): {@code id("x")} stays as the filter
     * {@code withId("x")}.
     */
    static J.MethodInvocation withoutTerminal(J.MethodInvocation terminal, J.MethodInvocation lookup) {
        return terminal.getSimpleName().equals("id")
                ? terminal.withSelect(lookup).withName(terminal.getName().withSimpleName("withId"))
                : lookup.withPrefix(terminal.getPrefix());
    }

    /**
     * The Wrapper an element expression converts to, when it is a lookup the Wrapper can express:
     * {@code $(XElement.class)}, then only the Wrapper's filters, then a terminal. Else {@code null}.
     */
    static Wrapper ofLookup(Expression element) {
        if (!(element instanceof J.MethodInvocation terminal) || !TERMINALS.contains(terminal.getSimpleName())) {
            return null;
        }
        Expression e = terminal.getSelect();
        List<String> filters = new ArrayList<>();
        while (e instanceof J.MethodInvocation call && !call.getSimpleName().equals("$")) {
            filters.add(call.getSimpleName());
            e = call.getSelect();
        }
        if (!(e instanceof J.MethodInvocation dollar) || dollar.getSelect() != null) {
            return null;
        }
        if (templateOf(dollar) != null) {
            return TEMPLATE.filters().containsAll(filters) ? TEMPLATE : null;
        }
        if (!(dollar.getArguments().get(0) instanceof J.FieldAccess classLiteral)
                || !(classLiteral.getTarget().getType() instanceof JavaType.FullyQualified type)) {
            return null;
        }
        Wrapper wrapper = forElement(type.getFullyQualifiedName());
        return wrapper != null && wrapper.filters().containsAll(filters) ? wrapper : null;
    }

    private static Set<String> with(Set<String> common, String... more) {
        return java.util.stream.Stream.concat(common.stream(), Set.of(more).stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
