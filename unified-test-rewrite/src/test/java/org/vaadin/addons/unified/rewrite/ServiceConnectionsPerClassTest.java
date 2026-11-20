package org.vaadin.addons.unified.rewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Each {@code @ServiceConnection} field is part of Spring's context cache key, so test classes that declare their own
 * start one Spring context each, and with {@code @UnifiedSpringBootTest} one embedded server each (#331).
 */
class ServiceConnectionsPerClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                        .activateRecipes("org.vaadin.addons.unified.rewrite.MigrateBrowserlessToUnified"))
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void classesWithTheirOwnServiceConnectionAreMarked() {
        String marker = "// TODO unified: one Spring context per class: 2 test classes (DashboardTest, ProfileTest) "
                + "declare their own @ServiceConnection field, and each starts a Spring context with an embedded "
                + "server; move it, with @UnifiedSpringBootTest and the profile, to one shared base class (spec 02)\n    ";
        rewriteRun(
                java(browserlessTest("DashboardTest"), unifiedTest("DashboardTest", marker)),
                java(browserlessTest("ProfileTest"), unifiedTest("ProfileTest", marker)));
    }

    @Test
    void aSharedBaseHoldingTheServiceConnectionIsLeftAsItIs() {
        rewriteRun(
                java("""
                        import com.vaadin.browserless.SpringBrowserlessTest;
                        import org.springframework.boot.test.context.SpringBootTest;
                        import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

                        @SpringBootTest
                        abstract class UiTestBase extends SpringBrowserlessTest {
                            @ServiceConnection
                            static final Object POSTGRES = new Object(); // a container
                        }
                        """, """
                        import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                        import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

                        @UnifiedSpringBootTest
                        abstract class UiTestBase {
                            @ServiceConnection
                            static final Object POSTGRES = new Object(); // a container
                        }
                        """),
                java(subclass("DashboardTest"), unifiedSubclass("DashboardTest")),
                java(subclass("ProfileTest"), unifiedSubclass("ProfileTest")));
    }

    private static String subclass(String name) {
        return """
                import com.vaadin.flow.component.button.Button;
                import org.junit.jupiter.api.Test;

                class %s extends UiTestBase {
                    @Test
                    void saves() {
                        test($(Button.class).withText("Save").single()).click();
                    }
                }
                """.formatted(name);
    }

    private static String unifiedSubclass(String name) {
        return """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                class %s extends UiTestBase {
                    @Test
                    void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """.formatted(name);
    }

    private static String browserlessTest(String name) {
        return """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.flow.component.button.Button;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
                
                @SpringBootTest
                class %s extends SpringBrowserlessTest {
                    @ServiceConnection
                    static final Object POSTGRES = new Object(); // a container

                    @Test
                    void saves() {
                        test($(Button.class).withText("Save").single()).click();
                    }
                }
                """.formatted(name);
    }

    private static String unifiedTest(String name, String marker) {
        return """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
                
                @UnifiedSpringBootTest
                class %s {
                    %s@ServiceConnection
                    static final Object POSTGRES = new Object(); // a container

                    @Test
                    void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """.formatted(name, marker);
    }
}
