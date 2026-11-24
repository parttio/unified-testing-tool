package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Which Wrapper replaces which Dramafinder element ({@code docs/spec/appendix-mapping.md}), the Dramafinder lookups
 * it can express, and the element methods and assertions it has (ADR 0006). A lookup is converted only when every
 * call made on what it finds is one of these; otherwise it stays Dramafinder, behind the Escape Hatch.
 */
final class DramafinderWrappers {

    private static final String ELEMENT = "org.vaadin.addons.dramafinder.element.";
    private static final String COMPONENT = "com.vaadin.testing.unified.component.";
    /** The Wrapper of any component: a raw locator's when no other has it, and a chat element's scope. */
    static final String CONTAINER = COMPONENT + "UnifiedContainer";
    static final String QUERIES = "org.vaadin.addons.dramafinder.element.query.ElementQueries";

    /** A Wrapper: its filters, the element methods it has (Dramafinder name to Unified name) and assertions. */
    record Wrapper(String fqn, Set<String> filters, Map<String, String> methods, Map<String, String> assertions) {

        /** The name a declaration uses, {@code UnifiedGrid.UnifiedGridRow} for a nested type. */
        String simpleName() {
            return fqn.substring(fqn.lastIndexOf('.') + 1).replace('$', '.');
        }

        /** The type to import: the outer type of a nested one. */
        String importFqn() {
            int nested = fqn.indexOf('$');
            return nested < 0 ? fqn : fqn.substring(0, nested);
        }

        boolean supports(String method) {
            return methods.containsKey(method) || assertions.containsKey(method);
        }
    }

    /** One filter of a lookup, in Unified terms. */
    record Filter(String name, List<Expression> arguments) {
    }

    /**
     * A Dramafinder lookup the Wrapper can express: {@code page.find(W.class)} and the filters (the Wrapper
     * itself, ADR 0007), then {@code all().get(index)} when {@code index} is set. {@code marker} is the Migration Marker it needs, if any.
     * {@code scope}, when set, is the filter of a container the Wrapper is found in:
     * {@code page.find(UnifiedContainer.class).<scope>.find(W.class)} (#511).
     */
    record Lookup(Wrapper wrapper, List<Filter> filters, Expression index, String marker, Filter scope) {

        Lookup(Wrapper wrapper, List<Filter> filters, Expression index, String marker) {
            this(wrapper, filters, index, marker, null);
        }
    }

    private static final Set<String> COMMON_FILTERS = Set.of("withAccessibleName", "withAccessibleNameContaining",
            "withTestId", "withId", "withClassName");
    private static final Set<String> LABEL_FILTERS = Set.of("withLabel", "withLabelContaining");
    private static final Set<String> TEXT_FILTERS = Set.of("withText", "withTextContaining");

    private static final Map<String, String> VISIBILITY = Map.of("assertVisible", "isVisible", "assertHidden", "isHidden",
            "assertEnabled", "isEnabled", "assertDisabled", "isDisabled");
    private static final Map<String, String> FIELD_ASSERTIONS = with(VISIBILITY, Map.of("assertValue", "hasValue",
            "assertLabel", "hasLabel", "assertValid", "isValid", "assertInvalid", "isInvalid",
            "assertErrorMessage", "hasErrorMessage"));
    private static final Map<String, String> TEXT_FIELD_METHODS = same("setValue", "getValue", "clear", "getLabel",
            "getPlaceholder", "getHelperText", "isEnabled", "isVisible");

