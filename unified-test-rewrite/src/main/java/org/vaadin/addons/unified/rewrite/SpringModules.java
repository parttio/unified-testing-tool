package org.vaadin.addons.unified.rewrite;

import java.util.HashSet;
import java.util.Set;
import org.openrewrite.SourceFile;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/**
 * The modules (Java projects) with a {@code @SpringBootApplication}, in main or test sources, and those whose sources
 * use Spring Security, as the scanners find them. A TestBench test without {@code @SpringBootTest} in a module with a
 * {@code @SpringBootApplication} ran against a server the build started (ADR 0013, #528).
 */
final class SpringModules {

    static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final Set<String> SECURITY_PACKAGES = Set.of("org.springframework.security.",
            "com.vaadin.flow.spring.security.");

    private final Set<String> applications = new HashSet<>();
    private final Set<String> security = new HashSet<>();

    /** Remembers what the source file tells about its module; called by the scanners. */
    void collect(J.CompilationUnit cu) {
        String module = module(cu);
        if (!security.contains(module) && cu.getTypesInUse().getTypesInUse().stream()
                .map(TypeUtils::asFullyQualified)
                .anyMatch(t -> t != null && SECURITY_PACKAGES.stream().anyMatch(t.getFullyQualifiedName()::startsWith))) {
            security.add(module);
        }
        for (J.ClassDeclaration c : cu.getClasses()) {
            if (c.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), SPRING_BOOT_APPLICATION))) {
                applications.add(module);
            }
        }
    }

    /** Whether the source file's module has a {@code @SpringBootApplication}. */
    boolean hasApplication(SourceFile source) {
        return applications.contains(module(source));
    }

    /** Whether the source file's module uses Spring Security. */
    boolean usesSecurity(SourceFile source) {
        return security.contains(module(source));
    }

    private static String module(SourceFile source) {
        return source.getMarkers().findFirst(JavaProject.class).map(JavaProject::getProjectName).orElse("");
    }
}
