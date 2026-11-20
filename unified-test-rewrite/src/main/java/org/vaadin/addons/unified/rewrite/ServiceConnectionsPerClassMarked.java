package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

/**
 * A {@code @ServiceConnection} field is part of Spring's context cache key, so Spring test classes that each declare
 * their own start one Spring context each; with {@code @UnifiedSpringBootTest}, always on a random port, each also
 * starts an embedded server, on Browserless too (#331). When several migrated classes do, a Migration Marker on each
 * field suggests one shared base class.
 */
public class ServiceConnectionsPerClassMarked extends ScanningRecipe<Set<String>> {

    static final String PREFIX = "one Spring context per class: ";
    private static final String SERVICE_CONNECTION =
            "org.springframework.boot.testcontainers.service.connection.ServiceConnection";
    private static final String UNIFIED_SPRING_BOOT_TEST = "com.vaadin.testing.unified.spring.UnifiedSpringBootTest";

    @Override
    public String getDisplayName() {
        return "Mark test classes that each declare their own @ServiceConnection";
    }

    @Override
    public String getDescription() {
        return "Leaves a Migration Marker on the `@ServiceConnection` fields of migrated Spring tests when several "
                + "classes declare their own: each starts a Spring context with an embedded server.";
    }

    /** The simple names of the Spring UI test classes that declare a {@code @ServiceConnection} field. */
    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new TreeSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> classes) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (springUiTest(classDecl) && classDecl.getBody().getStatements().stream()
                        .anyMatch(ServiceConnectionsPerClassMarked::serviceConnection)) {
                    classes.add(classDecl.getSimpleName());
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> classes) {
        String marker = PREFIX + classes.size() + " test classes (" + String.join(", ", classes) + ") declare their "
                + "own @ServiceConnection field, and each starts a Spring context with an embedded server; move it, "
                + "with @UnifiedSpringBootTest and the profile, to one shared base class (spec 02)";
        return new JavaIsoVisitor<>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations field, ExecutionContext ctx) {
                J.VariableDeclarations v = super.visitVariableDeclarations(field, ctx);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (classes.size() < 2 || !serviceConnection(v) || owner == null
                        || !classes.contains(owner.getSimpleName()) || !(getCursor().getParentTreeCursor().getValue() instanceof J.Block)
                        || v.getPrefix().getComments().stream().anyMatch(c -> c instanceof TextComment t && t.getText().contains(PREFIX))) {
                    return v;
                }
                List<Comment> comments = new ArrayList<>(v.getPrefix().getComments());
                comments.add(new TextComment(false, " TODO unified: " + marker, "\n" + v.getPrefix().getIndent(), Markers.EMPTY));
                return v.withPrefix(Space.build(v.getPrefix().getWhitespace(), comments));
            }
        };
    }

    /** A Browserless Spring test, or one the migration already turned into a {@code @UnifiedSpringBootTest}. */
    private static boolean springUiTest(J.ClassDeclaration c) {
        return c.getType() != null && TypeUtils.isAssignableTo(Browserless.SPRING_BROWSERLESS_TEST, c.getType())
                || c.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), UNIFIED_SPRING_BOOT_TEST));
    }

    private static boolean serviceConnection(Object statement) {
        return statement instanceof J.VariableDeclarations v && v.getLeadingAnnotations().stream()
                .anyMatch(a -> TypeUtils.isOfClassType(a.getType(), SERVICE_CONNECTION));
    }
}