    private static final Map<String, Wrapper> BY_ELEMENT = Stream.of(Map.of(
            ELEMENT + "TextFieldElement", new Wrapper(COMPONENT + "UnifiedTextField",
                    union(COMMON_FILTERS, LABEL_FILTERS, Set.of("withPlaceholder")), TEXT_FIELD_METHODS, FIELD_ASSERTIONS),
            ELEMENT + "TextAreaElement", new Wrapper(COMPONENT + "UnifiedTextArea",
                    union(COMMON_FILTERS, LABEL_FILTERS, Set.of("withPlaceholder")), TEXT_FIELD_METHODS, FIELD_ASSERTIONS),
            ELEMENT + "ButtonElement", new Wrapper(COMPONENT + "UnifiedButton", union(COMMON_FILTERS, TEXT_FILTERS),
                    same("click", "getText", "isEnabled", "isVisible"), with(VISIBILITY, Map.of("assertText", "hasText"))),
            ELEMENT + "NotificationElement", new Wrapper(COMPONENT + "UnifiedNotification", union(COMMON_FILTERS, TEXT_FILTERS),
                    same("isOpen"), Map.of("assertOpen", "isOpen", "assertContent", "hasTextContaining")),
            ELEMENT + "CheckboxElement", new Wrapper(COMPONENT + "UnifiedCheckbox", union(COMMON_FILTERS, LABEL_FILTERS),
                    same("check", "uncheck", "setChecked", "isChecked", "getLabel", "getHelperText", "isEnabled", "isVisible"),
                    with(VISIBILITY, Map.of("assertChecked", "isChecked", "assertNotChecked", "isNotChecked",
                            "assertLabel", "hasLabel", "assertIndeterminate", "isIndeterminate"))),
            ELEMENT + "DialogElement", new Wrapper(COMPONENT + "UnifiedDialog", union(COMMON_FILTERS, Set.of("withHeaderTitle")),
                    Map.of("isOpen", "isOpen", "getHeaderText", "getHeaderTitle", "closeWithEscape", "close"),
                    Map.of("assertOpen", "isOpen", "assertClosed", "isClosed", "assertHeaderText", "hasHeaderTitle")),
            ELEMENT + "GridElement", new Wrapper(COMPONENT + "UnifiedGrid", COMMON_FILTERS,
                    Map.of("getTotalRowCount", "getRowCount"), Map.of("assertRowCount", "hasRowCount"))), Map.of(
            // a chat, on Vaadin's AI components (#357)
            ELEMENT + "MessageInputElement", new Wrapper(COMPONENT + "UnifiedMessageInput", COMMON_FILTERS,
                    Map.of("typeAndSubmit", "send", "isEnabled", "isEnabled", "isVisible", "isVisible",
                            "focus", "element().focus"), VISIBILITY),
            ELEMENT + "MessageListElement", new Wrapper(COMPONENT + "UnifiedMessageList", COMMON_FILTERS,
                    same("isVisible"), with(VISIBILITY, Map.of("assertMessageCount", "hasMessageCount", "assertEmpty", "isEmpty")))), Map.of(
            // an email or password field: the same text-value Wrapper, of its own type (#555)
            ELEMENT + "EmailFieldElement", new Wrapper(COMPONENT + "UnifiedEmailField",
                    union(COMMON_FILTERS, LABEL_FILTERS, Set.of("withPlaceholder")), TEXT_FIELD_METHODS, FIELD_ASSERTIONS),
            ELEMENT + "PasswordFieldElement", new Wrapper(COMPONENT + "UnifiedPasswordField",
                    union(COMMON_FILTERS, LABEL_FILTERS, Set.of("withPlaceholder")), TEXT_FIELD_METHODS, FIELD_ASSERTIONS)))
            .flatMap(m -> m.entrySet().stream()).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    /** Elements whose {@code get(page)} finds the first one on the page: the Wrapper finds it, and a second one fails loudly. */
    private static final Set<String> GET_FIRST = Set.of(ELEMENT + "GridElement", ELEMENT + "MessageInputElement",
            ELEMENT + "MessageListElement");

    /**
     * Chat elements, whose {@code get(locator)} finds the first one inside the locator and whose constructor takes
     * the element's own locator: on an id or test id locator, a container scope and a filter (#511).
     */
    private static final Set<String> CHAT = Set.of(ELEMENT + "MessageInputElement", ELEMENT + "MessageListElement");

    /** A grid row: what {@code grid.findRow(index).orElseThrow()} becomes, {@code grid.row(index)}. */
    static final Wrapper ROW = new Wrapper(COMPONENT + "UnifiedGrid$UnifiedGridRow", Set.of(),
            same("select", "deselect", "click", "doubleClick"), Map.of("assertSelected", "isSelected"));

    /** Static factories: the Unified filter each one is. {@code getByLabel} on a button is its accessible name. */
    private static final Map<String, String> FACTORIES = Map.of("getByLabel", "withLabel", "getByLabelContaining",
            "withLabelContaining", "getByText", "withText", "getByTextContaining", "withTextContaining", "getById",
            "withId", "getByTestId", "withTestId", "getByHeaderText", "withHeaderTitle");

