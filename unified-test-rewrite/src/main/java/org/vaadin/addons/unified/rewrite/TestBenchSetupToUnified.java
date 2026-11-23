package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

/**
 * The parts of a TestBench test's setup that need more than a type change: drops
 * {@code extends BrowserTestBase} and the {@code @LocalServerPort} field, gives every
 * {@code @BrowserTest} method a {@code UnifiedPage page} parameter, and turns
 * {@code getDriver().get(url)} into {@code page.navigate(path)}.
 */
public class TestBenchSetupToUnified extends ScanningRecipe<TestBenchSetupToUnified.Scanned> {

    static final String BROWSER_TEST_BASE = "com.vaadin.testbench.BrowserTestBase";
    static final String TEST_BENCH_TEST_CASE = "com.vaadin.testbench.TestBenchTestCase";
    static final String PARALLEL_TEST = "com.vaadin.testbench.parallel.ParallelTest";
    static final String UNIFIED_UI_TEST = "com.vaadin.testing.unified.UnifiedUITest";
    private static final Set<String> TEST_ANNOTATIONS = Set.of("org.springframework.boot.test.context.SpringBootTest",
            "com.vaadin.testing.unified.spring.UnifiedSpringBootTest", UNIFIED_UI_TEST);
    static final String BROWSER_TEST = "com.vaadin.testbench.BrowserTest";
    static final String LOCAL_SERVER_PORT = "org.springframework.boot.test.web.server.LocalServerPort";
    static final String BEFORE_EACH = "org.junit.jupiter.api.BeforeEach";
    private static final String HELPERS = "unified.helpers";
    private static final String TESTS = "unified.tests";
    private static final String PAGE_NAME = "unified.page-name";
    static final String ROUTE = "com.vaadin.flow.router.Route";
    /** Gated on the Unified API's own setting, a TestBench test ran only on Selenium; migrated, it runs on all. */
    private static final AnnotationMatcher TESTING_TOOL_GATE = new AnnotationMatcher(
            "@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = \"vaadin.testing.tool\")");
    static final String UNIFIED_PAGE = "com.vaadin.testing.unified.UnifiedPage";

    private static final MethodMatcher DRIVER_GET = new MethodMatcher("org.openqa.selenium.WebDriver get(String)");
    private static final MethodMatcher GET_DRIVER = new MethodMatcher("com.vaadin.testbench.HasDriver getDriver()", true);

    @Override
    public String getDisplayName() {
        return "Migrate TestBench test setup to a Unified Page";
    }

    @Override
    public String getDescription() {
        return "Drops `extends BrowserTestBase` and the server port, gives each `@BrowserTest` a `UnifiedPage page` "
                + "parameter and turns `getDriver().get(url)` into `page.navigate(path)`.";
    }

    /**
     * Views by route path, and the TestBench test classes (and base classes): their helpers, {@code @BeforeEach}
     * helpers and test methods, resolved across the class hierarchy once every class is scanned.
     */
    public static final class Scanned {
        private final Map<String, Set<String>> routes = new HashMap<>();
        private final Map<String, J.ClassDeclaration> classes = new HashMap<>();
        private final Map<String, Set<String>> helpersByClass = new HashMap<>();
        private final Map<String, List<String>> beforeEachByClass = new HashMap<>();
        private final Map<String, Set<String>> testsByClass = new HashMap<>();
        private boolean resolved;

        Map<String, Set<String>> routes() {
            return routes;
        }

        Map<String, Set<String>> helpersByClass() {
            resolve();
            return helpersByClass;
        }

        Map<String, List<String>> beforeEachByClass() {
            resolve();
            return beforeEachByClass;
        }

        Map<String, Set<String>> testsByClass() {
            resolve();
            return testsByClass;
        }

