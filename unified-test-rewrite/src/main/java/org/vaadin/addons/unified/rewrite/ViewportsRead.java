package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

/**
 * The size a TestBench test starts at (ADR 0011, #496): a resize to plain numbers that comes first in a
 * {@code @BeforeEach} becomes the class's {@code @Viewport}, first in a test the test's, when no {@code @BeforeEach}
 * runs before it. Runs before
 * {@code TestBenchSetupToUnified} turns the {@code @BeforeEach} methods into helpers.
 */
public class ViewportsRead extends Recipe {

    @Override
    public String getDisplayName() {
        return "The size a TestBench or Dramafinder test starts at becomes @Viewport";
    }

    @Override
    public String getDescription() {
        return "A resize to plain numbers that comes first in a `@BeforeEach` becomes the class's `@Viewport`, first in a "
                + "test the test's.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                Viewports.Size forClass = null;
                String classNote = null;
                // a test starts with its own first statement only when no @BeforeEach, here or inherited, runs before
                boolean testsRunFirst = c.getExtends() != null
                        && (TestBenchSetupToUnified.isTestBenchBase(c.getExtends().getType())
                                || Dramafinder.isBaseType(c.getExtends().getType()))
                        && c.getBody().getStatements().stream()
                                .noneMatch(st -> st instanceof J.MethodDeclaration md && isBeforeEach(md));
                List<Statement> statements = new ArrayList<>();
                for (Statement s : c.getBody().getStatements()) {
                    if (s instanceof J.MethodDeclaration m && m.getBody() != null && !m.getBody().getStatements().isEmpty()) {
                        Viewports.Resize resize = Viewports.literalResize(m.getBody().getStatements().get(0));
                        String note = resize == null ? null : resize.note(new org.openrewrite.Cursor(getCursor(), m));
                        if (resize != null && isBeforeEach(m) && forClass == null) {
                            forClass = resize.size();
                            classNote = note;
                            s = withoutFirst(m);
                            if (((J.MethodDeclaration) s).getBody().getStatements().isEmpty()
                                    && !c.hasModifier(J.Modifier.Type.Abstract)) {
                                continue; // nothing left to run before each test
                            }
                        } else if (resize != null && testsRunFirst && Helpers.isBrowserTest(m)) {
                            s = Viewports.startsAt(withoutFirst(m), resize.size(), note);
                        }
                    }
                    statements.add(s);
                }
                c = c.withBody(c.getBody().withStatements(statements));
                return forClass == null ? c : Viewports.startsAt(c, forClass, classNote);
            }

            /** A method that makes a check only a browser can answer: marked to need one (#669). */
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (requiresBrowser(m.getLeadingAnnotations())
                        || enclosing != null && requiresBrowser(enclosing.getLeadingAnnotations())) {
                    return m; // already written: a converted call keeps the Testing Tool's method type
                }
                boolean browserOnly = new JavaIsoVisitor<java.util.concurrent.atomic.AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call,
                            java.util.concurrent.atomic.AtomicBoolean found) {
                        found.compareAndSet(false, Viewports.isBrowserOnly(call));
                        return super.visitMethodInvocation(call, found);
                    }
                }.reduce(m, new java.util.concurrent.atomic.AtomicBoolean()).get();
                return browserOnly && m.getMarkers().findFirst(Viewports.NeedsBrowser.class).isEmpty()
                        ? m.withMarkers(m.getMarkers().add(new Viewports.NeedsBrowser(org.openrewrite.Tree.randomId())))
                        : m;
            }

            private boolean requiresBrowser(List<J.Annotation> annotations) {
                return annotations.stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), Viewports.REQUIRES_BROWSER));
            }

            private boolean isBeforeEach(J.MethodDeclaration m) {
                return m.getLeadingAnnotations().stream()
                        .anyMatch(a -> TypeUtils.isOfClassType(a.getType(), TestBenchSetupToUnified.BEFORE_EACH));
            }

            private J.MethodDeclaration withoutFirst(J.MethodDeclaration m) {
                List<Statement> rest = new ArrayList<>(m.getBody().getStatements());
                Statement first = rest.remove(0);
                if (!rest.isEmpty()) {
                    rest.set(0, rest.get(0).withPrefix(first.getPrefix()));
                }
                return m.withBody(m.getBody().withStatements(rest));
            }
        };
    }
}