    /** Dramafinder 1.x label and text factories, and the Unified filter that matches at least as much. */
    private static final Map<String, String> VERSION_1_FACTORIES = Map.of("getByLabel", "withAccessibleNameContaining",
            "getByText", "withTextContaining");
    /** Dramafinder 1.x substring factories with no substring filter in the Unified API. */
    private static final Set<String> VERSION_1_UNMAPPED = Set.of("getByHeaderText");

    /** Factories that match a substring and take the first match even in Dramafinder 2.x. */
    private static final Set<String> FIRST_MATCH = Set.of(ELEMENT + "NotificationElement#getByText");

    /** Query API filters with the same name and meaning in the Unified API. */
    private static final Set<String> QUERY_FILTERS = union(COMMON_FILTERS, LABEL_FILTERS, TEXT_FILTERS,
            Set.of("withPlaceholder"));

    /**
     * The Wrappers a raw Playwright locator on an id or test id becomes (#356), by the class of the component the
     * application's sources give that id, with the {@code Locator} methods each has (Playwright's name to the
     * Wrapper's). Their {@code assertions} are empty: a Playwright check is {@code assertThat(locator).x()}, in
     * {@link #rawCheck}.
     */
    private static final Map<String, Wrapper> RAW_BY_COMPONENT = Map.of(
            "com.vaadin.flow.component.button.Button", new Wrapper(COMPONENT + "UnifiedButton", COMMON_FILTERS,
                    same("click", "isVisible", "isEnabled"), Map.of()),
            "com.vaadin.flow.component.textfield.TextField", new Wrapper(COMPONENT + "UnifiedTextField", COMMON_FILTERS,
                    with(same("clear", "isVisible", "isEnabled"), Map.of("fill", "setValue", "inputValue", "getValue")), Map.of()),
            "com.vaadin.flow.component.textfield.TextArea", new Wrapper(COMPONENT + "UnifiedTextArea", COMMON_FILTERS,
                    with(same("clear", "isVisible", "isEnabled"), Map.of("fill", "setValue", "inputValue", "getValue")), Map.of()),
            "com.vaadin.flow.component.textfield.EmailField", new Wrapper(COMPONENT + "UnifiedEmailField", COMMON_FILTERS,
                    with(same("clear", "isVisible", "isEnabled"), Map.of("fill", "setValue", "inputValue", "getValue")), Map.of()),
            "com.vaadin.flow.component.textfield.PasswordField", new Wrapper(COMPONENT + "UnifiedPasswordField", COMMON_FILTERS,
                    with(same("clear", "isVisible", "isEnabled"), Map.of("fill", "setValue", "inputValue", "getValue")), Map.of()),
            "com.vaadin.flow.component.checkbox.Checkbox", new Wrapper(COMPONENT + "UnifiedCheckbox", COMMON_FILTERS,
                    same("check", "uncheck", "setChecked", "isChecked", "isVisible", "isEnabled"), Map.of()));

    /** Any component: what a raw locator becomes when no other Wrapper has it. */
    private static final Wrapper RAW_CONTAINER = new Wrapper(CONTAINER, COMMON_FILTERS,
            same("click", "isVisible", "isEnabled"), Map.of());

    /**
     * The Playwright checks, {@code assertThat(locator).x()}, each raw Wrapper has as a retrying assertion of the
     * same name, besides the ones every raw Wrapper has ({@link #rawCheck}). Not {@code isHidden()}: Playwright
     * passes it when nothing matches, a Unified check fails.
     */
    private static final Map<String, Set<String>> RAW_ASSERTIONS = Map.of(
            COMPONENT + "UnifiedButton", Set.of("isVisible", "isEnabled", "isDisabled"),
            COMPONENT + "UnifiedTextField", Set.of("isVisible", "isEnabled", "isDisabled", "hasValue"),
            COMPONENT + "UnifiedTextArea", Set.of("isVisible", "isEnabled", "isDisabled", "hasValue"),
            COMPONENT + "UnifiedEmailField", Set.of("isVisible", "isEnabled", "isDisabled", "hasValue"),
            COMPONENT + "UnifiedPasswordField", Set.of("isVisible", "isEnabled", "isDisabled", "hasValue"),
            COMPONENT + "UnifiedCheckbox", Set.of("isVisible", "isEnabled", "isDisabled", "isChecked"),
            CONTAINER, Set.of("isVisible", "isEnabled", "isDisabled"));

