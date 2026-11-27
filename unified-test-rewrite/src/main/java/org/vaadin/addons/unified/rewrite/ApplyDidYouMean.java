package org.vaadin.addons.unified.rewrite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

/**
 * Applies the {@code Did you mean} of failed Unified UI Tests to their code: a repair as a diff to review, without
 * a model.
 * <p>
 * A lookup that finds nothing fails with what it asked for and, when one Near Miss is clearly what the test
 * meant, with the lookup to use instead. Both are in the test's {@code user-steps.json} ({@code failure.lookup}
 * and {@code failure.suggestion}). This recipe reads every {@code user-steps.json} under {@link #evidenceDir} and,
 * for each failure whose two lookups differ in one string only, replaces that string in the test's code.
 * <p>
 * <b>Where.</b> Only in the files on the failure's code path, and the scenario's test class: the call
 * {@code withLabel("Email")} is changed where it follows {@code find(UnifiedTextField.class)} of the failed
 * lookup's Wrapper; an item ({@code selectItem("Spain")}) wherever the call is in those files. Every such call in
 * those files is changed: the same lookup with the same value is broken the same way.
 * <p>
 * <b>Not applied:</b> a suggestion of another Wrapper type (that changes the code around it), a value that is not a
 * string literal in the code (a constant, a parameter), a lookup written in a file that is not on the code path.
 * <p>
 * It changes the test, not the application: when the application's new label is the mistake, revert the diff.
 * {@link Applied} lists each change.
 */
public class ApplyDidYouMean extends Recipe {

    private static final String FILE = "user-steps.json";
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern FIND = Pattern.compile("^find(?:Component)?\\((\\w+)\\.class\\)");
    private static final Pattern CALL = Pattern.compile("(\\w+)\\((?:[^()\"]|\"(?:[^\"\\\\]|\\\\.)*\")*$");
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", "node_modules", ".idea", ".gradle");

    @Option(displayName = "Evidence directory",
            description = "Where the failed run wrote its evidence; every `user-steps.json` under it is read, at any "
                    + "depth. Relative to the directory the build runs in. Default: that directory, which finds every "
                    + "module's `target/surefire-reports/unified-evidence` and `target/failsafe-reports/unified-evidence`.",
            example = "target/surefire-reports/unified-evidence", required = false)
    @Nullable
    String evidenceDir;

    final transient Applied applied = new Applied(this);
    private transient volatile List<Change> changes;

    public ApplyDidYouMean() {
    }

    public ApplyDidYouMean(@Nullable String evidenceDir) {
        this.evidenceDir = evidenceDir;
    }

    @Nullable
    public String getEvidenceDir() {
        return evidenceDir;
    }

    public void setEvidenceDir(@Nullable String evidenceDir) {
        this.evidenceDir = evidenceDir;
    }

    @Override
    public String getDisplayName() {
        return "Apply the `Did you mean` of failed Unified UI Tests";
    }

    @Override
    public String getDescription() {
        return "Reads the failures of a run from its evidence directory and, where a failed lookup came with a `Did you "
                + "mean`, writes the suggested value into the test: `withLabel(\"Email\")` becomes "
                + "`withLabel(\"E-mail\")`. Only string literals in the files on the failure's code path are changed. "
                + "The result is a diff to review: it fixes the test, also when the application is what changed by "
                + "mistake.";
    }

    /** One change a row of the data table records. */
    public static class Applied extends DataTable<Applied.Row> {

        public Applied(Recipe recipe) {
            super(recipe, "Did you mean, applied",
                    "Each value replaced in a test, with the scenario whose failure suggested it.");
        }

        /** One replaced value. */
        public record Row(
                @Column(displayName = "Source file", description = "The file changed.") String sourcePath,
                @Column(displayName = "Call", description = "The method whose argument was replaced.") String call,
                @Column(displayName = "From", description = "The value the test had.") String from,
                @Column(displayName = "To", description = "The value suggested by the failure.") String to,
                @Column(displayName = "Scenario", description = "A scenario that failed with this suggestion.")
                String scenario) {
        }
    }

