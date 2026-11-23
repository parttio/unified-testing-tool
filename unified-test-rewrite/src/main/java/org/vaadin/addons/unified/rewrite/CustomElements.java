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

    private CustomElements() {
    }

    /**
     * The type as a custom element: a {@code TestBenchElement} subclass TestBench doesn't ship, else {@code null}. The
     * element of a {@code LitTemplate} isn't one: its lookup becomes {@code findTemplate(..)} (read while
     * {@link LitTemplates#with} runs).
     */
    static JavaType.FullyQualified of(JavaType type) {
        JavaType.FullyQualified t = TypeUtils.asFullyQualified(type);
        if (t == null || t instanceof JavaType.Unknown || t.getFullyQualifiedName().startsWith("com.vaadin.")
                || !TypeUtils.isAssignableTo(TEST_BENCH_ELEMENT, t) || LitTemplates.ofElement(t.getFullyQualifiedName()) != null) {
            return null;
        }
        return t;
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
            if (s.getFullyQualifiedName().startsWith("com.vaadin.")) {
                return s.getClassName();
            }
        }
        return "TestBenchElement";
    }

    /** The Migration Marker on a statement calling the custom element. */
    static String marker(JavaType.FullyQualified element) {
        return "TestBench page object: " + element.getClassName() + " (custom element extending " + extended(element)
                + "): write a Wrapper or convert the page object";
    }
}
