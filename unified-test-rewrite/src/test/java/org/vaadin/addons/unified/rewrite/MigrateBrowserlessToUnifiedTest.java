package org.vaadin.addons.unified.rewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** The Browserless Migration Recipe's test code changes (ADR 0005), one rule per case. */
class MigrateBrowserlessToUnifiedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        // found by name on the classpath, as the rewrite Maven and Gradle plugins find it
        spec.recipe(Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                        .activateRecipes("org.vaadin.addons.unified.rewrite.MigrateBrowserlessToUnified"))
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void springBrowserlessTestBecomesUnifiedSpringBootTestOnAUnifiedPage() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.flow.component.button.Button;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class ProfileTest extends SpringBrowserlessTest {
                    @Test
                    void saves() {
                        test($(Button.class).withText("Save").single()).click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class ProfileTest {
                    @Test
                    void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    /** Browserless's withClassName takes several names; the Unified one takes one (#259). */
    @Test
    void severalClassNamesBecomeWithClassNames() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.flow.component.button.Button;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class ProfileTest extends SpringBrowserlessTest {
                    @Test
                    void saves() {
                        test($(Button.class).withClassName("primary", "large").single()).click();
                        test($(Button.class).withClassName("primary").single()).click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class ProfileTest {
                    @Test
                    void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withClassNames("primary", "large").click();
                        page.find(UnifiedButton.class).withClassName("primary").click();
                    }
                }
                """));
    }

    @Test
    void plainBrowserlessTestBecomesUnifiedUITestAndKeepsItsViewPackages() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.browserless.ViewPackages;
                import com.vaadin.flow.component.button.Button;
                import org.junit.jupiter.api.Test;

                @ViewPackages(packages = "com.example")
                class ProfileTest extends BrowserlessTest {
                    @Test
                    void saves() {
                        test($(Button.class).withText("Save").single()).click();
                    }
                }
                """,
                """
                import com.vaadin.browserless.ViewPackages;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;

                @ViewPackages(packages = "com.example")
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
    void helpersAndBeforeEachGetThePage() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.html.Div;
                import com.vaadin.flow.component.textfield.TextField;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;

                class ProfileTest extends BrowserlessTest {
                    @BeforeEach
                    void open() {
                        navigate(Div.class);
                    }

                    @Test
                    void types() {
                        typeName("Alice");
                    }

                    private void typeName(String name) {
                        test($(TextField.class).withLabel("Name").single()).setValue(name);
                    }
                }
                """,
                """
                import com.vaadin.flow.component.html.Div;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {
                    void open(UnifiedPage page) {
                        page.navigate(Div.class);
                    }

                    @Test
                    void types(UnifiedPage page) {
                        open(page);
                        typeName(page, "Alice");
                    }

                    private void typeName(UnifiedPage page, String name) {
                        page.find(UnifiedTextField.class).withLabel("Name").setValue(name);
                    }
                }
                """));
    }

    @Test
    void aLocalTakesTheWrapperType() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.textfield.TextField;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                class FieldTest extends BrowserlessTest {
                    @Test
                    void types() {
                        TextField name = $(TextField.class).withCaption("Name").single();
                        test(name).setValue("Alice");
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
                class FieldTest {
                    @Test
                    void types(UnifiedPage page) {
                        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
                        name.setValue("Alice");
                        assertThat(name).hasValue("Alice");
                    }
                }
                """));
    }

    @Test
    void checkboxCallsAndChecksTakeTheirWrapperNames() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.checkbox.Checkbox;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                class TermsTest extends BrowserlessTest {
                    @Test
                    void accepts() {
                        test($(Checkbox.class).withLabel("Terms").single()).check();
                        Assertions.assertTrue($(Checkbox.class).withLabel("Terms").single().getValue());
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
                class TermsTest {
                    @Test
                    void accepts(UnifiedPage page) {
                        page.find(UnifiedCheckbox.class).withLabel("Terms").check();
                        assertThat(page.find(UnifiedCheckbox.class).withLabel("Terms")).isChecked();
                    }
                }
                """));
    }

    @Test
    void buttonCaptionBecomesTextAndOtherTerminalsBecomeSingle() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.button.Button;
                import com.vaadin.flow.component.textfield.TextField;
                import org.junit.jupiter.api.Test;

                class ProfileTest extends BrowserlessTest {
                    @Test
                    void saves() {
                        test($(TextField.class).last()).setValue("x");
                        test($(TextField.class).atIndex(2)).clear();
                        test($(Button.class).withCaption("Save").single()).click();
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
                    @Test
                    void saves(UnifiedPage page) {
                        // TODO unified: was last(); the lookup fails when several components match
                        page.find(UnifiedTextField.class).setValue("x");
                        // TODO unified: was atIndex(2); the lookup fails when several components match
                        page.find(UnifiedTextField.class).clear();
                        // TODO unified: was withCaption("Save"); use withAccessibleName(..) for a button without text
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void callsWithAUnifiedMeaningAreConverted() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.Key;
                import com.vaadin.flow.component.KeyModifier;
                import com.vaadin.flow.component.UI;
                import com.vaadin.flow.component.html.Div;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                class ViewTest extends BrowserlessTest {
                    @Test
                    void opens() {
                        UI.getCurrent().navigate(Div.class);
                        roundTrip();
                        fireShortcut(Key.KEY_S, KeyModifier.CONTROL);

                        Assertions.assertTrue(getCurrentView() instanceof Div);
                        Assertions.assertInstanceOf(Div.class, getCurrentView());
                    }
                }
                """,
                """
                import com.vaadin.flow.component.html.Div;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class ViewTest {
                    @Test
                    void opens(UnifiedPage page) {
                        page.navigate(Div.class);
                        // TODO unified: was fireShortcut(..); pressKey also reaches the focused component and an open dialog
                        page.pressKey("Control+s");

                        assertThat(page).isAt(Div.class);
                        assertThat(page).isAt(Div.class);
                    }
                }
                """));
    }

    @Test
    void leftoversRunThroughTheEscapeHatchAndRequireBrowserless() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.textfield.TextField;
                import com.vaadin.flow.component.upload.Upload;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                class ProfileTest extends BrowserlessTest {
                    TextField name;

                    @Test
                    void readsTheView() {
                        test(name).setValue("Alice");
                        test($(TextField.class).withCondition(f -> f.isRequired()).single()).setValue("x");
                        Assertions.assertTrue(test($(Upload.class).single()).isUsable());
                    }
                }
                """,
                """
                import com.vaadin.flow.component.textfield.TextField;
                import com.vaadin.flow.component.upload.Upload;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {
                    TextField name;

                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void readsTheView(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Component Reference: test(name).setValue("Alice"): replace it with a locator that finds the same component
                        browserless.test(name).setValue("Alice");
                        // TODO unified: filter runs Java on the component: test($(TextField.class).withCondition(f -> f.isRequired()).single()).setValue("x"): replace withCondition(..) with a locator
                        browserless.test(browserless.$(TextField.class).withCondition(f -> f.isRequired()).single()).setValue("x");
                        // TODO unified: Browserless Escape Hatch: test($(Upload.class).single()).isUsable(), Upload has a Core Set Wrapper: use UnifiedUpload
                        Assertions.assertTrue(browserless.test(browserless.$(Upload.class).single()).isUsable());
                    }
                }
                """));
    }

    @Test
    void aTestCallingAHelperThatEscapesRequiresBrowserlessToo() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.upload.Upload;
                import org.junit.jupiter.api.Test;

                class PeopleTest extends BrowserlessTest {
                    @Test
                    void counts() {
                        count();
                    }

                    private boolean count() {
                        return test($(Upload.class).single()).isUsable();
                    }
                }
                """,
                """
                import com.vaadin.flow.component.upload.Upload;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void counts(UnifiedPage page) {
                        count(page);
                    }

                    private boolean count(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Browserless Escape Hatch: test($(Upload.class).single()).isUsable(), Upload has a Core Set Wrapper: use UnifiedUpload
                        return browserless.test(browserless.$(Upload.class).single()).isUsable();
                    }
                }
                """));
    }

    /** The page goes up the call graph, through overloads, to the tests (#324). */
    @Test
    void thePageIsThreadedThroughEveryHelperUpToTheTests() {
        rewriteRun(java(
                """
                package com.example;

                import com.vaadin.flow.component.Component;

                public class RowActionMenu extends Component {
                    public void choose(String action) {
                    }
                }
                """),
                java(
                """
                import com.example.RowActionMenu;
                import com.vaadin.browserless.BrowserlessTest;
                import org.junit.jupiter.api.Test;

                class LinesTest extends BrowserlessTest {
                    @Test
                    void overrides() {
                        overrideCount("Line 1", 3, "Recount");
                    }

                    private void overrideCount(String lineLabel, int count, String reason) {
                        clickRowAction(lineLabel, "Override");
                    }

                    private void clickRowAction(String rowLabel, String action) {
                        clickRowAction(rowMenu(rowLabel), action);
                    }

                    private void clickRowAction(RowActionMenu menu, String action) {
                        menu.choose(action);
                    }

                    private RowActionMenu rowMenu(String rowLabel) {
                        return $(RowActionMenu.class).withId(rowLabel).single();
                    }
                }
                """,
                """
                import com.example.RowActionMenu;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class LinesTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void overrides(UnifiedPage page) {
                        overrideCount(page, "Line 1", 3, "Recount");
                    }

                    private void overrideCount(UnifiedPage page, String lineLabel, int count, String reason) {
                        clickRowAction(page, lineLabel, "Override");
                    }

                    private void clickRowAction(UnifiedPage page, String rowLabel, String action) {
                        clickRowAction(page, rowMenu(page, rowLabel), action);
                    }

                    private void clickRowAction(UnifiedPage page, RowActionMenu menu, String action) {
                        menu.choose(action);
                    }

                    private RowActionMenu rowMenu(UnifiedPage page, String rowLabel) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Browserless Escape Hatch: $(RowActionMenu.class).withId(rowLabel).single(), no Wrapper for RowActionMenu: write one (ADR 0003)
                        return browserless.$(RowActionMenu.class).withId(rowLabel).single();
                    }
                }
                """));
    }

    @Test
    void aLeftoverOnAComponentTheCoreSetWrapsNamesItsWrapperInsteadOfAskingForOne() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.menubar.MenuBar;
                import org.junit.jupiter.api.Test;

                class MenuTest extends BrowserlessTest {
                    @Test
                    void opensTheMenu() {
                        test($(MenuBar.class).single()).isUsable();
                    }
                }
                """,
                """
                import com.vaadin.flow.component.menubar.MenuBar;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class MenuTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void opensTheMenu(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Browserless Escape Hatch: test($(MenuBar.class).single()).isUsable(), MenuBar has a Core Set Wrapper: use UnifiedMenuBar
                        browserless.test(browserless.$(MenuBar.class).single()).isUsable();
                    }
                }
                """));
    }

    @Test
    void aLeftoverOnASubclassOfACoreSetComponentNamesTheSuperclassWrapperAndItsFilters() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.dialog.Dialog;

                public class ErrorDialog extends Dialog {
                }
                """),
                java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import org.junit.jupiter.api.Test;

                class ErrorTest extends BrowserlessTest {
                    @Test
                    void showsTheError() {
                        test($(ErrorDialog.class).single()).isUsable();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ErrorTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void showsTheError(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Browserless Escape Hatch: test($(ErrorDialog.class).single()).isUsable(), ErrorDialog extends Dialog: use UnifiedDialog with a filter (withHeaderTitle, withClassName, ...)
                        browserless.test(browserless.$(ErrorDialog.class).single()).isUsable();
                    }
                }
                """));
    }

    @Test
    void aLeftoverOnAComponentNoCoreSetWrapperCoversStillAsksForAWrapper() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.html.Image;
                import org.junit.jupiter.api.Test;

                class LogoTest extends BrowserlessTest {
                    @Test
                    void showsTheLogo() {
                        test($(Image.class).single()).isUsable();
                    }
                }
                """,
                """
                import com.vaadin.flow.component.html.Image;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class LogoTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void showsTheLogo(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Browserless Escape Hatch: test($(Image.class).single()).isUsable(), no Wrapper for Image: write one (ADR 0003)
                        browserless.test(browserless.$(Image.class).single()).isUsable();
                    }
                }
                """));
    }

    @Test
    void aLookupWithACallItsWrapperLacksStaysBehindTheEscapeHatch() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.textfield.TextField;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                class ProfileTest extends BrowserlessTest {
                    @Test
                    void reads() {
                        Assertions.assertEquals(20, $(TextField.class).withLabel("Name").single().getMaxLength());
                    }
                }
                """,
                """
                import com.vaadin.flow.component.textfield.TextField;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void reads(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Browserless Escape Hatch: $(TextField.class).withLabel("Name").single().getMaxLength()
                        Assertions.assertEquals(20, browserless.$(TextField.class).withLabel("Name").single().getMaxLength());
                    }
                }
                """));
    }

    @Test
    void aMigrationMarkerCarriesOnlyTheCallNotTheCommentAboveIt() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.textfield.TextField;
                import org.junit.jupiter.api.Test;

                class ProfileTest extends BrowserlessTest {
                    @Test
                    void reads() {
                        // the field is capped:
                        // twenty characters
                        $(TextField.class).withLabel("Name").single().getMaxLength();
                    }
                }
                """,
                """
                import com.vaadin.flow.component.textfield.TextField;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class ProfileTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void reads(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // the field is capped:
                        // twenty characters
                        // TODO unified: Browserless Escape Hatch: $(TextField.class).withLabel("Name").single().getMaxLength()
                        browserless.$(TextField.class).withLabel("Name").single().getMaxLength();
                    }
                }
                """));
    }

    @Test
    void aTransactionalTestRequiresBrowserless() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.flow.component.button.Button;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.transaction.annotation.Transactional;

                @SpringBootTest
                class ProfileTest extends SpringBrowserlessTest {
                    @Test
                    @Transactional
                    void saves() {
                        test($(Button.class).withText("Save").single()).click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;
                import org.springframework.transaction.annotation.Transactional;

                @UnifiedSpringBootTest
                class ProfileTest {
                    // TODO unified: Browserless-only pattern: @Transactional rolls back what the views write only on Browserless; commit test data and clean up to run everywhere (spec 02)
                    @Test
                    @Transactional
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void saves(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void aNarrowSpringContextRequiresBrowserlessAndKeepsTheSpringExtension() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.flow.component.button.Button;
                import org.junit.jupiter.api.Test;
                import org.springframework.test.context.ContextConfiguration;

                @ContextConfiguration(classes = Object.class)
                class ProfileTest extends SpringBrowserlessTest {
                    @Test
                    void saves() {
                        test($(Button.class).withText("Save").single()).click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.springframework.test.context.ContextConfiguration;
                import org.springframework.test.context.junit.jupiter.SpringExtension;

                // TODO unified: Browserless-only pattern: a Spring context without @SpringBootTest has no web server; use @UnifiedSpringBootTest with @MockitoBean to run everywhere (spec 02)
                @ContextConfiguration(classes = Object.class)
                @ExtendWith(SpringExtension.class)
                @RequiresTestingTool(TestingTool.BROWSERLESS)
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
    void aClassPassingItselfToAPageObjectIsMarkedAndLeftAsItIs() {
        rewriteRun(
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

                        // TODO unified: not migrated: this class passes itself (this) to a page object or helper; make them take a UnifiedPage, then run the migration again
                        class LoginTest extends BrowserlessTest {
                            @Test
                            void logsIn() {
                                new LoginPage(this);
                            }
                        }
                        """));
    }

    @Test
    void addAgentSkillWritesTheMigrationMarkersSkill() throws Exception {
        String skill = new String(getClass().getResourceAsStream(
                "/META-INF/unified/skills/resolve-migration-markers/SKILL.md").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        rewriteRun(
                spec -> spec.recipe(new MigrateBrowserlessToUnified(null, null, true)),
                org.openrewrite.test.SourceSpecs.text(null, skill,
                        source -> source.path(".claude/skills/resolve-migration-markers/SKILL.md")));
    }

    @Test
    void anExtensionTestBecomesAUnifiedUITestWithItsViewPackages() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessExtension;
                import com.vaadin.flow.component.html.Div;
                import com.vaadin.flow.component.textfield.TextField;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.extension.RegisterExtension;

                class ProfileTest {
                    @RegisterExtension
                    BrowserlessExtension browserless = new BrowserlessExtension().withViewPackages(Div.class);

                    @Test
                    void types() {
                        browserless.navigate(Div.class);
                        browserless.findTextField().withLabel("Name").setValue("Alice");
                        browserless.test(browserless.find(TextField.class).withLabel("Email").single()).setValue("a@b");
                    }
                }
                """,
                """
                import com.vaadin.browserless.ViewPackages;
                import com.vaadin.flow.component.html.Div;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                @ViewPackages(classes = {Div.class})
                class ProfileTest {

                    @Test
                    void types(UnifiedPage page) {
                        page.navigate(Div.class);
                        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");
                        page.find(UnifiedTextField.class).withLabel("Email").setValue("a@b");
                    }
                }
                """));
    }

    @Test
    void aContextApiTestLosesItsWiringAndAssertsOnTheLocatorsLookup() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessApplicationContext;
                import com.vaadin.browserless.BrowserlessUIContext;
                import org.junit.jupiter.api.AfterEach;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;

                class ProfileTest {
                    BrowserlessUIContext window;

                    @BeforeEach
                    void open() {
                        window = BrowserlessApplicationContext.create("com.example").newUser().newWindow();
                    }

                    @AfterEach
                    void close() {
                        window.getUser().close();
                    }

                    @Test
                    void reads() {
                        window.findButton().withText("Save").click();
                        Assertions.assertEquals("Saved", window.findNotification().getText());
                    }
                }
                """,
                """
                import com.vaadin.browserless.ViewPackages;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedNotification;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                @ViewPackages(packages = {"com.example"})
                class ProfileTest {

                    @Test
                    void reads(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                        assertThat(page.find(UnifiedNotification.class)).hasText("Saved");
                    }
                }
                """));
    }

    @Test
    void aContextApiTestUsingItsApplicationIsMarkedAndLeftAsItIs() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessApplicationContext;
                import com.vaadin.browserless.BrowserlessUIContext;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;

                class TwoUsersTest {
                    BrowserlessApplicationContext app;
                    BrowserlessUIContext window;

                    @BeforeEach
                    void open() {
                        app = BrowserlessApplicationContext.create("com.example");
                        window = app.newUser().newWindow();
                    }

                    @Test
                    void seesTheOtherUser() {
                        BrowserlessUIContext other = app.newUser().newWindow();
                    }
                }
                """,
                """
                import com.vaadin.browserless.BrowserlessApplicationContext;
                import com.vaadin.browserless.BrowserlessUIContext;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;

                // TODO unified: not migrated: this test uses the Browserless application or user context beyond opening a window; use page.openWindow() or page.openAsUser(..) instead, then run the migration again
                class TwoUsersTest {
                    BrowserlessApplicationContext app;
                    BrowserlessUIContext window;

                    @BeforeEach
                    void open() {
                        app = BrowserlessApplicationContext.create("com.example");
                        window = app.newUser().newWindow();
                    }

                    @Test
                    void seesTheOtherUser() {
                        BrowserlessUIContext other = app.newUser().newWindow();
                    }
                }
                """));
    }

    @Test
    void gridTesterCallsMoveTheRowIndexToRow() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.grid.Grid;
                import com.vaadin.flow.component.grid.GridTester;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                class PeopleTest extends BrowserlessTest {
                    @Test
                    void edits() {
                        GridTester<Grid<String>, String> people = test($(Grid.class).single());
                        people.clickRow(2);
                        people.select(1);
                        Assertions.assertEquals(3, people.size());
                        Assertions.assertEquals("Alan", people.getCellText(2, 0));
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
                    void edits(UnifiedPage page) {
                        UnifiedGrid people = page.find(UnifiedGrid.class);
                        people.row(2).click();
                        people.row(1).select();
                        Assertions.assertEquals(3, people.getRowCount());
                        Assertions.assertEquals("Alan", people.row(2).getCellText(0));
                    }
                }
                """));
    }

    @Test
    void fieldAndContainerTesterCallsTakeTheirWrapperNames() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.combobox.ComboBox;
                import com.vaadin.flow.component.combobox.MultiSelectComboBox;
                import com.vaadin.flow.component.datepicker.DatePicker;
                import com.vaadin.flow.component.details.Details;
                import com.vaadin.flow.component.tabs.TabSheet;
                import com.vaadin.flow.component.textfield.IntegerField;
                import java.time.LocalDate;
                import org.junit.jupiter.api.Test;

                class FormTest extends BrowserlessTest {
                    @Test
                    void fills() {
                        test($(ComboBox.class).withCaption("Country").single()).selectItem("Finland");
                        test($(MultiSelectComboBox.class).withCaption("Tags").single()).selectItem("a", "b");
                        test($(DatePicker.class).withCaption("Start").single()).setValue(LocalDate.of(2026, 1, 1));
                        test($(IntegerField.class).withCaption("Age").single()).setValue(42);
                        test($(TabSheet.class).single()).select("Security");
                        test($(Details.class).single()).openDetails();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.*;

                import java.time.LocalDate;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class FormTest {
                    @Test
                    void fills(UnifiedPage page) {
                        page.find(UnifiedComboBox.class).withLabel("Country").selectItem("Finland");
                        page.find(UnifiedMultiSelectComboBox.class).withLabel("Tags").selectItems("a", "b");
                        page.find(UnifiedDatePicker.class).withLabel("Start").setValue(LocalDate.of(2026, 1, 1));
                        page.find(UnifiedIntegerField.class).withLabel("Age").setValue(42);
                        page.find(UnifiedTabSheet.class).selectTab("Security");
                        page.find(UnifiedDetails.class).open();
                    }
                }
                """));
    }


    /** A chat: MessageInput and MessageList (#357). */
    @Test
    void chatTesterCallsTakeTheirWrapperNames() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.messages.MessageInput;
                import com.vaadin.flow.component.messages.MessageList;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                class ChatTest extends BrowserlessTest {
                    @Test
                    void asks() {
                        test($(MessageInput.class).single()).send("Pancakes");
                        Assertions.assertEquals(3, test($(MessageList.class).single()).size());
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
                        page.find(UnifiedMessageInput.class).send("Pancakes");
                        Assertions.assertEquals(3, page.find(UnifiedMessageList.class).getMessageCount());
                    }
                }
                """));
    }

    @Test
    void aTabSheetSelectedByIndexStaysBehindTheEscapeHatch() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.tabs.TabSheet;
                import org.junit.jupiter.api.Test;

                class TabsTest extends BrowserlessTest {
                    @Test
                    void selects() {
                        test($(TabSheet.class).single()).select(1);
                    }
                }
                """,
                """
                import com.vaadin.flow.component.tabs.TabSheet;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.browserless.BrowserlessEscapeHatch;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class TabsTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void selects(UnifiedPage page) {
                        BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);
                        // TODO unified: Browserless Escape Hatch: test($(TabSheet.class).single()).select(1)
                        browserless.test(browserless.$(TabSheet.class).single()).select(1);
                    }
                }
                """));
    }

    @Test
    void withIncludePageObjectsAPageObjectTakesTheUnifiedPage() {
        rewriteRun(
                spec -> spec.recipe(new MigrateBrowserlessToUnified(null, null, true, null)),
                java(
                        """
                        import com.vaadin.browserless.BrowserlessTest;
                        import com.vaadin.flow.component.button.Button;
                        import com.vaadin.flow.component.textfield.TextField;

                        class LoginPage {
                            private final BrowserlessTest test;

                            LoginPage(BrowserlessTest test) {
                                this.test = test;
                            }

                            void logIn(String user) {
                                test.test(test.$(TextField.class).withCaption("User").single()).setValue(user);
                                test.test(test.$(Button.class).withText("Log in").single()).click();
                            }

                            TextField user() {
                                return test.$(TextField.class).withCaption("User").single();
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.component.UnifiedButton;
                        import com.vaadin.testing.unified.component.UnifiedTextField;

                        class LoginPage {
                            private final UnifiedPage test;

                            LoginPage(UnifiedPage test) {
                                this.test = test;
                            }

                            void logIn(String user) {
                                test.find(UnifiedTextField.class).withLabel("User").setValue(user);
                                test.find(UnifiedButton.class).withText("Log in").click();
                            }

                            UnifiedTextField user() {
                                return test.find(UnifiedTextField.class).withLabel("User");
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.browserless.BrowserlessTest;
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;

                        class LoginTest extends BrowserlessTest {
                            @Test
                            void logsIn() {
                                LoginPage login = new LoginPage(this);
                                login.logIn("alice");
                                Assertions.assertEquals("alice", login.user().getValue());
                            }
                        }
                        """,
                        """
                        import com.vaadin.testing.unified.UnifiedPage;
                        import com.vaadin.testing.unified.UnifiedUITest;
                        import org.junit.jupiter.api.Test;

                        import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                        @UnifiedUITest
                        class LoginTest {
                            @Test
                            void logsIn(UnifiedPage page) {
                                LoginPage login = new LoginPage(page);
                                login.logIn("alice");
                                assertThat(login.user()).hasValue("alice");
                            }
                        }
                        """));
    }

    @Test
    void aPageObjectThatCantConvertStaysAndItsTestIsMarked() {
        rewriteRun(
                spec -> spec.recipe(new MigrateBrowserlessToUnified(null, null, true, null)),
                java(
                        """
                        import com.vaadin.browserless.BrowserlessTest;
                        import com.vaadin.flow.component.upload.Upload;

                        class UploadPage {
                            private final BrowserlessTest test;

                            UploadPage(BrowserlessTest test) {
                                this.test = test;
                            }

                            boolean ready() {
                                return test.test(test.$(Upload.class).single()).isUsable();
                            }
                        }
                        """),
                java(
                        """
                        import com.vaadin.browserless.BrowserlessTest;
                        import org.junit.jupiter.api.Test;

                        class UploadTest extends BrowserlessTest {
                            @Test
                            void uploads() {
                                new UploadPage(this).ready();
                            }
                        }
                        """,
                        """
                        import com.vaadin.browserless.BrowserlessTest;
                        import org.junit.jupiter.api.Test;

                        // TODO unified: not migrated: this class passes itself (this) to a page object or helper; make them take a UnifiedPage, then run the migration again
                        class UploadTest extends BrowserlessTest {
                            @Test
                            void uploads() {
                                new UploadPage(this).ready();
                            }
                        }
                        """));
    }

    @Test
    void aGridRowScanBecomesARowLookupByText() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.BrowserlessTest;
                import com.vaadin.flow.component.grid.Grid;
                import com.vaadin.flow.component.grid.GridTester;
                import java.util.stream.IntStream;
                import org.junit.jupiter.api.Test;

                class PeopleTest extends BrowserlessTest {
                    @Test
                    void edits() {
                        GridTester<Grid<String>, String> people = test($(Grid.class).single());
                        int row = IntStream.range(0, people.size())
                                .filter(i -> people.getCellText(i, 1).equals("Alan"))
                                .findFirst().getAsInt();
                        people.clickRow(row);
                        int other = IntStream.range(0, people.size())
                                .filter(i -> people.getCellText(i, 1).startsWith("Al"))
                                .findFirst().getAsInt();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import com.vaadin.testing.unified.component.UnifiedGrid;

                import java.util.stream.IntStream;
                import org.junit.jupiter.api.Test;

                @UnifiedUITest
                class PeopleTest {
                    @Test
                    void edits(UnifiedPage page) {
                        UnifiedGrid people = page.find(UnifiedGrid.class);
                        // TODO unified: was a scan of column 1; withCellText(text) matches any column: name the column, withCellText(header, text), if another column can hold the text
                        int row = people.row().withCellText("Alan").getIndex();
                        people.row(row).click();
                        int other = IntStream.range(0, people.getRowCount())
                                .filter(i -> people.row(i).getCellText(1).startsWith("Al"))
                                .findFirst().getAsInt();
                    }
                }
                """));
    }

    @Test
    void aClassThatIsNotABrowserlessTestIsLeftAlone() {
        rewriteRun(java(
                """
                import com.vaadin.flow.component.textfield.TextField;
                import org.junit.jupiter.api.Test;

                class FieldTest {
                    @Test
                    void sets() {
                        new TextField().setValue("x");
                    }
                }
                """));
    }

    /** The Locators mixin's lookups are Unified Page lookups; the mixin goes when nothing uses it (#323). */
    @Test
    void locatorsMixinLookupsBecomeUnifiedPageLookups() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.browserless.locator.Locators;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class ProfileTest extends SpringBrowserlessTest implements Locators {
                    @Test
                    void saves() {
                        findTextField().withLabel("Name").setValue("Alice");
                        findButton().withText("Save").click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class ProfileTest {
                    @Test
                    void saves(UnifiedPage page) {
                        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    /** An aria-label is what the Unified API calls the accessible name (#323). */
    @Test
    void locatorsMixinAriaLabelBecomesTheAccessibleName() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.browserless.locator.Locators;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class ProfileTest extends SpringBrowserlessTest implements Locators {
                    @Test
                    void closes() {
                        findButton().withAriaLabel("Close").click();
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class ProfileTest {
                    @Test
                    void closes(UnifiedPage page) {
                        page.find(UnifiedButton.class).withAccessibleName("Close").click();
                    }
                }
                """));
    }

    /** A GridLocator's cell reads are UnifiedGrid row reads, on the locator and on a local holding it (#323). */
    @Test
    void locatorsMixinGridCellReadsBecomeUnifiedGridRows() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.browserless.locator.Locators;
                import com.vaadin.flow.component.grid.GridLocator;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class PeopleTest extends SpringBrowserlessTest implements Locators {
                    @Test
                    void lists() {
                        Assertions.assertEquals("Ada", findGrid(String.class).getCellText(0, 1));
                        GridLocator<String> people = findGrid(String.class).withId("people");
                        Assertions.assertEquals(3, people.size());
                        Assertions.assertEquals("Alan", people.getCellText(2, 0));
                    }
                }
                """,
                """
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedGrid;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    void lists(UnifiedPage page) {
                        Assertions.assertEquals("Ada", page.find(UnifiedGrid.class).row(0).getCellText(1));
                        UnifiedGrid people = page.find(UnifiedGrid.class).withId("people");
                        Assertions.assertEquals(3, people.getRowCount());
                        Assertions.assertEquals("Alan", people.row(2).getCellText(0));
                    }
                }
                """));
    }

    /**
     * A Locators mixin call no Wrapper says the same as keeps running on Browserless, marked, and the class keeps
     * the mixin, marked too (#323).
     */
    @Test
    void locatorsMixinCallsThatDontConvertAreMarkedAndRequireBrowserless() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.browserless.locator.Locators;
                import com.vaadin.flow.component.grid.GridLocator;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class PeopleTest extends SpringBrowserlessTest implements Locators {
                    @Test
                    void lists() {
                        findButton().withText("Load").click();
                        Assertions.assertEquals("Ada", findGrid(String.class).getRow(0));
                        Assertions.assertEquals(2, column(findGrid(String.class), 1));
                    }

                    private int column(GridLocator<String> grid, int index) {
                        return grid.getColumnPosition("name") + index;
                    }
                }
                """,
                """
                import com.vaadin.browserless.locator.Locators;
                import com.vaadin.flow.component.grid.GridLocator;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                // TODO unified: Browserless Locators mixin: its locators run on Browserless only; find the components the marked calls use with page.find(..), then drop implements Locators
                @UnifiedSpringBootTest
                class PeopleTest implements Locators {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void lists(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Load").click();
                        // TODO unified: Browserless Locators mixin: findGrid(String.class).getRow(0)
                        Assertions.assertEquals("Ada", findGrid(String.class).getRow(0));
                        // TODO unified: Browserless Locators mixin: findGrid(String.class)
                        Assertions.assertEquals(2, column(findGrid(String.class), 1));
                    }

                    private int column(GridLocator<String> grid, int index) {
                        return grid.getColumnPosition("name") + index;
                    }
                }
                """));
    }

    /**
     * The current UI and session are the server's, which only Browserless runs in the test's JVM: reading them is
     * marked and requires Browserless, in a test and in a helper it calls (#323).
     */
    @Test
    void currentUiAndSessionAreMarkedAndRequireBrowserless() {
        rewriteRun(java(
                """
                import com.vaadin.browserless.SpringBrowserlessTest;
                import com.vaadin.flow.component.UI;
                import com.vaadin.flow.component.html.Div;
                import com.vaadin.flow.server.VaadinSession;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class ErrorsTest extends SpringBrowserlessTest {
                    @Test
                    void handlesErrors() {
                        UI.getCurrent().navigate(Div.class);
                        Assertions.assertNotNull(VaadinSession.getCurrent().getErrorHandler());
                    }

                    @Test
                    void welcomes() {
                        Assertions.assertTrue(pageText().contains("Welcome"));
                    }

                    private String pageText() {
                        return UI.getCurrent().getElement().getTextRecursively();
                    }
                }
                """,
                """
                import com.vaadin.flow.component.UI;
                import com.vaadin.flow.component.html.Div;
                import com.vaadin.flow.server.VaadinSession;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class ErrorsTest {
                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void handlesErrors(UnifiedPage page) {
                        page.navigate(Div.class);
                        // TODO unified: server-side state: VaadinSession.getCurrent().getErrorHandler(): only Browserless runs the server in the test's JVM; check what the user sees instead
                        Assertions.assertNotNull(VaadinSession.getCurrent().getErrorHandler());
                    }

                    @Test
                    @RequiresTestingTool(TestingTool.BROWSERLESS)
                    void welcomes(UnifiedPage page) {
                        Assertions.assertTrue(pageText().contains("Welcome"));
                    }

                    private String pageText() {
                        // TODO unified: server-side state: UI.getCurrent().getElement().getTextRecursively(): only Browserless runs the server in the test's JVM; check what the user sees instead
                        return UI.getCurrent().getElement().getTextRecursively();
                    }
                }
                """));
    }
}