    /** Playwright text checks with a string, and the Unified check of each (#510). */
    private static final Map<String, String> RAW_TEXT_CHECKS = Map.of("containsText", "hasTextContaining",
            "hasText", "hasText");

    /** One class name, as {@code hasClass("x")} or {@code hasClass(Pattern.compile(".*x.*"))} names it. */
    private static final java.util.regex.Pattern CLASS_NAME = java.util.regex.Pattern.compile("-?[_a-zA-Z][\\w-]*");
    private static final java.util.regex.Pattern CONTAINS_CLASS_NAME = java.util.regex.Pattern.compile(
            "\\.\\*(-?[_a-zA-Z][\\w-]*)\\.\\*");

    /**
     * A Playwright check on a raw locator as a Unified one: {@code assertThat(<lookup>.<filter>).<assertion>(..)}.
     * {@code filter}, added to the lookup, is {@code null} when the check needs none.
     */
    record RawCheck(String assertion, List<Expression> arguments, Filter filter) {
    }

    private static final java.util.regex.Pattern ID_SELECTOR = java.util.regex.Pattern.compile("#([A-Za-z][\\w-]*)");
    private static final java.util.regex.Pattern TEST_ID_SELECTOR = java.util.regex.Pattern.compile(
            "\\[data-testid=(?:'([^']*)'|\"([^\"]*)\"|([\\w-]+))]");

    private DramafinderWrappers() {
    }

    /**
     * The known component class a component of the given class is, for a raw locator's Wrapper: the most specific
     * of {@link #RAW_BY_COMPONENT}, else the class itself.
     */
    static String rawComponentOf(JavaType.FullyQualified type) {
        return RAW_BY_COMPONENT.keySet().stream().filter(k -> TypeUtils.isAssignableTo(k, type)).findFirst()
                .orElse(type.getFullyQualifiedName());
    }

    /**
     * The Unified check the Playwright check {@code assertThat(locator).x(..)} or {@code assertThat(locator).not().x(..)}
     * is, on the Wrapper the raw locator became (#356, #510); {@code null} when it has none, as for a text or class
     * {@code Pattern}, or another {@code not()} check.
     */
    static RawCheck rawCheck(Wrapper wrapper, J.MethodInvocation check) {
        J.MethodInvocation assertThat = playwrightAssertThatOf(check);
        Set<String> same = RAW_ASSERTIONS.get(wrapper.fqn());
        if (assertThat == null || same == null) {
            return null;
        }
        String name = check.getSimpleName();
        List<Expression> arguments = check.getArguments().stream().filter(a -> !(a instanceof J.Empty)).toList();
        if (check.getSelect() != assertThat) { // not()
            if (name.equals("isVisible") && arguments.isEmpty()) {
                return new RawCheck("doesNotExist", List.of(), new Filter("visible", List.of()));
            }
            boolean enabled = name.equals("hasAttribute") && arguments.size() == 2
                    && TypeUtils.isString(arguments.get(1).getType())
                    && ("disabled".equals(literal(arguments.get(0)))
                            || "aria-disabled".equals(literal(arguments.get(0))) && "true".equals(literal(arguments.get(1))));
            return enabled ? new RawCheck("isEnabled", List.of(), null) : null;
        }
        if (arguments.isEmpty()) {
            return same.contains(name) && !name.equals("hasValue") ? new RawCheck(name, List.of(), null)
                    : name.equals("isAttached") ? new RawCheck("exists", List.of(), null) : null;
        }
        if (arguments.size() == 1 && TypeUtils.isString(arguments.get(0).getType())) {
            if (RAW_TEXT_CHECKS.containsKey(name)) {
                return new RawCheck(RAW_TEXT_CHECKS.get(name), arguments, null);
            }
            if (name.equals("hasValue") && same.contains(name)) {
                return new RawCheck(name, arguments, null);
            }
        }
        if (name.equals("hasAttribute") && arguments.size() == 2 && "disabled".equals(literal(arguments.get(0)))
                && "".equals(literal(arguments.get(1)))) {
            return new RawCheck("isDisabled", List.of(), null);
        }
        String className = name.equals("hasClass") && arguments.size() == 1 ? className(arguments.get(0)) : null;
        return className == null ? null
                : new RawCheck("exists", List.of(), new Filter("withClassName", List.of(Stubs.literal(className))));
    }

