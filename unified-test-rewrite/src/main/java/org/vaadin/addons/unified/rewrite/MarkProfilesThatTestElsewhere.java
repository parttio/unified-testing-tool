package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

/**
 * Leaves a Migration Marker on a Maven profile whose Failsafe run tested a server or browser outside the test (#613),
 * which the TestBench migration changes without a word: a migrated {@code @UnifiedSpringBootTest} starts the JVM app
 * itself, and the Selenium adapter always runs a local browser. The marker is an XML comment after the profile's
 * {@code <id>}; the recipe changes nothing else in the profile (ADR 0019, amending ADR 0013). A profile is marked when
 * it sets a TestBench grid parameter or a Sauce Labs setting anywhere, and, when it binds {@code maven-failsafe-plugin},
 * when it starts a server with {@code native-maven-plugin}, {@code exec-maven-plugin} or a {@code spring-boot} goal
 * other than {@code start}/{@code stop}/{@code build-info}, or gives Failsafe a property naming a URL or host.
 */
public class MarkProfilesThatTestElsewhere extends Recipe {

    static final String EXTERNAL_SERVER = "TODO unified: this profile ran the tests against a server it starts outside"
            + " the test";

    static final String GRID = "TODO unified: this profile sets TestBench grid or Sauce Labs settings";

    static final String SERVER_ADDRESS = "TODO unified: this profile gives the tests the address of a server outside"
            + " the test";

    private static final XPathMatcher PROFILE = new XPathMatcher("/project/profiles/profile");
    /** Goals that keep the JVM app the build starts, which the class marker covers, or don't start a server. */
    private static final List<String> SPRING_BOOT_GOALS_IN_PLACE = List.of("start", "stop", "build-info");
    private static final List<String> SERVER_STARTERS = List.of("native-maven-plugin", "exec-maven-plugin");

    @Override
    public String getDisplayName() {
        return "Mark Failsafe profiles that test a server or browser outside the test";
    }

    @Override
    public String getDescription() {
        return "Adds a Migration Marker to a Maven profile whose Failsafe run tested a server started outside the "
                + "test, a server address or a remote grid, which the migrated tests no longer do.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!PROFILE.matches(getCursor()) || t.getContent() == null) {
                    return t;
                }
                Xml.Tag marked = t;
                List<String> grid = settingNames(t).stream().filter(MarkProfilesThatTestElsewhere::isGridSetting)
                        .distinct().toList();
                if (!grid.isEmpty()) {
                    marked = mark(marked, GRID + " (" + String.join(", ", grid) + "). The Unified Selenium adapter"
                            + " reads none of them and has no remote grid (#645): the migrated tests run a local"
                            + " browser.");
                }
                List<Xml.Tag> plugins = t.getChild("build").flatMap(b -> b.getChild("plugins"))
                        .map(p -> p.getChildren("plugin")).orElse(List.of());
                List<String> ids = plugins.stream().map(p -> p.getChildValue("artifactId").orElse("")).toList();
                if (!ids.contains("maven-failsafe-plugin")) {
                    return marked;
                }
                List<String> starters = new ArrayList<>(ids.stream().filter(SERVER_STARTERS::contains).toList());
                plugins.stream().filter(p -> "spring-boot-maven-plugin".equals(p.getChildValue("artifactId")
                                .orElse(null)))
                        .flatMap(p -> goals(p).stream())
                        .filter(goal -> !SPRING_BOOT_GOALS_IN_PLACE.contains(goal))
                        .map(goal -> "spring-boot:" + goal).distinct().forEach(starters::add);
                List<String> addresses = plugins.stream().filter(p -> "maven-failsafe-plugin".equals(
                                p.getChildValue("artifactId").orElse(null)))
                        .flatMap(p -> p.getChild("configuration").map(c -> settingNames(c).stream())
                                .orElse(java.util.stream.Stream.empty()))
                        .filter(MarkProfilesThatTestElsewhere::isServerAddress).distinct().toList();
                if (!addresses.isEmpty()) {
                    marked = mark(marked, SERVER_ADDRESS + " (" + String.join(", ", addresses) + "). A migrated"
                            + " @UnifiedSpringBootTest opens the app it starts, on a random port, and ignores it."
                            + " Remove it, or migrate with springBootTest: false and set vaadin.testing.base-url"
                            + " instead.");
                }
                if (!starters.isEmpty()) {
                    marked = mark(marked, EXTERNAL_SERVER + " (" + String.join(", ", starters) + "). A migrated"
                            + " @UnifiedSpringBootTest starts the JVM app itself, so that server is no longer tested."
                            + " Remove its start and stop, or migrate with springBootTest: false and point"
                            + " vaadin.testing.base-url at it.");
                }
                return marked;
            }
        };
    }

    /**
     * The names of the settings the profile sets, in document order: its properties, Failsafe's system properties
     * and environment variables (each an element name, or a {@code <systemProperties>} entry's {@code <name>}).
     */
    private static List<String> settingNames(Xml.Tag profile) {
        List<String> names = new ArrayList<>();
        collect(profile, names);
        return names;
    }

    private static void collect(Xml.Tag tag, List<String> names) {
        for (Xml.Tag child : tag.getChildren()) {
            if (child.getChildren().isEmpty()) {
                names.add(child.getName().equals("name") ? child.getValue().orElse("") : child.getName());
            } else {
                collect(child, names);
            }
        }
    }

    private static List<String> goals(Xml.Tag plugin) {
        return plugin.getChild("executions").map(e -> e.getChildren("execution")).orElse(List.of()).stream()
                .flatMap(e -> e.getChild("goals").map(g -> g.getChildren("goal")).orElse(List.of()).stream())
                .map(g -> g.getValue().orElse("")).toList();
    }

    /** A property naming a URL or host, other than the Unified API's own and TestBench's grid parameters. */
    private static boolean isServerAddress(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return (lower.contains("url") || lower.contains("host")) && !lower.startsWith("vaadin.testing.")
                && !isGridSetting(name);
    }

    /** TestBench's grid parameters, by full or simple name, and anything about Sauce Labs. */
    private static boolean isGridSetting(String name) {
        String simple = name.substring(name.lastIndexOf('.') + 1);
        return name.startsWith("com.vaadin.testbench.Parameters.")
                || List.of("gridBrowsers", "hubHostname", "hubPort").contains(simple)
                || name.toLowerCase(java.util.Locale.ROOT).contains("sauce");
    }

    /** Adds the comment after the profile's {@code <id>}, or first. */
    private static Xml.Tag mark(Xml.Tag profile, String text) {
        String key = text.substring(0, text.indexOf(" ("));
        if (profile.getContent().stream().anyMatch(c -> c instanceof Xml.Comment comment
                && comment.getText().contains(key))) {
            return profile;
        }
        List<Content> content = new ArrayList<>(profile.getContent());
        int at = 0;
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i) instanceof Xml.Tag child && child.getName().equals("id")) {
                at = i + 1;
            }
        }
        while (at < content.size() && content.get(at) instanceof Xml.Comment) {
            at++;
        }
        String prefix = content.get(Math.min(at, content.size() - 1)).getPrefix();
        content.add(at, new Xml.Comment(Tree.randomId(), prefix, Markers.EMPTY, " " + text + " "));
        return profile.withContent(content);
    }
}
