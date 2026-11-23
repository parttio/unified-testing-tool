package org.vaadin.addons.unified.rewrite;

import java.util.HashSet;
import java.util.Set;
import org.openrewrite.Cursor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Where the setup recipe gave a method the Unified Page, and under which name. */
final class Pages {

    static final String NAME = "page";

    private Pages() {
    }

    static boolean hasPageParameter(J.MethodDeclaration method) {
        return parameterName(method) != null;
    }

    /** The name of the method's Unified Page parameter, or {@code null}. */
    static String parameterName(J.MethodDeclaration method) {
        for (var p : method.getParameters()) {
            if (p instanceof J.VariableDeclarations v && TypeUtils.isOfClassType(v.getType(), TestBenchSetupToUnified.UNIFIED_PAGE)) {
                return v.getVariables().get(0).getSimpleName();
            }
        }
        return null;
    }

    /** The Unified Page of the method enclosing the cursor: its parameter, {@code page} when it has none. */
    static J.Identifier page(Cursor cursor) {
        J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
        String name = method == null ? null : parameterName(method);
        return Stubs.page(name == null ? NAME : name);
    }

    /**
     * A name for the Unified Page parameter the method gains that no parameter, local or field it uses takes:
     * {@code page}, else {@code unifiedPage}, {@code unifiedPage2}, ... ({@code page} references an earlier
     * recipe wrote for this parameter don't count).
     */
    static String freeName(J.MethodDeclaration method) {
        Set<String> taken = new HashSet<>();
        new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable,
                    Set<String> names) {
                names.add(variable.getSimpleName());
                return super.visitVariable(variable, names);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> names) {
                if (identifier.getFieldType() != null && identifier.getFieldType().getOwner() instanceof JavaType.FullyQualified) {
                    names.add(identifier.getSimpleName()); // a field
                }
                return identifier;
            }
        }.visit(method, taken);
        if (!taken.contains(NAME)) {
            return NAME;
        }
        String name = "unifiedPage";
        for (int i = 2; taken.contains(name); i++) {
            name = "unifiedPage" + i;
        }
        return name;
    }
}
