package org.vaadin.addons.unified.rewrite;

import java.util.Map;
import java.util.TreeMap;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * The dry run of the migrations: every TestBench, Browserless and Dramafinder test class, with whether the migration
 * converts it or skips it, and for a skipped class what keeps it from migrating. Each class gets a search marker
 * ({@code migrates}, {@code skipped: ...}), and {@link TestClasses} lists them, so one run before the migration
 * shows every blocker instead of one per migration run (issue #352). It reports the same decisions as the
 * precondition recipes ({@code Find*Tests}) and the Migration Markers on skipped classes. A TestBench class also lists
 * the application's page objects and custom elements it uses, as {@link TestBenchPageObjectsToUnified} and
 * {@link CustomElements} decide them, and {@code migrates partly} when one of them stays TestBench (issue #527).
 */
public class FindTestClassesToMigrate extends ScanningRecipe<TestBenchPageObjectsToUnified.Found> {

    static final String MIGRATES = "migrates";
    static final String MIGRATES_PARTLY = "migrates partly";
    static final String CONVERTS = "converts with includePageObjects";
    static final String STAYS = "stays TestBench (Escape Hatch)";
    static final String SKIPPED = "skipped";
    static final String PASSES_ITSELF = "passes itself (this) to a page object or helper; "
            + "run with includePageObjects, or make it take a UnifiedPage";
    static final String USES_CONTEXT = "uses the Browserless application or user context beyond opening a window; "
            + "use page.openWindow() or page.openAsUser(..) instead";

    final transient TestClasses testClasses = new TestClasses(this);

    @Override
    public String getDisplayName() {
        return "Find the test classes the migrations convert or skip";
    }

    @Override
    public String getDescription() {
        return "Marks every TestBench, Browserless and Dramafinder test class with whether the migration converts it "
                + "or skips it, and why, and lists them in a data table. Run it with `dryRun` before the migration: "
                + "it changes nothing but the markers.";
    }

    /** One row per test class the migration recipes look at. */
    public static class TestClasses extends DataTable<TestClasses.Row> {

        public TestClasses(Recipe recipe) {
            super(recipe, "Test classes to migrate",
                    "Each TestBench, Browserless and Dramafinder test class, and whether the migration converts it.");
        }

        /** One test class. */
        public record Row(
                @Column(displayName = "Source file", description = "The file declaring the class.") String sourcePath,
                @Column(displayName = "Class", description = "The fully qualified name of the class.") String className,
                @Column(displayName = "Testing Tool", description = "TestBench, Browserless or Dramafinder.")
                String testingTool,
                @Column(displayName = "Status", description = "`migrates`; `migrates partly`: it uses a page object "
                        + "that stays TestBench behind the Escape Hatch; or `skipped`: the migration leaves the class as "
                        + "it is.") String status,
                @Column(displayName = "Reason", description = "For a skipped class, what keeps it from migrating.")
                String reason,
                @Column(displayName = "Page objects", description = "For a TestBench class, the application's page "
                        + "objects and custom elements it uses: each `converts with includePageObjects` or `stays "
                        + "TestBench (Escape Hatch)`, and why.") String pageObjects) {

            public Row(String sourcePath, String className, String testingTool, String status, String reason) {
                this(sourcePath, className, testingTool, status, reason, "");
            }
        }
    }

    @Override
    public TestBenchPageObjectsToUnified.Found getInitialValue(ExecutionContext ctx) {
        return new TestBenchPageObjectsToUnified().getInitialValue(ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(TestBenchPageObjectsToUnified.Found found) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                // which page objects convert with includePageObjects: the page-object recipe's own decision
                new TestBenchPageObjectsToUnified().getScanner(found).visit(cu, ctx);
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                DramafinderCopies.collect(classDecl, ctx); // a Dramafinder test's blockers depend on its copied base
                LitTemplates.collect(classDecl, ctx); // a LitTemplate's element converts: it is no custom element
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(TestBenchPageObjectsToUnified.Found found) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                String tool;
                String reason;
                String pageObjects = "";
                if (Dramafinder.isDramafinderClass(classDecl)) {
                    tool = "Dramafinder";
                    reason = String.join(", ", Dramafinder.blockers(classDecl, DramafinderCopies.of(ctx)));
                } else if (Browserless.isBrowserlessClass(classDecl)) {
                    tool = "Browserless";
                    reason = Browserless.passesItself(classDecl) ? PASSES_ITSELF
                            : Browserless.isContextClass(classDecl) && !BrowserlessContexts.migratable(classDecl)
                                    ? USES_CONTEXT : "";
                } else if (TestBenchSetupToUnified.isTestBenchClass(classDecl)) {
                    tool = "TestBench";
                    reason = "";
                    pageObjects = LitTemplates.with(ctx, () -> pageObjects(classDecl, found));
                } else {
                    return c;
                }
                String status = !reason.isEmpty() ? SKIPPED : pageObjects.contains(STAYS) ? MIGRATES_PARTLY : MIGRATES;
                testClasses.insertRow(ctx, new TestClasses.Row(
                        getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString().replace('\\', '/'),
                        classDecl.getType() == null ? classDecl.getSimpleName() : classDecl.getType().getFullyQualifiedName(),
                        tool, status, reason, pageObjects));
                String detail = reason.isEmpty() ? pageObjects : reason;
                return SearchResult.found(c, detail.isEmpty() ? status : status + ": " + detail);
            }
        };
    }

    /** The page objects and custom elements the class uses, by simple name, each with what the migration does to it. */
    private static String pageObjects(J.ClassDeclaration c, TestBenchPageObjectsToUnified.Found found) {
        Map<String, String> used = new TreeMap<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                add(identifier.getType());
                return super.visitIdentifier(identifier, p);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                if (method.getMethodType() != null) {
                    add(method.getMethodType().getReturnType());
                }
                return super.visitMethodInvocation(method, p);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, Integer p) {
                add(newClass.getType());
                return super.visitNewClass(newClass, p);
            }

            private void add(JavaType type) {
                JavaType.FullyQualified element = CustomElements.of(type);
                if (element != null) {
                    used.put(element.getClassName(), STAYS + ", a custom element extending "
                            + CustomElements.extended(element) + ": write a Wrapper (ADR 0003)");
                    return;
                }
                JavaType.FullyQualified t = TypeUtils.asFullyQualified(type);
                if (t != null && found.returnsByPageObject().containsKey(t.getFullyQualifiedName())) {
                    String blocked = found.blocked().get(t.getFullyQualifiedName());
                    used.put(t.getClassName(), blocked == null ? CONVERTS : STAYS + ", " + blocked);
                }
            }
        }.visit(c.getBody(), 0);
        StringBuilder text = new StringBuilder();
        used.forEach((name, what) -> text.append(text.isEmpty() ? "" : "; ").append(name).append(": ").append(what));
        return text.toString();
    }
}