    /**
     * One value to replace.
     *
     * @param wrapper the Wrapper's simple name the call must follow, {@code UnifiedTextField}; null for an item call
     * @param method the call, {@code withLabel}
     * @param argument which argument of the call, from 0
     * @param from the string the test has
     * @param to the string the failure suggests
     * @param files the ends of the paths it applies to: {@code demo/EditPersonTest.java}
     * @param scenario the scenario that failed
     */
    record Change(@Nullable String wrapper, String method, int argument, String from, String to, Set<String> files,
            String scenario) {

        boolean appliesTo(Path sourcePath) {
            String path = sourcePath.toString().replace('\\', '/');
            return files.stream().anyMatch(f -> path.equals(f) || path.endsWith("/" + f));
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile source && changes().stream()
                        .noneMatch(c -> c.appliesTo(((SourceFile) source).getSourcePath()))) {
                    return (J) tree;
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                Path path = getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getSourcePath();
                for (Change change : changes()) {
                    if (!change.appliesTo(path) || !m.getSimpleName().equals(change.method())
                            || m.getArguments().size() <= change.argument()
                            || !(m.getArguments().get(change.argument()) instanceof J.Literal literal)
                            || !change.from().equals(literal.getValue())
                            || (change.wrapper() != null && !follows(m, change.wrapper()))) {
                        continue;
                    }
                    List<Expression> arguments = new ArrayList<>(m.getArguments());
                    arguments.set(change.argument(), literal.withValue(change.to()).withValueSource(quoted(change.to())));
                    applied.insertRow(ctx, new Applied.Row(path.toString().replace('\\', '/'), change.method(),
                            change.from(), change.to(), change.scenario()));
                    return m.withArguments(arguments);
                }
                return m;
            }
        };
    }

    /** Whether the call's chain starts from {@code find(Wrapper.class)} or {@code findComponent(Wrapper.class)}. */
    private static boolean follows(J.MethodInvocation call, String wrapper) {
        Expression select = call.getSelect();
        while (select instanceof J.MethodInvocation before) {
            if ((before.getSimpleName().equals("find") || before.getSimpleName().equals("findComponent"))
                    && before.getArguments().size() == 1 && before.getArguments().get(0) instanceof J.FieldAccess type
                    && type.getSimpleName().equals("class") && type.getTarget() instanceof J.Identifier name
                    && name.getSimpleName().equals(wrapper)) {
                return true;
            }
            select = before.getSelect();
        }
        return false;
    }

    private static String quoted(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ---------------------------------------------------------------- the failures

    private List<Change> changes() {
        List<Change> known = changes;
        if (known == null) {
            synchronized (this) {
                known = changes;
                if (known == null) {
                    known = read(Path.of(evidenceDir == null || evidenceDir.isBlank() ? "." : evidenceDir));
                    changes = known;
                }
            }
        }
        return known;
    }

    /** The changes the failures under {@code dir} suggest: one per lookup, value and suggestion, its files merged. */
    static List<Change> read(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    return d.getFileName() != null && SKIPPED_DIRS.contains(d.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(FILE)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return List.of();
        }
        files.sort(null);
        Map<String, Change> merged = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        for (Path file : files) {
            try {
                change(mapper.readTree(file.toFile())).ifPresent(c -> merged.merge(
                        c.wrapper() + "#" + c.method() + "#" + c.argument() + "#" + c.from() + "#" + c.to(), c, (a, b) -> {
                            Set<String> both = new LinkedHashSet<>(a.files());
                            both.addAll(b.files());
                            return new Change(a.wrapper(), a.method(), a.argument(), a.from(), a.to(), both, a.scenario());
                        }));
            } catch (IOException | RuntimeException e) {
                // not a readable user-steps.json: nothing to apply from it
            }
        }
        return List.copyOf(merged.values());
    }

    /** The change one test's {@code user-steps.json} suggests, if its failure has one this recipe can apply. */
    static Optional<Change> change(JsonNode test) {
        JsonNode failure = test.path("failure");
        if (!"failed".equals(test.path("status").asText()) || !failure.path("lookup").isTextual()
                || !failure.path("suggestion").isTextual()) {
            return Optional.empty();
        }
        Set<String> files = new LinkedHashSet<>();
        addFile(files, test.path("testClass").asText(null), null);
        for (JsonNode site : failure.path("codePath")) {
            addFile(files, site.path("className").asText(null), site.path("file").asText(null));
        }
        String scenario = test.path("displayName").asText(test.path("testMethod").asText(""));
        return difference(failure.get("lookup").asText(), failure.get("suggestion").asText())
                .map(c -> new Change(c.wrapper(), c.method(), c.argument(), c.from(), c.to(), files, scenario));
    }

    /** {@code demo/EditPersonTest.java}: the class's package as directories, then its file. */
    private static void addFile(Set<String> files, @Nullable String className, @Nullable String file) {
        if (className == null || className.isBlank()) {
            return;
        }
        int dot = className.lastIndexOf('.');
        String simple = className.substring(dot + 1);
        int nested = simple.indexOf('$');
        String name = file != null && !file.isBlank() ? file : (nested < 0 ? simple : simple.substring(0, nested)) + ".java";
        files.add((dot < 0 ? "" : className.substring(0, dot).replace('.', '/') + "/") + name);
    }

    /**
     * What to change to get from the failed lookup to the suggested one, when they differ in exactly one string:
     * {@code find(UnifiedTextField.class).withLabel("Email")} and {@code ….withLabel("E-mail")} give
     * {@code UnifiedTextField}, {@code withLabel}, argument 0, {@code Email} → {@code E-mail}.
     */
    static Optional<Change> difference(String lookup, String suggestion) {
        List<String> before = strings(lookup);
        List<String> after = strings(suggestion);
        if (before.size() != after.size() || !STRING.matcher(lookup).replaceAll("\"\"")
                .equals(STRING.matcher(suggestion).replaceAll("\"\""))) {
            return Optional.empty();
        }
        int changed = -1;
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                if (changed >= 0) {
                    return Optional.empty();
                }
                changed = i;
            }
        }
        if (changed < 0) {
            return Optional.empty();
        }
        // the call the changed string is an argument of, and its position among the arguments
        Matcher strings = STRING.matcher(lookup);
        for (int i = 0; i <= changed; i++) {
            strings.find();
        }
        Matcher call = CALL.matcher(lookup.substring(0, strings.start()));
        if (!call.find()) {
            return Optional.empty();
        }
        String argumentsBefore = lookup.substring(call.start(1) + call.group(1).length() + 1, strings.start());
        int argument = (int) STRING.matcher(argumentsBefore).replaceAll("\"\"").chars().filter(c -> c == ',').count();
        Matcher find = FIND.matcher(lookup);
        return Optional.of(new Change(find.find() ? find.group(1) : null, call.group(1), argument,
                unquoted(before.get(changed)), unquoted(after.get(changed)), Set.of(), ""));
    }

    private static List<String> strings(String code) {
        List<String> strings = new ArrayList<>();
        Matcher m = STRING.matcher(code);
        while (m.find()) {
            strings.add(m.group());
        }
        return strings;
    }

    /** The value of a string literal as the failure writes it: {@code \"} and {@code \\} unescaped. */
    private static String unquoted(String literal) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < literal.length() - 1; i++) {
            char c = literal.charAt(i);
            if (c == '\\' && i + 1 < literal.length() - 1) {
                c = literal.charAt(++i);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
