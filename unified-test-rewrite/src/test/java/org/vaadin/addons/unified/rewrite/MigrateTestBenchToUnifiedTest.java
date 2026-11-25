package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.search.FindMissingTypes;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** The composite Migration Recipe, one case per rule. */
class MigrateTestBenchToUnifiedTest implements RewriteTest {

    /** The Unified API version the recipes add: this build's, installed in the local repository by the reactor. */
    private static final String UNIFIED_VERSION = System.getProperty("unified.version");

    @Override
    public void defaults(RecipeSpec spec) {
        // found by name on the classpath, as the rewrite Maven and Gradle plugins find it
        spec.recipe(Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                        .activateRecipes("org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified"))
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void browserTestBecomesUnifiedSpringBootTestOnAUnifiedPage() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.boot.test.web.server.LocalServerPort;

                @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
                class ProfileTest extends BrowserTestBase {

                    @LocalServerPort
                    int port;

                    @BrowserTest
                    void opensProfile() {
                        getDriver().get("http://localhost:" + port + "/profile");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class ProfileTest {

                    @Test
                    void opensProfile(UnifiedPage page) {
                        page.navigate("profile");
                    }
                }
                """));
    }

    @Test
    void navigatesToTheViewClassWhenOneViewHasTheRoute() {
        rewriteRun(
                java(
                        """
                        package demo;

                        import com.vaadin.flow.component.Component;
                        import com.vaadin.flow.router.Route;

                        @Route("profile")
                        public class ProfileView extends Component {
                        }
                        """),
                java(
                        """
                        package demo;

                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class ProfileTest extends BrowserTestBase {

                            @BrowserTest
                            void opensProfile() {
                                getDriver().get("http://localhost:8080/profile");
                            }
                        }
                        """,
                        """
                        package demo;

                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class ProfileTest {

                            @Test
                            void opensProfile(UnifiedPage page) {
                                page.navigate(ProfileView.class);
                            }
                        }
                        """));
    }

    @Test
    void textFieldLookupsFindTheUnifiedTextField() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void fillsTheName() {
                        $(TextFieldElement.class).withLabel("Name").single().setValue("Alice");
                        $(TextFieldElement.class).withId("email").single().setValue("alice@example.com");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void fillsTheName(UnifiedPage page) {
                        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");
                        page.find(UnifiedTextField.class).withId("email").setValue("alice@example.com");
                    }
                }
                """));
    }

