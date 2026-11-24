package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

/**
 * Migration Markers: {@code // TODO unified: ...} comments above the statements of a test method that a recipe
 * changed in a way a reviewer must check, or couldn't convert. Collected while visiting the method, written after.
 */
final class MigrationMarkers {

    private static final String KEY = "unified.migration-markers";

    private MigrationMarkers() {
    }

    /** Starts collecting markers for the method at the cursor. */
    static Map<UUID, List<String>> collect(Cursor methodCursor) {
        Map<UUID, List<String>> markers = new LinkedHashMap<>();
        methodCursor.putMessage(KEY, markers);
        return markers;
    }

    /** Adds a marker above the method-body statement the cursor is in; false outside a collecting method body. */
    static boolean add(Cursor cursor, String text) {
        Map<UUID, List<String>> markers = cursor.getNearestMessage(KEY);
        Cursor statement = statementInMethodBody(cursor);
        if (markers == null || statement == null) {
            return false;
        }
        List<String> texts = markers.computeIfAbsent(((Statement) statement.getValue()).getId(), id -> new ArrayList<>());
        // one line: code printed over several lines would end the // comment and break the file
        String line = text.replaceAll("\\s*\\R\\s*", "");
        if (!texts.contains(line)) {
            texts.add(line);
        }
        return true;
    }

    /**
     * Whether the method-body statement the cursor is in has a marker the test accepts already, collected or written:
     * other markers on the statement don't count. The test gets the text after {@code TODO unified: }.
     */
    static boolean marked(Cursor cursor, Predicate<String> marker) {
        Map<UUID, List<String>> markers = cursor.getNearestMessage(KEY);
        Cursor statement = statementInMethodBody(cursor);
        if (markers == null || statement == null) {
            return false;
        }
        Statement s = (Statement) statement.getValue();
        String written = " TODO unified: ";
        return markers.getOrDefault(s.getId(), List.of()).stream().anyMatch(marker)
                || s.getPrefix().getComments().stream().anyMatch(c -> c instanceof TextComment t
                        && t.getText().startsWith(written) && marker.test(t.getText().substring(written.length())));
    }

    /** The code a marker quotes: the tree printed without its prefix, so without the comments above its statement. */
    static String code(J tree, Cursor parent) {
        return tree.withPrefix(Space.EMPTY).printTrimmed(parent);
    }

    /** The method with the collected markers written above their statements. */
    static J.MethodDeclaration write(J.MethodDeclaration method, Map<UUID, List<String>> markers) {
        if (markers.isEmpty() || method.getBody() == null) {
            return method;
        }
        List<Statement> statements = new ArrayList<>();
        for (Statement s : method.getBody().getStatements()) {
            List<String> texts = markers.get(s.getId());
            if (texts != null) {
                String indent = s.getPrefix().getIndent();
                List<Comment> comments = new ArrayList<>(s.getPrefix().getComments());
                int before = comments.size();
                for (String text : texts) {
                    // a recipe running again in a later cycle finds the marker it left
                    if (comments.stream().noneMatch(c -> c instanceof TextComment t
                            && t.getText().equals(" TODO unified: " + text))) {
                        comments.add(new TextComment(false, " TODO unified: " + text, "\n" + indent, Markers.EMPTY));
                    }
                }
                if (comments.size() > before) {
                    s = s.withPrefix(s.getPrefix().withComments(comments));
                }
            }
            statements.add(s);
        }
        return method.withBody(method.getBody().withStatements(statements));
    }

    /** The class with a marker above its first annotation, or above the class; unchanged when it has the marker. */
    static J.ClassDeclaration onClass(J.ClassDeclaration c, String text) {
        if (!c.getLeadingAnnotations().isEmpty()) {
            J.Annotation first = c.getLeadingAnnotations().get(0);
            Space prefix = withClassMarker(first.getPrefix(), c.getPrefix().getIndent(), text);
            if (prefix == first.getPrefix()) {
                return c;
            }
            List<J.Annotation> annotations = new ArrayList<>(c.getLeadingAnnotations());
            annotations.set(0, first.withPrefix(prefix));
            return c.withLeadingAnnotations(annotations);
        }
        return c.withPrefix(withClassMarker(c.getPrefix(), c.getPrefix().getIndent(), text));
    }

    private static Space withClassMarker(Space prefix, String indent, String text) {
        List<Comment> comments = new ArrayList<>(prefix.getComments());
        if (comments.stream().anyMatch(c -> c instanceof TextComment t && t.getText().endsWith(text))) {
            return prefix;
        }
        comments.add(new TextComment(false, " TODO unified: " + text, "\n" + indent, Markers.EMPTY));
        return prefix.withComments(comments);
    }

    private static Cursor statementInMethodBody(Cursor cursor) {
        Cursor c = cursor;
        while (c.getParent() != null) {
            Cursor parent = c.getParentTreeCursor();
            if (parent.getValue() instanceof J.Block && parent.getParentTreeCursor().getValue() instanceof J.MethodDeclaration) {
                return c;
            }
            c = parent;
        }
        return null;
    }
}
