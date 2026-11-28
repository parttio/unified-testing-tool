package org.vaadin.addons.unified.rewrite;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markup;
import org.openrewrite.maven.tree.Version;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

/**
 * Installs the {@code write-unified-ui-tests} agent skill (spec chapter 12, "The agent skill") in a project: writes
 * {@code .claude/skills/write-unified-ui-tests/SKILL.md} and a pointer section in {@code AGENTS.md}, or in
 * {@code CLAUDE.md} when the project has that and no {@code AGENTS.md}.
 * <p>
 * The skill ships in this jar with a stamp, {@code <!-- unified-test-skill: write-unified-ui-tests VERSION -->},
 * VERSION being the release it came with (Maven resource filtering). A copy already in the project is replaced when
 * its stamp names an older release, left as it is when it names this one or a newer one, and left alone with a
 * warning ({@link Markup.Warn} on the file) when it has no stamp: someone edited it by hand.
 * <p>
 * The pointer is a section between {@code <!-- unified-test: write-unified-ui-tests -->} and
 * {@code <!-- /unified-test -->}, added at the end or brought up to date where it is; nothing else in the file
 * changes. It goes in {@code AGENTS.md}, created when missing, unless the project has a {@code CLAUDE.md} and no
 * {@code AGENTS.md}: Claude Code reads {@code CLAUDE.md}, so the section goes there and no {@code AGENTS.md} is
 * created. With both files, only {@code AGENTS.md} gets it.
 * <p>
 * It writes only into a project: a root with a build file ({@code pom.xml}, Gradle), {@code AGENTS.md},
 * {@code CLAUDE.md} or the skill, that has Java sources. When the root is no project but has Java sources, it writes
 * nothing and warns ({@link Markup.Warn}) on each build file below the root, a module's, so the skip is not silent
 * (issue #698). Every Migration Recipe runs it; it can also run alone.
 */
public class AddUnifiedTestingSkill extends ScanningRecipe<AddUnifiedTestingSkill.Scan> {

    static final Path PATH = Path.of(".claude/skills/write-unified-ui-tests/SKILL.md");
    static final Path AGENTS = Path.of("AGENTS.md");
    static final Path CLAUDE = Path.of("CLAUDE.md");
    private static final String RESOURCE = "/META-INF/unified/skills/write-unified-ui-tests/SKILL.md";
    private static final Pattern STAMP = Pattern.compile("<!-- unified-test-skill: write-unified-ui-tests (\\S+) -->");
    private static final Set<Path> BUILD_FILES = Set.of(Path.of("pom.xml"), Path.of("build.gradle"),
            Path.of("build.gradle.kts"), Path.of("settings.gradle"), Path.of("settings.gradle.kts"));

    static final String SECTION_START = "<!-- unified-test: write-unified-ui-tests -->";
    static final String SECTION_END = "<!-- /unified-test -->";
    static final String SECTION = SECTION_START + "\n## Unified UI Tests\n\n"
            + "For Unified UI Tests, follow `.claude/skills/write-unified-ui-tests/SKILL.md`.\n" + SECTION_END + "\n";
    static final String UNSTAMPED_WARNING = "write-unified-ui-tests: this copy of the skill has no version stamp, so "
            + "it was edited by hand and is left as it is. Delete it and run the recipe again to get the skill of this "
            + "release.";
    static final String NO_PROJECT_ROOT_WARNING = "write-unified-ui-tests: the skill was not installed. The recipe "
            + "writes it only at the build root, and the build root has no build file (pom.xml, Gradle), AGENTS.md or "
            + "CLAUDE.md; this build file is below it. Add an AGENTS.md at the build root to install the skill there, "
            + "or run the recipe with this module as the build root.";

    /** What the scan saw. */
    public static final class Scan {
        boolean project;
        boolean javaSources;
        boolean skillExists;
        boolean agentsExists;
        boolean claudeExists;

        /** The file that gets the pointer section. */
        Path pointerFile() {
            return claudeExists && !agentsExists ? CLAUDE : AGENTS;
        }
    }

    @Override
    public String getDisplayName() {
        return "Add the agent skill that writes Unified UI Tests";
    }