    /** TestBench's withClassName takes several names; the Unified one takes one (#259). */
    @Test
    void severalClassNamesBecomeWithClassNames() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void fillsTheName() {
                        $(TextFieldElement.class).withClassName("name", "required").single().setValue("Alice");
                        $(TextFieldElement.class).withClassName("name required").single().setValue("Bob");
                        $(TextFieldElement.class).withClassName("name").withLabel("Name").single().setValue("Carol");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void fillsTheName(UnifiedPage page) {
                        page.find(UnifiedTextField.class).withClassNames("name", "required").setValue("Alice");
                        page.find(UnifiedTextField.class).withClassNames("name required").setValue("Bob");
                        page.find(UnifiedTextField.class).withClassName("name").withLabel("Name").setValue("Carol");
                    }
                }
                """));
    }

    @Test
    void buttonLookupsFindTheUnifiedButton() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void saves() {
                        $(ButtonElement.class).withText("Save").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void emailAndPasswordFieldLookupsFindTheirWrappers() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.EmailFieldElement;
                import com.vaadin.flow.component.textfield.testbench.PasswordFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class SignUpTest extends BrowserTestBase {

                    @BrowserTest
                    void signsUp() {
                        $(EmailFieldElement.class).withLabel("Email").single().setValue("ada@example.com");
                        $(PasswordFieldElement.class).withLabel("Password").single().setValue("correct horse");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedEmailField;
                import com.vaadin.testing.unified.component.UnifiedPasswordField;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class SignUpTest {

                    @Test
                    void signsUp(UnifiedPage page) {
                        page.find(UnifiedEmailField.class).withLabel("Email").setValue("ada@example.com");
                        page.find(UnifiedPasswordField.class).withLabel("Password").setValue("correct horse");
                    }
                }
                """));
    }

    @Test
    void anchorLookupsFindTheUnifiedLink() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.html.testbench.AnchorElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class NavigationTest extends BrowserTestBase {

                    @BrowserTest
                    void opensUsers() {
                        $(AnchorElement.class).withText("Users").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedLink;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class NavigationTest {

                    @Test
                    void opensUsers(UnifiedPage page) {
                        page.find(UnifiedLink.class).withText("Users").click();
                    }
                }
                """));
    }

    @Test
    void confirmDialogButtonsBecomeTheirRole() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.confirmdialog.testbench.ConfirmDialogElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class OrderTest extends BrowserTestBase {

                    @BrowserTest
                    void deletesTheOrder() {
                        String header = $(ConfirmDialogElement.class).single().getHeaderText();
                        String message = $(ConfirmDialogElement.class).single().getMessageText();
                        $(ConfirmDialogElement.class).single().getConfirmButton().click();
                        $(ConfirmDialogElement.class).first().getRejectButton().click();
                        $(ConfirmDialogElement.class).withId("leave").single().getCancelButton().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class OrderTest {

                    @Test
                    void deletesTheOrder(UnifiedPage page) {
                        String header = page.find(UnifiedConfirmDialog.class).getHeaderTitle();
                        String message = page.find(UnifiedConfirmDialog.class).getText();
                        page.find(UnifiedConfirmDialog.class).confirm();
                        // TODO unified: was first(); the lookup fails when several components match
                        page.find(UnifiedConfirmDialog.class).reject();
                        page.find(UnifiedConfirmDialog.class).withId("leave").cancel();
                    }
                }
                """));
    }

    @Test
    void loginElementsBecomeUnifiedLogin() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.login.testbench.LoginFormElement;
                import com.vaadin.flow.component.login.testbench.LoginOverlayElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class LoginTest extends BrowserTestBase {

                    @BrowserTest
                    void readsTheLogin() {
                        boolean open = $(LoginOverlayElement.class).single().isOpened();
                        String title = $(LoginOverlayElement.class).single().getTitle();
                        String description = $(LoginOverlayElement.class).single().getDescription();
                        $(LoginFormElement.class).single().forgotPassword();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedLogin;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class LoginTest {

                    @Test
                    void readsTheLogin(UnifiedPage page) {
                        boolean open = page.find(UnifiedLogin.class).isOpen();
                        String title = page.find(UnifiedLogin.class).getTitle();
                        String description = page.find(UnifiedLogin.class).getDescription();
                        page.find(UnifiedLogin.class).forgotPassword();
                    }
                }
                """));
    }

    @Test
    void assertEqualsOnValueOrTextBecomesARetryingAssertion() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void showsTheProfile() {
                        Assertions.assertEquals("Alice", $(TextFieldElement.class).withLabel("Name").single().getValue());
                        Assertions.assertEquals("Save", $(ButtonElement.class).withText("Save").single().getText());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void showsTheProfile(UnifiedPage page) {
                        assertThat(page.find(UnifiedTextField.class).withLabel("Name")).hasValue("Alice");
                        assertThat(page.find(UnifiedButton.class).withText("Save")).hasText("Save");
                    }
                }
                """));
    }

    @Test
    void buildSwapsTestBenchForTheUnifiedBomAndAdapters() {
        rewriteRun(pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
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
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>vaadin-testbench-core-junit6</artifactId>
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
                            </dependency>
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
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-browserless</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-selenium</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(UNIFIED_VERSION)));
    }

    @Test
    void aFailsafeProfileWithTestBenchGridSettingsGetsAMigrationMarker() {
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
                            <artifactId>vaadin-testbench-core-junit6</artifactId>
                            <version>10.1.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <profiles>
                        <profile>
                            <id>it-saucelabs</id>
                            <properties>
                                <com.vaadin.testbench.Parameters.gridBrowsers>chrome</com.vaadin.testbench.Parameters.gridBrowsers>
                            </properties>
                        </profile>
                    </profiles>
                </project>
                """,
                spec -> spec.after(actual -> {
                    assertThat(actual).contains("<id>it-saucelabs</id>\n            <!-- "
                            + MarkProfilesThatTestElsewhere.GRID + " (com.vaadin.testbench.Parameters.gridBrowsers)");
                    return actual;
                })));
    }

    @Test
    void aStaleBrowserlessTestSpringVersionGivesWayToTheBoms() {
        // 1.1.2 is the old standalone add-on line: its explicit version would win over the Vaadin BOM's 25.x (#354)
        rewriteRun(pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
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
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>vaadin-testbench-core-junit6</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-spring</artifactId>
                            <version>1.1.2</version>
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
                            </dependency>
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
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-browserless</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-selenium</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(UNIFIED_VERSION)));
    }

    @Test
    void addPlaywrightAlsoAddsThePlaywrightAdapter() {
        rewriteRun(
                spec -> spec.recipe(new MigrateTestBenchToUnified(true)),
                pomXml(
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0</version>
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
                                    </dependency>
                                </dependencies>
                            </dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>com.vaadin</groupId>
                                    <artifactId>vaadin-testbench-core-junit6</artifactId>
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
                                    </dependency>
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
                                    <scope>test</scope>
                                </dependency>
                                <dependency>
                                    <groupId>org.vaadin.addons</groupId>
                                    <artifactId>unified-test-browserless</artifactId>
                                    <scope>test</scope>
                                </dependency>
                                <dependency>
                                    <groupId>org.vaadin.addons</groupId>
                                    <artifactId>unified-test-playwright</artifactId>
                                    <scope>test</scope>
                                </dependency>
                                <dependency>
                                    <groupId>org.vaadin.addons</groupId>
                                    <artifactId>unified-test-selenium</artifactId>
                                    <scope>test</scope>
                                </dependency>
                                <dependency>
                                    <groupId>org.vaadin.addons</groupId>
                                    <artifactId>unified-test-spring</artifactId>
                                    <scope>test</scope>
                                </dependency>
                            </dependencies>
                        </project>
                        """.formatted(UNIFIED_VERSION)));
    }

    @Test
    void aTestGatedOnTheTestingToolSettingRunsOnEveryTestingTool() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

                @EnabledIfSystemProperty(named = "vaadin.testing.tool", matches = "selenium")
                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void opensProfile() {
                        getDriver().get("http://localhost:8080/profile");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void opensProfile(UnifiedPage page) {
                        page.navigate("profile");
                    }
                }
                """));
    }

    @Test
    void leftoverTestBenchCallsRunThroughTheEscapeHatchOnSeleniumOnly() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.upload.testbench.UploadElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void countsPeople() {
                        getDriver().get("http://localhost:8080/people");
                        String text = $(UploadElement.class).first().getText();
                        executeScript("window.scrollTo(0, 0)");
                    }
                }
                """,
                """
                import com.vaadin.flow.component.upload.testbench.UploadElement;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void countsPeople(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        page.navigate("people");
                        // TODO unified: TestBench Escape Hatch: $(UploadElement.class)
                        String text = testBench.$(UploadElement.class).first().getText();
                        // TODO unified: TestBench Escape Hatch: executeScript("window.scrollTo(0, 0)")
                        testBench.executeScript("window.scrollTo(0, 0)");
                    }
                }
                """));
    }

    /** Bakery's check for a local browser (#621): {@code TestBenchEscapeHatch} answers it, so the test compiles. */
    @Test
    void runLocallyBrowserGoesThroughTheEscapeHatch() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void scrolls() {
                        getDriver().get("http://localhost:8080/people");
                        if (getRunLocallyBrowser() == null) {
                            System.out.println("remote");
                        }
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void scrolls(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        page.navigate("people");
                        // TODO unified: TestBench Escape Hatch: getRunLocallyBrowser()
                        if (testBench.getRunLocallyBrowser() == null) {
                            System.out.println("remote");
                        }
                    }
                }
                """,
                spec -> spec.afterRecipe(cu -> assertTypeChecksAgainstTheEscapeHatch(cu.printAll(), "getRunLocallyBrowser"))));
    }

    @Test
    void runLocallyBrowserVersionGoesThroughTheEscapeHatch() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void scrolls() {
                        getDriver().get("http://localhost:8080/people");
                        if (getRunLocallyBrowserVersion().isEmpty()) {
                            System.out.println("any version");
                        }
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void scrolls(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        page.navigate("people");
                        // TODO unified: TestBench Escape Hatch: getRunLocallyBrowserVersion()
                        if (testBench.getRunLocallyBrowserVersion().isEmpty()) {
                            System.out.println("any version");
                        }
                    }
                }
                """,
                spec -> spec.afterRecipe(cu -> assertTypeChecksAgainstTheEscapeHatch(cu.printAll(),
                        "getRunLocallyBrowserVersion"))));
    }

    /**
     * The migrated source, parsed again against the real Unified API and TestBench: no type is missing, and
     * {@code method} resolves on {@code TestBenchEscapeHatch}.
     */
    private static void assertTypeChecksAgainstTheEscapeHatch(String migrated, String method) {
        J.CompilationUnit cu = (J.CompilationUnit) JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath())
                .build().parse(new InMemoryExecutionContext(Throwable::printStackTrace), migrated)
                .findFirst().orElseThrow();
        assertThat(FindMissingTypes.findMissingTypes(cu, true)).extracting(r -> r.getMessage() + ": " + r.getPrintedTree())
                .isEmpty();
        List<JavaType.Method> calls = new ArrayList<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call, Integer p) {
                if (call.getSimpleName().equals(method)) {
                    calls.add(call.getMethodType());
                }
                return super.visitMethodInvocation(call, p);
            }
        }.visit(cu, 0);
        assertThat(calls).singleElement().extracting(t -> t.getDeclaringType().getFullyQualifiedName())
                .isEqualTo("com.vaadin.testing.unified.selenium.TestBenchEscapeHatch");
    }

    @Test
    void anEscapeHatchMarkerCarriesOnlyTheCallNotTheCommentAboveIt() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void scrolls() {
                        getDriver().get("http://localhost:8080/people");
                        // back to the top:
                        // the header is sticky
                        executeScript("window.scrollTo(0, 0)");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void scrolls(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        page.navigate("people");
                        // back to the top:
                        // the header is sticky
                        // TODO unified: TestBench Escape Hatch: executeScript("window.scrollTo(0, 0)")
                        testBench.executeScript("window.scrollTo(0, 0)");
                    }
                }
                """));
    }

    @Test
    void aLookupStaysTestBenchWhenTheWrapperLacksACallUsedOnIt() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void typesTheName() {
                        $(TextFieldElement.class).withLabel("Name").single().sendKeys("Alice");
                        $(TextFieldElement.class).withLabel("Email").single().setValue("alice@example.com");
                    }
                }
                """,
                """
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void typesTheName(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        // TODO unified: TestBench Escape Hatch: $(TextFieldElement.class)
                        testBench.$(TextFieldElement.class).withLabel("Name").single().sendKeys("Alice");
                        page.find(UnifiedTextField.class).withLabel("Email").setValue("alice@example.com");
                    }
                }
                """));
    }

    @Test
    void anAssertionStaysJUnitWhenItsLookupStaysTestBench() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void showsTheNickname() {
                        Assertions.assertEquals("Ali", $(TextFieldElement.class).withAttribute("name", "nick").single().getValue());
                    }
                }
                """,
                """
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void showsTheNickname(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        // TODO unified: TestBench Escape Hatch: $(TextFieldElement.class)
                        Assertions.assertEquals("Ali", testBench.$(TextFieldElement.class).withAttribute("name", "nick").single().getValue());
                    }
                }
                """));
    }

    @Test
    void aLocalHoldingAConvertedLookupTakesTheWrapperType() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void savesTheName() {
                        TextFieldElement name = $(TextFieldElement.class).withLabel("Name").single();
                        name.setValue("Alice");
                        Assertions.assertEquals("Alice", name.getValue());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void savesTheName(UnifiedPage page) {
                        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
                        name.setValue("Alice");
                        assertThat(name).hasValue("Alice");
                    }
                }
                """));
    }

    @Test
    void firstBecomesSingleWithAMarker() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void saves() {
                        $(ButtonElement.class).withText("Save").first().click();
                        Assertions.assertEquals("Archive", $(ButtonElement.class).withText("Archive").waitForFirst().getText());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void saves(UnifiedPage page) {
                        // TODO unified: was first(); the lookup fails when several components match
                        page.find(UnifiedButton.class).withText("Save").click();
                        // TODO unified: was waitForFirst(); the lookup fails when several components match
                        assertThat(page.find(UnifiedButton.class).withText("Archive")).hasText("Archive");
                    }
                }
                """));
    }

    @Test
    void beforeEachAndHelpersUsingTestBenchGetThePage() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.BeforeEach;

                class ProfileTest extends BrowserTestBase {

                    @BeforeEach
                    void open() {
                        getDriver().get("http://localhost:8080/profile");
                    }

                    @BrowserTest
                    void savesTheName() {
                        fillName("Alice");
                        $(ButtonElement.class).withText("Save").single().click();
                    }

                    private void fillName(String name) {
                        $(TextFieldElement.class).withLabel("Name").single().setValue(name);
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    void open(UnifiedPage page) {
                        page.navigate("profile");
                    }

                    @Test
                    void savesTheName(UnifiedPage page) {
                        open(page);
                        fillName(page, "Alice");
                        page.find(UnifiedButton.class).withText("Save").click();
                    }

                    private void fillName(UnifiedPage page, String name) {
                        page.find(UnifiedTextField.class).withLabel("Name").setValue(name);
                    }
                }
                """));
    }

    @Test
    void aTestCallingAHelperThatEscapesRequiresSelenium() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.upload.testbench.UploadElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void countsPeople() {
                        uploadText();
                    }

                    private String uploadText() {
                        return $(UploadElement.class).first().getText();
                    }
                }
                """,
                """
                import com.vaadin.flow.component.upload.testbench.UploadElement;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void countsPeople(UnifiedPage page) {
                        uploadText(page);
                    }

                    private String uploadText(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        // TODO unified: TestBench Escape Hatch: $(UploadElement.class)
                        return testBench.$(UploadElement.class).first().getText();
                    }
                }
                """));
    }

    @Test
    void notificationLookupsFindTheUnifiedNotification() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.notification.testbench.NotificationElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void confirmsTheSave() {
                        Assertions.assertEquals("Saved", $(NotificationElement.class).waitForFirst().getText());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedNotification;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void confirmsTheSave(UnifiedPage page) {
                        // TODO unified: was waitForFirst(); the lookup fails when several components match
                        assertThat(page.find(UnifiedNotification.class)).hasText("Saved");
                    }
                }
                """));
    }


    /** A chat: MessageInputElement and MessageListElement (#357). */
    @Test
    void chatLookupsFindTheUnifiedMessageInputAndList() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.messages.testbench.MessageInputElement;
                import com.vaadin.flow.component.messages.testbench.MessageListElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class ChatTest extends BrowserTestBase {

                    @BrowserTest
                    void asks() {
                        $(MessageInputElement.class).first().submit("Pancakes");
                        Assertions.assertEquals(3, $(MessageListElement.class).single().getMessageElements().size());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedMessageInput;
                import com.vaadin.testing.unified.component.UnifiedMessageList;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ChatTest {

                    @Test
                    void asks(UnifiedPage page) {
                        // TODO unified: was first(); the lookup fails when several components match
                        page.find(UnifiedMessageInput.class).send("Pancakes");
                        Assertions.assertEquals(3, page.find(UnifiedMessageList.class).getMessageCount());
                    }
                }
                """));
    }

    @Test
    void gridLookupsFindTheUnifiedGridAndCellsByIndexReadThroughTheRow() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.grid.testbench.GridElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void listsPeople() {
                        GridElement people = $(GridElement.class).waitForFirst();
                        Assertions.assertEquals("Alan Turing", people.getCell(0, 0).getText());
                        Assertions.assertEquals(3, people.getRowCount());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedGrid;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    void listsPeople(UnifiedPage page) {
                        // TODO unified: was waitForFirst(); the lookup fails when several components match
                        UnifiedGrid people = page.find(UnifiedGrid.class);
                        Assertions.assertEquals("Alan Turing", people.row(0).getCellText(0));
                        Assertions.assertEquals(3, people.getRowCount());
                    }
                }
                """));
    }

    @Test
    void aCellFoundByTextBecomesItsRow() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.grid.testbench.GridElement;
                import com.vaadin.flow.component.grid.testbench.GridTHTDElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void opensAlan() {
                        GridElement people = $(GridElement.class).single();
                        GridTHTDElement alan = people.getCell("Alan Turing");
                        int row = alan.getRow();
                        alan.click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedGrid;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    void opensAlan(UnifiedPage page) {
                        UnifiedGrid people = page.find(UnifiedGrid.class);
                        // TODO unified: was getCell("Alan Turing"); the row lookup fails when several rows match
                        UnifiedGrid.UnifiedGridRow alan = people.row().withCellText("Alan Turing");
                        int row = alan.getIndex();
                        alan.click();
                    }
                }
                """));
    }

    @Test
    void checkboxLookupsAndCheckedAssertions() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.checkbox.testbench.CheckboxElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class PreferencesTest extends BrowserTestBase {

                    @BrowserTest
                    void togglesNewsletter() {
                        CheckboxElement newsletter = $(CheckboxElement.class).withLabel("Newsletter").single();
                        newsletter.setChecked(true);
                        Assertions.assertTrue(newsletter.isChecked());
                        Assertions.assertFalse($(CheckboxElement.class).withLabel("Dark mode").single().isChecked());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedCheckbox;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class PreferencesTest {

                    @Test
                    void togglesNewsletter(UnifiedPage page) {
                        UnifiedCheckbox newsletter = page.find(UnifiedCheckbox.class).withLabel("Newsletter");
                        newsletter.setChecked(true);
                        assertThat(newsletter).isChecked();
                        assertThat(page.find(UnifiedCheckbox.class).withLabel("Dark mode")).isNotChecked();
                    }
                }
                """));
    }

    @Test
    void comboBoxCallsTakeTheirUnifiedNames() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class OrderTest extends BrowserTestBase {

                    @BrowserTest
                    void picksAProduct() {
                        ComboBoxElement product = $(ComboBoxElement.class).withLabel("Product").single();
                        product.selectByText("Tea");
                        Assertions.assertEquals("Tea", product.getSelectedText());
                        Assertions.assertEquals(3, product.getOptions().size());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedComboBox;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class OrderTest {

                    @Test
                    void picksAProduct(UnifiedPage page) {
                        UnifiedComboBox product = page.find(UnifiedComboBox.class).withLabel("Product");
                        product.selectItem("Tea");
                        assertThat(product).hasSelectedItem("Tea");
                        Assertions.assertEquals(3, product.getItems().size());
                    }
                }
                """));
    }

    @Test
    void radioButtonGroupCallsTakeTheirUnifiedNames() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.radiobutton.testbench.RadioButtonGroupElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class OrderTest extends BrowserTestBase {

                    @BrowserTest
                    void picksDelivery() {
                        $(RadioButtonGroupElement.class).withLabel("Delivery").single().selectByText("Express");
                        Assertions.assertEquals("Express", $(RadioButtonGroupElement.class).withLabel("Delivery").single().getSelectedText());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class OrderTest {

                    @Test
                    void picksDelivery(UnifiedPage page) {
                        page.find(UnifiedRadioButtonGroup.class).withLabel("Delivery").selectItem("Express");
                        assertThat(page.find(UnifiedRadioButtonGroup.class).withLabel("Delivery")).hasSelectedItem("Express");
                    }
                }
                """));
    }

    @Test
    void multiSelectComboBoxCallsTakeTheirUnifiedNames() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.combobox.testbench.MultiSelectComboBoxElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import java.util.List;
                import org.junit.jupiter.api.Assertions;

                class OrderTest extends BrowserTestBase {

                    @BrowserTest
                    void picksExtras() {
                        MultiSelectComboBoxElement extras = $(MultiSelectComboBoxElement.class).withLabel("Extras").single();
                        extras.selectByText("Milk");
                        extras.selectByText("Sugar");
                        extras.deselectByText("Milk");
                        Assertions.assertEquals(List.of("Sugar"), extras.getSelectedTexts());
                        Assertions.assertEquals(List.of("Milk", "Sugar", "Honey"), extras.getOptions());
                        extras.deselectAll();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class OrderTest {

                    @Test
                    void picksExtras(UnifiedPage page) {
                        UnifiedMultiSelectComboBox extras = page.find(UnifiedMultiSelectComboBox.class).withLabel("Extras");
                        extras.selectItems("Milk");
                        extras.selectItems("Sugar");
                        extras.deselectItems("Milk");
                        assertThat(extras).hasSelectedItems("Sugar");
                        assertThat(extras).hasItems("Milk", "Sugar", "Honey");
                        extras.clear();
                    }
                }
                """));
    }

    @Test
    void datePickerCallsTakeTheirUnifiedNames() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.datepicker.testbench.DatePickerElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import java.time.LocalDate;
                import org.junit.jupiter.api.Assertions;

                class PreferencesTest extends BrowserTestBase {

                    @BrowserTest
                    void setsTheBirthday() {
                        DatePickerElement birthday = $(DatePickerElement.class).withLabel("Birthday").single();
                        birthday.setDate(LocalDate.of(1990, 5, 17));
                        Assertions.assertEquals(LocalDate.of(1990, 5, 17), birthday.getDate());
                        birthday.setInputValue("1/2/2000");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedDatePicker;
                import org.junit.jupiter.api.Test;

                import java.time.LocalDate;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class PreferencesTest {

                    @Test
                    void setsTheBirthday(UnifiedPage page) {
                        UnifiedDatePicker birthday = page.find(UnifiedDatePicker.class).withLabel("Birthday");
                        birthday.setValue(LocalDate.of(1990, 5, 17));
                        assertThat(birthday).hasValue(LocalDate.of(1990, 5, 17));
                        birthday.typeText("1/2/2000");
                    }
                }
                """));
    }

    @Test
    void numberFieldStringLiteralsBecomeNumbers() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.NumberFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class PreferencesTest extends BrowserTestBase {

                    @BrowserTest
                    void setsTheAge() {
                        NumberFieldElement age = $(NumberFieldElement.class).withLabel("Age").single();
                        age.setValue("42");
                        Assertions.assertEquals("42", age.getValue());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedNumberField;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class PreferencesTest {

                    @Test
                    void setsTheAge(UnifiedPage page) {
                        UnifiedNumberField age = page.find(UnifiedNumberField.class).withLabel("Age");
                        age.setValue(42.0);
                        assertThat(age).hasValue(42.0);
                    }
                }
                """));
    }

    @Test
    void aNumberFieldValueThatIsNotALiteralStaysTestBench() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.testbench.NumberFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class PreferencesTest extends BrowserTestBase {

                    @BrowserTest
                    void setsTheAge() {
                        String age = "42";
                        $(NumberFieldElement.class).withLabel("Age").single().setValue(age);
                    }
                }
                """,
                """
                import com.vaadin.flow.component.textfield.testbench.NumberFieldElement;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PreferencesTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void setsTheAge(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        String age = "42";
                        // TODO unified: TestBench Escape Hatch: $(NumberFieldElement.class)
                        testBench.$(NumberFieldElement.class).withLabel("Age").single().setValue(age);
                    }
                }
                """));
    }

    @Test
    void buttonLabelAndCaptionFiltersBecomeTextWithAMarker() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void saves() {
                        $(ButtonElement.class).withCaption("Save").single().click();
                        $(ButtonElement.class).withLabel("Cancel").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void saves(UnifiedPage page) {
                        // TODO unified: was withCaption("Save"); use withAccessibleName(..) for a button without text
                        page.find(UnifiedButton.class).withText("Save").click();
                        // TODO unified: was withLabel("Cancel"); use withAccessibleName(..) for a button without text
                        page.find(UnifiedButton.class).withText("Cancel").click();
                    }
                }
                """));
    }

    @Test
    void dialogLookupsAndAssertions() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.dialog.testbench.DialogElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void opensTheDialog() {
                        DialogElement dialog = $(DialogElement.class).single();
                        Assertions.assertTrue(dialog.isOpen());
                        Assertions.assertEquals("Change email", dialog.getHeaderTitle());
                        Assertions.assertFalse($(DialogElement.class).withId("help").single().isOpen());
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedDialog;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void opensTheDialog(UnifiedPage page) {
                        UnifiedDialog dialog = page.find(UnifiedDialog.class);
                        assertThat(dialog).isOpen();
                        assertThat(dialog).hasHeaderTitle("Change email");
                        assertThat(page.find(UnifiedDialog.class).withId("help")).isClosed();
                    }
                }
                """));
    }

    @Test
    void lookupsInsideADialogFindInTheDialog() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.flow.component.dialog.testbench.DialogElement;
                import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void changesTheEmail() {
                        DialogElement dialog = $(DialogElement.class).single();
                        dialog.$(TextFieldElement.class).withLabel("Email").single().setValue("bob@example.com");
                        dialog.$(ButtonElement.class).withText("Save").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedDialog;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void changesTheEmail(UnifiedPage page) {
                        UnifiedDialog dialog = page.find(UnifiedDialog.class);
                        dialog.find(UnifiedTextField.class).withLabel("Email").setValue("bob@example.com");
                        dialog.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void tabSheetLabelFormsTakeTheirUnifiedNames() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.flow.component.tabs.testbench.TabSheetElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import java.util.List;
                import org.junit.jupiter.api.Assertions;

                class AccountTest extends BrowserTestBase {

                    @BrowserTest
                    void changesThePassword() {
                        TabSheetElement tabs = $(TabSheetElement.class).single();
                        Assertions.assertEquals(List.of("Profile", "Security", "Billing"), tabs.getTabLabels());
                        tabs.setSelectedTabIndex(tabs.getTab("Security"));
                        Assertions.assertEquals("Security", tabs.getSelectedTabElement().getLabel());
                        tabs.getContent().$(ButtonElement.class).withText("Change password").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedTabSheet;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class AccountTest {

                    @Test
                    void changesThePassword(UnifiedPage page) {
                        UnifiedTabSheet tabs = page.find(UnifiedTabSheet.class);
                        assertThat(tabs).hasTabs("Profile", "Security", "Billing");
                        tabs.selectTab("Security");
                        assertThat(tabs).hasSelectedTab("Security");
                        tabs.content().find(UnifiedButton.class).withText("Change password").click();
                    }
                }
                """));
    }

    @Test
    void aBaseClassAndItsSubclassesMigrateTogether() {
        rewriteRun(
                java(
                        """
                        import com.vaadin.testbench.BrowserTestBase;
                        import org.springframework.boot.test.context.SpringBootTest;
                        import org.springframework.boot.test.web.server.LocalServerPort;

                        @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
                        abstract class AbstractIT extends BrowserTestBase {

                            @LocalServerPort
                            int port;

                            protected void open(String path) {
                                getDriver().get("http://localhost:" + port + "/" + path);
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

                        @UnifiedSpringBootTest
                        abstract class AbstractIT {

                            protected void open(UnifiedPage page, String path) {
                                page.navigate(path);
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.testbench.BrowserTest;

                        class ProfileIT extends AbstractIT {

                            @BrowserTest
                            void saves() {
                                open("profile");
                                $(ButtonElement.class).withText("Save").single().click();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.component.UnifiedButton;
                        import org.junit.jupiter.api.Test;

                        class ProfileIT extends AbstractIT {

                            @Test
                            void saves(UnifiedPage page) {
                                open(page, "profile");
                                page.find(UnifiedButton.class).withText("Save").click();
                            }
                        }
                        """));
    }

    @Test
    void anInheritedBeforeEachIsCalledFirstInEachSubclassTest() {
        rewriteRun(
                java(
                        """
                        import com.vaadin.testbench.BrowserTestBase;
                        import org.junit.jupiter.api.BeforeEach;

                        abstract class AbstractIT extends BrowserTestBase {

                            @BeforeEach
                            void openProfile() {
                                getDriver().get("http://localhost:8080/profile");
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;

                        @UnifiedUITest
                        abstract class AbstractIT {

                            void openProfile(UnifiedPage page) {
                                page.navigate("profile");
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.testbench.BrowserTest;

                        class ProfileIT extends AbstractIT {

                            @BrowserTest
                            void saves() {
                                $(ButtonElement.class).withText("Save").single().click();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.component.UnifiedButton;
                        import org.junit.jupiter.api.Test;

                        class ProfileIT extends AbstractIT {

                            @Test
                            void saves(UnifiedPage page) {
                                openProfile(page);
                                page.find(UnifiedButton.class).withText("Save").click();
                            }
                        }
                        """));
    }

    @Test
    void pageObjectsMigrateWithIncludePageObjects() {
        rewriteRun(
                spec -> spec.recipe(new MigrateTestBenchToUnified(null, true)),
                java(
                        """
                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                        import com.vaadin.testbench.HasElementQuery;

                        class ProfilePage {

                            private final HasElementQuery test;

                            ProfilePage(HasElementQuery test) {
                                this.test = test;
                            }

                            void setName(String name) {
                                test.$(TextFieldElement.class).withLabel("Name").single().setValue(name);
                            }

                            ButtonElement save() {
                                return test.$(ButtonElement.class).withText("Save").single();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.component.UnifiedButton;
                        import com.vaadin.testing.unified.component.UnifiedTextField;

                        class ProfilePage {

                            private final UnifiedPage test;

                            ProfilePage(UnifiedPage test) {
                                this.test = test;
                            }

                            void setName(String name) {
                                test.find(UnifiedTextField.class).withLabel("Name").setValue(name);
                            }

                            UnifiedButton save() {
                                return test.find(UnifiedButton.class).withText("Save");
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class ProfileTest extends BrowserTestBase {

                            @BrowserTest
                            void saves() {
                                ProfilePage profile = new ProfilePage(this);
                                profile.setName("Alice");
                                profile.save().click();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class ProfileTest {

                            @Test
                            void saves(UnifiedPage page) {
                                ProfilePage profile = new ProfilePage(page);
                                profile.setName("Alice");
                                profile.save().click();
                            }
                        }
                        """));
    }

    @Test
    void withoutIncludePageObjectsAPageObjectStaysTestBench() {
        rewriteRun(
                java("""
                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                        import com.vaadin.testbench.HasElementQuery;

                        class ProfilePage {

                            private final HasElementQuery test;

                            ProfilePage(HasElementQuery test) {
                                this.test = test;
                            }

                            void setName(String name) {
                                test.$(TextFieldElement.class).withLabel("Name").single().setValue(name);
                            }

                            ButtonElement save() {
                                return test.$(ButtonElement.class).withText("Save").single();
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class ProfileTest extends BrowserTestBase {

                            @BrowserTest
                            void saves() {
                                ProfilePage profile = new ProfilePage(this);
                                profile.setName("Alice");
                                profile.save().click();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class ProfileTest {

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void saves(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench page object: new ProfilePage(this)
                                ProfilePage profile = new ProfilePage(testBench);
                                profile.setName("Alice");
                                profile.save().click();
                            }
                        }
                        """));
    }

    @Test
    void aPageObjectMarkerCarriesOnlyTheCallNotTheCommentAboveIt() {
        rewriteRun(
                java("""
                        import com.vaadin.testbench.HasElementQuery;

                        class Steps {
                            static void signIn(HasElementQuery test) {
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class ProfileTest extends BrowserTestBase {

                            @BrowserTest
                            void saves() {
                                getDriver().get("http://localhost:8080/profile");
                                // as an admin:
                                // users cannot save
                                Steps.signIn(this);
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class ProfileTest {

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void saves(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                page.navigate("profile");
                                // as an admin:
                                // users cannot save
                                // TODO unified: TestBench page object: Steps.signIn(this)
                                Steps.signIn(testBench);
                            }
                        }
                        """));
    }

    @Test
    void aCustomElementGetsAMarkerPointingToWrappers() {
        rewriteRun(
                java(
                        """
                        import com.vaadin.testbench.TestBenchElement;
                        import com.vaadin.testbench.elementsbase.Element;

                        @Element("main-view")
                        public class MainViewElement extends TestBenchElement {
                            public void logout() {
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class MainTest extends BrowserTestBase {

                            @BrowserTest
                            void logsOut() {
                                $(MainViewElement.class).single().logout();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class MainTest {

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void logsOut(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: $(MainViewElement.class), a custom element: write a Wrapper for it (ADR 0003)
                                testBench.$(MainViewElement.class).single().logout();
                            }
                        }
                        """));
    }

    private static final String LOGIN_VIEW_ELEMENT = """
            import com.vaadin.flow.component.login.testbench.LoginOverlayElement;
            import com.vaadin.testbench.elementsbase.Element;

            @Element("login-view")
            public class LoginViewElement extends LoginOverlayElement {
                public String getUsernameLabel() {
                    return "";
                }

                public void login(String user, String password) {
                }
            }
            """;

    @Test
    void aCallOnACustomPageObjectFromAHelperGetsAMarkerAndRequiresSelenium() {
        rewriteRun(
                java(LOGIN_VIEW_ELEMENT),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;
                        import org.junit.jupiter.api.Assertions;

                        class LoginIT extends BrowserTestBase {

                            @BrowserTest
                            void loginWorks() {
                                LoginViewElement loginView = openLoginView();
                                Assertions.assertEquals("Email", loginView.getUsernameLabel());
                                loginView.login("barista@vaadin.com", "barista");
                            }

                            private LoginViewElement openLoginView() {
                                return $(LoginViewElement.class).waitForFirst();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class LoginIT {

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void loginWorks(UnifiedPage page) {
                                LoginViewElement loginView = openLoginView(page);
                                // TODO unified: TestBench page object: LoginViewElement (custom element extending LoginOverlayElement): write a Wrapper or convert the page object
                                Assertions.assertEquals("Email", loginView.getUsernameLabel());
                                // TODO unified: TestBench page object: LoginViewElement (custom element extending LoginOverlayElement): write a Wrapper or convert the page object
                                loginView.login("barista@vaadin.com", "barista");
                            }

                            private LoginViewElement openLoginView(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: $(LoginViewElement.class), a custom element: write a Wrapper for it (ADR 0003)
                                return testBench.$(LoginViewElement.class).waitForFirst();
                            }
                        }
                        """));
    }

    private static final String BAKERY_LOGIN_VIEW_ELEMENT = """
            package com.vaadin.starter.bakery.testbench;

            import com.vaadin.flow.component.login.testbench.LoginOverlayElement;
            import com.vaadin.testbench.elementsbase.Element;

            @Element("login-view")
            public class LoginViewElement extends LoginOverlayElement {
                public String getUsernameLabel() {
                    return "";
                }

                public void login(String user, String password) {
                }
            }
            """;

    private static final String BAKERY_ABSTRACT_IT = """
            package com.vaadin.starter.bakery.testbench;

            import com.vaadin.testbench.BrowserTestBase;

            public abstract class AbstractIT extends BrowserTestBase {

                protected LoginViewElement openLoginView() {
                    return $(LoginViewElement.class).waitForFirst();
                }
            }
            """;

    private static final String BAKERY_LOGIN_IT = """
            package com.vaadin.starter.bakery.testbench;

            import com.vaadin.testbench.BrowserTest;
            import org.junit.jupiter.api.Assertions;

            public class LoginIT extends AbstractIT {

                @BrowserTest
                void loginWorks() {
                    LoginViewElement loginView = openLoginView();
                    Assertions.assertEquals("Email", loginView.getUsernameLabel());
                    loginView.login("barista@vaadin.com", "barista");
                }
            }
            """;

    /** Issue #606: Bakery's page objects are in {@code com.vaadin.starter.bakery}, not a package TestBench ships. */
    @Test
    void aCustomElementInAnApplicationPackageUnderComVaadinIsMarkedAndRequiresSelenium() {
        String marker = "// TODO unified: TestBench page object: LoginViewElement (custom element extending "
                + "LoginOverlayElement): write a Wrapper or convert the page object\n        ";
        rewriteRun(
                java(BAKERY_LOGIN_VIEW_ELEMENT),
                java(BAKERY_ABSTRACT_IT, spec -> spec.after(after -> {
                    org.assertj.core.api.Assertions.assertThat(after).contains("// TODO unified: TestBench Escape Hatch: "
                            + "$(LoginViewElement.class), a custom element: write a Wrapper for it (ADR 0003)");
                    return after;
                })),
                java(BAKERY_LOGIN_IT, spec -> spec.after(after -> {
                    org.assertj.core.api.Assertions.assertThat(after)
                            .contains("@RequiresTestingTool(TestingTool.SELENIUM)")
                            .containsOnlyOnce(marker + "Assertions.assertEquals(\"Email\", loginView.getUsernameLabel());")
                            .containsOnlyOnce(marker + "loginView.login(\"barista@vaadin.com\", \"barista\");");
                    return after;
                })));
    }

    @Test
    void aCustomElementReachedThroughAPageObjectInAnApplicationPackageUnderComVaadinIsMarked() {
        rewriteRun(
                java("""
                        package com.vaadin.starter.bakery.testbench;

                        import com.vaadin.flow.component.html.testbench.DivElement;
                        import com.vaadin.testbench.elementsbase.Element;

                        @Element("order-card")
                        public class OrderCardElement extends DivElement {
                        }
                        """),
                java("""
                        package com.vaadin.starter.bakery.testbench;

                        import com.vaadin.testbench.TestBenchElement;
                        import com.vaadin.testbench.elementsbase.Element;

                        @Element("storefront-view")
                        public class StorefrontViewElement extends TestBenchElement {
                            public OrderCardElement getOrderCard(int index) {
                                return $(OrderCardElement.class).get(index);
                            }
                        }
                        """),
                java("""
                        package com.vaadin.starter.bakery.testbench;

                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        public class StorefrontIT extends BrowserTestBase {

                            @BrowserTest
                            void opensAnOrder() {
                                StorefrontViewElement storefront = $(StorefrontViewElement.class).first();
                                storefront.getOrderCard(0).click();
                            }
                        }
                        """, spec -> spec.after(after -> {
                    org.assertj.core.api.Assertions.assertThat(after)
                            .contains("@RequiresTestingTool(TestingTool.SELENIUM)")
                            .containsOnlyOnce("// TODO unified: TestBench page object: StorefrontViewElement (custom "
                                    + "element extending TestBenchElement): write a Wrapper or convert the page object"
                                    + "\n        storefront.getOrderCard(0).click();");
                    return after;
                })));
    }

    @Test
    void aTestCallingOnlyAnInheritedHelperThatStaysTestBenchRequiresSelenium() {
        rewriteRun(
                java(LOGIN_VIEW_ELEMENT),
                java(
                        """
                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.flow.component.upload.testbench.UploadElement;
                        import com.vaadin.testbench.BrowserTestBase;

                        abstract class AbstractIT extends BrowserTestBase {

                            protected LoginViewElement openLoginView() {
                                return $(LoginViewElement.class).waitForFirst();
                            }

                            protected void loginAsBarista() {
                                openLoginView().login("barista@vaadin.com", "barista");
                            }

                            protected String uploadText() {
                                return $(UploadElement.class).first().getText();
                            }

                            protected void save() {
                                $(ButtonElement.class).withText("Save").single().click();
                            }
                        }
                        """,
                        """
                        import com.vaadin.flow.component.upload.testbench.UploadElement;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.component.UnifiedButton;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

                        @UnifiedUITest
                        abstract class AbstractIT {

                            protected LoginViewElement openLoginView(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: $(LoginViewElement.class), a custom element: write a Wrapper for it (ADR 0003)
                                return testBench.$(LoginViewElement.class).waitForFirst();
                            }

                            protected void loginAsBarista(UnifiedPage page) {
                                // TODO unified: TestBench page object: LoginViewElement (custom element extending LoginOverlayElement): write a Wrapper or convert the page object
                                openLoginView(page).login("barista@vaadin.com", "barista");
                            }

                            protected String uploadText(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: $(UploadElement.class)
                                return testBench.$(UploadElement.class).first().getText();
                            }

                            protected void save(UnifiedPage page) {
                                page.find(UnifiedButton.class).withText("Save").click();
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;

                        class LoginIT extends AbstractIT {

                            @BrowserTest
                            void logsIn() {
                                loginAsBarista();
                            }

                            @BrowserTest
                            void uploads() {
                                uploadText();
                            }

                            @BrowserTest
                            void saves() {
                                save();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import org.junit.jupiter.api.Test;

                        class LoginIT extends AbstractIT {

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void logsIn(UnifiedPage page) {
                                loginAsBarista(page);
                            }

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void uploads(UnifiedPage page) {
                                uploadText(page);
                            }

                            @Test
                            void saves(UnifiedPage page) {
                                save(page);
                            }
                        }
                        """));
    }

    @Test
    void aJUnit4TestBenchTestCaseMigratesToJUnit6() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.ScreenshotOnFailureRule;
                import com.vaadin.testbench.TestBenchTestCase;
                import org.junit.After;
                import org.junit.Assert;
                import org.junit.Before;
                import org.junit.Rule;
                import org.junit.Test;
                import org.openqa.selenium.chrome.ChromeDriver;

                public class ProfileIT extends TestBenchTestCase {

                    @Rule
                    public ScreenshotOnFailureRule screenshots = new ScreenshotOnFailureRule(this, true);

                    @Before
                    public void setUp() {
                        setDriver(new ChromeDriver());
                        getDriver().get("http://localhost:8080/profile");
                    }

                    @Test
                    public void saves() {
                        $(ButtonElement.class).withText("Save").single().click();
                        Assert.assertEquals("Save", $(ButtonElement.class).withText("Save").single().getText());
                    }

                    @After
                    public void tearDown() {
                        getDriver().quit();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                public class ProfileIT {

                    public void setUp(UnifiedPage page) {
                        page.navigate("profile");
                    }

                    @Test
                    public void saves(UnifiedPage page) {
                        setUp(page);
                        page.find(UnifiedButton.class).withText("Save").click();
                        assertThat(page.find(UnifiedButton.class).withText("Save")).hasText("Save");
                    }
                }
                """));
    }

    @Test
    void anIgnoredJUnit4TestBenchTestStaysDisabled() {
        // #386: JUnit 4's @Ignore means nothing to Jupiter, so a skipped test would start running
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.TestBenchTestCase;
                import org.junit.Ignore;
                import org.junit.Test;

                public class ProfileIT extends TestBenchTestCase {
                    @Ignore
                    @Test
                    public void saves() {
                        $(ButtonElement.class).withText("Save").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Disabled;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                public class ProfileIT {
                    @Disabled
                    @Test
                    public void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void runnersAndBrowserSetupGoWithAMarker() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.annotations.BrowserConfiguration;
                import com.vaadin.testbench.annotations.RunLocally;
                import com.vaadin.testbench.parallel.Browser;
                import com.vaadin.testbench.parallel.ParallelTest;
                import java.util.List;
                import org.junit.Test;
                import org.junit.runner.RunWith;
                import org.openqa.selenium.remote.DesiredCapabilities;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.test.context.junit4.SpringRunner;

                @RunWith(SpringRunner.class)
                @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
                @RunLocally(Browser.CHROME)
                public class OrderIT extends ParallelTest {

                    @BrowserConfiguration
                    public List<DesiredCapabilities> browsers() {
                        return List.of(Browser.FIREFOX.getDesiredCapabilities());
                    }

                    @Test
                    public void opens() {
                        getDriver().get("http://localhost:8080/order");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                // TODO unified: browser setup removed (@RunLocally, @BrowserConfiguration): configure the Unified Selenium adapter (vaadin.testing.* settings)
                @UnifiedSpringBootTest
                public class OrderIT {

                    @Test
                    public void opens(UnifiedPage page) {
                        page.navigate("order");
                    }
                }
                """));
    }

    @Test
    void junit4AndTheVintageEngineGoWhenOnlyTestBenchTestsUsedJUnit4() {
        rewriteRun(
                pomXml(
                        """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0</version>
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
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.vaadin</groupId>
                                <artifactId>vaadin-testbench-core</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                                <version>4.13.2</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.junit.vintage</groupId>
                                <artifactId>junit-vintage-engine</artifactId>
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
                                </dependency>
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
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-browserless</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-selenium</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-spring</artifactId>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(UNIFIED_VERSION)),
                java("""
                    import com.vaadin.testbench.TestBenchTestCase;
                    import org.junit.Test;

                    public class ProfileIT extends TestBenchTestCase {

                        @Test
                        public void opens() {
                            getDriver().get("http://localhost:8080/profile");
                        }
                    }
                    """, spec -> spec.after(after -> after)),
                InstalledSkill.files());
    }

    @Test
    void junit4AndTheVintageEngineStayWhileOtherJUnit4TestsRemain() {
        rewriteRun(
                pomXml(
                        """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0</version>
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
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.vaadin</groupId>
                                <artifactId>vaadin-testbench-core</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                                <version>4.13.2</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.junit.vintage</groupId>
                                <artifactId>junit-vintage-engine</artifactId>
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
                                </dependency>
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
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                                <version>4.13.2</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.junit.vintage</groupId>
                                <artifactId>junit-vintage-engine</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-browserless</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-selenium</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-spring</artifactId>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(UNIFIED_VERSION)),
                java("""
                    import com.vaadin.testbench.TestBenchTestCase;
                    import org.junit.Test;

                    public class ProfileIT extends TestBenchTestCase {

                        @Test
                        public void opens() {
                            getDriver().get("http://localhost:8080/profile");
                        }
                    }
                    """, spec -> spec.after(after -> after)),
                java("""
                    import org.junit.Assert;
                    import org.junit.Test;

                    public class MathTest {

                        @Test
                        public void adds() {
                            Assert.assertEquals(2, 1 + 1);
                        }
                    }
                    """),
                InstalledSkill.files());
    }

    @Test
    void testBenchStaysAsJUnit6WhileAPageObjectStillUsesAnElementJar() {
        rewriteRun(
                pomXml(testBenchJUnit5Pom(), keptTestBenchPom()),
                java("""
                    import com.vaadin.flow.component.html.testbench.DivElement;
                    import com.vaadin.testbench.TestBenchElement;

                    public class ProfileViewElement extends TestBenchElement {

                        public DivElement header() {
                            return $(DivElement.class).first();
                        }
                    }
                    """),
                java("""
                    import com.vaadin.testbench.BrowserTest;
                    import com.vaadin.testbench.BrowserTestBase;

                    class ProfileTest extends BrowserTestBase {

                        @BrowserTest
                        void opens() {
                            getDriver().get("http://localhost:8080/profile");
                        }
                    }
                    """, spec -> spec.after(after -> after)),
                InstalledSkill.files());
    }

    @Test
    void testBenchGoesWhenEveryTestConverts() {
        rewriteRun(
                pomXml(testBenchJUnit5Pom(), unifiedOnlyPom()),
                java("""
                    import com.vaadin.testbench.BrowserTest;
                    import com.vaadin.testbench.BrowserTestBase;

                    class ProfileTest extends BrowserTestBase {

                        @BrowserTest
                        void opens() {
                            getDriver().get("http://localhost:8080/profile");
                        }
                    }
                    """, spec -> spec.after(after -> after)),
                InstalledSkill.files());
    }

    @Test
    void testBenchStaysWhileAnEscapeHatchRemains() {
        rewriteRun(
                pomXml(testBenchJUnit5Pom(), keptTestBenchPom()),
                java("""
                    import com.vaadin.testbench.BrowserTest;
                    import com.vaadin.testbench.BrowserTestBase;

                    class ProfileTest extends BrowserTestBase {

                        @BrowserTest
                        void scrolls() {
                            getDriver().get("http://localhost:8080/profile");
                            executeScript("window.scrollTo(0, 0)");
                        }
                    }
                    """, spec -> spec.after(after -> after)),
                InstalledSkill.files());
    }

    private static String testBenchJUnit5Pom() {
        return pomWith("""
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>vaadin-testbench-junit5</artifactId>
                            <scope>test</scope>
                        </dependency>
                """, "");
    }

    private static String keptTestBenchPom() {
        return pomWith("""
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>vaadin-testbench-junit6</artifactId>
                            <scope>test</scope>
                        </dependency>
                """ + UNIFIED_DEPENDENCIES, UNIFIED_BOM);
    }

    private static String unifiedOnlyPom() {
        return pomWith("""
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>
                """ + UNIFIED_DEPENDENCIES, UNIFIED_BOM);
    }

    private static final String UNIFIED_BOM = """
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-bom</artifactId>
                                <version>%s</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                """.formatted(UNIFIED_VERSION);

    private static final String UNIFIED_DEPENDENCIES = """
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-browserless</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-selenium</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>
                """;

    /** A Vaadin 25.3 Spring Boot pom with these dependencies, and these BOMs after Vaadin's and Spring Boot's. */
    private static String pomWith(String dependencies, String moreBoms) {
        return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
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
                            </dependency>
                %s        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                %s    </dependencies>
                </project>
                """.formatted(moreBoms, dependencies);
    }

    @Test
    void addAgentSkillWritesTheMigrationMarkersSkill() throws Exception {
        String skill = new String(getClass().getResourceAsStream(
                "/META-INF/unified/skills/resolve-migration-markers/SKILL.md").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        rewriteRun(
                spec -> spec.recipe(new MigrateTestBenchToUnified(null, null, true)),
                text(null, skill, source -> source.path(".claude/skills/resolve-migration-markers/SKILL.md")));
    }

    @Test
    void idBecomesWithId() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void saves() {
                        $(ButtonElement.class).id("save").click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withId("save").click();
                    }
                }
                """));
    }

    @Test
    void lookupsInsideALitTemplateEnterIt() {
        rewriteRun(
                // the application's template, and the test's page object for its element: the same tag
                java(
                        """
                        package demo;

                        import com.vaadin.flow.component.Tag;
                        import com.vaadin.flow.component.button.Button;
                        import com.vaadin.flow.component.littemplate.LitTemplate;
                        import com.vaadin.flow.component.template.Id;

                        @Tag("search-bar")
                        public class SearchBar extends LitTemplate {
                            @Id("new")
                            Button newOrder;
                        }
                        """),
                java(
                        """
                        package demo;

                        import com.vaadin.testbench.TestBenchElement;
                        import com.vaadin.testbench.elementsbase.Element;

                        @Element("search-bar")
                        public class SearchBarElement extends TestBenchElement {
                        }
                        """),
                java(
                        """
                        package demo;

                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class OrdersTest extends BrowserTestBase {

                            @BrowserTest
                            void createsAnOrder() {
                                SearchBarElement bar = $(SearchBarElement.class).single();
                                bar.$(ButtonElement.class).id("new").click();
                                $(SearchBarElement.class).single().$(TextFieldElement.class).id("filter").setValue("open");
                                $("search-bar").single().$(ButtonElement.class).withText("New order").single().click();
                            }
                        }
                        """,
                        """
                        package demo;

                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.component.UnifiedButton;
                        import com.vaadin.testing.unified.component.UnifiedTemplate;
                        import com.vaadin.testing.unified.component.UnifiedTextField;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class OrdersTest {

                            @Test
                            void createsAnOrder(UnifiedPage page) {
                                UnifiedTemplate bar = page.findTemplate(SearchBar.class);
                                bar.find(UnifiedButton.class).withId("new").click();
                                page.findTemplate(SearchBar.class).find(UnifiedTextField.class).withId("filter").setValue("open");
                                // TODO unified: inside a Template, text written in its HTML is empty on Browserless: find it by id to run on every Testing Tool
                                page.findTemplate(SearchBar.class).find(UnifiedButton.class).withText("New order").click();
                            }
                        }
                        """));
    }

    @Test
    void aCustomElementWithoutATemplateKeepsItsMarker() {
        // no LitTemplate in the sources has the tag: a third-party web component, through the Escape Hatch as before
        rewriteRun(
                java(
                        """
                        package demo;

                        import com.vaadin.testbench.TestBenchElement;
                        import com.vaadin.testbench.elementsbase.Element;

                        @Element("map-view")
                        public class MapElement extends TestBenchElement {
                        }
                        """),
                java(
                        """
                        package demo;

                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class MapTest extends BrowserTestBase {

                            @BrowserTest
                            void zooms() {
                                $(MapElement.class).single().$(ButtonElement.class).id("zoom").click();
                            }
                        }
                        """,
                        """
                        package demo;

                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Test;

                        @UnifiedUITest
                        class MapTest {

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void zooms(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: $(MapElement.class), a custom element: write a Wrapper for it (ADR 0003)
                                testBench.$(MapElement.class).single().$(ButtonElement.class).id("zoom").click();
                            }
                        }
                        """));
    }

    @Test
    void aSuiteOnAnAbstractGenericBaseClassMigratesToCodeThatCompiles() {
        rewriteRun(
                java(
                        """
                        import com.vaadin.testbench.BrowserTestBase;
                        import com.vaadin.testbench.TestBenchDriverProxy;
                        import com.vaadin.testbench.TestBenchElement;
                        import org.junit.jupiter.api.BeforeEach;
                        import org.openqa.selenium.Dimension;
                        import org.openqa.selenium.WebDriver;

                        public abstract class AbstractIT<E extends TestBenchElement> extends BrowserTestBase {

                            @BeforeEach
                            void setup() throws Exception {
                                getDriver().manage().window().setSize(new Dimension(1024, 800));
                            }

                            @Override
                            public TestBenchDriverProxy getDriver() {
                                return (TestBenchDriverProxy) super.getDriver();
                            }

                            protected TestBenchElement openLoginView(WebDriver driver, String url) {
                                driver.get(url);
                                return $("login-view").first();
                            }

                            protected abstract E openView();
                        }
                        """,
                        """
                        import com.vaadin.testbench.TestBenchDriverProxy;
                        import com.vaadin.testbench.TestBenchElement;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.openqa.selenium.Dimension;
                        import org.openqa.selenium.WebDriver;

                        @UnifiedUITest
                        public abstract class AbstractIT<E extends TestBenchElement> {

                            void setup(UnifiedPage page) throws Exception {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: getDriver()
                                testBench.getDriver().manage().window().setSize(new Dimension(1024, 800));
                            }

                            public TestBenchDriverProxy getDriver(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: super.getDriver()
                                return (TestBenchDriverProxy) testBench.getDriver();
                            }

                            protected TestBenchElement openLoginView(UnifiedPage page, WebDriver driver, String url) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: navigation to url: not a local application URL the recipe can read; use page.navigate("<path>")
                                driver.get(url);
                                // TODO unified: TestBench Escape Hatch: $("login-view")
                                return testBench.$("login-view").first();
                            }

                            protected abstract E openView(UnifiedPage page);
                        }
                        """),
                java(
                        """
                        import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                        import com.vaadin.testbench.BrowserTest;

                        class UsersViewIT extends AbstractIT<TextFieldElement> {

                            @Override
                            protected TextFieldElement openView() {
                                return $(TextFieldElement.class).id("users");
                            }

                            @BrowserTest
                            void editsUser() {
                                TextFieldElement page = openView();
                                page.setValue("Alan");
                            }
                        }
                        """,
                        """
                        import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Test;

                        class UsersViewIT extends AbstractIT<TextFieldElement> {

                            @Override
                            protected TextFieldElement openView(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: $(TextFieldElement.class)
                                return testBench.$(TextFieldElement.class).id("users");
                            }

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void editsUser(UnifiedPage unifiedPage) throws Exception {
                                setup(unifiedPage);
                                TextFieldElement page = openView(unifiedPage);
                                page.setValue("Alan");
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.TestBenchElement;

                        class LoginIT extends AbstractIT<TestBenchElement> {

                            @Override
                            protected TestBenchElement openView() {
                                return openLoginView(getDriver(), "http://localhost:8080/");
                            }

                            @BrowserTest
                            void loginToNotDefaultUrl() {
                                openLoginView(getDriver(), "http://localhost:8080/users");
                            }

                            @BrowserTest
                            void openLoginAfterLoggedIn() {
                                loginToNotDefaultUrl();
                                openView();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testbench.TestBenchElement;
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Test;

                        class LoginIT extends AbstractIT<TestBenchElement> {

                            @Override
                            protected TestBenchElement openView(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: getDriver()
                                return openLoginView(page, testBench.getDriver(), "http://localhost:8080/");
                            }

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void loginToNotDefaultUrl(UnifiedPage page) throws Exception {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                setup(page);
                                // TODO unified: TestBench Escape Hatch: getDriver()
                                openLoginView(page, testBench.getDriver(), "http://localhost:8080/users");
                            }

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void openLoginAfterLoggedIn(UnifiedPage page) throws Exception {
                                setup(page);
                                loginToNotDefaultUrl(page);
                                openView(page);
                            }
                        }
                        """));
    }

    @Test
    void aCellRowIsReadThroughTheUnifiedRowOnlyWhereTheGridConverts() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.grid.testbench.GridElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.Assertions;

                class PeopleTest extends BrowserTestBase {

                    @BrowserTest
                    void findsRows() {
                        GridElement people = $(GridElement.class).single();
                        int alan = people.getCell("Alan Turing").getRow();
                        Assertions.assertNotNull(people);
                        GridElement pets = $(GridElement.class).id("pets");
                        int rex = pets.getCell("Rex").getRow();
                    }
                }
                """,
                """
                import com.vaadin.flow.component.grid.testbench.GridElement;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedGrid;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void findsRows(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        // TODO unified: TestBench Escape Hatch: $(GridElement.class)
                        GridElement people = testBench.$(GridElement.class).single();
                        int alan = people.getCell("Alan Turing").getRow();
                        Assertions.assertNotNull(people);
                        UnifiedGrid pets = page.find(UnifiedGrid.class).withId("pets");
                        // TODO unified: was getCell("Rex"); the row lookup fails when several rows match
                        int rex = pets.row().withCellText("Rex").getIndex();
                    }
                }
                """));
    }

    @Test
    void driverGetOfAUrlConstantNavigatesToItsPath() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.openqa.selenium.WebDriver;

                class UsersIT extends BrowserTestBase {

                    private static final String APP_URL = "http://localhost:8080/";

                    WebDriver driver;

                    @BrowserTest
                    void opensUsers() {
                        driver.get(APP_URL + "users");
                    }

                    @BrowserTest
                    void opensRoot() {
                        driver.get(APP_URL);
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.WebDriver;

                @UnifiedUITest
                class UsersIT {

                    WebDriver driver;

                    @Test
                    void opensUsers(UnifiedPage page) {
                        page.navigate("users");
                    }

                    @Test
                    void opensRoot(UnifiedPage page) {
                        page.navigate("");
                    }
                }
                """));
    }

    @Test
    void driverGetOnAParameterOrWithTheSlashOnThePathNavigatesTheSameWay() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.openqa.selenium.WebDriver;

                class UsersIT extends BrowserTestBase {

                    static final String APP_URL = "http://127.0.0.1:8080";

                    WebDriver driver;

                    @BrowserTest
                    void opensUsers() {
                        open(driver);
                    }

                    void open(WebDriver driver) {
                        driver.get(APP_URL + "/users");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.WebDriver;

                @UnifiedUITest
                class UsersIT {

                    WebDriver driver;

                    @Test
                    void opensUsers(UnifiedPage page) {
                        open(page, driver);
                    }

                    void open(UnifiedPage page, WebDriver driver) {
                        page.navigate("users");
                    }
                }
                """));
    }

    @Test
    void driverGetOfABaseTheRecipeCantReadIsMarked() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class UsersIT extends BrowserTestBase {

                    static final String REMOTE_URL = "https://staging.example.com/";
                    String appUrl = "http://localhost:8080/";

                    @BrowserTest
                    void opensUsers() {
                        getDriver().get(appUrl + "users");
                        getDriver().get(REMOTE_URL + "users");
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class UsersIT {

                    static final String REMOTE_URL = "https://staging.example.com/";
                    String appUrl = "http://localhost:8080/";

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void opensUsers(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        // TODO unified: navigation to appUrl + "users": not a local application URL the recipe can read; use page.navigate("<path>")
                        // TODO unified: TestBench Escape Hatch: getDriver()
                        testBench.getDriver().get(appUrl + "users");
                        // TODO unified: navigation to REMOTE_URL + "users": not a local application URL the recipe can read; use page.navigate("<path>")
                        // TODO unified: TestBench Escape Hatch: getDriver()
                        testBench.getDriver().get(REMOTE_URL + "users");
                    }
                }
                """));
    }

    @Test
    void aUrlConstantStillReadElsewhereIsKept() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.openqa.selenium.WebDriver;

                class UsersIT extends BrowserTestBase {

                    static final String APP_URL = "http://localhost:8080/";

                    WebDriver driver;

                    @BrowserTest
                    void opensUsers() {
                        driver.get(APP_URL + "users");
                        System.out.println(APP_URL);
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.WebDriver;

                @UnifiedUITest
                class UsersIT {

                    static final String APP_URL = "http://localhost:8080/";

                    WebDriver driver;

                    @Test
                    void opensUsers(UnifiedPage page) {
                        page.navigate("users");
                        System.out.println(APP_URL);
                    }
                }
                """));
    }

    @Test
    void aBaseClassUrlConstantUsedOnlyForNavigationIsRemoved() {
        rewriteRun(
                java(
                        """
                        import com.vaadin.testbench.BrowserTestBase;

                        public abstract class AbstractIT extends BrowserTestBase {

                            protected static final String APP_URL = "http://localhost:8080/";
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedUITest;

                        @UnifiedUITest
                        public abstract class AbstractIT {
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;

                        class UsersIT extends AbstractIT {

                            @BrowserTest
                            void opensUsers() {
                                getDriver().get(APP_URL + "users");
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import org.junit.jupiter.api.Test;

                        class UsersIT extends AbstractIT {

                            @Test
                            void opensUsers(UnifiedPage page) {
                                page.navigate("users");
                            }
                        }
                        """));
    }

    // A Spring Boot app tested against a server the build starts (#528, ADR 0013)

    private static final String SPRING_BOOT_APPLICATION = """
            package org.springframework.boot.autoconfigure;

            public @interface SpringBootApplication {}
            """;

    private static final String APPLICATION = """
            package com.example;

            import org.springframework.boot.autoconfigure.SpringBootApplication;

            @SpringBootApplication
            public class Application {}
            """;

    private static final String ENABLE_WEB_SECURITY = """
            package org.springframework.security.config.annotation.web.configuration;

            public @interface EnableWebSecurity {}
            """;

    private static final String SECURITY_CONFIGURATION = """
            package com.example;

            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

            @EnableWebSecurity
            public class SecurityConfiguration {}
            """;

    private static final String ABSTRACT_IT = """
            package com.example;

            import com.vaadin.testbench.BrowserTestBase;

            public abstract class AbstractIT extends BrowserTestBase {
            }
            """;

    private static final String USERS_IT = """
            package com.example;

            import com.vaadin.testbench.BrowserTest;

            class UsersIT extends AbstractIT {

                @BrowserTest
                void opensUsers() {
                    getDriver().get("http://localhost:8080/users");
                }
            }
            """;

    private static final String USERS_IT_MIGRATED = """
            package com.example;

            import com.vaadin.testing.unified.UnifiedPage;
            import org.junit.jupiter.api.Test;

            class UsersIT extends AbstractIT {

                @Test
                void opensUsers(UnifiedPage page) {
                    page.navigate("users");
                }
            }
            """;

    @Test
    void aSpringBootAppsTestWithoutSpringBootTestStartsTheAppByDefault() {
        rewriteRun(java(SPRING_BOOT_APPLICATION), java(APPLICATION),
                java(ABSTRACT_IT,
                        """
                        package com.example;

                        import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

                        // TODO unified: %s
                        @UnifiedSpringBootTest
                        public abstract class AbstractIT {
                        }
                        """.formatted(TestBenchSetupToUnified.STARTS_THE_APP)),
                java(USERS_IT, USERS_IT_MIGRATED));
    }

    @Test
    void springBootTestFalseKeepsTheTestAgainstTheServerTheBuildStartsWithAMarker() {
        rewriteRun(spec -> spec.recipe(new MigrateTestBenchToUnified(null, null, null, false)),
                java(SPRING_BOOT_APPLICATION), java(APPLICATION),
                java(ABSTRACT_IT,
                        """
                        package com.example;

                        import com.vaadin.testing.unified.UnifiedUITest;

                        // TODO unified: %s
                        @UnifiedUITest
                        public abstract class AbstractIT {
                        }
                        """.formatted(TestBenchSetupToUnified.EXTERNAL_SERVER)),
                java(USERS_IT, USERS_IT_MIGRATED));
    }

    @Test
    void withoutASpringBootApplicationATestWithoutSpringBootTestStaysAUnifiedUITestWhateverTheOption() {
        for (Boolean springBootTest : new Boolean[] {null, true, false}) {
            rewriteRun(spec -> spec.recipe(new MigrateTestBenchToUnified(null, null, null, springBootTest)),
                    java(ABSTRACT_IT,
                            """
                            package com.example;

                            import com.vaadin.testing.unified.UnifiedUITest;

                            @UnifiedUITest
                            public abstract class AbstractIT {
                            }
                            """),
                    java(USERS_IT, USERS_IT_MIGRATED));
        }
    }

    // Navigation the recipe can't turn into page.navigate(..), and fixed-port URLs once the app starts in the test (#607)

    @Test
    void aBaseClassHelpersDriverGetOfAParameterIsMarked() {
        rewriteRun(
                java(
                        """
                        import com.vaadin.testbench.BrowserTestBase;
                        import com.vaadin.testbench.TestBenchElement;
                        import org.openqa.selenium.WebDriver;

                        public abstract class AbstractIT extends BrowserTestBase {

                            protected TestBenchElement open(WebDriver driver, String url) {
                                driver.get(url);
                                return $("login-view").waitForFirst();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testbench.TestBenchElement;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.openqa.selenium.WebDriver;

                        @UnifiedUITest
                        public abstract class AbstractIT {

                            protected TestBenchElement open(UnifiedPage page, WebDriver driver, String url) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: navigation to url: not a local application URL the recipe can read; use page.navigate("<path>")
                                driver.get(url);
                                // TODO unified: TestBench Escape Hatch: $("login-view")
                                return testBench.$("login-view").waitForFirst();
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;

                        class LoginIT extends AbstractIT {

                            @BrowserTest
                            void opensLogin() {
                                open(getDriver(), "http://localhost:8080/login");
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.RequiresTestingTool;
                        import com.vaadin.testing.unified.TestingTool;
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                        import org.junit.jupiter.api.Test;

                        class LoginIT extends AbstractIT {

                            @Test
                            @RequiresTestingTool(TestingTool.SELENIUM)
                            void opensLogin(UnifiedPage page) {
                                TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                                // TODO unified: TestBench Escape Hatch: getDriver()
                                open(page, testBench.getDriver(), "http://localhost:8080/login");
                            }
                        }
                        """));
    }

    @Test
    void driverGetOfALocalVariableOrAMethodCallIsMarked() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class UsersIT extends BrowserTestBase {

                    @BrowserTest
                    void opensUsers() {
                        String url = urlFor("users");
                        getDriver().get(url);
                        getDriver().get(urlFor("users"));
                    }

                    String urlFor(String path) {
                        return "http://localhost:8080/" + path;
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class UsersIT {

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void opensUsers(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        String url = urlFor("users");
                        // TODO unified: navigation to url: not a local application URL the recipe can read; use page.navigate("<path>")
                        // TODO unified: TestBench Escape Hatch: getDriver()
                        testBench.getDriver().get(url);
                        // TODO unified: navigation to urlFor("users"): not a local application URL the recipe can read; use page.navigate("<path>")
                        // TODO unified: TestBench Escape Hatch: getDriver()
                        testBench.getDriver().get(urlFor("users"));
                    }

                    String urlFor(String path) {
                        return "http://localhost:8080/" + path;
                    }
                }
                """));
    }

    @Test
    void aDriverPassedToAMethodThatMakesNoTestBenchCallComesFromADeclaredEscapeHatch() {
        rewriteRun(java(
                """
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.openqa.selenium.WebDriver;

                class UsersIT extends BrowserTestBase {

                    public String APP_URL = "http://localhost:8080/";

                    @BrowserTest
                    void opensUsers() {
                        open(getDriver(), APP_URL);
                    }

                    String open(WebDriver driver, String url) {
                        driver.get(url);
                        return url;
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.WebDriver;

                @UnifiedUITest
                class UsersIT {

                    public String APP_URL = "http://localhost:8080/";

                    @Test
                    @RequiresTestingTool(TestingTool.SELENIUM)
                    void opensUsers(UnifiedPage page) {
                        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                        // TODO unified: TestBench Escape Hatch: getDriver()
                        open(testBench.getDriver(), APP_URL);
                    }

                    String open(WebDriver driver, String url) {
                        // TODO unified: navigation to url: not a local application URL the recipe can read; use page.navigate("<path>")
                        driver.get(url);
                        return url;
                    }
                }
                """));
    }

    private static final String ABSTRACT_IT_WITH_APP_URL = """
            package com.example;

            import com.vaadin.testbench.BrowserTestBase;

            public abstract class AbstractIT extends BrowserTestBase {

                public String APP_URL = "http://localhost:8080/";
            }
            """;

    @Test
    void aFixedPortUrlFieldIsMarkedWhenTheTestNowStartsTheApp() {
        rewriteRun(java(SPRING_BOOT_APPLICATION), java(APPLICATION),
                java(ABSTRACT_IT_WITH_APP_URL,
                        """
                        package com.example;

                        import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

                        // TODO unified: %s
                        @UnifiedSpringBootTest
                        public abstract class AbstractIT {

                            // TODO unified: %s
                            public String APP_URL = "http://localhost:8080/";
                        }
                        """.formatted(TestBenchSetupToUnified.STARTS_THE_APP, TestBenchSetupToUnified.FIXED_PORT_URL)),
                java(USERS_IT, USERS_IT_MIGRATED));
    }

    @Test
    void aFixedPortUrlFieldIsNotMarkedWhenTheTestKeepsTheServerTheBuildStarts() {
        rewriteRun(spec -> spec.recipe(new MigrateTestBenchToUnified(null, null, null, false)),
                java(SPRING_BOOT_APPLICATION), java(APPLICATION),
                java(ABSTRACT_IT_WITH_APP_URL,
                        """
                        package com.example;

                        import com.vaadin.testing.unified.UnifiedUITest;

                        // TODO unified: %s
                        @UnifiedUITest
                        public abstract class AbstractIT {

                            public String APP_URL = "http://localhost:8080/";
                        }
                        """.formatted(TestBenchSetupToUnified.EXTERNAL_SERVER)),
                java(USERS_IT, USERS_IT_MIGRATED));
    }

    @Test
    void aUrlConstantTheMigrationRemovedLeavesNoFixedPortMarker() {
        rewriteRun(java(SPRING_BOOT_APPLICATION), java(APPLICATION),
                java(
                        """
                        package com.example;

                        import com.vaadin.testbench.BrowserTestBase;

                        public abstract class AbstractIT extends BrowserTestBase {

                            protected static final String APP_URL = "http://localhost:8080/";
                        }
                        """,
                        """
                        package com.example;

                        import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

                        // TODO unified: %s
                        @UnifiedSpringBootTest
                        public abstract class AbstractIT {
                        }
                        """.formatted(TestBenchSetupToUnified.STARTS_THE_APP)),
                java(
                        """
                        package com.example;

                        import com.vaadin.testbench.BrowserTest;

                        class UsersIT extends AbstractIT {

                            @BrowserTest
                            void opensUsers() {
                                getDriver().get(APP_URL + "users");
                            }
                        }
                        """,
                        USERS_IT_MIGRATED));
    }

    private static final String SIGN_IN_VIEW_ELEMENT = """
            package com.example;

            import com.vaadin.flow.component.login.testbench.LoginOverlayElement;
            import com.vaadin.testbench.annotations.Attribute;

            @Attribute(name = "id", value = "login")
            public class LoginViewElement extends LoginOverlayElement {
                public void signIn(String user) {
                    getUsernameField().setValue(user);
                }
            }
            """;

    private static final String ORDERS_IT = """
            package com.example;

            import com.vaadin.testbench.BrowserTest;
            import com.vaadin.testbench.BrowserTestBase;

            public class OrdersIT extends BrowserTestBase {

                @BrowserTest
                void opensOrders() {
                    LoginViewElement login = $(LoginViewElement.class).first();
                    login.signIn("admin");
                }
            }
            """;

    @Test
    void aLoginPageObjectCallInASpringSecurityModuleIsMarkedWithWithMockUser() {
        rewriteRun(

                java(SPRING_BOOT_APPLICATION), java(APPLICATION), java(ENABLE_WEB_SECURITY), java(SECURITY_CONFIGURATION),
                java(SIGN_IN_VIEW_ELEMENT), java(ORDERS_IT, spec -> spec.after(after -> {
                    org.assertj.core.api.Assertions.assertThat(after)
                            .contains("@UnifiedSpringBootTest")
                            .containsOnlyOnce("// TODO unified: " + TestBenchSetupToUnified.SIGNS_IN + "\n        "
                                    + "// TODO unified: TestBench page object: LoginViewElement (custom element extending "
                                    + "LoginOverlayElement): write a Wrapper or convert the page object\n        login.signIn(");
                    return after;
                })));
    }

    @Test
    void aLoginPageObjectCallIsNotMarkedWithSpringBootTestFalseOrWithoutSpringSecurity() {
        rewriteRun(spec -> spec.recipe(new MigrateTestBenchToUnified(null, null, null, false)),
                java(SPRING_BOOT_APPLICATION), java(APPLICATION), java(ENABLE_WEB_SECURITY), java(SECURITY_CONFIGURATION),
                java(SIGN_IN_VIEW_ELEMENT), java(ORDERS_IT, spec -> spec.after(after -> {
                    org.assertj.core.api.Assertions.assertThat(after).doesNotContain(TestBenchSetupToUnified.SIGNS_IN);
                    return after;
                })));
        rewriteRun(java(SPRING_BOOT_APPLICATION), java(APPLICATION), java(SIGN_IN_VIEW_ELEMENT),
                java(ORDERS_IT, spec -> spec.after(after -> {
                    org.assertj.core.api.Assertions.assertThat(after).doesNotContain(TestBenchSetupToUnified.SIGNS_IN);
                    return after;
                })));
    }

    @Test
    void aTestAssertingOnALoginElementChecksTheLoginAndGetsNoSignInMarker() {
        rewriteRun(java(SPRING_BOOT_APPLICATION), java(APPLICATION), java(ENABLE_WEB_SECURITY),
                java(SECURITY_CONFIGURATION), java("""
                        package com.example;

                        import com.vaadin.flow.component.login.testbench.LoginOverlayElement;
                        import com.vaadin.testbench.annotations.Attribute;

                        @Attribute(name = "id", value = "login")
                        public class LoginViewElement extends LoginOverlayElement {
                            public void signIn(String user) {
                                getUsernameField().setValue(user);
                            }

                            public String getUsernameLabel() {
                                return getUsernameField().getPropertyString("label");
                            }
                        }
                        """), java("""
                        package com.example;

                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;
                        import org.junit.jupiter.api.Assertions;

                        public class LoginIT extends BrowserTestBase {

                            @BrowserTest
                            void checksTheLogin() {
                                LoginViewElement checked = $(LoginViewElement.class).first();
                                Assertions.assertEquals("Email", checked.getUsernameLabel());
                                checked.signIn("admin");
                            }

                            @BrowserTest
                            void signsIn() {
                                LoginViewElement login = $(LoginViewElement.class).first();
                                login.signIn("admin");
                            }
                        }
                        """, spec -> spec.after(after -> {
                    org.assertj.core.api.Assertions.assertThat(after)
                            .containsOnlyOnce("// TODO unified: " + TestBenchSetupToUnified.SIGNS_IN)
                            .contains("// TODO unified: " + TestBenchSetupToUnified.SIGNS_IN + "\n        "
                                    + "// TODO unified: TestBench page object: LoginViewElement (custom element extending "
                                    + "LoginOverlayElement): write a Wrapper or convert the page object\n        login.signIn(");
                    return after;
                })));
    }

    private static final String SECURITY_POM = """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
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
                                <version>%BOOT%</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-security</artifactId>
                        </dependency>%EXTRA%
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>vaadin-testbench-core-junit6</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <profiles>
                        <profile>
                            <id>it</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.springframework.boot</groupId>
                                        <artifactId>spring-boot-maven-plugin</artifactId>
                                        <executions>
                                            <execution>
                                                <id>start-spring-boot</id>
                                                <phase>pre-integration-test</phase>
                                                <goals><goal>start</goal></goals>
                                            </execution>
                                            <execution>
                                                <id>stop-spring-boot</id>
                                                <phase>post-integration-test</phase>
                                                <goals><goal>stop</goal></goals>
                                            </execution>
                                        </executions>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                    </profiles>
                </project>
                """;

    private static final String SECURITY_POM_AFTER = """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
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
                                <version>%BOOT%</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.vaadin.addons</groupId>
                                <artifactId>unified-test-bom</artifactId>
                                <version>%UNIFIED%</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-security</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.vaadin</groupId>
                            <artifactId>browserless-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>%EXTRA%
                %ADDED%        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-browserless</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-selenium</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.vaadin.addons</groupId>
                            <artifactId>unified-test-spring</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <profiles>
                        <profile>
                            <id>it</id>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.springframework.boot</groupId>
                                        <artifactId>spring-boot-maven-plugin</artifactId>
                                        <executions>
                                            <execution>
                                                <id>start-spring-boot</id>
                                                <phase>pre-integration-test</phase>
                                                <goals><goal>start</goal></goals>
                                            </execution>
                                            <execution>
                                                <id>stop-spring-boot</id>
                                                <phase>post-integration-test</phase>
                                                <goals><goal>stop</goal></goals>
                                            </execution>
                                        </executions>
                                    </plugin>
                                </plugins>
                            </build>
                        </profile>
                    </profiles>
                </project>
                """;

    /** Already in the pom; the migration sorts the dependencies it adds in before it. */
    private static final String SECURITY_TEST = "\n" + """
                    <dependency>
                        <groupId>org.springframework.security</groupId>
                        <artifactId>spring-security-test</artifactId>
                        <scope>test</scope>
                    </dependency>""".indent(8).stripTrailing();

    private static String securityPom(String template, String boot, String extra, String added) {
        return template.replace("%BOOT%", boot).replace("%EXTRA%", extra)
                .replace("%ADDED%", added).replace("%UNIFIED%", String.valueOf(UNIFIED_VERSION));
    }

    private static String added(String groupId, String artifactId) {
        return """
                        <dependency>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <scope>test</scope>
                        </dependency>
                """.formatted(groupId, artifactId);
    }

    @Test
    void aSpringSecurityAppGetsTheSecurityTestStarterAndKeepsItsServerStartExecutions() {
        rewriteRun(pomXml(securityPom(SECURITY_POM, "4.1.1", "", ""),
                securityPom(SECURITY_POM_AFTER, "4.1.1", "", added("org.springframework.boot", "spring-boot-starter-security-test"))));
    }

    @Test
    void aSpringSecurityAppOnASpringBootWithoutTheStarterGetsSpringSecurityTest() {
        rewriteRun(pomXml(securityPom(SECURITY_POM, "3.5.0", "", ""),
                securityPom(SECURITY_POM_AFTER, "3.5.0", "", added("org.springframework.security", "spring-security-test"))));
    }

    @Test
    void aSpringSecurityTestDependencyPresentIsNotAddedAgain() {
        rewriteRun(pomXml(securityPom(SECURITY_POM, "4.1.1", SECURITY_TEST, ""),
                securityPom(SECURITY_POM_AFTER, "4.1.1", SECURITY_TEST, "")));
    }
}
