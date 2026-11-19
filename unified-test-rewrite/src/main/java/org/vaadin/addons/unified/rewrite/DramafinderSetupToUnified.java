package org.vaadin.addons.unified.rewrite;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

/**
 * The setup of a Dramafinder test (ADR 0006): drops {@code extends SpringPlaywrightIT} /
 * {@code extends AbstractBasePlaywrightIT} and its {@code getUrl()} / {@code getView()} overrides, makes the class a
 * {@code @UnifiedSpringBootTest} or {@code @UnifiedUITest}, removes an {@code @EnabledIfSystemProperty} guard on
 * the Testing Tool, gives every test method and every helper using the test base a {@code UnifiedPage page}
 * parameter, and converts the base class's navigation calls and checks to the Unified Page. A test starts at the
 * view {@code getView()} named, unless it navigates first itself. A test extending a project's copy of
 * {@code SpringPlaywrightIT} gets the copy's class annotations ({@code @SpringBootTest}'s attributes,
 * {@code @ActiveProfiles}, {@code @Import}, ...), which applied to it through the copy, or a Migration Marker
 * naming them when the copy isn't among the sources.
 */
public class DramafinderSetupToUnified extends ScanningRecipe<Map<String, List<J.Annotation>>> {

    private static final String HELPERS = "unified.dramafinder.helpers";
    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    private static final String UNIFIED_SPRING_BOOT_TEST = "com.vaadin.testing.unified.spring.UnifiedSpringBootTest";
    private static final String ENABLED_IF_SYSTEM_PROPERTY = "org.junit.jupiter.api.condition.EnabledIfSystemProperty";
    private static final Set<String> TEST_ANNOTATIONS = Set.of(SPRING_BOOT_TEST, UNIFIED_SPRING_BOOT_TEST,
            TestBenchSetupToUnified.UNIFIED_UI_TEST);

    @Override
    public String getDisplayName() {
        return "Migrate Dramafinder test setup to a Unified Page";
    }

    @Override
    public String getDescription() {
        return "Drops `extends SpringPlaywrightIT` / `extends AbstractBasePlaywrightIT`, gives each test method, and each "
                + "helper using the test base, a `UnifiedPage page` parameter, and moves navigation to the page.";
    }

