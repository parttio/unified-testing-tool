package org.vaadin.addons.unified.rewrite;

import java.util.Optional;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

/**
 * Gives Failsafe's runs their own evidence directory (#512). The Unified API's default,
 * {@code target/surefire-reports/unified-evidence}, is the same whichever plugin runs the tests, so a build with
 * Surefire for its Browserless tests and Failsafe for its ITs mixes both phases in one {@code summary.md}. Every
 * {@code maven-failsafe-plugin} under {@code <build><plugins>} and under a profile's gets
 * {@code vaadin.testing.evidence-dir=${project.build.directory}/failsafe-reports/unified-evidence} in its
 * plugin-level {@code systemPropertyVariables}, which reach every execution. A plugin that already names
 * {@code vaadin.testing.evidence-dir} anywhere is left alone, and so are Surefire and {@code <pluginManagement>}.
 */
public class SeparateFailsafeEvidence extends Recipe {

    static final String PROPERTY = "vaadin.testing.evidence-dir";
    static final String DIRECTORY = "${project.build.directory}/failsafe-reports/unified-evidence";

    private static final XPathMatcher PLUGIN = new XPathMatcher("/project/build/plugins/plugin");
    private static final XPathMatcher PROFILE_PLUGIN = new XPathMatcher("/project/profiles/profile/build/plugins/plugin");

    @Override
    public String getDisplayName() {
        return "Give Failsafe its own Unified evidence directory";
    }

    @Override
    public String getDescription() {
        return "Sets `" + PROPERTY + "` to `" + DIRECTORY + "` in each `maven-failsafe-plugin`'s "
                + "`systemPropertyVariables`, so Failsafe's runs don't write over Surefire's evidence and run summary.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!(PLUGIN.matches(getCursor()) || PROFILE_PLUGIN.matches(getCursor()))
                        || !"maven-failsafe-plugin".equals(t.getChildValue("artifactId").orElse(null))
                        || t.printTrimmed(getCursor()).contains(PROPERTY)) {
                    return t;
                }
                String entry = "<" + PROPERTY + ">" + DIRECTORY + "</" + PROPERTY + ">";
                Optional<Xml.Tag> configuration = t.getChild("configuration");
                if (configuration.isEmpty()) {
                    doAfterVisit(new AddToTagVisitor<>(t, Xml.Tag.build("<configuration><systemPropertyVariables>"
                            + entry + "</systemPropertyVariables></configuration>"), new PluginChildOrder()));
                    return t;
                }
                Optional<Xml.Tag> variables = configuration.get().getChild("systemPropertyVariables");
                if (variables.isEmpty()) {
                    doAfterVisit(new AddToTagVisitor<>(configuration.get(), Xml.Tag.build(
                            "<systemPropertyVariables>" + entry + "</systemPropertyVariables>")));
                } else {
                    doAfterVisit(new AddToTagVisitor<>(variables.get(), Xml.Tag.build(entry)));
                }
                return t;
            }
        };
    }

    /** Keeps a new {@code <configuration>} before {@code <executions>}, where Maven's own poms put it. */
    private static final class PluginChildOrder implements java.util.Comparator<org.openrewrite.xml.tree.Content> {
        @Override
        public int compare(org.openrewrite.xml.tree.Content a, org.openrewrite.xml.tree.Content b) {
            return Integer.compare(rank(a), rank(b));
        }

        private static int rank(org.openrewrite.xml.tree.Content c) {
            if (!(c instanceof Xml.Tag tag)) {
                return 0;
            }
            return switch (tag.getName()) {
                case "executions" -> 2;
                case "dependencies" -> 3;
                default -> 1;
            };
        }
    }
}