    /** {@code "x"} or {@code Pattern.compile(".*x.*")}: the one class name {@code hasClass(..)} matches, else {@code null}. */
    private static String className(Expression argument) {
        String literal = literal(argument);
        if (literal != null) {
            return CLASS_NAME.matcher(literal).matches() ? literal : null;
        }
        if (argument instanceof J.MethodInvocation compile && compile.getSimpleName().equals("compile")
                && compile.getArguments().size() == 1 && compile.getMethodType() != null
                && TypeUtils.isOfClassType(compile.getMethodType().getDeclaringType(), "java.util.regex.Pattern")
                && literal(compile.getArguments().get(0)) instanceof String regex) {
            java.util.regex.Matcher contains = CONTAINS_CLASS_NAME.matcher(regex);
            return contains.matches() ? contains.group(1) : null;
        }
        return null;
    }

    private static String literal(Expression e) {
        return e instanceof J.Literal l && l.getValue() instanceof String s ? s : null;
    }

    /**
     * The Playwright {@code assertThat(locator)} a check is made on, directly or through {@code not()};
     * {@code null} for any other call.
     */
    static J.MethodInvocation playwrightAssertThatOf(J.MethodInvocation check) {
        Expression select = check.getSelect();
        if (select instanceof J.MethodInvocation not && not.getSimpleName().equals("not")
                && not.getArguments().stream().allMatch(a -> a instanceof J.Empty)) {
            select = not.getSelect();
        }
        return select instanceof J.MethodInvocation assertThat && isPlaywrightAssertThat(assertThat) ? assertThat : null;
    }

    /** {@code PlaywrightAssertions.assertThat(locator)} with one argument. */
    static boolean isPlaywrightAssertThat(J.MethodInvocation call) {
        return call.getSimpleName().equals("assertThat") && call.getArguments().size() == 1 && call.getMethodType() != null
                && TypeUtils.isOfClassType(call.getMethodType().getDeclaringType(),
                        "com.microsoft.playwright.assertions.PlaywrightAssertions");
    }

    /**
     * {@code page.locator("#id")}, {@code page.locator("[data-testid=x]")} or {@code page.getByTestId("x")} on the
     * test's page (#356): {@code page.find(W.class).withId(..)} or {@code withTestId(..)}, the Wrapper of the
     * component the application's sources give that id (read by {@link DramafinderIds}), else
     * {@code UnifiedContainer}, which finds any component, with a Migration Marker.
     */
    private static Lookup ofRawLocator(J.MethodInvocation call, ExecutionContext ctx) {
        Filter idFilter = ctx == null ? null : idFilter(call);
        if (idFilter == null) {
            return null;
        }
        J.Literal l = (J.Literal) call.getArguments().get(0);
        String filter = idFilter.name();
        String value = (String) ((J.Literal) idFilter.arguments().get(0)).getValue();
        String what = filter.equals("withId") ? "id" : "test id";
        String component = DramafinderIds.of(ctx, (filter.equals("withId") ? "id:" : "testid:") + value);
        Wrapper wrapper = component == null || component.equals(DramafinderIds.AMBIGUOUS) ? null
                : RAW_BY_COMPONENT.getOrDefault(component, RAW_CONTAINER);
        String marker = null;
        if (wrapper == null) {
            wrapper = RAW_CONTAINER;
            marker = "was " + call.getSimpleName() + "(" + l.getValueSource() + "): "
                    + (component == null ? "no view in the sources sets this " + what
                            : "the sources set this " + what + " on components of different classes")
                    + "; UnifiedContainer finds any component, use the component's own Wrapper if it has one";
        }
        return new Lookup(wrapper, List.of(new Filter(filter, List.of(Stubs.literal(value)))), null, marker);
    }

