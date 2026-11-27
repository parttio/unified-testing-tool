package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** {@link SeparateFailsafeEvidence}: Failsafe's runs keep their evidence apart from Surefire's (#512). */
class SeparateFailsafeEvidenceTest implements RewriteTest {

    private static final String ENTRY = "<vaadin.testing.evidence-dir>${project.build.directory}/failsafe-reports/"
            + "unified-evidence</vaadin.testing.evidence-dir>";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SeparateFailsafeEvidence());
    }

    private static String pom(String build) {
        return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                %s</project>
                """.formatted(build);
    }

    @Test
    void failsafeWithoutConfigurationGetsItsOwnEvidenceDirectory() {
        rewriteRun(pomXml(pom("""
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-surefire-plugin</artifactId>
                            </plugin>
                            <plugin>
                                <artifactId>maven-failsafe-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                """), pom("""
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-surefire-plugin</artifactId>
                            </plugin>
                            <plugin>
                                <artifactId>maven-failsafe-plugin</artifactId>
                                <configuration>
                                    <systemPropertyVariables>
                                        %s
                                    </systemPropertyVariables>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                """.formatted(ENTRY))));
    }

    @Test
    void aNewConfigurationGoesBeforeTheExecutions() {
        rewriteRun(pomXml(pom("""
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-failsafe-plugin</artifactId>
                                <version>3.5.4</version>
                                <executions>
                                    <execution>
                                        <goals>
                                            <goal>integration-test</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                """), pom("""
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-failsafe-plugin</artifactId>
                                <version>3.5.4</version>
                                <configuration>
                                    <systemPropertyVariables>
                                        %s
                                    </systemPropertyVariables>
                                </configuration>
                                <executions>
                                    <execution>
                                        <goals>
                                            <goal>integration-test</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                """.formatted(ENTRY))));
    }

    @Test
    void failsafeInAProfileKeepsItsOtherSettings() {
        rewriteRun(pomXml(pom("""
                    <profiles>
                        <profile>
                            <id>it</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <vaadin.testing.tool>playwright</vaadin.testing.tool>
                                            </systemPropertyVariables>
                                        </configuration>
                                        <executions>
                                            <execution>
                                                <goals>
                                                    <goal>integration-test</goal>
                                                    <goal>verify</goal>
                                                </goals>
                                            </execution>
                                        </executions>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                    </profiles>
                """), pom("""
                    <profiles>
                        <profile>
                            <id>it</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <vaadin.testing.tool>playwright</vaadin.testing.tool>
                                                %s
                                            </systemPropertyVariables>
                                        </configuration>
                                        <executions>
                                            <execution>
                                                <goals>
                                                    <goal>integration-test</goal>
                                                    <goal>verify</goal>
                                                </goals>
                                            </execution>
                                        </executions>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                    </profiles>
                """.formatted(ENTRY))));
    }

    @Test
    void configurationWithoutSystemPropertyVariablesKeepsItsSettings() {
        rewriteRun(pomXml(pom("""
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-failsafe-plugin</artifactId>
                                <configuration>
                                    <includes>
                                        <include>**/*IT.java</include>
                                    </includes>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                """), pom("""
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-failsafe-plugin</artifactId>
                                <configuration>
                                    <includes>
                                        <include>**/*IT.java</include>
                                    </includes>
                                    <systemPropertyVariables>
                                        %s
                                    </systemPropertyVariables>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                """.formatted(ENTRY))));
    }

    @Test
    void anEvidenceDirectorySetAnywhereInThePluginIsLeftAlone() {
        rewriteRun(pomXml(pom("""
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-failsafe-plugin</artifactId>
                                <executions>
                                    <execution>
                                        <id>browserless</id>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <vaadin.testing.evidence-dir>target/b</vaadin.testing.evidence-dir>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                """)));
    }

    @Test
    void pluginManagementAndPomsWithoutFailsafeAreLeftAlone() {
        rewriteRun(pomXml(pom("""
                    <build>
                        <pluginManagement>
                            <plugins>
                                <plugin>
                                    <artifactId>maven-failsafe-plugin</artifactId>
                                </plugin>
                            </plugins>
                        </pluginManagement>
                        <plugins>
                            <plugin>
                                <artifactId>maven-surefire-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                """)));
    }

    @Test
    void everyBuildRecipeIncludesIt() throws IOException {
        for (String recipe : new String[] { "browserless", "dramafinder", "testbench" }) {
            try (InputStream yml = getClass().getResourceAsStream("/META-INF/rewrite/" + recipe + ".yml")) {
                String text = new String(yml.readAllBytes(), StandardCharsets.UTF_8);
                String build = text.substring(text.indexOf("name: org.vaadin.addons.unified.rewrite.Migrate"));
                build = build.substring(0, build.indexOf("\n---"));
                assertThat(build).as(recipe).contains("org.vaadin.addons.unified.rewrite.SeparateFailsafeEvidence");
            }
        }
    }
}
