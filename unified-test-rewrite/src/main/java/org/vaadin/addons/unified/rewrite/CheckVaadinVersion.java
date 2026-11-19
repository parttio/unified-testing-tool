package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Recipe;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

/**
 * Warns in {@code pom.xml} when the project's Vaadin is older than the Unified API needs (#387). The migration goes
 * on: the warning, a Migration Marker at the top of the pom, says that the migrated tests fail when they run until
 * the project moves to Vaadin {@value #MIN_MAJOR}.{@value #MIN_MINOR}. Flow's version is Vaadin's (Vaadin 25.3 runs
 * on Flow 25.3); the recipe reads the {@code flow-server} the build resolves, else the one it manages. A project
 * whose Flow it can't find is left alone.
 */
public class CheckVaadinVersion extends Recipe {

    /** The oldest Vaadin the Unified API runs on; keep in step with {@code VaadinVersionCheck} in unified-test-api. */
    static final int MIN_MAJOR = 25;
    static final int MIN_MINOR = 3;

    static final String MARKER = "TODO unified: the Unified API needs Vaadin " + MIN_MAJOR + "." + MIN_MINOR
            + " or newer";

    private static final Pattern MAJOR_MINOR = Pattern.compile("^(\\d+)\\.(\\d+)");

    @Override
    public String getDisplayName() {
        return "Warn when Vaadin is older than the Unified API needs";
    }

    @Override
    public String getDescription() {
        return "Adds a Migration Marker to the top of `pom.xml` when the project's Vaadin (its Flow) is older than "
                + MIN_MAJOR + "." + MIN_MINOR + ", the oldest the Unified API runs on.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Tag root = document.getRoot();
                String flow = flowVersion(getResolutionResult());
                if (flow == null || !tooOld(flow) || root.getContent() == null
                        || root.getContent().stream().anyMatch(c -> c instanceof Xml.Comment comment
                                && comment.getText().contains(MARKER))) {
                    return document;
                }
                Matcher m = MAJOR_MINOR.matcher(flow);
                m.find();
                String text = " " + MARKER + ", and this project has Vaadin " + m.group(1) + "." + m.group(2)
                        + " (Flow " + flow + "). Move it to Vaadin " + MIN_MAJOR + "." + MIN_MINOR
                        + " or newer: until then the migrated tests fail when they run. ";
                String prefix = root.getContent().isEmpty() ? "\n    " : root.getContent().get(0).getPrefix();
                List<Content> content = new ArrayList<>(root.getContent());
                content.add(0, new Xml.Comment(Tree.randomId(), prefix, Markers.EMPTY, text));
                return document.withRoot(root.withContent(content));
            }
        };
    }

    /** The {@code flow-server} version the build resolves, else the one it manages, else null. */
    static String flowVersion(MavenResolutionResult maven) {
        for (Scope scope : Scope.values()) {
            for (ResolvedDependency d : maven.getDependencies().getOrDefault(scope, List.of())) {
                if (d.getGroupId().equals("com.vaadin") && d.getArtifactId().equals("flow-server")) {
                    return d.getVersion();
                }
            }
        }
        return maven.getPom().getManagedVersion("com.vaadin", "flow-server", null, null);
    }

    /** Whether {@code flowVersion} is older than {@value #MIN_MAJOR}.{@value #MIN_MINOR}; false when unreadable. */
    static boolean tooOld(String flowVersion) {
        Matcher m = MAJOR_MINOR.matcher(flowVersion);
        if (!m.find()) {
            return false;
        }
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        return major < MIN_MAJOR || major == MIN_MAJOR && minor < MIN_MINOR;
    }
}
