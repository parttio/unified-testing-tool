package org.vaadin.addons.unified.rewrite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

/**
 * A project's copies of {@code SpringPlaywrightIT} (Dramafinder 1.x projects copied it from a template), and what a
 * test extending one takes from it once it no longer does: the copy's JUnit lifecycle methods and
 * {@code @RegisterExtension} fields, and the fields and helpers the test uses, as if the test declared them itself.
 */
final class DramafinderCopies {

    private static final String KEY = "unified.dramafinder.copies";
    private static final Set<String> LIFECYCLE = Set.of("org.junit.jupiter.api.BeforeEach",
            "org.junit.jupiter.api.AfterEach", "org.junit.jupiter.api.BeforeAll", "org.junit.jupiter.api.AfterAll");
    private static final String REGISTER_EXTENSION = "org.junit.jupiter.api.extension.RegisterExtension";

    /**
     * A test with the members it takes from its copied base class.
     *
     * @param c        the test with those members first in its body, typed as its own
     * @param inlined  for each member taken: where it came from ({@code clickSave() of SpringPlaywrightIT})
     * @param blockers the members it would need but can't take, why the test can't migrate
     */
    record Merged(J.ClassDeclaration c, Map<UUID, String> inlined, List<String> blockers) {
    }

    private DramafinderCopies() {
    }

    /** Remembers the class if it is a copy; called by the scanners, which see every source before any visitor. */
    static void collect(J.ClassDeclaration c, ExecutionContext ctx) {
        if (Dramafinder.isCopiedSpringBase(c.getType())) {
            ctx.<Map<String, J.ClassDeclaration>>computeMessageIfAbsent(KEY, k -> new HashMap<>())
                    .put(c.getType().getFullyQualifiedName(), c);
        }
    }

    /** The copies the scanners found, by fully qualified name. */
    static Map<String, J.ClassDeclaration> of(ExecutionContext ctx) {
        return ctx.getMessage(KEY, Map.of());
    }

    /** The test with what it takes from its copied base class; the test itself when it extends no copy seen. */
    static Merged merge(J.ClassDeclaration test, Map<String, J.ClassDeclaration> copies) {
        JavaType.FullyQualified parent = test.getExtends() == null ? null : TypeUtils.asFullyQualified(test.getExtends().getType());
        J.ClassDeclaration copy = parent == null || !Dramafinder.isCopiedSpringBase(parent) ? null
                : copies.get(parent.getFullyQualifiedName());
        if (copy == null || test.getType() == null) {
            return new Merged(test, Map.of(), List.of());
        }
        String copyName = parent.getFullyQualifiedName();
        Set<String> own = new LinkedHashSet<>();
        for (Statement s : test.getBody().getStatements()) {
            own.addAll(names(s));
        }

        Map<String, Statement> members = new LinkedHashMap<>(); // the copy's, by name
        Deque<String> needed = new ArrayDeque<>();
        for (Statement s : copy.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration m && (m.isConstructor()
                    || Dramafinder.CONVERTED_OVERRIDES.contains(m.getSimpleName())
                    && m.getParameters().stream().allMatch(p -> p instanceof J.Empty))) {
                continue; // getUrl() and getView(): the recipe drops them
            }
            for (String name : names(s)) {
                if (!own.contains(name)) {
                    members.putIfAbsent(name, s);
                    if (runsForEachTest(copy, s)) {
                        needed.add(name);
                    }
                }
            }
        }
        needed.addAll(references(test.getBody(), copyName, members.keySet()));

        List<String> blockers = new ArrayList<>();
        Map<UUID, String> inlined = new LinkedHashMap<>();
        Set<String> taken = new LinkedHashSet<>();
        String copySimpleName = parent.getClassName();
        while (!needed.isEmpty()) {
            String name = needed.poll();
            if (!taken.add(name)) {
                continue;
            }
            Statement s = members.get(name);
            String label = label(s, name) + " of " + copySimpleName;
            String base = s instanceof J.MethodDeclaration m ? Dramafinder.overriddenBaseOf(copy, m) : null;
            if (base != null) {
                blockers.add("overrides " + label(s, name) + " of " + base + " in " + copySimpleName);
            } else if (s instanceof J.VariableDeclarations v && v.getVariables().stream()
                    .anyMatch(var -> var.getInitializer() != null && usesTestBase(var.getInitializer()))) {
                blockers.add("uses " + label + ", which uses the test base");
            } else {
                inlined.putIfAbsent(s.getId(), label);
                needed.addAll(references(s, copyName, members.keySet()));
            }
        }
        if (inlined.isEmpty()) {
            return new Merged(test, Map.of(), blockers);
        }

