package org.vaadin.addons.unified.rewrite;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.Cursor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * A Browserless test's Component Reference to a {@code LitTemplate}'s {@code @Id} field, such as
 * {@code view.panel.search.newOrder}, as the lookup inside the Templates that finds the same component (spec 04,
 * "Templates"): {@code page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class)
 * .find(UnifiedButton.class).withId("new")}. The Template the chain starts from, a view's field or a
 * {@code $(SearchBar.class).single()}, is entered from the page; each Template bound with {@code @Id} in the one
 * before is entered from it.
 */
final class TemplateFields {

    /** The Templates to enter, outermost first, then the Wrapper and the id of the component. */
    record Chain(List<String> templates, Browserless.Wrapper wrapper, String id) {

        /** The lookup as a template for {@link JavaTemplate}, on the page as its one parameter. */
        String code() {
            StringBuilder sb = new StringBuilder("#{any(" + TestBenchSetupToUnified.UNIFIED_PAGE + ")}");
            templates.forEach(t -> sb.append(".findTemplate(").append(simple(t)).append(".class)"));
            return sb.append(".find(").append(wrapper.simpleName()).append(".class).withId(\"")
                    .append(id.replace("\\", "\\\\").replace("\"", "\\\"")).append("\")").toString();
        }

        /** The lookup in place of the chain at the cursor, from {@code page}. */
        Expression apply(Cursor chain) {
            List<String> stubs = new ArrayList<>();
            templates.forEach(t -> stubs.add(Stubs.classStub(t)));
            stubs.add("package " + pkg(wrapper.fqn()) + ";\npublic interface " + wrapper.simpleName() + " { "
                    + wrapper.simpleName() + " withId(String id); }");
            List<String> imports = new ArrayList<>(templates);
            imports.add(wrapper.fqn());
            Expression e = (Expression) chain.getValue();
            return JavaTemplate.builder(code())
                    .imports(imports.toArray(String[]::new))
                    .javaParser(Stubs.parser(stubs.toArray(String[]::new)))
                    .build()
                    .apply(chain, e.getCoordinates().replace(), Stubs.page());
        }
    }

    private TemplateFields() {
    }

    /**
     * The lookup the expression is a Component Reference to, or {@code null}: an {@code @Id} field of a
     * {@code LitTemplate} whose component has a Wrapper, reached through {@code @Id} fields of Templates from any
     * expression of a {@code LitTemplate} type. Needs a visit that reads {@link LitTemplates}.
     */
    static Chain of(Expression e) {
        if (!(e instanceof J.FieldAccess field) || field.getName().getFieldType() == null) {
            return null;
        }
        JavaType.FullyQualified owner = TypeUtils.asFullyQualified(field.getName().getFieldType().getOwner());
        JavaType.FullyQualified component = TypeUtils.asFullyQualified(field.getType());
        String id = owner == null ? null : LitTemplates.idOf(owner.getFullyQualifiedName(), field.getSimpleName());
        Browserless.Wrapper wrapper = component == null ? null
                : Browserless.forComponent(component.getFullyQualifiedName());
        if (id == null || wrapper == null) {
            return null;
        }
        List<String> templates = new ArrayList<>();
        Expression receiver = field.getTarget();
        String template = owner.getFullyQualifiedName();
        while (true) {
            templates.add(0, template);
            // the Template is itself bound with @Id in the one that holds it: enter that one first
            if (receiver instanceof J.FieldAccess outer && outer.getName().getFieldType() != null
                    && TypeUtils.asFullyQualified(outer.getName().getFieldType().getOwner()) instanceof JavaType.FullyQualified o
                    && LitTemplates.idOf(o.getFullyQualifiedName(), outer.getSimpleName()) != null) {
                template = o.getFullyQualifiedName();
                receiver = outer.getTarget();
            } else {
                return LitTemplates.isLitTemplate(receiver.getType()) ? new Chain(templates, wrapper, id) : null;
            }
        }
    }

    /** The local variable the chain starts from, such as {@code view}, or {@code null}. */
    static String root(Expression e) {
        Expression r = e;
        while (r instanceof J.FieldAccess f) {
            r = f.getTarget();
        }
        return r instanceof J.Identifier id && id.getFieldType() != null
                && !(id.getFieldType().getOwner() instanceof JavaType.FullyQualified) ? id.getSimpleName() : null;
    }

    /**
     * Whether the chain at the cursor converts: a Component Reference to an {@code @Id} field, used only through
     * {@code test(it)} and a tester call the Wrapper has, or a getter the Wrapper has.
     */
    static boolean converts(Cursor chain) {
        Chain c = of((Expression) chain.getValue());
        if (c == null) {
            return false;
        }
        Cursor parentCursor = chain.getParentTreeCursor();
        if (parentCursor.getValue() instanceof J.MethodInvocation test && Browserless.isTest(test)
                && test.getArguments().get(0) == chain.getValue()) {
            return parentCursor.getParentTreeCursor().getValue() instanceof J.MethodInvocation call
                    && call.getSelect() == test && Browserless.operation(c.wrapper(), call) != null;
        }
        return parentCursor.getValue() instanceof J.MethodInvocation call && call.getSelect() == chain.getValue()
                && Browserless.noArguments(call) && c.wrapper().getters().containsKey(call.getSimpleName());
    }

    /**
     * Whether the local variable is used only as the start of chains that {@link #converts}: then nothing is left
     * that needs it once they have.
     */
    static boolean onlyStartsChains(Cursor method, String local) {
        java.util.concurrent.atomic.AtomicBoolean other = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean used = new java.util.concurrent.atomic.AtomicBoolean();
        new org.openrewrite.java.JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                Cursor parent = getCursor().getParentTreeCursor();
                if (!identifier.getSimpleName().equals(local) || parent.getValue() instanceof J.VariableDeclarations.NamedVariable
                        || parent.getValue() instanceof J.FieldAccess f && f.getName() == identifier) {
                    return identifier;
                }
                Cursor chain = getCursor();
                while (chain.getParentTreeCursor().getValue() instanceof J.FieldAccess f && f.getTarget() == chain.getValue()) {
                    chain = chain.getParentTreeCursor();
                }
                used.set(true);
                if (!converts(chain)) {
                    other.set(true);
                }
                return identifier;
            }
        }.visit((J) method.getValue(), 0, method.getParentOrThrow());
        return used.get() && !other.get();
    }

    private static String simple(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private static String pkg(String fqn) {
        return fqn.substring(0, fqn.lastIndexOf('.'));
    }
}
