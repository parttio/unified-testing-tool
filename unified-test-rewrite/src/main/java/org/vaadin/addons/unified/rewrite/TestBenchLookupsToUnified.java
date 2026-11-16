package org.vaadin.addons.unified.rewrite;

import static java.util.Collections.emptyList;

import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/**
 * Turns {@code $(XElement.class)} into {@code page.find(UnifiedX.class)} for elements that have a Wrapper,
 * in test methods that have the {@code UnifiedPage page} parameter. The filters after it ({@code withLabel},
 * {@code withText}, {@code withId}) keep their names; the terminal ({@code single()}, {@code first()},
 * {@code waitForFirst()}) goes, as the Wrapper is the lookup (ADR 0007). {@code first()} and
 * {@code waitForFirst()} leave a Migration Marker.
 */
public class TestBenchLookupsToUnified extends Recipe {

    private static final MethodMatcher DOLLAR = new MethodMatcher("com.vaadin.testbench.HasElementQuery $(java.lang.Class)", true);

    private static final Set<String> FIRST = Set.of("first", "waitForFirst");

    @Override
    public String getDisplayName() {
        return "Migrate TestBench element lookups to Unified Page lookups";
    }

    @Override
    public String getDescription() {
        return "Turns `$(XElement.class)` into `page.find(UnifiedX.class)` for elements that have a Wrapper.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                var markers = MigrationMarkers.collect(getCursor());
                return MigrationMarkers.write(super.visitMethodDeclaration(method, ctx), markers);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                Wrappers.Wrapper wrapper = method == null || multiVariable.getVariables().size() != 1
                        || multiVariable.getTypeExpression() == null || !Pages.hasPageParameter(method)
                        ? null : Locals.wrapperOf(method, multiVariable.getVariables().get(0));
                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                if (wrapper == null) {
                    return v;
                }
                maybeAddImport(wrapper.fqn());
                return v.withTypeExpression(new J.Identifier(Tree.randomId(), v.getTypeExpression().getPrefix(),
                        Markers.EMPTY, emptyList(), wrapper.simpleName(), JavaType.ShallowClass.build(wrapper.fqn()), null));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (Wrappers.TERMINALS.contains(m.getSimpleName()) && hasPageParameter()) {
                    if (convertedLookup(method, m) && m.getSelect() instanceof J.MethodInvocation lookup
                            && (!FIRST.contains(m.getSimpleName()) || MigrationMarkers.add(getCursor(),
                                    "was " + m.getSimpleName() + "(); the lookup fails when several components match"))) {
                        // the Wrapper is the lookup: no terminal (ADR 0007)
                        return lookup.withPrefix(m.getPrefix());
                    }
                    return m;
                }
                if (!DOLLAR.matches(method) || !hasPageParameter()) {
                    return m;
                }
                Wrappers.Wrapper wrapper = Chains.ofDollar(method);
                if (wrapper == null || !Chains.supported(getCursor(), false, wrapper)) {
                    return m;
                }
                // $(..) on the page, or on a container that becomes a Unified Scope
                String scope = TestBenchSetupToUnified.UNIFIED_PAGE;
                Object receiver = Stubs.page();
                if (method.getSelect() instanceof J.MethodInvocation content && content.getSimpleName().equals("content")
                        && content.getSelect() != null && isTabSheet(content.getSelect())) {
                    scope = "com.vaadin.testing.unified.Scope"; // a TabSheet's content()
                    receiver = m.getSelect();
                } else if (method.getSelect() != null) {
                    Wrappers.Wrapper container = method.getSelect() instanceof J.Identifier local
                            ? Locals.wrapperOf(getCursor(), local)
                            : Wrappers.ofLookup(method.getSelect());
                    if (container == null || !Chains.SCOPES.contains(container.fqn())) {
                        return m;
                    }
                    scope = container.fqn();
                    receiver = m.getSelect();
                }
                String element = ((J.FieldAccess) method.getArguments().get(0)).getTarget().getType() instanceof JavaType.FullyQualified t
                        ? t.getFullyQualifiedName() : null;
                maybeAddImport(wrapper.fqn());
                maybeRemoveImport(element);
                return JavaTemplate.builder("#{any(" + scope + ")}.find(" + wrapper.simpleName() + ".class)")
                        .imports(wrapper.fqn())
                        .javaParser(Stubs.parser(Stubs.interfaceStub(wrapper.fqn())))
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), receiver);
            }

            /**
             * Whether the terminal's lookup is one this visit converted: its {@code $(..)} (on the page or a
             * Scope) became {@code find(..)}, which it does only when the Wrapper expresses the whole chain.
             */
            private boolean convertedLookup(J.MethodInvocation original, J.MethodInvocation converted) {
                org.openrewrite.java.tree.Expression o = original.getSelect();
                org.openrewrite.java.tree.Expression c = converted.getSelect();
                while (o instanceof J.MethodInvocation oc && c instanceof J.MethodInvocation cc) {
                    if (oc.getSimpleName().equals("$")) {
                        return DOLLAR.matches(oc) && cc.getSimpleName().equals("find");
                    }
                    o = oc.getSelect();
                    c = cc.getSelect();
                }
                return false;
            }

            private boolean isTabSheet(org.openrewrite.java.tree.Expression tabSheet) {
                Wrappers.Wrapper w = tabSheet instanceof J.Identifier local
                        ? Locals.wrapperOf(getCursor(), local)
                        : Wrappers.ofLookup(tabSheet);
                return w != null && w.fqn().equals(TabSheets.TAB_SHEET);
            }

            private boolean hasPageParameter() {
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                return enclosing != null && Pages.hasPageParameter(enclosing);
            }
        };
    }
}
