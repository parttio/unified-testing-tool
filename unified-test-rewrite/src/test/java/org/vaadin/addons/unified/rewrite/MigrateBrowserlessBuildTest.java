package org.vaadin.addons.unified.rewrite;

import static org.openrewrite.maven.Assertions.pomXml;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** The Browserless Migration Recipe's build changes (ADR 0005). */
class MigrateBrowserlessBuildTest implements RewriteTest {

    /** The Unified API version the recipes add: this build's, installed in the local repository by the reactor. */
    private static final String UNIFIED_VERSION = System.getProperty("unified.version");

    private static final String MANAGED = """
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>vaadin-bom</artifactId>
                            <version>25.3.5</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>4.1.1</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>%s
                    </dependencies>
                </dependencyManagement>
            """;

    private static final String UNIFIED_BOM = """
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-bom</artifactId>
                            <version>%s</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
            """;

    @Override
    public void defaults(RecipeSpec spec) {
        // found by name on the classpath, as the rewrite Maven and Gradle plugins find it
        spec.recipe(Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                .activateRecipes("org.vaadin.addons.unified.rewrite.MigrateBrowserlessToUnified"));
    }

    private static String pom(boolean unifiedBom, String dependencies) {
        return pom("", unifiedBom, dependencies);
    }

    /** {@code head}: what the recipe adds at the top of the project, such as a Migration Marker. */
    private static String pom(String head, boolean unifiedBom, String dependencies) {
        return """
                <project>%s
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                %s    <dependencies>
                %s    </dependencies>
                </project>
                """.formatted(head, MANAGED.formatted(unifiedBom ? "\n" + UNIFIED_BOM.formatted(UNIFIED_VERSION).stripTrailing() : ""),
                dependencies);
    }

    private static String dependency(String groupId, String artifactId, String version) {
        return """
                        <dependency>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>%s
                            <scope>test</scope>
                        </dependency>
                """.formatted(groupId, artifactId,
                version == null ? "" : "\n                <version>" + version + "</version>");
    }

    private static String unified(String artifactId) {
        return dependency("org.vaadin.addons", artifactId, null);
    }

    @Test
    void springProjectGetsTheBomTheBrowserlessAdapterAndTheSpringIntegration() {
        rewriteRun(pomXml(
                pom(false, dependency("com.vaadin", "browserless-test-junit6", "25.3.2")
                        + dependency("com.vaadin", "browserless-test-spring", "25.3.2")
                        + dependency("org.springframework.boot", "spring-boot-starter-test", null)),
                pom(true, dependency("com.vaadin", "browserless-test-spring", null)
                        + dependency("org.springframework.boot", "spring-boot-starter-test", null)
                        + unified("unified-test-browserless")
                        + unified("unified-test-spring"))));
    }

    @Test
    void aStaleBrowserlessTestSpringVersionGivesWayToTheBoms() {
        // 1.1.2 is the old standalone add-on line: its explicit version would win over the Vaadin BOM's 25.x (#354)
        rewriteRun(pomXml(
                pom(false, dependency("com.vaadin", "browserless-test-junit6", "25.3.2")
                        + dependency("com.vaadin", "browserless-test-spring", "1.1.2")
                        + dependency("org.springframework.boot", "spring-boot-starter-test", null)),
                pom(true, dependency("com.vaadin", "browserless-test-spring", null)
                        + dependency("org.springframework.boot", "spring-boot-starter-test", null)
                        + unified("unified-test-browserless")
                        + unified("unified-test-spring"))));
    }

    @Test
    void plainProjectGetsNoSpringIntegration() {
        rewriteRun(pomXml(
                pom(false, dependency("com.vaadin", "browserless-test-junit6", "25.3.2")),
                pom(true, unified("unified-test-browserless"))));
    }

    @Test
    void addPlaywrightAndAddSeleniumAddTheBrowserAdapters() {
        rewriteRun(spec -> spec.recipe(new MigrateBrowserlessToUnified(true, true)),
                pomXml(
                        pom(false, dependency("com.vaadin", "browserless-test-junit6", "25.3.2")),
                        pom(true, unified("unified-test-browserless")
                                + unified("unified-test-playwright")
                                + unified("unified-test-selenium"))));
    }

    @Test
    void testBenchUiUnitDependenciesAreReplaced() {
        rewriteRun(pomXml(
                pom(false, dependency("com.vaadin", "vaadin-testbench-unit-junit5", "9.6.6")),
                pom(true, unified("unified-test-browserless"))));
    }

    @Test
    void projectWithoutBrowserlessIsLeftAlone() {
        rewriteRun(pomXml(pom(false, dependency("org.springframework.boot", "spring-boot-starter-test", null))));
    }

    @Test
    void withoutAVaadinBomBrowserlessTestSpringKeepsItsVersion() {
        // nothing manages it then: without its version, the pom would not build (#354, #387)
        rewriteRun(pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-junit6</artifactId>
                            <version>25.3.2</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-spring</artifactId>
                            <version>25.3.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-bom</artifactId>
                                <version>%s</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-spring</artifactId>
                            <version>25.3.2</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-browserless</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(UNIFIED_VERSION)));
    }

    @Test
    void olderVaadinIsMigratedWithAMarkerAtTheTop() {
        String before = pom(false, dependency("com.vaadin", "browserless-test-junit6", "1.1.0")).replace("25.3.5", "25.2.0");
        String after = pom("\n    <!-- TODO unified: the Unified API needs Vaadin 25.3 or newer, and this project has Vaadin 25.2"
                + " (Flow 25.2.0). Move it to Vaadin 25.3 or newer: until then the migrated tests fail when they run. -->",
                true, unified("unified-test-browserless")).replace("25.3.5", "25.2.0");
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1), pomXml(before, after));
    }
}