        /**
         * The helpers of every class: the methods using TestBench or calling a helper, an abstract method one of
         * whose overrides is a helper, and an override of a helper, so declarations and overrides keep matching
         * once helpers gain the page.
         */
        private void resolve() {
            if (resolved) {
                return;
            }
            resolved = true;
            Map<String, Set<String>> forced = new HashMap<>();
            Set<String> gained = new java.util.HashSet<>(); // declaringClass#method of methods that gain the page
            boolean changed = true;
            while (changed) {
                changed = false;
                for (var e : classes.entrySet()) {
                    String fqn = e.getKey();
                    J.ClassDeclaration c = e.getValue();
                    Set<String> callable = new java.util.HashSet<>(inheritedHelpers(c.getType(), helpersByClass));
                    callable.addAll(forced.getOrDefault(fqn, Set.of()));
                    Set<String> helpers = new java.util.HashSet<>(Helpers.usingTestBench(c, callable));
                    helpers.addAll(forced.getOrDefault(fqn, Set.of()));
                    for (Statement st : c.getBody().getStatements()) {
                        if (!(st instanceof J.MethodDeclaration m) || Helpers.isBrowserTest(m)
                                || m.hasModifier(J.Modifier.Type.Static) || m.getMethodType() == null) {
                            continue;
                        }
                        List<JavaType.Method> overridden = new ArrayList<>();
                        for (JavaType.Method o = TypeUtils.findOverriddenMethod(m.getMethodType()).orElse(null); o != null;
                                o = TypeUtils.findOverriddenMethod(o).orElse(null)) {
                            overridden.add(o);
                        }
                        if (!helpers.contains(m.getSimpleName())
                                && overridden.stream().anyMatch(o -> gained.contains(key(o)))) {
                            helpers.add(m.getSimpleName());
                        }
                        if (helpers.contains(m.getSimpleName())) {
                            changed |= gained.add(fqn + "#" + m.getSimpleName());
                            for (JavaType.Method o : overridden) {
                                changed |= gained.add(key(o));
                                String declaring = o.getDeclaringType().getFullyQualifiedName();
                                if (classes.containsKey(declaring)) {
                                    changed |= forced.computeIfAbsent(declaring, k -> new java.util.HashSet<>())
                                            .add(o.getName());
                                }
                            }
                        }
                    }
                    if (!helpers.equals(helpersByClass.get(fqn))) {
                        helpersByClass.put(fqn, helpers);
                        changed = true;
                    }
                }
            }
            classes.forEach((fqn, c) -> {
                beforeEachByClass.put(fqn, beforeEachOf(c, helpersByClass.get(fqn)));
                Set<String> tests = new java.util.HashSet<>();
                for (Statement st : c.getBody().getStatements()) {
                    if (st instanceof J.MethodDeclaration m && Helpers.isBrowserTest(m)) {
                        tests.add(m.getSimpleName());
                    }
                }
                testsByClass.put(fqn, tests);
            });
        }

        private static String key(JavaType.Method m) {
            return m.getDeclaringType().getFullyQualifiedName() + "#" + m.getName();
        }
    }

    @Override
    public Scanned getInitialValue(ExecutionContext ctx) {
        return new Scanned();
    }

