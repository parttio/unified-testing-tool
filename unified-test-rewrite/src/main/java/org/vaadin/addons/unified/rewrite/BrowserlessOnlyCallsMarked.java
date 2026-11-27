package org.vaadin.addons.unified.rewrite;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Calls in migrated test code that only work on Browserless and that no other rule converted or marked: the fluent
 * locator mixin {@code com.vaadin.browserless.locator.Locators} (after the lookups recipe converted the locators a
 * Wrapper says the same as), and the server's static {@code UI.getCurrent()} and {@code VaadinSession.getCurrent()}.
 * Each gets a Migration Marker, and a test making one, itself or through a helper, requires Browserless. A class that
 * no longer calls the mixin stops implementing it; one that still does keeps it with a Migration Marker.
 */
public class BrowserlessOnlyCallsMarked extends Recipe {

    private static final Predicate<TypeTree> IS_LOCATORS = t -> TypeUtils.isOfClassType(t.getType(), Browserless.LOCATORS);
    private static final MethodMatcher UI_CURRENT = new MethodMatcher("com.vaadin.flow.component.UI getCurrent()");
    private static final MethodMatcher SESSION_CURRENT =
            new MethodMatcher("com.vaadin.flow.server.VaadinSession getCurrent()");
    private static final String CLASS_MARKER = "Browserless Locators mixin: its locators run on Browserless only; "
            + "find the components the marked calls use with page.find(..), then drop implements Locators";

    @Override
    public String getDisplayName() {
        return "Mark Browserless-only calls no rule converted";
    }

    @Override
    public String getDescription() {
        return "Marks the `Locators` mixin's calls and `UI.getCurrent()` / `VaadinSession.getCurrent()` with a Migration "
                + "Marker and makes the tests making one require Browserless; drops `implements Locators` once unused.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (c.getImplements() == null || c.getImplements().stream().noneMatch(IS_LOCATORS)) {
                    return c;
                }
                if (callsTheMixin(c.getBody())) {
                    return marked(c);
                }
                List<TypeTree> kept = c.getImplements().stream().filter(IS_LOCATORS.negate()).toList();
                maybeRemoveImport(Browserless.LOCATORS);
                return kept.isEmpty() ? c.getPadding().withImplements(null) : c.withImplements(kept);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.ClassDeclaration c = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (method.getBody() == null || c == null || !migrated(c)) {
                    return method;
                }
                var markers = MigrationMarkers.collect(getCursor());
                J.MethodDeclaration m = MigrationMarkers.write(super.visitMethodDeclaration(method, ctx), markers);
                boolean browserlessOnly = !markers.isEmpty() || callsABrowserlessOnlyHelper(c, m);
                return browserlessOnly && Helpers.isBrowserTest(m) ? requiresBrowserless(m) : m;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (Browserless.isLocatorsMixinCall(method) && !insideAnotherMixinCall()) {
                    MigrationMarkers.add(getCursor(), "Browserless Locators mixin: " + printedChain());
                } else if (isCurrentUiOrSession(method)) {
                    MigrationMarkers.add(getCursor(), "server-side state: " + printedChain()
                            + ": only Browserless runs the server in the test's JVM; check what the user sees instead");
                }
                return m;
            }

            /** The call and the calls chained on it. */
            private String printedChain() {
                Cursor shown = getCursor();
                while (shown.getParentTreeCursor().getValue() instanceof J.MethodInvocation chained
                        && chained.getSelect() == shown.getValue()) {
                    shown = shown.getParentTreeCursor();
                }
                return ((J) shown.getValue()).printTrimmed(shown.getParentTreeCursor());
            }

            /** Whether a mixin call of the same statement encloses this one: it carries the statement's marker. */
            private boolean insideAnotherMixinCall() {
                for (Cursor c = getCursor().getParentTreeCursor(); c.getValue() instanceof J; c = c.getParentTreeCursor()) {
                    if (c.getValue() instanceof J.MethodInvocation call && Browserless.isLocatorsMixinCall(call)) {
                        return true;
                    }
                    if (c.getValue() instanceof J.Block || c.getValue() instanceof J.MethodDeclaration) {
                        return false;
                    }
                }
                return false;
            }

            private J.MethodDeclaration requiresBrowserless(J.MethodDeclaration m) {
                if (m.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(),
                        TestBenchLeftoversToEscapeHatch.REQUIRES_TESTING_TOOL))) {
                    return m;
                }
                maybeAddImport(TestBenchLeftoversToEscapeHatch.REQUIRES_TESTING_TOOL);
                maybeAddImport(TestBenchLeftoversToEscapeHatch.TESTING_TOOL);
                return JavaTemplate.builder("@RequiresTestingTool(TestingTool.BROWSERLESS)")
                        .imports(TestBenchLeftoversToEscapeHatch.REQUIRES_TESTING_TOOL, TestBenchLeftoversToEscapeHatch.TESTING_TOOL)
                        .javaParser(Stubs.parser())
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().addAnnotation((a, b) -> 1));
            }
        };
    }

    /** The class with the mixin's Migration Marker above its first annotation, or above the class. */
    private static J.ClassDeclaration marked(J.ClassDeclaration c) {
        return MigrationMarkers.onClass(c, CLASS_MARKER);
    }

    private static boolean callsTheMixin(J tree) {
        AtomicBoolean calls = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call, AtomicBoolean found) {
                if (Browserless.isLocatorsMixinCall(call)) {
                    found.set(true);
                }
                return super.visitMethodInvocation(call, found);
            }
        }.visit(tree, calls);
        return calls.get();
    }

    /**
     * A class the migration made a Unified test: it has a test or helper taking the Unified Page, or keeps the
     * mixin. Other classes in the file stay as they are.
     */
    private static boolean migrated(J.ClassDeclaration c) {
        return c.getImplements() != null && c.getImplements().stream().anyMatch(IS_LOCATORS)
                || c.getBody().getStatements().stream().anyMatch(s -> s instanceof J.MethodDeclaration m
                        && Pages.hasPageParameter(m));
    }

    private static boolean isCurrentUiOrSession(J.MethodInvocation call) {
        return UI_CURRENT.matches(call) || SESSION_CURRENT.matches(call);
    }

    private static boolean browserlessOnly(J.MethodInvocation call) {
        return Browserless.isLocatorsMixinCall(call) || isCurrentUiOrSession(call);
    }

    /** Whether the method calls one of the class's helpers making a Browserless-only call, itself or through another. */
    private static boolean callsABrowserlessOnlyHelper(J.ClassDeclaration c, J.MethodDeclaration m) {
        Set<String> using = Helpers.using(c, Set.of(), BrowserlessOnlyCallsMarked::browserlessOnly);
        AtomicBoolean calls = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call, AtomicBoolean found) {
                if (call.getSelect() == null && using.contains(call.getSimpleName())) {
                    found.set(true);
                }
                return super.visitMethodInvocation(call, found);
            }
        }.visit(m.getBody(), calls);
        return calls.get();
    }
}
