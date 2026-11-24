package org.vaadin.addons.unified.rewrite;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * An application's own {@code TestBenchElement} subclasses ({@code @Element("order-card") OrderCardElement extends
 * DivElement}, {@code LoginViewElement extends LoginOverlayElement}): TestBench page objects no recipe converts. They
 * stay behind the Escape Hatch (ADR 0004): every call on one gets a Migration Marker, and a test reaching one, itself
 * or through a helper, requires Selenium.
 */
final class CustomElements {

    static final String TEST_BENCH_ELEMENT = "com.vaadin.testbench.TestBenchElement";
    /** How the Migration Marker on a statement calling a custom element starts. */
    static final String MARKER = "TestBench page object: ";
    /** How the Escape Hatch marker on a {@code $(X.class)} lookup of a custom element ends. */
    static final String LOOKUP_SUFFIX = ", a custom element: write a Wrapper for it (ADR 0003)";

    private CustomElements() {
    }

    /**
     * The type as a custom element: a {@code TestBenchElement} subclass TestBench doesn't ship, else {@code null}. The
     * element of a {@code LitTemplate} isn't one: its lookup becomes {@code findTemplate(..)} (read while
     * {@link LitTemplates#with} runs).
     */
    static JavaType.FullyQualified of(JavaType type) {
        JavaType.FullyQualified t = TypeUtils.asFullyQualified(type);
        if (t == null || t instanceof JavaType.Unknown || shippedByTestBench(t)
                || !TypeUtils.isAssignableTo(TEST_BENCH_ELEMENT, t) || LitTemplates.ofElement(t.getFullyQualifiedName()) != null) {
            return null;
        }
        return t;
    }

    /**
     * Whether TestBench or Vaadin ships the class: TestBench's own ({@code com.vaadin.testbench.*}) or a component's
     * element ({@code com.vaadin.flow.component.**.testbench}). A {@code com.vaadin.} package alone isn't enough: an
     * application can live there too (Bakery's {@code com.vaadin.starter.bakery}, issue #606).
     */
    static boolean shippedByTestBench(JavaType.FullyQualified type) {
        String name = type.getFullyQualifiedName();
        return name.startsWith("com.vaadin.testbench.")
                || name.startsWith("com.vaadin.flow.component.") && type.getPackageName().endsWith(".testbench");
    }

    /** The custom element the call is made on, or {@code null}. */
    static JavaType.FullyQualified receiver(J.MethodInvocation call) {
        return call.getSelect() == null ? null : of(call.getSelect().getType());
    }

    /** Whether the call is made on a custom element or returns one. */
    static boolean used(J.MethodInvocation call) {
        return receiver(call) != null || call.getMethodType() != null && of(call.getMethodType().getReturnType()) != null;
    }

    /** The TestBench element class the custom element extends: the nearest of its superclasses TestBench ships. */
    static String extended(JavaType.FullyQualified element) {
        for (JavaType.FullyQualified s = element.getSupertype(); s != null; s = s.getSupertype()) {
            if (shippedByTestBench(s)) {
                return s.getClassName();
            }
        }
        return "TestBenchElement";
    }

    /**
     * Whether the Migration Marker already says the statement uses a custom element: a page-object marker, or the
     * Escape Hatch marker on its {@code $(X.class)} lookup. Other markers on the statement, such as the sign-in one,
     * don't: the statement still gets its page-object marker (issue #606).
     */
    static boolean marks(String marker) {
        return marker.startsWith(MARKER) || marker.endsWith(LOOKUP_SUFFIX);
    }

    /** The Migration Marker on a statement calling the custom element. */
    static String marker(JavaType.FullyQualified element) {
        return MARKER + element.getClassName() + " (custom element extending " + extended(element)
                + "): write a Wrapper or convert the page object";
    }
}
