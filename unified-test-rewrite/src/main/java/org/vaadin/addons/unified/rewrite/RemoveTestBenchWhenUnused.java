package org.vaadin.addons.unified.rewrite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Result;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId;
import org.openrewrite.maven.ChangeDependencyScope;
import org.openrewrite.maven.RemoveDependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.xml.tree.Xml;

/**
 * The Selenium adapter brings TestBench core and some component element jars, not all of them: TestBench stays in
 * the build while a test source still uses it after the code migration (an unconverted page object, an element class,
 * a {@code TestBenchEscapeHatch}), and goes once none does. A kept {@code vaadin-testbench-junit5} becomes
 * {@code vaadin-testbench-junit6}, which brings every element jar and runs on the JUnit 6 the migrated tests use.
 *
 * <p>The scan phase sees the sources before any recipe changed them, so this runs the test-code migration on the
 * scanned Java sources itself and looks at its result.
 */
public class RemoveTestBenchWhenUnused extends ScanningRecipe<RemoveTestBenchWhenUnused.Seen> {

    private static final String RECIPES = "/META-INF/rewrite/testbench.yml";

    /** The Java sources, and the directories of those still using TestBench after the code migration. */
    public static final class Seen {
        final List<SourceFile> javaSources = new ArrayList<>();
        @Nullable Set<Path> usingTestBench;
    }

    @Option(displayName = "Include page objects",
            description = "Whether the code migration also migrates page objects, as `MigrateTestBenchToUnified` does.",
            required = false)
    private final @Nullable Boolean includePageObjects;

    public RemoveTestBenchWhenUnused() {
        this(null);
    }

    public RemoveTestBenchWhenUnused(@Nullable Boolean includePageObjects) {
        this.includePageObjects = includePageObjects;
    }

    public @Nullable Boolean getIncludePageObjects() {
        return includePageObjects;
    }

    @Override
    public String getDisplayName() {
        return "Remove TestBench when no migrated test uses it";
    }

    @Override
    public String getDescription() {
        return "Removes direct `vaadin-testbench*` dependencies when no test source uses TestBench after the code "
                + "migration; otherwise keeps them, with `vaadin-testbench-junit5` becoming `vaadin-testbench-junit6`.";
    }

    @Override
    public Seen getInitialValue(ExecutionContext ctx) {
        return new Seen();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Seen seen) {
        return new TreeVisitor<>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile && tree instanceof SourceFile source) {
                    synchronized (seen) {
                        seen.javaSources.add(source);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Seen seen) {
        return new TreeVisitor<>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document pom) || !hasTestBench(pom)) {
                    return tree;
                }
                Path module = pom.getSourcePath().toAbsolutePath().getParent();
                boolean used = usingTestBench(seen, ctx).stream()
                        .anyMatch(dir -> module == null || dir.startsWith(module));
                if (!used) {
                    return new RemoveDependency("com.vaadin", "vaadin-testbench*", null).getVisitor().visit(tree, ctx);
                }
                ChangeDependencyGroupIdAndArtifactId toJUnit6 = new ChangeDependencyGroupIdAndArtifactId(
                        "com.vaadin", "vaadin-testbench-junit5", null, "vaadin-testbench-junit6", null, null);
                Tree t = toJUnit6.getVisitor(toJUnit6.getInitialValue(ctx)).visit(tree, ctx);
                return new ChangeDependencyScope("com.vaadin", "vaadin-testbench-junit6", "test").getVisitor()
                        .visit(t, ctx);
            }
        };
    }

    /** Runs the test-code migration once, on every Java source, and finds the directories of those using TestBench. */
    private Set<Path> usingTestBench(Seen seen, ExecutionContext ctx) {
        synchronized (seen) {
            if (seen.usingTestBench == null) {
                Map<Path, SourceFile> migrated = new LinkedHashMap<>();
                seen.javaSources.forEach(source -> migrated.put(source.getSourcePath(), source));
                ExecutionContext nested = new InMemoryExecutionContext(ctx.getOnError());
                for (Result result : codeMigration().run(new InMemoryLargeSourceSet(seen.javaSources), nested)
                        .getChangeset().getAllResults()) {
                    if (result.getBefore() != null) {
                        migrated.remove(result.getBefore().getSourcePath());
                    }
                    if (result.getAfter() != null) {
                        migrated.put(result.getAfter().getSourcePath(), result.getAfter());
                    }
                }
                seen.usingTestBench = migrated.values().stream()
                        .filter(source -> source instanceof JavaSourceFile cu && !isMainSource(source)
                                && usesTestBench(cu))
                        .map(source -> source.getSourcePath().toAbsolutePath().getParent())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            }
            return seen.usingTestBench;
        }
    }

    /** The code part of {@link MigrateTestBenchToUnified}, with the same page-object option. */
    private Recipe codeMigration() {
        Environment recipes = Environment.builder().load(DeclaredRecipes.load(RECIPES)).build();
        List<Recipe> list = new ArrayList<>();
        if (Boolean.TRUE.equals(includePageObjects)) {
            list.add(new TestBenchPageObjectsToUnified());
        }
        list.add(recipes.activateRecipes("org.vaadin.addons.unified.rewrite.MigrateTestBenchTestCode"));
        return new CompositeRecipe(list);
    }

    private static boolean hasTestBench(Xml.Document pom) {
        return pom.getMarkers().findFirst(MavenResolutionResult.class).map(MavenResolutionResult::getPom)
                .map(p -> p.getRequestedDependencies().stream().anyMatch(d -> "com.vaadin".equals(d.getGroupId())
                        && d.getArtifactId().startsWith("vaadin-testbench")))
                .orElse(false);
    }

    private static boolean isMainSource(SourceFile source) {
        return source.getMarkers().findFirst(JavaSourceSet.class).map(set -> "main".equals(set.getName()))
                .orElse(false);
    }

    /** A TestBench type: TestBench's own, a component's element class, or the TestBench Escape Hatch. */
    static boolean usesTestBench(JavaSourceFile cu) {
        return cu.getTypesInUse().getTypesInUse().stream()
                .anyMatch(type -> type instanceof JavaType.FullyQualified fq && isTestBench(fq.getFullyQualifiedName()))
                || cu.getImports().stream().map(J.Import::getTypeName).anyMatch(RemoveTestBenchWhenUnused::isTestBench);
    }

    private static boolean isTestBench(String name) {
        return name.startsWith("com.vaadin.testbench.") || name.contains(".testbench.")
                || name.equals(TestBenchLeftoversToEscapeHatch.ESCAPE_HATCH);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RemoveTestBenchWhenUnused other
                && Objects.equals(includePageObjects, other.includePageObjects);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(includePageObjects);
    }
}
