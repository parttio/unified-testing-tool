package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class the application declares for a Template's tag (ADR 0017): among the classes in the application's
 * packages (and their subpackages) that extend {@link Component}, aren't abstract and declare {@link Tag}
 * themselves, the only one with that tag. The packages are scanned once per JVM, when a tag is first asked for,
 * by reading their class files: no class is loaded but the one returned.
 */
final class TemplateClasses {
    private TemplateClasses() {}

    /** Per set of packages: each tag, and the classes that declare it. */
    private static final Map<Set<String>, Map<String, List<String>>> SCANNED = new ConcurrentHashMap<>();

    /** The only class in these packages that declares this tag; none when no class or several do. */
    static Optional<Class<?>> forTag(String tag, Collection<String> packages) {
        if (packages.isEmpty()) {
            return Optional.empty();
        }
        List<String> declaring = SCANNED.computeIfAbsent(new TreeSet<>(packages), TemplateClasses::scan)
                .getOrDefault(tag, List.of());
        if (declaring.size() != 1) {
            return Optional.empty();
        }
        try {
            Class<?> type = Class.forName(declaring.get(0), false, Thread.currentThread().getContextClassLoader());
            return Component.class.isAssignableFrom(type) ? Optional.of(type) : Optional.empty();
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static Map<String, List<String>> scan(Set<String> packages) {
        Map<String, List<String>> byTag = new HashMap<>();
        try (ScanResult result = new ClassGraph().enableClassInfo().enableAnnotationInfo()
                .acceptPackages(packages.toArray(String[]::new)).scan()) {
            for (ClassInfo type : result.getClassesWithAnnotation(Tag.class)) {
                AnnotationInfo declared = type.getAnnotationInfo().directOnly().get(Tag.class.getName());
                if (declared == null || type.isAbstract() || type.isInterface() || type.isAnnotation()) {
                    continue; // inherits its tag, or never on a page itself
                }
                Object value = declared.getParameterValues().getValue("value");
                if (value instanceof String tag) {
                    byTag.computeIfAbsent(tag, t -> new ArrayList<>()).add(type.getName());
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // the class path can't be read here: every tag keeps its placeholder
        }
        return byTag;
    }
}
