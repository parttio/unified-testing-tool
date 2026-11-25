package org.vaadin.addons.unified.rewrite;

import static org.openrewrite.maven.Assertions.pomXml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * {@link MarkProfilesThatTestElsewhere}: a Failsafe profile that tested a server or browser outside the test says what
 * the TestBench migration changes there (#613).
 */
class MarkProfilesThatTestElsewhereTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MarkProfilesThatTestElsewhere());
    }

    private static String pom(String profiles) {
        return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <profiles>
                %s    </profiles>
                </project>
                """.formatted(profiles);
    }

    @Test
    void aProfileRunningFailsafeAgainstANativeExecutableSaysTheTestsNowStartTheJvmApp() {
        rewriteRun(pomXml(pom("""
                        <profile>
                            <id>it-native</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.graalvm.buildtools</groupId>
                                        <artifactId>native-maven-plugin</artifactId>
                                    </plugin>
                                    <plugin>
                                        <groupId>org.codehaus.mojo</groupId>
                                        <artifactId>exec-maven-plugin</artifactId>
                                    </plugin>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """), pom("""
                        <profile>
                            <id>it-native</id>
                            <!-- TODO unified: this profile ran the tests against a server it starts outside the test (native-maven-plugin, exec-maven-plugin). A migrated @UnifiedSpringBootTest starts the JVM app itself, so that server is no longer tested. Remove its start and stop, or migrate with springBootTest: false and point vaadin.testing.base-url at it. -->
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.graalvm.buildtools</groupId>
                                        <artifactId>native-maven-plugin</artifactId>
                                    </plugin>
                                    <plugin>
                                        <groupId>org.codehaus.mojo</groupId>
                                        <artifactId>exec-maven-plugin</artifactId>
                                    </plugin>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """)));
    }

    @Test
    void aProfileWithTestBenchGridOrSauceLabsSettingsSaysTheTestsRunALocalBrowser() {
        rewriteRun(pomXml(pom("""
                        <profile>
                            <id>it-saucelabs</id>
                            <properties>
                                <sauce.user>someone</sauce.user>
                            </properties>
                            <build>
                                <plugins>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <com.vaadin.testbench.Parameters.gridBrowsers>chrome</com.vaadin.testbench.Parameters.gridBrowsers>
                                            </systemPropertyVariables>
                                            <environmentVariables>
                                                <SAUCE_ACCESS_KEY>secret</SAUCE_ACCESS_KEY>
                                            </environmentVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """), pom("""
                        <profile>
                            <id>it-saucelabs</id>
                            <!-- TODO unified: this profile sets TestBench grid or Sauce Labs settings (sauce.user, com.vaadin.testbench.Parameters.gridBrowsers, SAUCE_ACCESS_KEY). The Unified Selenium adapter reads none of them and has no remote grid (#645): the migrated tests run a local browser. -->
                            <properties>
                                <sauce.user>someone</sauce.user>
                            </properties>
                            <build>
                                <plugins>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <com.vaadin.testbench.Parameters.gridBrowsers>chrome</com.vaadin.testbench.Parameters.gridBrowsers>
                                            </systemPropertyVariables>
                                            <environmentVariables>
                                                <SAUCE_ACCESS_KEY>secret</SAUCE_ACCESS_KEY>
                                            </environmentVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """)));
    }
    @Test
    void aPlainItProfileStartingTheAppWithSpringBootGetsNoMarker() {
        // the class marker already says to remove spring-boot:start/stop (ADR 0013)
        rewriteRun(pomXml(pom("""
                        <profile>
                            <id>it</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.springframework.boot</groupId>
                                        <artifactId>spring-boot-maven-plugin</artifactId>
                                        <executions>
                                            <execution>
                                                <goals>
                                                    <goal>start</goal>
                                                    <goal>stop</goal>
                                                </goals>
                                            </execution>
                                        </executions>
                                    </plugin>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """)));
    }
    @Test
    void aSpringBootGoalOtherThanStartAndStopCountsAsAServerStartedOutsideTheTest() {
        rewriteRun(pomXml(pom("""
                        <profile>
                            <id>it-aot</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.springframework.boot</groupId>
                                        <artifactId>spring-boot-maven-plugin</artifactId>
                                        <executions>
                                            <execution>
                                                <goals>
                                                    <goal>process-aot</goal>
                                                    <goal>start</goal>
                                                </goals>
                                            </execution>
                                        </executions>
                                    </plugin>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """), pom("""
                        <profile>
                            <id>it-aot</id>
                            <!-- TODO unified: this profile ran the tests against a server it starts outside the test (spring-boot:process-aot). A migrated @UnifiedSpringBootTest starts the JVM app itself, so that server is no longer tested. Remove its start and stop, or migrate with springBootTest: false and point vaadin.testing.base-url at it. -->
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.springframework.boot</groupId>
                                        <artifactId>spring-boot-maven-plugin</artifactId>
                                        <executions>
                                            <execution>
                                                <goals>
                                                    <goal>process-aot</goal>
                                                    <goal>start</goal>
                                                </goals>
                                            </execution>
                                        </executions>
                                    </plugin>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """)));
    }
    @Test
    void aProfileGivingFailsafeAServerAddressSaysTheTestsIgnoreIt() {
        rewriteRun(pomXml(pom("""
                        <profile>
                            <id>it-staging</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <app.url>https://staging.example.com</app.url>
                                                <vaadin.testing.evidence-dir>target/e</vaadin.testing.evidence-dir>
                                                <com.vaadin.testbench.Parameters.hubHostname>grid</com.vaadin.testbench.Parameters.hubHostname>
                                            </systemPropertyVariables>
                                            <systemProperties>
                                                <property>
                                                    <name>server.host</name>
                                                    <value>staging</value>
                                                </property>
                                            </systemProperties>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """), pom("""
                        <profile>
                            <id>it-staging</id>
                            <!-- TODO unified: this profile sets TestBench grid or Sauce Labs settings (com.vaadin.testbench.Parameters.hubHostname). The Unified Selenium adapter reads none of them and has no remote grid (#645): the migrated tests run a local browser. -->
                            <!-- TODO unified: this profile gives the tests the address of a server outside the test (app.url, server.host). A migrated @UnifiedSpringBootTest opens the app it starts, on a random port, and ignores it. Remove it, or migrate with springBootTest: false and set vaadin.testing.base-url instead. -->
                            <build>
                                <plugins>
                                    <plugin>
                                        <artifactId>maven-failsafe-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <app.url>https://staging.example.com</app.url>
                                                <vaadin.testing.evidence-dir>target/e</vaadin.testing.evidence-dir>
                                                <com.vaadin.testbench.Parameters.hubHostname>grid</com.vaadin.testbench.Parameters.hubHostname>
                                            </systemPropertyVariables>
                                            <systemProperties>
                                                <property>
                                                    <name>server.host</name>
                                                    <value>staging</value>
                                                </property>
                                            </systemProperties>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                """)));
    }
}
