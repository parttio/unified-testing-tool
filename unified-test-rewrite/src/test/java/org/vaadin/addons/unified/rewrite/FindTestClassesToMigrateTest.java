package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** The dry run that lists every test class a migration would convert or skip, and why (issue #352). */
class FindTestClassesToMigrateTest implements RewriteTest {

    private static final String VIEW = """
            package com.example;

            import com.vaadin.flow.component.Component;
            import com.vaadin.flow.router.Route;

            @Route("people")
            public class PeopleView extends Component {}
            """;

    @Override
    public void defaults(RecipeSpec spec) {
        // found by name on the classpath, as the rewrite Maven and Gradle plugins find it
        spec.recipe(Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                        .activateRecipes("org.vaadin.addons.unified.rewrite.FindTestClassesToMigrate"))
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void aDramafinderClassTheMigrationConvertsIsListedAsMigrating() {
        rewriteRun(spec -> spec.dataTable(FindTestClassesToMigrate.TestClasses.Row.class, rows -> assertThat(rows)
                        .containsExactly(new FindTestClassesToMigrate.TestClasses.Row("com/example/PeopleTest.java",
                                "com.example.PeopleTest", "Dramafinder", "migrates", ""))),
                java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void opens() {
                        navigate(PeopleView.class);
                    }
                }
                """,
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                /*~~(migrates)~~>*/class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void opens() {
                        navigate(PeopleView.class);
                    }
                }
                """));
    }

    @Test
    void aDramafinderClassTheMigrationSkipsIsListedWithTheMembersThatKeepItFromMigrating() {
        rewriteRun(spec -> spec.dataTable(FindTestClassesToMigrate.TestClasses.Row.class, rows -> assertThat(rows)
                        .containsExactly(new FindTestClassesToMigrate.TestClasses.Row("com/example/PeopleTest.java",
                                "com.example.PeopleTest", "Dramafinder", "skipped",
                                "overrides setupTest() of AbstractBasePlaywrightIT, calls getUrl() of SpringPlaywrightIT"))),
                java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Override
                    public void setupTest() throws Exception {
                        super.setupTest();
                        navigate(PeopleView.class);
                    }

                    @Test
                    void opens() {
                        String base = getUrl();          // not a navigation: still left for a person
                    }
                }
                """,
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                /*~~(skipped: overrides setupTest() of AbstractBasePlaywrightIT, calls getUrl() of SpringPlaywrightIT)~~>*/class PeopleTest extends SpringPlaywrightIT {
                    @Override
                    public void setupTest() throws Exception {
                        super.setupTest();
                        navigate(PeopleView.class);
                    }

                    @Test
                    void opens() {
                        String base = getUrl();          // not a navigation: still left for a person
                    }
                }
                """));
    }

    @Test
    void aScreenshotExtensionOfACopiedBaseIsListedWithAdviceToRemoveIt() {
        rewriteRun(spec -> spec.dataTable(FindTestClassesToMigrate.TestClasses.Row.class, rows -> assertThat(rows)
                        .containsExactly(new FindTestClassesToMigrate.TestClasses.Row("com/example/PeopleTest.java",
                                "com.example.PeopleTest", "Dramafinder", "skipped",
                                "uses screenshots of SpringPlaywrightIT, which uses the test base; the Unified API writes failure evidence itself: remove it"))),
                java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.extension.ExtensionContext;
                import org.junit.jupiter.api.extension.RegisterExtension;
                import org.junit.jupiter.api.extension.TestWatcher;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.vaadin.addons.dramafinder.AbstractBasePlaywrightIT;

                @SpringBootTest
                public abstract class SpringPlaywrightIT extends AbstractBasePlaywrightIT {
                    @RegisterExtension
                    TestWatcher screenshots = new TestWatcher() {
                        @Override
                        public void testFailed(ExtensionContext context, Throwable cause) {
                            page.screenshot();
                        }
                    };
                }
                """), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void opens() {
                        navigate(PeopleView.class);
                    }
                }
                """,
                """
                package com.example;

                import org.junit.jupiter.api.Test;

                /*~~(skipped: uses screenshots of SpringPlaywrightIT, which uses the test base; the Unified API writes failure evidence itself: remove it)~~>*/class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void opens() {
                        navigate(PeopleView.class);
                    }
                }
                """));
    }

    @Test
    void aBrowserlessClassPassingItselfToAPageObjectIsListedAsSkipped() {
        rewriteRun(spec -> spec.dataTable(FindTestClassesToMigrate.TestClasses.Row.class, rows -> assertThat(rows)
                        .containsExactly(new FindTestClassesToMigrate.TestClasses.Row("LoginTest.java", "LoginTest",
                                "Browserless", "skipped", FindTestClassesToMigrate.PASSES_ITSELF))),
                java(
                        """
                        import com.vaadin.browserless.BrowserlessTest;

                        class LoginPage {
                            LoginPage(BrowserlessTest test) {
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.browserless.BrowserlessTest;
                        import org.junit.jupiter.api.Test;

                        class LoginTest extends BrowserlessTest {
                            @Test
                            void logsIn() {
                                new LoginPage(this);
                            }
                        }
                        """,
                        """
                        import com.vaadin.browserless.BrowserlessTest;
                        import org.junit.jupiter.api.Test;

                        /*~~(skipped: passes itself (this) to a page object or helper; run with includePageObjects, or make it take a UnifiedPage)~~>*/class LoginTest extends BrowserlessTest {
                            @Test
                            void logsIn() {
                                new LoginPage(this);
                            }
                        }
                        """));
    }

    @Test
    void aTestBenchClassIsListedAsMigratingAndOtherClassesAreNotListed() {
        rewriteRun(spec -> spec.dataTable(FindTestClassesToMigrate.TestClasses.Row.class, rows -> assertThat(rows)
                        .containsExactly(new FindTestClassesToMigrate.TestClasses.Row("ProfileTest.java", "ProfileTest",
                                "TestBench", "migrates", ""))),
                java(
                        """
                        class Helper {
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTestBase;

                        class ProfileTest extends BrowserTestBase {
                        }
                        """,
                        """
                        import com.vaadin.testbench.BrowserTestBase;

                        /*~~(migrates)~~>*/class ProfileTest extends BrowserTestBase {
                        }
                        """));
    }

    @Test
    void aTestBenchClassUsingAPageObjectThatStaysTestBenchMigratesPartlyAndListsIt() {
        rewriteRun(spec -> spec.dataTable(FindTestClassesToMigrate.TestClasses.Row.class, rows -> assertThat(rows)
                        .containsExactly(new FindTestClassesToMigrate.TestClasses.Row("LoginIT.java", "LoginIT",
                                "TestBench", "migrates partly", "",
                                "LoginViewElement: stays TestBench (Escape Hatch), a custom element extending "
                                        + "LoginOverlayElement: write a Wrapper (ADR 0003); ProfilePage: converts with "
                                        + "includePageObjects; SettingsPage: stays TestBench (Escape Hatch), uses the "
                                        + "TestBench test for more than lookups a Wrapper expresses"))),
                java(
                        """
                        import com.vaadin.flow.component.login.testbench.LoginOverlayElement;
                        import com.vaadin.testbench.elementsbase.Element;

                        @Element("login-view")
                        public class LoginViewElement extends LoginOverlayElement {
                            public void login(String user, String password) {
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.testbench.HasElementQuery;

                        class ProfilePage {
                            private final HasElementQuery test;

                            ProfilePage(HasElementQuery test) {
                                this.test = test;
                            }

                            void save() {
                                test.$(ButtonElement.class).withText("Save").single().click();
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTestBase;

                        class SettingsPage {
                            private final BrowserTestBase test;

                            SettingsPage(BrowserTestBase test) {
                                this.test = test;
                            }

                            void reload() {
                                test.getDriver().navigate().refresh();
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class LoginIT extends BrowserTestBase {
                            @BrowserTest
                            void logsIn() {
                                new SettingsPage(this).reload();
                                new ProfilePage(this).save();
                                $(LoginViewElement.class).single().login("barista", "barista");
                            }
                        }
                        """,
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        /*~~(migrates partly: LoginViewElement: stays TestBench (Escape Hatch), a custom element extending LoginOverlayElement: write a Wrapper (ADR 0003); ProfilePage: converts with includePageObjects; SettingsPage: stays TestBench (Escape Hatch), uses the TestBench test for more than lookups a Wrapper expresses)~~>*/class LoginIT extends BrowserTestBase {
                            @BrowserTest
                            void logsIn() {
                                new SettingsPage(this).reload();
                                new ProfilePage(this).save();
                                $(LoginViewElement.class).single().login("barista", "barista");
                            }
                        }
                        """));
    }

    @Test
    void aTestBenchClassUsingOnlyPageObjectsThatConvertMigrates() {
        rewriteRun(spec -> spec.dataTable(FindTestClassesToMigrate.TestClasses.Row.class, rows -> assertThat(rows)
                        .containsExactly(new FindTestClassesToMigrate.TestClasses.Row("ProfileIT.java", "ProfileIT",
                                "TestBench", "migrates", "", "ProfilePage: converts with includePageObjects"))),
                java(
                        """
                        import com.vaadin.flow.component.button.testbench.ButtonElement;
                        import com.vaadin.testbench.HasElementQuery;

                        class ProfilePage {
                            private final HasElementQuery test;

                            ProfilePage(HasElementQuery test) {
                                this.test = test;
                            }

                            void save() {
                                test.$(ButtonElement.class).withText("Save").single().click();
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        class ProfileIT extends BrowserTestBase {
                            @BrowserTest
                            void saves() {
                                new ProfilePage(this).save();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testbench.BrowserTest;
                        import com.vaadin.testbench.BrowserTestBase;

                        /*~~(migrates: ProfilePage: converts with includePageObjects)~~>*/class ProfileIT extends BrowserTestBase {
                            @BrowserTest
                            void saves() {
                                new ProfilePage(this).save();
                            }
                        }
                        """));
    }
}