    /**
     * {@code withId("x")} or {@code withTestId("x")} for {@code page.locator("#x")}, {@code page.locator("[data-testid=x]")}
     * or {@code page.getByTestId("x")} on the test's page; else {@code null}.
     */
    private static Filter idFilter(Expression e) {
        if (!(e instanceof J.MethodInvocation call) || call.getMethodType() == null || call.getSelect() == null
                || !Dramafinder.isPageField(call.getSelect()) || call.getArguments().size() != 1
                || !(call.getArguments().get(0) instanceof J.Literal l) || !(l.getValue() instanceof String selector)
                || !TypeUtils.isOfClassType(call.getMethodType().getDeclaringType(), Dramafinder.PAGE)) {
            return null;
        }
        java.util.regex.Matcher id = ID_SELECTOR.matcher(selector);
        java.util.regex.Matcher testId = TEST_ID_SELECTOR.matcher(selector);
        String filter;
        String value;
        if (call.getSimpleName().equals("getByTestId")) {
            filter = "withTestId";
            value = selector;
        } else if (!call.getSimpleName().equals("locator")) {
            return null;
        } else if (id.matches()) {
            filter = "withId";
            value = id.group(1);
        } else if (testId.matches()) {
            filter = "withTestId";
            value = testId.group(1) != null ? testId.group(1) : testId.group(2) != null ? testId.group(2) : testId.group(3);
        } else {
            return null;
        }
        return new Filter(filter, List.of(Stubs.literal(value)));
    }