    /** For each project copy of {@code SpringPlaywrightIT}: its class annotations. */
    @Override
    public Map<String, List<J.Annotation>> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, List<J.Annotation>> copies) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (Dramafinder.isCopiedSpringBase(classDecl.getType())) {
                    copies.put(classDecl.getType().getFullyQualifiedName(), classDecl.getLeadingAnnotations());
                }
                DramafinderCopies.collect(classDecl, ctx);
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, List<J.Annotation>> copies) {
        return new JavaIsoVisitor<>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration original, ExecutionContext ctx) {
                if (!Dramafinder.isDramafinderClass(original) || !Dramafinder.migratable(original, DramafinderCopies.of(ctx))) {
                    return super.visitClassDeclaration(original, ctx);
                }
                // the members the test takes from a copied base class, as its own
                DramafinderCopies.Merged merged = DramafinderCopies.merge(original, DramafinderCopies.of(ctx));
                J.ClassDeclaration classDecl = merged.c();
                for (Statement s : classDecl.getBody().getStatements()) {
                    if (merged.inlined().containsKey(s.getId())) {
                        typesIn(s).forEach(t -> maybeAddImport(t));
                    }
                }
                Set<String> helpers = new java.util.HashSet<>(Helpers.using(classDecl, Set.of(), Dramafinder::usesTestBase));
                // a setupTest() that seeds and then calls super.setupTest() becomes a plain @BeforeEach
                Set<java.util.UUID> onlySuperSetup = new java.util.HashSet<>();
                for (Statement s : classDecl.getBody().getStatements()) {
                    if (s instanceof J.MethodDeclaration m && Dramafinder.isSeedingSetup(m)) {
                        helpers.remove(m.getSimpleName());
                        if (m.getBody().getStatements().size() == 1) {
                            onlySuperSetup.add(m.getId());
                        }
                    }
                }
                getCursor().putMessage(HELPERS, helpers);
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                        .filter(s -> !onlySuperSetup.contains(s.getId())).toList()));
                List<String> beforeEach = beforeEachOf(classDecl, helpers);
                c = c.withBody(c.getBody().withStatements(startAtView(classDecl, Dramafinder.viewOf(classDecl),
                        Helpers.callBeforeEachFirst(c, beforeEach))));
                // getUrl() and getView(): the base URL is configuration, the view is now each test's first step
                c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                        .filter(s -> !(s instanceof J.MethodDeclaration m && Dramafinder.CONVERTED_OVERRIDES.contains(m.getSimpleName())
                                && m.getParameters().stream().allMatch(p -> p instanceof J.Empty)))
                        .toList()));
                boolean spring = Dramafinder.isSpringClass(classDecl);
                JavaType.FullyQualified parent = TypeUtils.asFullyQualified(classDecl.getExtends().getType());
                c = c.withExtends(null);
                maybeRemoveImport(Dramafinder.BASE);
                maybeRemoveImport(Dramafinder.SPRING_BASE);
                if (parent != null) {
                    maybeRemoveImport(parent.getFullyQualifiedName()); // a copy of SpringPlaywrightIT
                    c = withCopiedAnnotations(c, copies.getOrDefault(parent.getFullyQualifiedName(), List.of()));
                }
                c = withoutToolGuard(c, ctx);
                if (c.getLeadingAnnotations().stream().noneMatch(a -> TEST_ANNOTATIONS.stream()
                        .anyMatch(t -> TypeUtils.isOfClassType(a.getType(), t)))) { // else @SpringBootTest becomes @UnifiedSpringBootTest
                    String annotation = spring ? UNIFIED_SPRING_BOOT_TEST : TestBenchSetupToUnified.UNIFIED_UI_TEST;
                    String simpleName = annotation.substring(annotation.lastIndexOf('.') + 1);
                    String pkg = annotation.substring(0, annotation.lastIndexOf('.'));
                    maybeAddImport(annotation);
                    c = JavaTemplate.builder("@" + simpleName)
                            .imports(annotation)
                            .javaParser(Stubs.parser("package " + pkg + "; public @interface " + simpleName + " {}"))
                            .build()
                            .apply(updateCursor(c), c.getCoordinates().addAnnotation((a, b) -> 0));
                }
                return parent != null && Dramafinder.isCopiedSpringBase(parent)
                        && !copies.containsKey(parent.getFullyQualifiedName()) ? withUnseenCopyMarker(c, parent) : c;
            }

            /**
             * The class with a Migration Marker for the copied base class's annotations: the copy is on the classpath
             * but not among the migrated sources (another module, a jar), so its annotations, and their arguments,
             * can't be carried over.
             */
            private J.ClassDeclaration withUnseenCopyMarker(J.ClassDeclaration c, JavaType.FullyQualified copy) {
                List<String> names = new ArrayList<>();
                names.add("@SpringBootTest attributes");
                for (JavaType.FullyQualified a : copy.getAnnotations()) {
                    if (!a.getFullyQualifiedName().equals(SPRING_BOOT_TEST)) {
                        names.add("@" + a.getClassName());
                    }
                }
                List<Comment> comments = new ArrayList<>(c.getPrefix().getComments());
                comments.add(new TextComment(false, " TODO unified: extended " + copy.getFullyQualifiedName()
                        + ", whose source the recipe didn't see: copy its class annotations here ("
                        + String.join(", ", names) + "), with their arguments", "\n" + c.getPrefix().getIndent(),
                        Markers.EMPTY));
                return c.withPrefix(Space.build(c.getPrefix().getWhitespace(), comments));
            }

            /**
             * The class with the copied base class's annotations it doesn't have itself, before its own: Spring found
             * them on the copy, and nothing does once the class no longer extends it.
             */
            private J.ClassDeclaration withCopiedAnnotations(J.ClassDeclaration c, List<J.Annotation> inherited) {
                List<J.Annotation> own = c.getLeadingAnnotations();
                boolean ownTestAnnotation = own.stream().anyMatch(a -> TEST_ANNOTATIONS.stream()
                        .anyMatch(t -> TypeUtils.isOfClassType(a.getType(), t)));
                List<J.Annotation> carried = new ArrayList<>();
                for (J.Annotation a : inherited) {
                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                    if (type == null || own.stream().anyMatch(o -> TypeUtils.isOfType(o.getType(), type))
                            || ownTestAnnotation && TEST_ANNOTATIONS.contains(type.getFullyQualifiedName())) {
                        continue;
                    }
                    typesIn(a).forEach(t -> maybeAddImport(t, false));
                    carried.add(a.withId(Tree.randomId()));
                }
                if (carried.isEmpty()) {
                    return c;
                }
                Space newLine = Space.format("\n" + c.getPrefix().getIndent());
                List<J.Annotation> annotations = new ArrayList<>();
                for (int i = 0; i < carried.size(); i++) {
                    annotations.add(carried.get(i).withPrefix(i == 0 ? Space.EMPTY : newLine));
                }
                if (!own.isEmpty()) {
                    annotations.add(own.get(0).withPrefix(newLine));
                    annotations.addAll(own.subList(1, own.size()));
                } else if (!c.getModifiers().isEmpty()) {
                    List<J.Modifier> modifiers = new ArrayList<>(c.getModifiers());
                    modifiers.set(0, modifiers.get(0).withPrefix(newLine));
                    c = c.withModifiers(modifiers);
                } else {
                    c = c.getPadding().withKind(c.getPadding().getKind().withPrefix(newLine));
                }
                return c.withLeadingAnnotations(annotations);
            }

            /**
             * A {@code setupTest()} override that seeds the test, as a plain {@code @BeforeEach}: without its final
             * {@code super.setupTest()}, which opened the page and navigated, now each test's first step, and
             * without {@code @Override}, as nothing is left to override.
             */
            private J.MethodDeclaration seedingBeforeEach(J.MethodDeclaration m) {
                List<Statement> body = m.getBody().getStatements();
                m = m.withBody(m.getBody().withStatements(body.subList(0, body.size() - 1)));
                List<J.Annotation> annotations = new ArrayList<>(m.getLeadingAnnotations());
                for (int i = 0; i < annotations.size(); i++) {
                    if (TypeUtils.isOfClassType(annotations.get(i).getType(), "java.lang.Override")) {
                        J.Annotation removed = annotations.remove(i);
                        if (i == 0 && !annotations.isEmpty()) {
                            annotations.set(0, annotations.get(0).withPrefix(removed.getPrefix()));
                        } else if (i == 0) {
                            m = m.withModifiers(withFirstPrefix(m.getModifiers(), removed.getPrefix()));
                        }
                        break;
                    }
                }
                m = m.withLeadingAnnotations(annotations);
                if (annotations.stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), TestBenchSetupToUnified.BEFORE_EACH))) {
                    return m;
                }
                maybeAddImport(TestBenchSetupToUnified.BEFORE_EACH);
                return JavaTemplate.builder("@BeforeEach")
                        .imports(TestBenchSetupToUnified.BEFORE_EACH)
                        .javaParser(Stubs.parser("package org.junit.jupiter.api; public @interface BeforeEach {}"))
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().addAnnotation((a, b) -> 0));
            }

            private List<J.Modifier> withFirstPrefix(List<J.Modifier> modifiers, Space prefix) {
                List<J.Modifier> result = new ArrayList<>(modifiers);
                if (!result.isEmpty()) {
                    result.set(0, result.get(0).withPrefix(prefix));
                }
                return result;
            }

            /** Without {@code @EnabledIfSystemProperty(named = "vaadin.testing.tool", ..)}: the test now runs on any tool. */
            private J.ClassDeclaration withoutToolGuard(J.ClassDeclaration c, ExecutionContext ctx) {
                boolean guarded = c.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(),
                        ENABLED_IF_SYSTEM_PROPERTY) && a.getArguments() != null && a.getArguments().stream().anyMatch(arg ->
                        arg instanceof J.Assignment as && as.getVariable() instanceof J.Identifier n
                                && n.getSimpleName().equals("named") && as.getAssignment() instanceof J.Literal l
                                && "vaadin.testing.tool".equals(l.getValue())));
                if (!guarded) {
                    return c;
                }
                maybeRemoveImport(ENABLED_IF_SYSTEM_PROPERTY);
                return (J.ClassDeclaration) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + ENABLED_IF_SYSTEM_PROPERTY))
                        .visitNonNull(c, ctx, getCursor().getParentOrThrow());
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                Set<String> helpers = getCursor().getNearestMessage(HELPERS);
                if (helpers != null && Dramafinder.isSeedingSetup(method)) {
                    return seedingBeforeEach(m);
                }
                if (helpers == null || Pages.hasPageParameter(m)
                        || !Helpers.isBrowserTest(m) && !helpers.contains(m.getSimpleName())) {
                    return m;
                }
                maybeAddImport(TestBenchSetupToUnified.UNIFIED_PAGE);
                if (helpers.contains(m.getSimpleName())) {
                    maybeRemoveImport(TestBenchSetupToUnified.BEFORE_EACH);
                    m = (J.MethodDeclaration) new RemoveAnnotationVisitor(
                            new AnnotationMatcher("@" + TestBenchSetupToUnified.BEFORE_EACH))
                            .visitNonNull(m, ctx, getCursor().getParentOrThrow());
                }
                List<Statement> others = m.getParameters().stream().filter(p -> !(p instanceof J.Empty)).toList();
                J.MethodDeclaration withPage = JavaTemplate.builder("UnifiedPage page")
                        .imports(TestBenchSetupToUnified.UNIFIED_PAGE)
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
                Set<String> helpers = getCursor().getNearestMessage(HELPERS);
                if (helpers == null) {
                    return m;
                }
                if (m.getSelect() == null && helpers.contains(m.getSimpleName())) {
                    List<Expression> arguments = new ArrayList<>();
                    arguments.add(Stubs.page());
                    for (Expression a : m.getArguments()) {
                        if (!(a instanceof J.Empty)) {
                            arguments.add(a.withPrefix(Space.SINGLE_SPACE));
                        }
                    }
                    return m.withArguments(arguments);
                }
                if (!Dramafinder.isBaseCall(method)) {
                    return m;
                }
                List<Expression> arguments = m.getArguments().stream().filter(a -> !(a instanceof J.Empty)).toList();
                return switch (m.getSimpleName()) {
                    case "navigate" -> onPage(m, "navigate", arguments);
                    case "assertAt" -> pageAssertion(m, "isAt", "java.lang.Class", arguments);
                    case "assertPath" -> pageAssertion(m, "hasPath", "java.lang.String", arguments);
                    case "assertQueryParameter" -> pageAssertion(m, "hasQueryParameter", "java.lang.String", arguments);
                    default -> m; // getPage(): the Escape Hatch's page
                };
            }

            /** {@code page.<name>(..)} with the call's arguments. */
            private J.MethodInvocation onPage(J.MethodInvocation m, String name, List<Expression> arguments) {
                Object[] parameters = new Object[arguments.size() + 1];
                parameters[0] = Stubs.page();
                for (int i = 0; i < arguments.size(); i++) {
                    parameters[i + 1] = arguments.get(i);
                }
                String placeholders = String.join(", ", java.util.Collections.nCopies(arguments.size(), "#{any()}"));
                return JavaTemplate.builder("#{any(" + TestBenchSetupToUnified.UNIFIED_PAGE + ")}." + name + "(" + placeholders + ")")
                        .javaParser(Stubs.parser())
                        .build()
                        .<J.MethodInvocation>apply(getCursor(), m.getCoordinates().replace(), parameters)
                        .withId(m.getId()).withPrefix(m.getPrefix());
            }

            /** {@code assertThat(page).<name>(..)} with the call's arguments. */
            private J.MethodInvocation pageAssertion(J.MethodInvocation m, String name, String argumentType,
                    List<Expression> arguments) {
                maybeAddImport(TestBenchAssertionsToUnified.UNIFIED_ASSERTIONS, "assertThat", false);
                Object[] parameters = new Object[arguments.size() + 1];
                parameters[0] = Stubs.page();
                for (int i = 0; i < arguments.size(); i++) {
                    parameters[i + 1] = arguments.get(i);
                }
                String placeholders = String.join(", ",
                        java.util.Collections.nCopies(arguments.size(), "#{any(" + argumentType + ")}"));
                return JavaTemplate.builder("assertThat(#{any(" + TestBenchSetupToUnified.UNIFIED_PAGE + ")})." + name
                                + "(" + placeholders + ")")
                        .staticImports(TestBenchAssertionsToUnified.UNIFIED_ASSERTIONS + ".assertThat")
                        .javaParser(Stubs.parser())
                        .build()
                        .<J.MethodInvocation>apply(getCursor(), m.getCoordinates().replace(), parameters)
                        .withId(m.getId()).withPrefix(m.getPrefix());
            }
        };
    }

    /** The classes a tree names by their simple name: an annotation's type, a field's type, a class literal's. */
    private static Set<String> typesIn(J tree) {
        Set<String> types = new LinkedHashSet<>();
        new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> found) {
                if (identifier.getFieldType() == null && identifier.getType() instanceof JavaType.FullyQualified t
                        && t.getClassName().equals(identifier.getSimpleName())) {
                    found.add(t.getFullyQualifiedName());
                }
                return identifier;
            }
        }.visit(tree, types);
        return types;
    }

    /**
     * The class's statements with every test that doesn't navigate first starting at the view, as the base class's
     * per-test setup opened it.
     */
    private static List<Statement> startAtView(J.ClassDeclaration original, String view, List<Statement> statements) {
        List<Statement> originals = original.getBody().getStatements();
        List<Statement> result = new ArrayList<>();
        for (Statement s : statements) {
            if (s instanceof J.MethodDeclaration m && Helpers.isBrowserTest(m) && m.getBody() != null
                    && !m.getBody().getStatements().isEmpty() && !navigatesFirst(originals, m)) {
                Space prefix = m.getBody().getStatements().get(0).getPrefix().withComments(emptyList());
                List<Statement> body = new ArrayList<>();
                body.add(navigate(view, prefix));
                body.addAll(m.getBody().getStatements());
                s = m.withBody(m.getBody().withStatements(body));
            }
            result.add(s);
        }
        return result;
    }

    /** Whether the test's own first statement navigates. */
    private static boolean navigatesFirst(List<Statement> originals, J.MethodDeclaration m) {
        return originals.stream().anyMatch(o -> o instanceof J.MethodDeclaration om && om.getId().equals(m.getId())
                && om.getBody() != null && !om.getBody().getStatements().isEmpty()
                && om.getBody().getStatements().get(0) instanceof J.MethodInvocation call
                && call.getSimpleName().equals("navigate"));
    }

    private static J.MethodInvocation navigate(String view, Space prefix) {
        JavaType.Method type = new JavaType.Method(null, 1L, JavaType.ShallowClass.build(TestBenchSetupToUnified.UNIFIED_PAGE),
                "navigate", JavaType.Primitive.Void, List.of("path"), List.of(JavaType.Primitive.String), null, null,
                null, null);
        return new J.MethodInvocation(Tree.randomId(), prefix, Markers.EMPTY, JRightPadded.build(Stubs.page()), null,
                new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "navigate", type, null),
                JContainer.build(Space.EMPTY, List.of(JRightPadded.<Expression>build(Stubs.literal(view))), Markers.EMPTY),
                type);
    }

    private static List<String> beforeEachOf(J.ClassDeclaration c, Set<String> helpers) {
        List<String> names = new ArrayList<>();
        for (Statement s : c.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration m && helpers.contains(m.getSimpleName())
                    && m.getLeadingAnnotations().stream()
                            .anyMatch(a -> TypeUtils.isOfClassType(a.getType(), TestBenchSetupToUnified.BEFORE_EACH))) {
                names.add(m.getSimpleName());
            }
        }
        return names;
    }
}
