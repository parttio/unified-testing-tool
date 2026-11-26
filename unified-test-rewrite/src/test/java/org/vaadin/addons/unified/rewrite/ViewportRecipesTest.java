package org.vaadin.addons.unified.rewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Viewport and window resizes in TestBench and Dramafinder tests (ADR 0011, #496): the size a test starts at becomes
 * {@code @Viewport}, a later resize {@code page.setViewport(..)}. One case per row of the mapping appendix.
 */
class ViewportRecipesTest implements RewriteTest {

    private static void testBench(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                        .activateRecipes("org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified"))
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void aResizeBeforeTheFirstNavigationOfABeforeEachBecomesTheClassViewport() {
        rewriteRun(ViewportRecipesTest::testBench, java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.BeforeEach;

                class ProfileTest extends BrowserTestBase {

                    @BeforeEach
                    void open() {
                        testBench().resizeViewPortTo(375, 667);
                        getDriver().get("http://localhost:8080/profile");
                    }

                    @BrowserTest
                    void editsTheProfile() {
                        $(ButtonElement.class).withText("Edit").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.Viewport;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                @Viewport(width = 375, height = 667)
                class ProfileTest {

                    void open(UnifiedPage page) {
                        page.navigate("profile");
                    }

                    @Test
                    void editsTheProfile(UnifiedPage page) {
                        open(page);
                        page.find(UnifiedButton.class).withText("Edit").click();
                    }
                }
                """));
    }

    @Test
    void aResizeAsATestsFirstStatementBecomesTheTestsViewport() {
        rewriteRun(ViewportRecipesTest::testBench, java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void editsTheProfileOnAPhone() {
                        testBench().resizeViewPortTo(375, 667);
                        getDriver().get("http://localhost:8080/profile");
                        $(ButtonElement.class).withText("Edit").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.Viewport;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    @Viewport(width = 375, height = 667)
                    void editsTheProfileOnAPhone(UnifiedPage page) {
                        page.navigate("profile");
                        page.find(UnifiedButton.class).withText("Edit").click();
                    }
                }
                """));
    }

    @Test
    void aLaterResizeBecomesSetViewportAndTheTestRequiresABrowser() {
        rewriteRun(ViewportRecipesTest::testBench, java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void theMenuFoldsOnAPhone() {
                        testBench().resizeViewPortTo(1920, 1080);
                        getDriver().get("http://localhost:8080/profile");
                        testBench().resizeViewPortTo(375, 667);
                        $(ButtonElement.class).withText("Menu").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresBrowser;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.Viewport;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    @Viewport(width = 1920, height = 1080)
                    @RequiresBrowser
                    void theMenuFoldsOnAPhone(UnifiedPage page) {
                        page.navigate("profile");
                        page.setViewport(375, 667);
                        page.find(UnifiedButton.class).withText("Menu").click();
                    }
                }
                """));
    }

    @Test
    void aWindowSizeBecomesTheSameViewportWithAMarkerSinceThePageIsTallerNow() {
        rewriteRun(ViewportRecipesTest::testBench, java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.BeforeEach;
                import org.openqa.selenium.Dimension;

                class ProfileTest extends BrowserTestBase {

                    @BeforeEach
                    void open() {
                        getDriver().manage().window().setSize(new Dimension(1024, 800));
                        getDriver().get("http://localhost:8080/profile");
                    }

                    @BrowserTest
                    void theMenuFoldsOnAPhone() {
                        getDriver().manage().window().setSize(new Dimension(375, 667));
                        $(ButtonElement.class).withText("Menu").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresBrowser;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.Viewport;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                // TODO unified: was a window size: the viewport is now exactly 1024 × 800, about 143 px taller than before on Chrome
                @UnifiedUITest
                @Viewport(width = 1024, height = 800)
                class ProfileTest {

                    void open(UnifiedPage page) {
                        page.navigate("profile");
                    }

                    @Test
                    @RequiresBrowser
                    void theMenuFoldsOnAPhone(UnifiedPage page) {
                        open(page);
                        // TODO unified: was a window size: the viewport is now exactly 375 × 667, about 143 px taller than before on Chrome
                        page.setViewport(375, 667);
                        page.find(UnifiedButton.class).withText("Menu").click();
                    }
                }
                """));
    }

    @Test
    void maximizingTheWindowIsRemovedWithAMarkerAskingForAnExactSize() {
        rewriteRun(ViewportRecipesTest::testBench, java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void editsTheProfileOnABigScreen() {
                        getDriver().manage().window().maximize();
                        getDriver().get("http://localhost:8080/profile");
                        getDriver().manage().window().fullscreen();
                        $(ButtonElement.class).withText("Edit").single().click();
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
                    void editsTheProfileOnABigScreen(UnifiedPage page) {
                        // TODO unified: was window().maximize(): a test's browser has no screen to fill; give the test an exact size with @Viewport(width = .., height = ..), or page.setViewport(..) during it
                        page.navigate("profile");
                        // TODO unified: was window().fullscreen(): a test's browser has no screen to fill; give the test an exact size with @Viewport(width = .., height = ..), or page.setViewport(..) during it
                        page.find(UnifiedButton.class).withText("Edit").click();
                    }
                }
                """));
    }

    @Test
    void theWindowsWidthAndHeightAreReadAsTheViewports() {
        rewriteRun(ViewportRecipesTest::testBench, java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;

                class ProfileTest extends BrowserTestBase {

                    @BrowserTest
                    void editsTheProfile() {
                        getDriver().get("http://localhost:8080/profile");
                        if (getDriver().manage().window().getSize().getWidth() < 600
                                || getDriver().manage().window().getSize().getHeight() < 600) {
                            $(ButtonElement.class).withText("Menu").single().click();
                        }
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresBrowser;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {

                    @Test
                    @RequiresBrowser
                    void editsTheProfile(UnifiedPage page) {
                        page.navigate("profile");
                        if (page.viewport().width() < 600
                                || page.viewport().height() < 600) {
                            page.find(UnifiedButton.class).withText("Menu").click();
                        }
                    }
                }
                """));
    }

    @Test
    void aDriverStartedWithAWindowSizeGetsAMarkerPointingAtTheRunsViewport() {
        rewriteRun(ViewportRecipesTest::testBench, java(
                """
                import com.vaadin.flow.component.button.testbench.ButtonElement;
                import com.vaadin.testbench.BrowserTest;
                import com.vaadin.testbench.BrowserTestBase;
                import org.junit.jupiter.api.BeforeEach;
                import org.openqa.selenium.chrome.ChromeDriver;
                import org.openqa.selenium.chrome.ChromeOptions;

                class ProfileTest extends BrowserTestBase {

                    @BeforeEach
                    void startTheBrowser() {
                        ChromeOptions options = new ChromeOptions();
                        options.addArguments("--headless=new", "--window-size=1024,768");
                        setDriver(new ChromeDriver(options));
                    }

                    @BrowserTest
                    void editsTheProfile() {
                        getDriver().get("http://localhost:8080/profile");
                        $(ButtonElement.class).withText("Edit").single().click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.chrome.ChromeOptions;

                // TODO unified: the browser was started with --window-size=1024,768: tests now run at an exact Viewport, 1280 × 720 unless they have @Viewport; for this size, set vaadin.testing.viewport=1024x768 for the run, or add @Viewport(width = 1024, height = 768)
                @UnifiedUITest
                class ProfileTest {

                    void startTheBrowser(UnifiedPage page) {
                        ChromeOptions options = new ChromeOptions();
                        options.addArguments("--headless=new", "--window-size=1024,768");
                    }

                    @Test
                    void editsTheProfile(UnifiedPage page) {
                        startTheBrowser(page);
                        page.navigate("profile");
                        page.find(UnifiedButton.class).withText("Edit").click();
                    }
                }
                """));
    }
}