    /** Collects the views by route path, to navigate by class when exactly one view has the path. */
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scanned scanned) {
        Map<String, Set<String>> routes = scanned.routes();
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (isTestBenchClass(classDecl)) {
                    scanned.classes.put(classDecl.getType().getFullyQualifiedName(), classDecl);
                }
                LitTemplates.collect(classDecl, ctx);
                if (classDecl.getType() != null) {
                    for (J.Annotation a : classDecl.getLeadingAnnotations()) {
                        String path = routePath(a);
                        if (path != null) {
                            routes.computeIfAbsent(path, p -> new TreeSet<>())
                                    .add(classDecl.getType().getFullyQualifiedName());
                        }
                    }
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }

    /** The explicit path of a {@code @Route("path")} or {@code @Route(value = "path")}, else {@code null}. */
    static String routePath(J.Annotation a) {
        if (!TypeUtils.isOfClassType(a.getType(), ROUTE) || a.getArguments() == null) {
            return null;
        }
        for (Expression arg : a.getArguments()) {
            Expression value = arg instanceof J.Assignment as && as.getVariable() instanceof J.Identifier id
                    && id.getSimpleName().equals("value") ? as.getAssignment() : arg;
            if (value instanceof J.Literal literal && literal.getValue() instanceof String path) {
                return path;
            }
        }
        return null;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned scanned) {
        Map<String, Set<String>> routes = scanned.routes();
        return new JavaIsoVisitor<>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (!isTestBenchClass(classDecl)) {
                    return super.visitClassDeclaration(classDecl, ctx);
                }
                String fqn = classDecl.getType().getFullyQualifiedName();
                Set<String> inherited = inheritedHelpers(classDecl.getType(), scanned.helpersByClass());
                Set<String> own = scanned.helpersByClass().getOrDefault(fqn, Set.of());
                Set<String> callable = new java.util.HashSet<>(inherited);
                callable.addAll(own);
                Set<String> helpers = new java.util.HashSet<>(Helpers.usingTestBench(classDecl, callable));
                helpers.addAll(callable);
                getCursor().putMessage(HELPERS, helpers);
                Set<String> tests = new java.util.HashSet<>(scanned.testsByClass().getOrDefault(fqn, Set.of()));
                for (JavaType.FullyQualified s = classDecl.getType().getSupertype(); s != null; s = s.getSupertype()) {
                    tests.addAll(scanned.testsByClass().getOrDefault(s.getFullyQualifiedName(), Set.of()));
                }
                getCursor().putMessage(TESTS, tests);
                if (classDecl.getExtends() == null || !isTestBenchBase(classDecl.getExtends().getType())) {
                    // a subclass of a TestBench base class: its tests call the inherited @BeforeEach helpers first
                    J.ClassDeclaration sub = super.visitClassDeclaration(classDecl, ctx);
                    doAfterVisit(new RemoveAnnotationVisitor(TESTING_TOOL_GATE));
                    List<String> beforeEach = new ArrayList<>(inheritedBeforeEach(classDecl.getType(), scanned.beforeEachByClass()));
                    beforeEach.addAll(beforeEachOf(classDecl, helpers));
                    importThrown(classDecl, beforeEach);
                    return sub.withBody(sub.getBody().withStatements(Helpers.callBeforeEachFirst(sub, beforeEach)));
                }
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                importThrown(classDecl, beforeEachOf(classDecl, helpers));
                c = c.withBody(c.getBody().withStatements(Helpers.callBeforeEachFirst(c, beforeEachOf(classDecl, helpers))));
                c = c.withExtends(null);
                doAfterVisit(new RemoveAnnotationVisitor(TESTING_TOOL_GATE));
                c = c.withBody(c.getBody().withStatements(withoutUnusedPortFields(c)));
                maybeRemoveImport(BROWSER_TEST_BASE);
                maybeRemoveImport(TEST_BENCH_TEST_CASE);
                maybeRemoveImport(PARALLEL_TEST);
                if (c.getLeadingAnnotations().stream().noneMatch(a -> TEST_ANNOTATIONS.stream()
                        .anyMatch(t -> TypeUtils.isOfClassType(a.getType(), t)))) {
                    // no Spring test: a test against a running application
                    maybeAddImport(UNIFIED_UI_TEST);
                    c = JavaTemplate.builder("@UnifiedUITest")
                            .imports(UNIFIED_UI_TEST)
                            .javaParser(Stubs.parser("package com.vaadin.testing.unified; public @interface UnifiedUITest {}"))
                            .build()
                            .apply(updateCursor(c), c.getCoordinates().addAnnotation((a, b) -> 0));
                }
                maybeRemoveImport(LOCAL_SERVER_PORT);
                return c;
            }

            /** The exceptions of the {@code @BeforeEach} helpers the tests now call, which the tests now declare. */
            private void importThrown(J.ClassDeclaration c, List<String> beforeEach) {
                for (JavaType.FullyQualified thrown : Helpers.thrownBy(c, beforeEach)) {
                    maybeAddImport(thrown.getFullyQualifiedName());
                }
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                String page = Pages.parameterName(method);
                String name = page != null ? page : Pages.freeName(method);
                getCursor().putMessage(PAGE_NAME, name);
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                Set<String> helpers = getCursor().getNearestMessage(HELPERS, Set.of());
                boolean browserTest = Helpers.isBrowserTest(m)
                        && getCursor().firstEnclosing(J.ClassDeclaration.class) != null
                        && isTestBenchClass(getCursor().firstEnclosing(J.ClassDeclaration.class));
                if (!browserTest && !helpers.contains(m.getSimpleName()) || Pages.hasPageParameter(m)) {
                    return m;
                }
                maybeAddImport(UNIFIED_PAGE);
                if (helpers.contains(m.getSimpleName())) {
                    m = (J.MethodDeclaration) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + BEFORE_EACH))
                            .visitNonNull(m, ctx, getCursor().getParentOrThrow());
                }
                if (overridesTestBase(m)) {
                    // with the page, it overrides nothing: the class no longer extends TestBench's base class
                    m = (J.MethodDeclaration) new RemoveAnnotationVisitor(new AnnotationMatcher("@java.lang.Override"))
                            .visitNonNull(m, ctx, getCursor().getParentOrThrow());
                }
                List<Statement> others = m.getParameters().stream().filter(p -> !(p instanceof J.Empty)).toList();
                J.MethodDeclaration withPage = JavaTemplate.builder("UnifiedPage " + name)
                        .imports(UNIFIED_PAGE)
                        .javaParser(Stubs.parser())
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().replaceParameters());
                List<Statement> parameters = new ArrayList<>(withPage.getParameters());
                for (Statement other : others) {
                    parameters.add(other.withPrefix(Space.SINGLE_SPACE));
                }
                return withPage.withParameters(parameters);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                Set<String> helpers = getCursor().getNearestMessage(HELPERS, Set.of());
                Set<String> tests = getCursor().getNearestMessage(TESTS, Set.of());
                if (m.getSelect() == null && (helpers.contains(m.getSimpleName()) || tests.contains(m.getSimpleName()))
                        && !TestBenchLeftoversToEscapeHatch.isTestBaseCall(m) && !passesPage(m)) {
                    // a helper, or another test: both gain the page (a TestBench base call goes through the Escape Hatch)
                    List<Expression> arguments = new ArrayList<>();
                    arguments.add(page());
                    for (Expression a : m.getArguments()) {
                        if (!(a instanceof J.Empty)) {
                            arguments.add(a.withPrefix(Space.SINGLE_SPACE));
                        }
                    }
                    return m.withArguments(arguments);
                }
                if (!DRIVER_GET.matches(m) || !(m.getSelect() instanceof J.MethodInvocation select)
                        || !GET_DRIVER.matches(select)) {
                    return m;
                }
                String path = Urls.pathOf(m.getArguments().get(0));
                if (path == null) {
                    Expression pathExpression = Urls.pathExpression(m.getArguments().get(0));
                    return pathExpression == null ? m
                            : JavaTemplate.builder("#{any(" + UNIFIED_PAGE + ")}.navigate(#{any(String)})")
                                    .javaParser(Stubs.parser())
                                    .build()
                                    .apply(getCursor(), m.getCoordinates().replace(), page(), pathExpression);
                }
                Set<String> views = routes.getOrDefault(path, Set.of());
                if (views.size() == 1) {
                    String view = views.iterator().next();
                    maybeAddImport(view);
                    return JavaTemplate.builder("#{any(" + UNIFIED_PAGE + ")}.navigate(" + simpleName(view) + ".class)")
                            .imports(view)
                            .javaParser(Stubs.parser(Stubs.classStub(view)))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), page());
                }
                return JavaTemplate.builder("#{any(" + UNIFIED_PAGE + ")}.navigate(#{any(String)})")
                        .javaParser(Stubs.parser())
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), page(), Stubs.literal(path));
            }

            /** The Unified Page of the method being visited, under the name it has or gains. */
            private J.Identifier page() {
                return Stubs.page(getCursor().getNearestMessage(PAGE_NAME, Pages.NAME));
            }
        };
    }

    /** A call that already passes a Unified Page, rewritten on an earlier cycle. */
    private static boolean passesPage(J.MethodInvocation m) {
        return !m.getArguments().isEmpty() && TypeUtils.isOfClassType(m.getArguments().get(0).getType(), UNIFIED_PAGE);
    }

    /** An override of a TestBench base method ({@code getDriver()}), which the class no longer inherits. */
    private static boolean overridesTestBase(J.MethodDeclaration m) {
        return m.getMethodType() != null && TypeUtils.findOverriddenMethod(m.getMethodType())
                .filter(TestBenchLeftoversToEscapeHatch::isTestBaseMethod).isPresent();
    }

    /** A class that is, or extends, TestBench's {@code BrowserTestBase}. */
    static boolean isTestBenchClass(J.ClassDeclaration c) {
        return c.getType() != null && (TypeUtils.isAssignableTo(BROWSER_TEST_BASE, c.getType())
                || TypeUtils.isAssignableTo(TEST_BENCH_TEST_CASE, c.getType()));
    }

    /** TestBench's own test base classes: JUnit 5/6 {@code BrowserTestBase}, JUnit 4 {@code TestBenchTestCase}. */
    static boolean isTestBenchBase(JavaType type) {
        return TypeUtils.isOfClassType(type, BROWSER_TEST_BASE) || TypeUtils.isOfClassType(type, TEST_BENCH_TEST_CASE)
                || TypeUtils.isOfClassType(type, PARALLEL_TEST);
    }

    /** The {@code @BeforeEach} helpers the class's superclasses declare, the farthest ancestor's first. */
    private static List<String> inheritedBeforeEach(JavaType.FullyQualified type, Map<String, List<String>> byClass) {
        List<String> inherited = new ArrayList<>();
        for (JavaType.FullyQualified s = type.getSupertype(); s != null; s = s.getSupertype()) {
            inherited.addAll(0, byClass.getOrDefault(s.getFullyQualifiedName(), List.of()));
        }
        return inherited;
    }

    /** The TestBench helpers the class's superclasses declare. */
    private static Set<String> inheritedHelpers(JavaType.FullyQualified type, Map<String, Set<String>> helpersByClass) {
        Set<String> inherited = new java.util.HashSet<>();
        for (JavaType.FullyQualified s = type.getSupertype(); s != null; s = s.getSupertype()) {
            inherited.addAll(helpersByClass.getOrDefault(s.getFullyQualifiedName(), Set.of()));
        }
        return inherited;
    }

    /** The helpers that are {@code @BeforeEach} methods, in declaration order. */
    private static List<String> beforeEachOf(J.ClassDeclaration c, Set<String> helpers) {
        List<String> names = new ArrayList<>();
        for (Statement s : c.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration m && helpers.contains(m.getSimpleName())
                    && m.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), BEFORE_EACH))) {
                names.add(m.getSimpleName());
            }
        }
        return names;
    }

    private static String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1).replace('$', '.');
    }

    /** The class's statements without {@code @LocalServerPort} fields nothing reads any more. */
    private static List<Statement> withoutUnusedPortFields(J.ClassDeclaration c) {
        List<Statement> kept = new ArrayList<>();
        for (Statement s : c.getBody().getStatements()) {
            if (s instanceof J.VariableDeclarations v
                    && v.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), LOCAL_SERVER_PORT))
                    && v.getVariables().stream().noneMatch(var -> isRead(c, var.getSimpleName()))) {
                continue;
            }
            kept.add(s);
        }
        return kept;
    }

    private static boolean isRead(J.ClassDeclaration c, String name) {
        AtomicBoolean read = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                if (identifier.getSimpleName().equals(name) && identifier.getFieldType() != null
                        && !(getCursor().getParentTreeCursor().getValue() instanceof J.VariableDeclarations.NamedVariable)) {
                    found.set(true);
                }
                return identifier;
            }
        }.visit(c.getBody(), read);
        return read.get();
    }
}
