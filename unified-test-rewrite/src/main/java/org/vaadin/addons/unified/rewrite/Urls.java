package org.vaadin.addons.unified.rewrite;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;

/**
 * Reads the application path out of a URL a TestBench test opens, such as
 * {@code "http://localhost:" + port + "/people"}, {@code "http://localhost:8080/people"} or
 * {@code APP_URL + "people"} with {@code APP_URL} a constant {@code "http://localhost:8080/"}.
 */
final class Urls {

    /** A URL to the local application: {@code http://localhost:<port>} or {@code http://127.0.0.1:<port>}. */
    private static final Pattern LOCAL = Pattern.compile("https?://(?:localhost|127\\.0\\.0\\.1):\\d+(/.*)?");

    private Urls() {
    }

    /**
     * The path expression in {@code base + "/" + path}, for a path only known at run time, else {@code null}.
     */
    static Expression pathExpression(Expression url) {
        if (url instanceof J.Binary b && b.getOperator() == J.Binary.Type.Addition
                && !(b.getRight() instanceof J.Literal)
                && b.getLeft() instanceof J.Binary left && left.getRight() instanceof J.Literal slash
                && "/".equals(slash.getValue())) {
            return b.getRight().withPrefix(Space.EMPTY);
        }
        return null;
    }

    /**
     * The path after the host and port, without its leading slash, or {@code null} when it can't be read: a local
     * URL literal or constant, optionally followed by a literal path, or {@code "http://localhost:" + port + "/path"}.
     *
     * @param constants the value of a constant string field an expression reads, else {@code null}
     */
    static String pathOf(Expression url, Function<Expression, String> constants) {
        if (url instanceof J.Binary b && b.getOperator() == J.Binary.Type.Addition
                && b.getRight() instanceof J.Literal right && right.getValue() instanceof String tail) {
            if (b.getLeft() instanceof J.Binary) {
                // "http://localhost:" + port + "/path"
                return tail.startsWith("/") ? tail.substring(1) : null;
            }
            String base = value(b.getLeft(), constants);
            String basePath = base == null ? null : localPath(base);
            if (basePath == null) {
                return null;
            }
            String path = stripLeadingSlashes(tail);
            return basePath.isEmpty() ? path : basePath.replaceAll("/+$", "") + "/" + path;
        }
        String full = value(url, constants);
        return full == null ? null : localPath(full);
    }

    /** The field an identifier or field access reads, else {@code null}. */
    static JavaType.Variable field(Expression e) {
        JavaType.Variable v = e instanceof J.Identifier id ? id.getFieldType()
                : e instanceof J.FieldAccess fa ? fa.getName().getFieldType() : null;
        return v != null && v.getOwner() instanceof JavaType.FullyQualified ? v : null;
    }

    /** The key of a field in the constants a scan collects: {@code declaringClass#name}. */
    static String key(JavaType.Variable field) {
        return ((JavaType.FullyQualified) field.getOwner()).getFullyQualifiedName() + "#" + field.getName();
    }

    /** The path of a local application URL, without leading slashes ({@code ""} for the root), else {@code null}. */
    static String localPath(String url) {
        Matcher m = LOCAL.matcher(url);
        if (!m.matches()) {
            return null;
        }
        return m.group(1) == null ? "" : stripLeadingSlashes(m.group(1));
    }

    private static String value(Expression e, Function<Expression, String> constants) {
        if (e instanceof J.Literal literal) {
            return literal.getValue() instanceof String s ? s : null;
        }
        return constants.apply(e);
    }

    private static String stripLeadingSlashes(String path) {
        return path.replaceFirst("^/+", "");
    }
}