    /**
     * {@code new MessageListElement(page.getByTestId("x"))}, a chat element on its own id or test id locator:
     * {@code page.find(UnifiedMessageList.class).withTestId("x")} (#511); else {@code null}.
     */
    private static Lookup ofConstructor(J.NewClass newClass) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(newClass.getType());
        Filter filter = type == null || !CHAT.contains(type.getFullyQualifiedName()) || newClass.getArguments().size() != 1
                ? null : idFilter(newClass.getArguments().get(0));
        return filter == null ? null : new Lookup(BY_ELEMENT.get(type.getFullyQualifiedName()), List.of(filter), null, null);
    }

    static Wrapper forElement(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? null : BY_ELEMENT.get(fq.getFullyQualifiedName());
    }

    static String elementOf(String wrapperFqn) {
        return BY_ELEMENT.entrySet().stream().filter(e -> e.getValue().fqn().equals(wrapperFqn)).map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    /**
     * The lookup the expression is, when the Wrapper can express it on the test's page; else {@code null}.
     * {@code version2}: the project uses Dramafinder 2.x, whose label and text factories are exact. {@code ctx}
     * holds the ids the application's sources set ({@link DramafinderIds}), for raw Playwright locators.
     */
    static Lookup ofLookup(Expression e, boolean version2, ExecutionContext ctx) {
        if (e instanceof J.NewClass newClass) {
            return ofConstructor(newClass);
        }
        if (!(e instanceof J.MethodInvocation call) || call.getMethodType() == null) {
            return null;
        }
        Lookup factory = ofFactory(call, version2);
        if (factory != null) {
            return factory;
        }
        Lookup raw = ofRawLocator(call, ctx);
        return raw != null ? raw : ofQuery(call);
    }

    /** {@code XElement.getByLabel(page, "Name")} and the other static factories. */
    private static Lookup ofFactory(J.MethodInvocation call, boolean version2) {
        JavaType.FullyQualified declaring = call.getMethodType().getDeclaringType();
        Wrapper wrapper = BY_ELEMENT.get(declaring.getFullyQualifiedName());
        if (wrapper == null || !call.getMethodType().hasFlags(org.openrewrite.java.tree.Flag.Static)
                || call.getArguments().isEmpty()) {
            return null;
        }
        if (!Dramafinder.isPageField(call.getArguments().get(0))) {
            // MessageInputElement.get(page.getByTestId("dock")): the first one inside that component (#511)
            Filter scope = call.getSimpleName().equals("get") && call.getArguments().size() == 1
                    && CHAT.contains(declaring.getFullyQualifiedName()) ? idFilter(call.getArguments().get(0)) : null;
            return scope == null ? null : new Lookup(wrapper, List.of(), null, null, scope);
        }
        String name = call.getSimpleName();
        if (name.equals("get") && call.getArguments().size() == 1 && GET_FIRST.contains(declaring.getFullyQualifiedName())) {
            return new Lookup(wrapper, List.of(), null, null); // the first one; a second one fails loudly
        }
        String filter = name.equals("getByLabel") && wrapper.simpleName().equals("UnifiedButton")
                ? "withAccessibleName" : FACTORIES.get(name);
        if (filter == null || call.getArguments().size() != 2
                || !TypeUtils.isString(call.getArguments().get(1).getType()) || !wrapper.filters().contains(filter)) {
            return null;
        }
        String marker = null;
        if (!version2 && VERSION_1_FACTORIES.containsKey(name)
                && !FIRST_MATCH.contains(declaring.getFullyQualifiedName() + "#" + name)) {
            // 1.x: a case-insensitive substring of the label, aria-label or placeholder, and the first match
            filter = VERSION_1_FACTORIES.get(name);
            if (!wrapper.filters().contains(filter)) {
                return null;
            }
            String exact = name.equals("getByText") ? "withText" : "withLabel";
            marker = "was 1.x " + name + "(..), a case-insensitive substring and first match; tighten it to "
                    + exact + "(..) once the test passes";
        } else if (!version2 && VERSION_1_UNMAPPED.contains(name)) {
            return null; // 1.x matched a substring, and the Unified filter is exact
        } else if (FIRST_MATCH.contains(declaring.getFullyQualifiedName() + "#" + name)) {
            filter = "withTextContaining";
            marker = "was " + name + "(..), a substring and first match; the lookup fails when several components match";
        }
        return new Lookup(wrapper, List.of(new Filter(filter, List.of(call.getArguments().get(1)))), null, marker);
    }

    /** {@code find(page, XElement.class).withLabel("Name").single()} and the other query terminals. */
    private static Lookup ofQuery(J.MethodInvocation terminal) {
        String name = terminal.getSimpleName();
        if (!Set.of("single", "first", "get").contains(name)) {
            return null;
        }
        List<Filter> filters = new ArrayList<>();
        Expression e = terminal.getSelect();
        while (e instanceof J.MethodInvocation call && !isFind(call)) {
            if (!QUERY_FILTERS.contains(call.getSimpleName())) {
                return null;
            }
            filters.add(0, new Filter(call.getSimpleName(), call.getArguments()));
            e = call.getSelect();
        }
        if (!(e instanceof J.MethodInvocation find) || !isFind(find) || find.getArguments().size() != 2
                || !Dramafinder.isPageField(find.getArguments().get(0))
                || !(find.getArguments().get(1) instanceof J.FieldAccess literal)) {
            return null;
        }
        Wrapper wrapper = forElement(literal.getTarget().getType());
        if (wrapper == null || filters.stream().anyMatch(f -> !wrapper.filters().contains(f.name()))) {
            return null;
        }
        return switch (name) {
            case "first" -> new Lookup(wrapper, filters, null, "was first(); the lookup fails when several components match");
            case "get" -> new Lookup(wrapper, filters, terminal.getArguments().get(0), null);
            default -> new Lookup(wrapper, filters, null, null);
        };
    }

    private static boolean isFind(J.MethodInvocation call) {
        return call.getSimpleName().equals("find") && call.getMethodType() != null
                && TypeUtils.isOfClassType(call.getMethodType().getDeclaringType(), QUERIES);
    }

    /** The lookup as template code on {@code #{any(UnifiedPage)}}, and the arguments for its placeholders. */
    static String code(Lookup lookup, List<Object> parameters) {
        StringBuilder code = new StringBuilder("#{any(" + TestBenchSetupToUnified.UNIFIED_PAGE + ")}");
        parameters.add(Stubs.page());
        if (lookup.scope() != null) {
            code.append(".find(UnifiedContainer.class)");
            filter(lookup.scope(), code, parameters);
        }
        code.append(".find(").append(lookup.wrapper().simpleName()).append(".class)");
        for (Filter f : lookup.filters()) {
            filter(f, code, parameters);
        }
        if (lookup.index() != null) {
            code.append(".all().get(#{any(int)})");
            parameters.add(lookup.index());
        }
        return code.toString();
    }

    /** {@code .<filter>(#{any()}, ..)}, its arguments added to the template's. */
    static void filter(Filter f, StringBuilder code, List<Object> parameters) {
        code.append('.').append(f.name()).append('(');
        code.append(String.join(", ", java.util.Collections.nCopies(f.arguments().size(), "#{any()}")));
        code.append(')');
        parameters.addAll(f.arguments());
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        return Stream.of(sets).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, String> same(String... names) {
        return Stream.of(names).collect(Collectors.toUnmodifiableMap(n -> n, n -> n));
    }

    private static Map<String, String> with(Map<String, String> common, Map<String, String> more) {
        Map<String, String> all = new HashMap<>(common);
        all.putAll(more);
        return Map.copyOf(all);
    }
}
