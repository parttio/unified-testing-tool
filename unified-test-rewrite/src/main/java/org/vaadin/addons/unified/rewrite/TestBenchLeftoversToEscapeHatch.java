package org.vaadin.addons.unified.rewrite;

import static java.util.Collections.emptyList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

/**
 * Keeps the TestBench base-class calls no rule converted ({@code $(GridElement.class)}, {@code executeScript},
 * {@code waitUntil}, ...) compiling and running on Selenium: they go through a {@code TestBenchEscapeHatch} on
 * the test's page, each statement gets a Migration Marker naming the call, and the method gets
 * {@code @RequiresTestingTool(TestingTool.SELENIUM)}. A call on a custom element ({@link CustomElements}) stays
 * TestBench as it is, with a Migration Marker too. A test reaching either through a helper of its class hierarchy,
 * transitively, requires Selenium as well.
 */
public class TestBenchLeftoversToEscapeHatch extends ScanningRecipe<TestBenchLeftoversToEscapeHatch.Scanned> {

    static final String ESCAPE_HATCH = "com.vaadin.testing.unified.selenium.TestBenchEscapeHatch";
    static final String REQUIRES_TESTING_TOOL = "com.vaadin.testing.unified.RequiresTestingTool";
    static final String TESTING_TOOL = "com.vaadin.testing.unified.TestingTool";
    static final String VARIABLE = "testBench";

    /** The types whose methods a {@code BrowserTestBase} test calls without a receiver. */
    private static final Set<String> TEST_BASE_TYPES = Set.of(
            "com.vaadin.testbench.BrowserTestBase",
            "com.vaadin.testbench.TestBenchTestCase",
            "com.vaadin.testbench.parallel.ParallelTest",
            "com.vaadin.testbench.AbstractBrowserDriverTestBase",
            "com.vaadin.testbench.AbstractBrowserTestBase",
            "com.vaadin.testbench.HasElementQuery",
            "com.vaadin.testbench.HasSearchContext",
            "com.vaadin.testbench.HasDriver",
            "com.vaadin.testbench.HasTestBenchCommandExecutor");

    private static final String LEFTOVERS = "unified.leftovers";
    private static final String PAGE_OBJECTS = "unified.page-objects";

    /**
     * The TestBench test classes as scanned, before any recipe changed them, and from them the helpers of each class
     * that stay TestBench: those a subclass's tests reach, whose source the subclass's visit doesn't see.
     */
    public static final class Scanned {
        private final Map<String, J.ClassDeclaration> classes = new HashMap<>();
        private final Map<String, Set<String>> staying = new HashMap<>();

        /** The helpers the class's superclasses declare that stay TestBench, themselves or through one another. */
        synchronized Set<String> inherited(JavaType.FullyQualified type, ExecutionContext ctx) {
            Set<String> inherited = new HashSet<>();
            for (JavaType.FullyQualified s = type == null ? null : type.getSupertype(); s != null; s = s.getSupertype()) {
                inherited.addAll(stayingIn(s.getFullyQualifiedName(), ctx));
            }
            return inherited;
        }

        private Set<String> stayingIn(String fqn, ExecutionContext ctx) {
            J.ClassDeclaration c = classes.get(fqn);
            if (c == null) {
                return Set.of();
            }
            Set<String> known = staying.get(fqn);
            if (known != null) {
                return known;
            }
            staying.put(fqn, Set.of()); // a cycle in broken code ends here
            Set<String> inherited = inherited(c.getType(), ctx);
            Set<UUID> calls = LitTemplates.with(ctx, () -> callsStayingTestBench(c));
            Set<String> helpers = Helpers.using(c, inherited, call -> calls.contains(call.getId()));
            staying.put(fqn, helpers);
            return helpers;
        }
    }

    @Override
    public String getDisplayName() {
        return "Keep unconverted TestBench calls running through the Escape Hatch";
    }

    @Override
    public String getDescription() {
        return "Routes TestBench base-class calls no rule converted through `TestBenchEscapeHatch`, marks each with a "
                + "Migration Marker and makes the test method require Selenium.";
    }

