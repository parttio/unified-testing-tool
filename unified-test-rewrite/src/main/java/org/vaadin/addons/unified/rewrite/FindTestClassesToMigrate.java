package org.vaadin.addons.unified.rewrite;

import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * The dry run of the migrations: every TestBench, Browserless and Dramafinder test class, with whether the migration
 * converts it or skips it, and for a skipped class what keeps it from migrating. Each class gets a search marker
 * ({@code migrates}, {@code skipped: ...}), and {@link TestClasses} lists them, so one run before the migration
 * shows every blocker instead of one per migration run (issue #352). It reports the same decisions as the
 * precondition recipes ({@code Find*Tests}) and the Migration Markers on skipped classes.
 */
public class FindTestClassesToMigrate extends ScanningRecipe<Void> {

    static final String MIGRATES = "migrates";
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
                @Column(displayName = "Status", description = "`migrates`, or `skipped`: the migration leaves the "
                        + "class as it is.") String status,
                @Column(displayName = "Reason", description = "For a skipped class, what keeps it from migrating.")
                String reason) {
        }
    }

    @Override
    public Void getInitialValue(ExecutionContext ctx) {
        return null;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Void acc) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                DramafinderCopies.collect(classDecl, ctx); // a Dramafinder test's blockers depend on its copied base
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Void acc) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                String tool;
                String reason;
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
                } else {
                    return c;
                }
                String status = reason.isEmpty() ? MIGRATES : SKIPPED;
                testClasses.insertRow(ctx, new TestClasses.Row(
                        getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString().replace('\\', '/'),
                        classDecl.getType() == null ? classDecl.getSimpleName() : classDecl.getType().getFullyQualifiedName(),
                        tool, status, reason));
                return SearchResult.found(c, reason.isEmpty() ? status : status + ": " + reason);
            }
        };
    }
}
