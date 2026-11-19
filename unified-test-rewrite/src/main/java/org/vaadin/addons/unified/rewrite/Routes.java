package org.vaadin.addons.unified.rewrite;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * The views by the path of their {@code @Route}, for a recipe that turns a literal path into the route class: a
 * renamed route then breaks at compile time, and the User Step reads {@code open Plan}.
 */
final class Routes {

    private static final String KEY = "unified.routes";
    private static final String ROUTE_PREFIX = "com.vaadin.flow.router.RoutePrefix";
    private static final String PARENT_LAYOUT = "com.vaadin.flow.router.ParentLayout";
    /**
     * A view the recipe doesn't name by its class: one whose path it can't tell from its {@code @Route} alone, under a
     * layout with a prefix, or a nested class.
     */
    private static final String UNSURE = "?";

    private Routes() {
    }

    /** Remembers the class if it has a {@code @Route("path")}; called by the scanners, which see every source first. */
    static void collect(J.ClassDeclaration c, ExecutionContext ctx) {
        if (c.getType() == null) {
            return;
        }
        for (J.Annotation a : c.getLeadingAnnotations()) {
            String path = TestBenchSetupToUnified.routePath(a);
            if (path != null) {
                ctx.<Map<String, Set<String>>>computeMessageIfAbsent(KEY, k -> new HashMap<>())
                        .computeIfAbsent(strip(path), p -> new TreeSet<>())
                        .add(underPrefixedLayout(a) || c.getType().getFullyQualifiedName().contains("$") ? UNSURE
                                : c.getType().getFullyQualifiedName());
            }
        }
    }

    /**
     * The fully qualified name of the one view at the path, leading slash or not, or {@code null} when none, several,
     * or one under a layout that may add a prefix has it.
     */
    static String viewAt(String path, ExecutionContext ctx) {
        if (path == null || strip(path).isEmpty()) {
            return null;
        }
        Set<String> views = ctx.<Map<String, Set<String>>>getMessage(KEY, Map.of()).getOrDefault(strip(path), Set.of());
        return views.size() == 1 && !views.contains(UNSURE) ? views.iterator().next() : null;
    }

    /** Whether the route's {@code layout} has a {@code @RoutePrefix}, or a parent layout that may have one. */
    private static boolean underPrefixedLayout(J.Annotation route) {
        for (Expression arg : route.getArguments()) {
            if (arg instanceof J.Assignment as && as.getVariable() instanceof J.Identifier id
                    && id.getSimpleName().equals("absolute")) {
                return false;
            }
        }
        for (Expression arg : route.getArguments()) {
            if (arg instanceof J.Assignment as && as.getVariable() instanceof J.Identifier id
                    && id.getSimpleName().equals("layout")) {
                JavaType.FullyQualified layout = as.getAssignment() instanceof J.FieldAccess literal
                        ? TypeUtils.asFullyQualified(literal.getTarget().getType()) : null;
                return layout == null || layout.getAnnotations().stream()
                        .anyMatch(t -> TypeUtils.isOfClassType(t, ROUTE_PREFIX) || TypeUtils.isOfClassType(t, PARENT_LAYOUT));
            }
        }
        return false;
    }

    private static String strip(String path) {
        String p = path;
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
