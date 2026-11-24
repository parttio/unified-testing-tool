package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.tree.JavaType;

class CustomElementsTest {

    @Test
    void elementsTestBenchShipsAreTestBenchsOwn() {
        assertThat(CustomElements.shippedByTestBench(JavaType.ShallowClass.build(
                "com.vaadin.flow.component.button.testbench.ButtonElement"))).isTrue();
        assertThat(CustomElements.shippedByTestBench(JavaType.ShallowClass.build(
                "com.vaadin.flow.component.login.testbench.LoginOverlayElement"))).isTrue();
        assertThat(CustomElements.shippedByTestBench(JavaType.ShallowClass.build(
                "com.vaadin.testbench.TestBenchElement"))).isTrue();
    }

    /** Issue #606: an application can live under {@code com.vaadin.} too. */
    @Test
    void anApplicationsElementUnderComVaadinIsNotTestBenchsOwn() {
        assertThat(CustomElements.shippedByTestBench(JavaType.ShallowClass.build(
                "com.vaadin.starter.bakery.testbench.elements.ui.LoginViewElement"))).isFalse();
        assertThat(CustomElements.shippedByTestBench(JavaType.ShallowClass.build(
                "com.vaadin.flow.component.mine.OrderCardElement"))).isFalse();
    }
}
