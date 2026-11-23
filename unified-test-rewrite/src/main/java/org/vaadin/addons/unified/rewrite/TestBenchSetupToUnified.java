package org.vaadin.addons.unified.rewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openrewrite.Cursor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
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
 * {@code getDriver().get(url)} (or {@code get(url)} on any {@code WebDriver}) into {@code page.navigate(path)} when
 * the URL is one to the local application, removing a URL constant only navigation read. A test class it takes off
 * TestBench's base class becomes a {@code @UnifiedUITest}, unless it is a Spring test; in a module with a
 * {@code @SpringBootApplication}, a test without {@code @SpringBootTest} ran against a server the build started, and
 * becomes a {@code @UnifiedSpringBootTest} unless {@code springBootTest} is false (ADR 0013).
 */
public class TestBenchSetupToUnified extends ScanningRecipe<TestBenchSetupToUnified.Scanned> {

    static final String BROWSER_TEST_BASE = "com.vaadin.testbench.BrowserTestBase";
    static final String TEST_BENCH_TEST_CASE = "com.vaadin.testbench.TestBenchTestCase";
    static final String PARALLEL_TEST = "com.vaadin.testbench.parallel.ParallelTest";
    static final String UNIFIED_UI_TEST = "com.vaadin.testing.unified.UnifiedUITest";
    static final String UNIFIED_SPRING_BOOT_TEST = "com.vaadin.testing.unified.spring.UnifiedSpringBootTest";
    private static final Set<String> TEST_ANNOTATIONS = Set.of("org.springframework.boot.test.context.SpringBootTest",
            UNIFIED_SPRING_BOOT_TEST, UNIFIED_UI_TEST);
    /** On a class that became a {@code @UnifiedSpringBootTest} where it had no {@code @SpringBootTest}. */
    static final String STARTS_THE_APP = "the tests ran against a server started outside the test; they now start "
            + "the app themselves, on a random port and with its test configuration (@UnifiedSpringBootTest); the "
            + "build's spring-boot:start/stop executions can be removed (recipe option springBootTest)";
    /** On such a class kept a {@code @UnifiedUITest} by {@code springBootTest: false}. */
    static final String EXTERNAL_SERVER = "a Spring Boot app tested against a server the build starts: Browserless "
            + "can't build its Spring views and @WithMockUser has no effect; consider @UnifiedSpringBootTest (recipe "
            + "option springBootTest)";
    /** On a statement signing in through a login page object, in a Spring Security module. */
    static final String SIGNS_IN = "signs in through the login form: unless this test checks the login itself, "
            + "consider @WithMockUser";
    private static final Set<String> LOGIN_ELEMENTS = Set.of("com.vaadin.flow.component.login.testbench.LoginOverlayElement",
            "com.vaadin.flow.component.login.testbench.LoginFormElement");
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

    private static final MethodMatcher DRIVER_GET = new MethodMatcher("org.openqa.selenium.WebDriver get(String)", true);
    private static final MethodMatcher GET_DRIVER = new MethodMatcher("com.vaadin.testbench.HasDriver getDriver()", true);

    @Option(displayName = "Test a Spring Boot app in the test",
            description = SPRING_BOOT_TEST_OPTION,
            required = false)
    private final @Nullable Boolean springBootTest;

    static final String SPRING_BOOT_TEST_OPTION = "Test a Spring Boot application by starting it in the test "
            + "(`@UnifiedSpringBootTest`) instead of against a server the build starts.";

    public TestBenchSetupToUnified() {
        this(null);
    }

    @JsonCreator
    public TestBenchSetupToUnified(@JsonProperty("springBootTest") @Nullable Boolean springBootTest) {
        this.springBootTest = springBootTest;
    }

    public @Nullable Boolean getSpringBootTest() {
        return springBootTest;
    }

    /** The option's value: true unless set to false. */
    private boolean startsTheApp() {
        return !Boolean.FALSE.equals(springBootTest);
    }

    @Override
    public String getDisplayName() {
        return "Migrate TestBench test setup to a Unified Page";
    }