        List<Statement> statements = new ArrayList<>();
        for (Statement s : copy.getBody().getStatements()) {
            if (inlined.containsKey(s.getId())) {
                statements.add(s);
            }
        }
        List<Statement> ownStatements = test.getBody().getStatements();
        for (int i = 0; i < ownStatements.size(); i++) {
            Statement s = ownStatements.get(i);
            if (i == 0) {
                Space prefix = s.getPrefix();
                s = s.withPrefix(prefix.withWhitespace("\n" + prefix.getWhitespace()));
            }
            statements.add(s);
        }
        J.ClassDeclaration merged = test.withBody(test.getBody().withStatements(statements));
        return new Merged(retyped(merged, copyName, test.getType(), taken), inlined, blockers);
    }

    /** A JUnit lifecycle method, a registered extension, or an override of the test base: each runs for every test. */
    private static boolean runsForEachTest(J.ClassDeclaration copy, Statement s) {
        if (s instanceof J.MethodDeclaration m) {
            return m.getLeadingAnnotations().stream().anyMatch(a -> LIFECYCLE.stream()
                    .anyMatch(l -> TypeUtils.isOfClassType(a.getType(), l))) || Dramafinder.overriddenBaseOf(copy, m) != null;
        }
        return s instanceof J.VariableDeclarations v && v.getLeadingAnnotations().stream()
                .anyMatch(a -> TypeUtils.isOfClassType(a.getType(), REGISTER_EXTENSION));
    }

    private static List<String> names(Statement s) {
        if (s instanceof J.MethodDeclaration m) {
            return List.of(m.getSimpleName());
        }
        if (s instanceof J.VariableDeclarations v) {
            return v.getVariables().stream().map(J.VariableDeclarations.NamedVariable::getSimpleName).toList();
        }
        return List.of();
    }

    private static String label(Statement s, String name) {
        return s instanceof J.MethodDeclaration m
                ? name + (m.getParameters().stream().allMatch(p -> p instanceof J.Empty) ? "()" : "(..)") : name;
    }

    /** The copy's members the tree uses: its fields, and its methods called on this test. */
    private static Set<String> references(J tree, String copy, Set<String> members) {
        Set<String> found = new LinkedHashSet<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier id, Integer p) {
                if (id.getFieldType() != null && members.contains(id.getSimpleName())
                        && TypeUtils.isOfClassType(id.getFieldType().getOwner(), copy)) {
                    found.add(id.getSimpleName());
                }
                return id;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                if (members.contains(m.getSimpleName()) && m.getMethodType() != null
                        && TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), copy)
                        && (m.getSelect() == null || m.getSelect() instanceof J.Identifier s && s.getSimpleName().equals("this"))) {
                    found.add(m.getSimpleName());
                }
                return super.visitMethodInvocation(m, p);
            }
        }.visit(tree, 0);
        return found;
    }

    /** Whether the tree uses the test base: its page, or a member of a Dramafinder base class or of a copy. */
    private static boolean usesTestBase(J tree) {
        AtomicBoolean uses = new AtomicBoolean();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier id, Integer p) {
                if (id.getFieldType() != null && Dramafinder.isBaseType(id.getFieldType().getOwner())) {
                    uses.set(true);
                }
                return id;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                if (m.getMethodType() != null && Dramafinder.isBaseType(m.getMethodType().getDeclaringType())) {
                    uses.set(true);
                }
                return super.visitMethodInvocation(m, p);
            }
        }.visit(tree, 0);
        return uses.get();
    }

    /** The class with the taken members, and every use of them, typed as the test's own. */
    private static J.ClassDeclaration retyped(J.ClassDeclaration c, String copy, JavaType.FullyQualified test,
            Set<String> taken) {
        return (J.ClassDeclaration) new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier id, Integer p) {
                J.Identifier i = super.visitIdentifier(id, p);
                if (i.getFieldType() != null && taken.contains(i.getSimpleName())
                        && TypeUtils.isOfClassType(i.getFieldType().getOwner(), copy)) {
                    i = i.withFieldType(i.getFieldType().withOwner(test));
                }
                if (i.getType() instanceof JavaType.Method mt && taken.contains(i.getSimpleName())
                        && TypeUtils.isOfClassType(mt.getDeclaringType(), copy)) {
                    i = i.withType(mt.withDeclaringType(test));
                }
                return i;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
                J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, p);
                if (v.getVariableType() != null && taken.contains(v.getSimpleName())
                        && TypeUtils.isOfClassType(v.getVariableType().getOwner(), copy)) {
                    v = v.withVariableType(v.getVariableType().withOwner(test));
                }
                return v;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                J.MethodInvocation m = super.visitMethodInvocation(method, p);
                if (m.getMethodType() != null && taken.contains(m.getSimpleName())
                        && TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), copy)) {
                    m = m.withMethodType(m.getMethodType().withDeclaringType(test));
                }
                return m;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Integer p) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, p);
                if (m.getMethodType() != null && taken.contains(m.getSimpleName())
                        && TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), copy)) {
                    m = m.withMethodType(m.getMethodType().withDeclaringType(test));
                }
                return m;
            }
        }.visitNonNull(c, 0);
    }
}
