package org.vaadin.addons.unified.rewrite;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/**
 * The application's {@code LitTemplate}s by the tag of their {@code @Tag}, and the tests' TestBench page objects by
 * the tag of their {@code @Element}, for the recipes that turn a lookup of a Template's element into
 * {@code findTemplate(SearchBar.class)} (spec 04, "Templates"). The scanners see every source first and
 * {@link #collect} what they find; a visitor reads it through {@link #with}, as the helpers it calls take no context.
 */
final class LitTemplates {

    static final String LIT_TEMPLATE = "com.vaadin.flow.component.littemplate.LitTemplate";
    private static final String TAG = "com.vaadin.flow.component.Tag";
    private static final String ELEMENT = "com.vaadin.testbench.elementsbase.Element";
    private static final String TEMPLATES_KEY = "unified.lit-templates";
    private static final String ELEMENTS_KEY = "unified.element-tags";

    /** The context of the visitor running now, on this thread: what the scanners found. */
    private static final ThreadLocal<ExecutionContext> CURRENT = new ThreadLocal<>();

    private LitTemplates() {
    }

    /** Remembers a {@code LitTemplate} with a {@code @Tag}, or a page object with an {@code @Element}. */
    static void collect(J.ClassDeclaration c, ExecutionContext ctx) {
        if (c.getType() == null) {
            return;
        }
        String fqn = c.getType().getFullyQualifiedName();
        String tag = annotationValue(c, TAG);
        if (tag != null && TypeUtils.isAssignableTo(LIT_TEMPLATE, c.getType())) {
            ctx.<Map<String, Set<String>>>computeMessageIfAbsent(TEMPLATES_KEY, k -> new HashMap<>())
                    .computeIfAbsent(tag, t -> new TreeSet<>()).add(fqn);
        }
        String element = annotationValue(c, ELEMENT);
        if (element != null) {
            ctx.<Map<String, String>>computeMessageIfAbsent(ELEMENTS_KEY, k -> new HashMap<>()).put(fqn, element);
        }
    }

    /** Runs {@code visit} with what the scanners found readable by {@link #ofTag} and {@link #ofElement}. */
    static <T> T with(ExecutionContext ctx, Supplier<T> visit) {
        ExecutionContext before = CURRENT.get();
        CURRENT.set(ctx);
        try {
            return visit.get();
        } finally {
            CURRENT.set(before);
        }
    }

    /** The one {@code LitTemplate} class with this tag, or {@code null} when none, several, or no visit is running. */
    static String ofTag(String tag) {
        ExecutionContext ctx = CURRENT.get();
        Set<String> found = ctx == null ? Set.of()
                : ctx.<Map<String, Set<String>>>getMessage(TEMPLATES_KEY, Map.of()).getOrDefault(tag, Set.of());
        return found.size() == 1 ? found.iterator().next() : null;
    }

    /** The {@code LitTemplate} class whose tag a TestBench page object's {@code @Element} names, or {@code null}. */
    static String ofElement(String elementFqn) {
        ExecutionContext ctx = CURRENT.get();
        String tag = ctx == null ? null : ctx.<Map<String, String>>getMessage(ELEMENTS_KEY, Map.of()).get(elementFqn);
        return tag == null ? null : ofTag(tag);
    }

    private static String annotationValue(J.ClassDeclaration c, String annotationType) {
        for (J.Annotation a : c.getLeadingAnnotations()) {
            if (TypeUtils.isOfClassType(a.getType(), annotationType) && a.getArguments() != null
                    && a.getArguments().size() == 1) {
                Expression arg = a.getArguments().get(0);
                if (arg instanceof J.Assignment as && as.getVariable() instanceof J.Identifier id
                        && id.getSimpleName().equals("value")) {
                    arg = as.getAssignment();
                }
                if (arg instanceof J.Literal literal && literal.getValue() instanceof String value) {
                    return value;
                }
            }
        }
        return null;
    }
}