    @Override
    public Scanned getInitialValue(ExecutionContext ctx) {
        return new Scanned();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scanned scanned) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (TestBenchSetupToUnified.isTestBenchClass(classDecl)) {
                    synchronized (scanned) {
                        scanned.classes.put(classDecl.getType().getFullyQualifiedName(), classDecl);
                    }
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned scanned) {
        return new JavaIsoVisitor<>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                // the LitTemplates the scanners found: a Template's element is no custom element
                return LitTemplates.with(ctx, () -> super.visitCompilationUnit(cu, ctx));
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getBody() == null || !Pages.hasPageParameter(method)) {
                    return method;
                }
                Map<UUID, List<String>> markers = MigrationMarkers.collect(getCursor());
                AtomicBoolean escapes = new AtomicBoolean();
                AtomicBoolean pageObjects = new AtomicBoolean();
                getCursor().putMessage(LEFTOVERS, escapes);
                getCursor().putMessage(PAGE_OBJECTS, pageObjects);
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!escapes.get()) {
                    // calls on a custom element keep their markers: the test runs on Selenium all the same
                    return requiresSeleniumIfItCallsAStayingHelper(MigrationMarkers.write(m, markers), ctx);
                }
                m = MigrationMarkers.write(m, markers);
                maybeAddImport(ESCAPE_HATCH);
                m = JavaTemplate.builder("TestBenchEscapeHatch " + VARIABLE + " = TestBenchEscapeHatch.of(#{any("
                                + TestBenchSetupToUnified.UNIFIED_PAGE + ")});")
                        .imports(ESCAPE_HATCH)
                        .javaParser(Stubs.parser())
                        .build()
                        .apply(updateCursor(m), m.getBody().getStatements().get(0).getCoordinates().before(),
                                Stubs.page(Pages.parameterName(m)));
                if (!Helpers.isBrowserTest(m)) {
                    return m;
                }
                return requiresSelenium(m);
            }

            /**
             * A test that calls a helper of its class hierarchy going through the Escape Hatch or using a custom
             * element, or gets a custom element from a call, runs on Selenium only, too.
             */
            private J.MethodDeclaration requiresSeleniumIfItCallsAStayingHelper(J.MethodDeclaration m, ExecutionContext ctx) {
                J.ClassDeclaration c = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (!Helpers.isBrowserTest(m) || c == null) {
                    return m;
                }
                // the class's own helpers as this recipe gets them, after the lookups converted; inherited ones as scanned
                Set<String> inherited = scanned.inherited(c.getType(), ctx);
                Set<String> staying = new HashSet<>(inherited);
                staying.addAll(Helpers.using(c, inherited, call -> (call.getSelect() == null || Helpers.isSuper(call.getSelect()))
                        && isTestBaseCall(call) || CustomElements.used(call)));
                AtomicBoolean calls = new AtomicBoolean();
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call, Integer p) {
                        if (call.getSelect() == null && staying.contains(call.getSimpleName()) || CustomElements.used(call)) {
                            calls.set(true);
                        }
                        return super.visitMethodInvocation(call, p);
                    }
                }.visit(m.getBody(), 0);
                return calls.get() ? requiresSelenium(m) : m;
            }

            private J.MethodDeclaration requiresSelenium(J.MethodDeclaration m) {
                if (m.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), REQUIRES_TESTING_TOOL)
                        || a.getSimpleName().equals("RequiresTestingTool"))) {
                    return m; // gated already, by hand or in an earlier cycle
                }
                maybeAddImport(REQUIRES_TESTING_TOOL);
                maybeAddImport(TESTING_TOOL);
                return JavaTemplate.builder("@RequiresTestingTool(TestingTool.SELENIUM)")
                        .imports(REQUIRES_TESTING_TOOL, TESTING_TOOL)
                        .javaParser(Stubs.parser())
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().addAnnotation((a, b) -> 1));
            }

            /** {@code this} handed to a page object or helper as the TestBench test: it gets the Escape Hatch instead. */
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                AtomicBoolean escapes = getCursor().getNearestMessage(LEFTOVERS);
                Object parent = getCursor().getParentTreeCursor().getValue();
                J.ClassDeclaration c = getCursor().firstEnclosing(J.ClassDeclaration.class);
                boolean argument = parent instanceof J.NewClass n && n.getArguments().contains(identifier)
                        || parent instanceof J.MethodInvocation call && call.getArguments().contains(identifier);
                if (escapes == null || !id.getSimpleName().equals("this") || !argument || c == null
                        || !TestBenchSetupToUnified.isTestBenchClass(c)
                        || !MigrationMarkers.add(getCursor(), "TestBench page object: "
                                + MigrationMarkers.code((J) parent, getCursor().getParentTreeCursor().getParentTreeCursor()))) {
                    return id;
                }
                escapes.set(true);
                return new J.Identifier(Tree.randomId(), id.getPrefix(), Markers.EMPTY, emptyList(), VARIABLE,
                        JavaType.ShallowClass.build(ESCAPE_HATCH), null);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                AtomicBoolean pageObjects = getCursor().getNearestMessage(PAGE_OBJECTS);
                JavaType.FullyQualified element = CustomElements.receiver(method);
                if (pageObjects != null && element != null && !MigrationMarkers.marked(getCursor(), CustomElements::marks)
                        && MigrationMarkers.add(getCursor(), CustomElements.marker(element))) {
                    // a custom element stays TestBench as it is: one marker on the statement, the first of its chain
                    pageObjects.set(true);
                }
                AtomicBoolean escapes = getCursor().getNearestMessage(LEFTOVERS);
                if (escapes == null || m.getSelect() != null && !Helpers.isSuper(m.getSelect()) || !isTestBaseCall(m)
                        || !MigrationMarkers.add(getCursor(), "TestBench Escape Hatch: " + MigrationMarkers.code(method, getCursor().getParentTreeCursor())
                                + (isCustomElementLookup(method) ? CustomElements.LOOKUP_SUFFIX : ""))) {
                    return m;
                }
                escapes.set(true);
                return m.getPadding().withSelect(JRightPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY,
                        Markers.EMPTY, emptyList(), VARIABLE, JavaType.ShallowClass.build(ESCAPE_HATCH), null)));
            }
        };
    }

    /** A {@code $(X.class)} of an application's own {@code @Element} class, not one TestBench ships. */
    private static boolean isCustomElementLookup(J.MethodInvocation m) {
        if (!m.getSimpleName().equals("$") || m.getArguments().size() != 1
                || !(m.getArguments().get(0) instanceof J.FieldAccess classLiteral)
                || !(classLiteral.getTarget().getType() instanceof JavaType.FullyQualified element)) {
            return false;
        }
        return !CustomElements.shippedByTestBench(element) && element.getAnnotations().stream()
                .anyMatch(a -> a.getFullyQualifiedName().equals("com.vaadin.testbench.elementsbase.Element"));
    }

    /**
     * The calls in the class, as scanned, that stay TestBench once the recipes ran: calls on or returning a custom
     * element, and calls to TestBench's test base no recipe converts. A lookup converts as
     * {@link TestBenchLookupsToUnified} converts it; {@code getDriver().get(..)} becomes a navigation, and the driver's
     * own lifecycle goes ({@link TestBenchDriverLifecycleRemoved}).
     */
    private static Set<UUID> callsStayingTestBench(J.ClassDeclaration c) {
        Set<UUID> calls = new HashSet<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call, Integer p) {
                J.MethodInvocation m = super.visitMethodInvocation(call, p);
                if (CustomElements.used(call) || (call.getSelect() == null || Helpers.isSuper(call.getSelect()))
                        && isTestBaseCall(call) && !converts(call)) {
                    calls.add(call.getId());
                }
                return m;
            }

            private boolean converts(J.MethodInvocation call) {
                Object parent = getCursor().getParentTreeCursor().getValue();
                return switch (call.getSimpleName()) {
                    case "$" -> Chains.ofDollar(call) != null && Chains.supported(getCursor(), false, Chains.ofDollar(call));
                    case "getDriver" -> parent instanceof J.MethodInvocation next && next.getSelect() == call
                            && Set.of("get", "quit", "close").contains(next.getSimpleName());
                    case "setDriver" -> true;
                    default -> false;
                };
            }
        }.visit(c, 0);
        return calls;
    }

    static boolean isTestBaseCall(J.MethodInvocation m) {
        return isTestBaseMethod(m.getMethodType());
    }

    /** A method of TestBench's test base, or a test class's override of one ({@code getDriver()}). */
    static boolean isTestBaseMethod(JavaType.Method type) {
        for (JavaType.Method t = type; t != null; t = TypeUtils.findOverriddenMethod(t).orElse(null)) {
            if (TEST_BASE_TYPES.contains(t.getDeclaringType().getFullyQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}