    @Override
    public String getDescription() {
        return "Drops `extends BrowserTestBase` and the server port, gives each `@BrowserTest` a `UnifiedPage page` "
                + "parameter and turns `getDriver().get(url)`, or `driver.get(APP_URL + \"path\")` on any `WebDriver`, into "
                + "`page.navigate(path)`.";
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
        /** The constant string fields' values, by {@code declaringClass#name}. */
        private final Map<String, String> constants = new HashMap<>();
        /** The places reading each field, by {@code declaringClass#name}. */
        private final Map<String, List<FieldRead>> fieldReads = new HashMap<>();
        /** The modules with a {@code @SpringBootApplication}, and those using Spring Security. */
        private final SpringModules spring = new SpringModules();
        private boolean resolved;

        /** The value of the constant string field the expression reads, else {@code null}. */
        String constant(Expression e) {
            JavaType.Variable field = Urls.field(e);
            return field == null ? null : constants.get(Urls.key(field));
        }

        /**
         * Whether the field is a local application URL constant that only navigation the recipe rewrites reads, so
         * nothing reads it once migrated.
         */
        boolean onlyReadByRewrittenNavigation(String key) {
            String value = constants.get(key);
            List<FieldRead> reads = fieldReads.getOrDefault(key, List.of());
            return value != null && Urls.localPath(value) != null && !reads.isEmpty()
                    && reads.stream().allMatch(this::rewritten);
        }

        /**
         * The class's own helpers using TestBench, counting calls to the given inherited helpers, and a navigation the
         * recipe turns into {@code page.navigate(..)} ({@code driver.get(APP_URL + "users")}), as such use.
         */
        Set<String> usingTestBench(J.ClassDeclaration c, Set<String> inherited) {
            return Helpers.using(c, inherited, call -> (call.getSelect() == null || Helpers.isSuper(call.getSelect()))
                    && TestBenchLeftoversToEscapeHatch.isTestBaseCall(call) || isNavigation(call));
        }

        /** A {@code get(url)} on a {@code WebDriver} to a URL the recipe reads a path from. */
        boolean isNavigation(J.MethodInvocation call) {
            return DRIVER_GET.matches(call) && call.getSelect() != null
                    && (Urls.pathOf(call.getArguments().get(0), this::constant) != null
                            || Urls.pathExpression(call.getArguments().get(0)) != null);
        }

        /** Whether the read is the base of a navigation the recipe turns into {@code page.navigate(..)}. */
        private boolean rewritten(FieldRead read) {
            return read.navigationBase() && (read.testBenchDriver() || classes.containsKey(read.classFqn())
                    && (read.browserTest() || helpersByClass().getOrDefault(read.classFqn(), Set.of()).contains(read.method())));
        }

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
                    Set<String> helpers = new java.util.HashSet<>(usingTestBench(c, callable));
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

    /**
     * A read of a field: in which class and method, and whether it is the base of a navigation, as in
     * {@code driver.get(APP_URL + "users")}, on {@code getDriver()} or another {@code WebDriver}.
     */
    record FieldRead(String classFqn, String method, boolean browserTest, boolean navigationBase,
            boolean testBenchDriver) {
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
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                scanned.spring.collect(cu);
                return super.visitCompilationUnit(cu, ctx);
            }

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

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                    ExecutionContext ctx) {
                if (multiVariable.hasModifier(J.Modifier.Type.Final)) {
                    for (J.VariableDeclarations.NamedVariable v : multiVariable.getVariables()) {
                        JavaType.Variable field = v.getVariableType();
                        if (field != null && field.getOwner() instanceof JavaType.FullyQualified
                                && v.getInitializer() instanceof J.Literal literal && literal.getValue() instanceof String value) {
                            scanned.constants.put(Urls.key(field), value);
                        }
                    }
                }
                return super.visitVariableDeclarations(multiVariable, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                Cursor parent = getCursor().getParentTreeCursor();
                Expression read = parent.getValue() instanceof J.FieldAccess fa && fa.getName() == identifier ? fa : identifier;
                JavaType.Variable field = Urls.field(read);
                if (field != null && !(parent.getValue() instanceof J.VariableDeclarations.NamedVariable)
                        && !(read == identifier && parent.getValue() instanceof J.FieldAccess)) {
                    Cursor readCursor = read == identifier ? getCursor() : parent;
                    J.MethodInvocation navigation = navigationWithBase(readCursor);
                    J.ClassDeclaration c = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    J.MethodDeclaration m = getCursor().firstEnclosing(J.MethodDeclaration.class);
                    scanned.fieldReads.computeIfAbsent(Urls.key(field), k -> new ArrayList<>()).add(new FieldRead(
                            c == null || c.getType() == null ? "" : c.getType().getFullyQualifiedName(),
                            m == null ? "" : m.getSimpleName(), m != null && Helpers.isBrowserTest(m),
                            navigation != null, navigation != null && navigation.getSelect() instanceof J.MethodInvocation s
                                    && GET_DRIVER.matches(s)));
                }
                return super.visitIdentifier(identifier, ctx);
            }

            /** The {@code get(..)} call the read at the cursor is the base URL of, alone or before a literal path. */
            private J.MethodInvocation navigationWithBase(Cursor read) {
                Cursor up = read.getParentTreeCursor();
                Object arg = read.getValue();
                if (up.getValue() instanceof J.Binary b && b.getLeft() == arg && b.getOperator() == J.Binary.Type.Addition
                        && b.getRight() instanceof J.Literal) {
                    arg = b;
                    up = up.getParentTreeCursor();
                }
                return up.getValue() instanceof J.MethodInvocation call && DRIVER_GET.matches(call) && call.getSelect() != null
                        && call.getArguments().get(0) == arg ? call : null;
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
                Set<String> helpers = new java.util.HashSet<>(scanned.usingTestBench(classDecl, callable));
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
                    sub = sub.withBody(sub.getBody().withStatements(Helpers.callBeforeEachFirst(sub, beforeEach)));
                    return sub.withBody(sub.getBody().withStatements(withoutUnusedUrlConstants(sub, scanned)));
                }
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                importThrown(classDecl, beforeEachOf(classDecl, helpers));
                c = c.withBody(c.getBody().withStatements(Helpers.callBeforeEachFirst(c, beforeEachOf(classDecl, helpers))));
                c = c.withExtends(null);
                doAfterVisit(new RemoveAnnotationVisitor(TESTING_TOOL_GATE));
                c = c.withBody(c.getBody().withStatements(withoutUnusedPortFields(c)));
                c = c.withBody(c.getBody().withStatements(withoutUnusedUrlConstants(c, scanned)));
                maybeRemoveImport(BROWSER_TEST_BASE);
                maybeRemoveImport(TEST_BENCH_TEST_CASE);
                maybeRemoveImport(PARALLEL_TEST);
                if (c.getLeadingAnnotations().stream().noneMatch(a -> TEST_ANNOTATIONS.stream()
                        .anyMatch(t -> TypeUtils.isOfClassType(a.getType(), t)))) {
                    // no Spring test: a test against a running application, which a Spring Boot app's build started
                    boolean springBootApp = scanned.spring.hasApplication(getCursor().firstEnclosingOrThrow(SourceFile.class));
                    if (springBootApp && startsTheApp()) {
                        maybeAddImport(UNIFIED_SPRING_BOOT_TEST);
                        c = JavaTemplate.builder("@UnifiedSpringBootTest")
                                .imports(UNIFIED_SPRING_BOOT_TEST)
                                .javaParser(Stubs.parser("package com.vaadin.testing.unified.spring; "
                                        + "public @interface UnifiedSpringBootTest {}"))
                                .build()
                                .apply(updateCursor(c), c.getCoordinates().addAnnotation((a, b) -> 0));
                        c = MigrationMarkers.onClass(c, STARTS_THE_APP);
                    } else {
                        maybeAddImport(UNIFIED_UI_TEST);
                        c = JavaTemplate.builder("@UnifiedUITest")
                                .imports(UNIFIED_UI_TEST)
                                .javaParser(Stubs.parser("package com.vaadin.testing.unified; public @interface UnifiedUITest {}"))
                                .build()
                                .apply(updateCursor(c), c.getCoordinates().addAnnotation((a, b) -> 0));
                        if (springBootApp) {
                            c = MigrationMarkers.onClass(c, EXTERNAL_SERVER);
                        }
                    }
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
                var markers = MigrationMarkers.collect(getCursor());
                J.MethodDeclaration m = MigrationMarkers.write(super.visitMethodDeclaration(method, ctx), markers);
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
                if (signsIn(m)) {
                    MigrationMarkers.add(getCursor(), SIGNS_IN);
                }
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
                if (!DRIVER_GET.matches(m) || m.getSelect() == null
                        || !(m.getSelect() instanceof J.MethodInvocation select && GET_DRIVER.matches(select)) && !bearsPage()) {
                    return m;
                }
                Expression url = m.getArguments().get(0);
                String path = Urls.pathOf(url, scanned::constant);
                if (path == null) {
                    Expression pathExpression = Urls.pathExpression(url);
                    if (pathExpression != null) {
                        return JavaTemplate.builder("#{any(" + UNIFIED_PAGE + ")}.navigate(#{any(String)})")
                                .javaParser(Stubs.parser())
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), page(), pathExpression);
                    }
                    if (Urls.hasBase(url)) {
                        MigrationMarkers.add(getCursor(), "navigation to " + MigrationMarkers.code(url, getCursor())
                                + ": not a local application URL the recipe can read; use page.navigate(\"<path>\")");
                    }
                    return m;
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

            /**
             * A call on a TestBench login element, or a page object extending one, in a test of a Spring Security
             * module that starts the app: {@code @WithMockUser} could sign in instead.
             */
            private boolean signsIn(J.MethodInvocation m) {
                J.ClassDeclaration c = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return startsTheApp() && m.getSelect() != null && c != null && isTestBenchClass(c)
                        && LOGIN_ELEMENTS.stream().anyMatch(login -> TypeUtils.isAssignableTo(login, m.getSelect().getType()))
                        && scanned.spring.usesSecurity(getCursor().firstEnclosingOrThrow(SourceFile.class));
            }

            /** Whether the method the cursor is in has or gains the Unified Page: a test or a helper of a TestBench class. */
            private boolean bearsPage() {
                J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                J.ClassDeclaration c = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (method == null || c == null || !isTestBenchClass(c)) {
                    return false;
                }
                return Pages.hasPageParameter(method) || Helpers.isBrowserTest(method)
                        || getCursor().getNearestMessage(HELPERS, Set.<String>of()).contains(method.getSimpleName());
            }

            /** The Unified Page of the method being visited, under the name it has or gains. */
            private J.Identifier page() {
                return Stubs.page(getCursor().getNearestMessage(PAGE_NAME, Pages.NAME));
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TestBenchSetupToUnified other && Objects.equals(springBootTest, other.springBootTest);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(springBootTest);
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

    /**
     * A TestBench class that gets the test annotation: it extends TestBench's base class directly, so its subclasses
     * inherit it, and has none of its own.
     */
    static boolean takesTestAnnotation(J.ClassDeclaration c) {
        return isTestBenchClass(c) && c.getExtends() != null && isTestBenchBase(c.getExtends().getType())
                && c.getLeadingAnnotations().stream().noneMatch(a -> TEST_ANNOTATIONS.stream()
                        .anyMatch(t -> TypeUtils.isOfClassType(a.getType(), t)));
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

    /** The class's statements without the URL constants only rewritten navigation read, which nothing reads now. */
    private static List<Statement> withoutUnusedUrlConstants(J.ClassDeclaration c, Scanned scanned) {
        List<Statement> kept = new ArrayList<>();
        for (Statement s : c.getBody().getStatements()) {
            if (s instanceof J.VariableDeclarations v && v.getVariables().size() == 1
                    && v.getVariables().get(0).getVariableType() != null
                    && v.getVariables().get(0).getVariableType().getOwner() instanceof JavaType.FullyQualified
                    && scanned.onlyReadByRewrittenNavigation(Urls.key(v.getVariables().get(0).getVariableType()))
                    && !isRead(c, v.getVariables().get(0).getSimpleName())) {
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