    @Override
    public String getDescription() {
        return "Writes `.claude/skills/write-unified-ui-tests/SKILL.md`, the agent skill for writing Unified UI Tests "
                + "of this Unified API version, replacing an older copy (but not a hand-edited one), and points to it "
                + "from `AGENTS.md` (or from `CLAUDE.md`, when the project has that and no `AGENTS.md`).";
    }

    @Override
    public Scan getInitialValue(ExecutionContext ctx) {
        return new Scan();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scan scan) {
        return new TreeVisitor<>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source) {
                    Path path = source.getSourcePath();
                    if (path.equals(PATH)) {
                        scan.skillExists = true;
                        scan.project = true;
                    } else if (path.equals(AGENTS)) {
                        scan.agentsExists = true;
                        scan.project = true;
                    } else if (path.equals(CLAUDE)) {
                        scan.claudeExists = true;
                        scan.project = true;
                    } else if (BUILD_FILES.contains(path)) {
                        scan.project = true;
                    } else if (path.toString().endsWith(".java")) {
                        scan.javaSources = true;
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Scan scan, ExecutionContext ctx) {
        if (!applies(scan)) {
            return List.of();
        }
        List<SourceFile> generated = new ArrayList<>();
        PlainTextParser parser = PlainTextParser.builder().build();
        if (!scan.skillExists) {
            parser.parse(skill()).map(s -> (SourceFile) s.withSourcePath(PATH)).forEach(generated::add);
        }
        if (!scan.agentsExists && !scan.claudeExists) {
            parser.parse(SECTION).map(s -> (SourceFile) s.withSourcePath(AGENTS)).forEach(generated::add);
        }
        return generated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scan scan) {
        return new TreeVisitor<>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!scan.project && scan.javaSources && tree instanceof SourceFile source
                        && isNestedBuildFile(source.getSourcePath())) {
                    return Markup.warn(source, new IllegalStateException(NO_PROJECT_ROOT_WARNING));
                }
                if (!applies(scan) || !(tree instanceof PlainText text)) {
                    return tree;
                }
                if (text.getSourcePath().equals(PATH)) {
                    return updateSkill(text);
                }
                if (text.getSourcePath().equals(scan.pointerFile())) {
                    String updated = withSection(text.getText());
                    return updated.equals(text.getText()) ? text : text.withText(updated);
                }
                return tree;
            }
        };
    }

    private static boolean applies(Scan scan) {
        return scan.project && scan.javaSources;
    }

    /** A build file below the build root: a module's, which makes no project at the root. */
    private static boolean isNestedBuildFile(Path path) {
        return path.getNameCount() > 1 && BUILD_FILES.contains(path.getFileName());
    }

    private static PlainText updateSkill(PlainText existing) {
        String found = stamp(existing.getText());
        if (found == null) {
            return Markup.warn(existing, new IllegalStateException(UNSTAMPED_WARNING));
        }
        String shipped = skill();
        String current = stamp(shipped);
        if (current != null && new Version(found).compareTo(new Version(current)) < 0) {
            return existing.withText(shipped);
        }
        return existing;
    }

    /** The version a copy of the skill is stamped with, or null for none. */
    static @Nullable String stamp(String skill) {
        Matcher m = STAMP.matcher(skill);
        return m.find() ? m.group(1) : null;
    }

    /**
     * {@code AGENTS.md} or {@code CLAUDE.md} with the pointer section replaced where it is, or added at the end; the file keeps its
     * newline at the end, or its lack of one.
     */
    static String withSection(String file) {
        String section = SECTION.substring(0, SECTION.length() - 1);
        int start = file.indexOf(SECTION_START);
        int end = start < 0 ? -1 : file.indexOf(SECTION_END, start);
        if (start >= 0 && end >= 0) {
            return file.substring(0, start) + section + file.substring(end + SECTION_END.length());
        }
        if (file.isBlank()) {
            return SECTION;
        }
        String body = file.stripTrailing();
        return body + "\n\n" + section + (file.endsWith("\n") ? "\n" : "");
    }

    /** The skill as this release ships it. */
    static String skill() {
        try (InputStream in = AddUnifiedTestingSkill.class.getResourceAsStream(RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
