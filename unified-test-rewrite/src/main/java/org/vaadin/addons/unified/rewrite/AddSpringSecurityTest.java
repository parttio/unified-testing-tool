package org.vaadin.addons.unified.rewrite;

import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.AddDependencyVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.tree.Xml;

/**
 * Adds Spring Security's test support to a module that depends on Spring Security, so a migrated test can sign in
 * with {@code @WithMockUser} instead of through the login form (ADR 0013, #528): Spring Boot's
 * {@code spring-boot-starter-security-test}, or {@code spring-security-test} on a Spring Boot without that starter.
 * A module that has either already is left alone.
 */
public class AddSpringSecurityTest extends Recipe {

    private static final String BOOT = "org.springframework.boot";
    private static final String SECURITY = "org.springframework.security";
    private static final String STARTER = "spring-boot-starter-security-test";
    private static final String SECURITY_TEST = "spring-security-test";
    private static final Set<String> SECURITY_MODULES = Set.of("spring-security-core", "spring-security-web",
            "spring-security-config");

    @Override
    public String getDisplayName() {
        return "Add Spring Security's test support";
    }

    @Override
    public String getDescription() {
        return "Adds `spring-boot-starter-security-test` (or `spring-security-test` on a Spring Boot without it), test "
                + "scope, to a module that depends on Spring Security, for `@WithMockUser`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                MavenResolutionResult maven = getResolutionResult();
                List<Dependency> requested = maven.getPom().getRequestedDependencies();
                if (requested.stream().noneMatch(AddSpringSecurityTest::springSecurity)
                        || requested.stream().anyMatch(AddSpringSecurityTest::securityTest)) {
                    return document;
                }
                ResolvedPom pom = maven.getPom();
                // a managed version stays the BOM's: the dependency is added without one
                String starter = pom.getManagedVersion(BOOT, STARTER, null, null);
                if (starter != null) {
                    doAfterVisit(new AddDependencyVisitor(BOOT, STARTER, starter, null, "test", true, null, null, null,
                            null));
                    return document;
                }
                String version = pom.getManagedVersion(SECURITY, SECURITY_TEST, null, null);
                if (version == null) {
                    version = securityVersion(maven);
                }
                if (version != null) {
                    doAfterVisit(new AddDependencyVisitor(SECURITY, SECURITY_TEST, version, null, "test", true, null, null,
                            null, null));
                }
                return document;
            }
        };
    }

    /** Spring Boot's security starter, or one of Spring Security's own modules. */
    private static boolean springSecurity(Dependency d) {
        return BOOT.equals(d.getGroupId()) && "spring-boot-starter-security".equals(d.getArtifactId())
                || SECURITY.equals(d.getGroupId()) && SECURITY_MODULES.contains(d.getArtifactId());
    }

    private static boolean securityTest(Dependency d) {
        return BOOT.equals(d.getGroupId()) && STARTER.equals(d.getArtifactId())
                || SECURITY.equals(d.getGroupId()) && SECURITY_TEST.equals(d.getArtifactId());
    }

    /** The {@code spring-security-core} version the build resolves, else null. */
    private static String securityVersion(MavenResolutionResult maven) {
        for (ResolvedDependency d : maven.getDependencies().getOrDefault(Scope.Test, List.of())) {
            if (SECURITY.equals(d.getGroupId()) && "spring-security-core".equals(d.getArtifactId())) {
                return d.getVersion();
            }
        }
        return null;
    }
}
