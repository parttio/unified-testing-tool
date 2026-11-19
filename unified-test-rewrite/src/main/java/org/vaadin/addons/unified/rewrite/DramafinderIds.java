package org.vaadin.addons.unified.rewrite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * The ids and test ids the application's sources give their components, with the component's class: what a raw
 * Playwright locator on {@code #id} or {@code [data-testid=..]} finds (#356). Read from {@code c.setId("x")} and
 * {@code c.getElement().setAttribute("data-testid", "x")} with a literal, where {@code c}'s type is known.
 */
final class DramafinderIds {

    private static final String KEY = "unified.dramafinder.ids";
    private static final String COMPONENT = "com.vaadin.flow.component.Component";
    /** An id set on components of different classes: the type can't be told from the sources. */
    static final String AMBIGUOUS = "?";

    private DramafinderIds() {
    }

    /** Adds the ids the tree sets to those found so far ({@link #of}). */
    static void collect(J tree, ExecutionContext ctx) {
        Map<String, String> ids = ctx.computeMessageIfAbsent(KEY, k -> new HashMap<>());
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                String key = null;
                JavaType component = null;
                List<Expression> arguments = m.getArguments();
                if (m.getSimpleName().equals("setId") && arguments.size() == 1 && literal(arguments.get(0)) != null) {
                    key = "id:" + literal(arguments.get(0));
                    component = receiver(m);
                } else if (m.getSimpleName().equals("setAttribute") && arguments.size() == 2
                        && "data-testid".equals(literal(arguments.get(0))) && literal(arguments.get(1)) != null
                        && m.getSelect() instanceof J.MethodInvocation element && element.getSimpleName().equals("getElement")
                        && element.getArguments().stream().allMatch(a -> a instanceof J.Empty)) {
                    key = "testid:" + literal(arguments.get(1));
                    component = receiver(element);
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(component);
                if (key != null && type != null && TypeUtils.isAssignableTo(COMPONENT, type)) {
                    ids.merge(key, type.getFullyQualifiedName(), (a, b) -> a.equals(b) ? a : AMBIGUOUS);
                }
                return super.visitMethodInvocation(m, p);
            }

            /** The type of the component a call is made on: its target, or the class it is made in. */
            private JavaType receiver(J.MethodInvocation m) {
                if (m.getSelect() != null) {
                    return m.getSelect().getType();
                }
                J.ClassDeclaration c = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return c == null ? null : c.getType();
            }
        }.visit(tree, 0);
    }

    /**
     * The class of the component the sources give the id ({@code "id:x"}) or test id ({@code "testid:x"}),
     * {@link #AMBIGUOUS} when several classes have it, {@code null} when none does.
     */
    static String of(ExecutionContext ctx, String key) {
        Map<String, String> ids = ctx.getMessage(KEY, Map.of());
        return ids.get(key);
    }

    private static String literal(Expression e) {
        return e instanceof J.Literal l && l.getValue() instanceof String s ? s : null;
    }
}
