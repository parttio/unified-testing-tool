package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

/**
 * A Dramafinder test class the migration skips, because it uses a base class member no rule converts, gets a
 * Migration Marker above the class naming those members: otherwise nothing in the output says it was skipped.
 */
public class DramafinderSkippedMarked extends Recipe {

    static final String PREFIX = "not migrated: ";

    @Override
    public String getDisplayName() {
        return "Mark Dramafinder tests the migration skips";
    }

    @Override
    public String getDescription() {
        return "Leaves a Migration Marker on Dramafinder test classes the migration skips, naming the base class "
                + "members that kept them from migrating.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (!Dramafinder.isDramafinderClass(classDecl)) {
                    return c;
                }
                List<String> blockers = Dramafinder.blockers(classDecl);
                if (blockers.isEmpty() || c.getComments().stream().anyMatch(comment -> comment instanceof TextComment t
                        && t.getText().contains("TODO unified: " + PREFIX))) {
                    return c;
                }
                List<Comment> comments = new ArrayList<>(c.getPrefix().getComments());
                comments.add(new TextComment(false, " TODO unified: " + PREFIX + String.join(", ", blockers)
                        + "; convert them by hand, then run the migration again", "\n" + c.getPrefix().getIndent(),
                        Markers.EMPTY));
                return c.withPrefix(Space.build(c.getPrefix().getWhitespace(), comments));
            }
        };
    }
}
