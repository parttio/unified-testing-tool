package org.vaadin.addons.unified.rewrite;

import static org.openrewrite.java.Assertions.java;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** The Dramafinder Migration Recipe's setup and Escape Hatch (ADR 0006), one rule per case. */
class MigrateDramafinderToUnifiedTest implements RewriteTest {

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
                        .activateRecipes("org.vaadin.addons.unified.rewrite.MigrateDramafinderToUnified"))
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    /** A recipe declared in a project's {@code rewrite.yml}, found with this jar's recipes as the plugins find it. */
    private static Recipe projectRecipe(String yaml, String name) {
        return Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite")
                .load(new YamlResourceLoader(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                        URI.create("rewrite.yml"), new Properties()))
                .build().activateRecipes(name);
    }

    @Test
    void addAgentSkillSetInAProjectsCompositeRecipeWritesTheMigrationMarkersSkill() {
        // issue #353: OpenRewrite builds a recipe from its options by constructor parameter name
        rewriteRun(
                spec -> spec.recipe(projectRecipe("""
                        type: specs.openrewrite.org/v1beta/recipe
                        name: com.example.MigrateToUnified
                        displayName: Migrate to Unified
                        description: The options as a project sets them.
                        recipeList:
                          - org.vaadin.addons.unified.rewrite.MigrateDramafinderToUnified:
                              addAgentSkill: true
                        """, "com.example.MigrateToUnified")),
                java(VIEW),
                org.openrewrite.test.SourceSpecs.text(null, AddMigrationMarkersSkill.skill(),
                        source -> source.path(AddMigrationMarkersSkill.PATH)));
    }

    @Test
    void springPlaywrightITBecomesUnifiedSpringBootTestAndNavigatesOnTheUnifiedPage() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void opensPeople() {
                        navigate(PeopleView.class);
                        assertAt(PeopleView.class);
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    void opensPeople(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        assertThat(page).isAt(PeopleView.class);
                    }
                }
                """));
    }


    /** A chat: MessageInputElement and MessageListElement (#357). */
    @Test
    void chatElementsBecomeTheUnifiedMessageInputAndList() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import static org.vaadin.addons.dramafinder.element.query.ElementQueries.find;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.MessageInputElement;
                import org.vaadin.addons.dramafinder.element.MessageListElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class ChatTest extends SpringPlaywrightIT {
                    @Test
                    void asks() {
                        navigate(PeopleView.class);
                        MessageListElement messages = MessageListElement.get(page);
                        messages.assertEmpty();
                        MessageInputElement.get(page).typeAndSubmit("Pancakes");
                        messages.assertMessageCount(2);
                        find(page, MessageInputElement.class).withTestId("chat").single().assertEnabled();
                    }
                }
                """,
                """
                package com.example;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedMessageInput;
                import com.vaadin.testing.unified.component.UnifiedMessageList;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class ChatTest {
                    @Test
                    void asks(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        UnifiedMessageList messages = page.find(UnifiedMessageList.class);
                        assertThat(messages).isEmpty();
                        page.find(UnifiedMessageInput.class).send("Pancakes");
                        assertThat(messages).hasMessageCount(2);
                        assertThat(page.find(UnifiedMessageInput.class).withTestId("chat")).isEnabled();
                    }
                }
                """));
    }

    @Test
    void springBootTestAndTheToolGuardGo() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.vaadin.addons.dramafinder.element.ButtonElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
                @EnabledIfSystemProperty(named = "vaadin.testing.tool", matches = "playwright")
                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void saves() {
                        navigate(PeopleView.class);
                        ButtonElement.getByText(page, "Save").click();
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    void saves(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void plainBaseClassBecomesUnifiedUITestAndEachTestStartsAtTheView() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.AbstractBasePlaywrightIT;

                class PeopleTest extends AbstractBasePlaywrightIT {

                    @Override
                    public String getUrl() {
                        return "http://localhost:8080/";
                    }

                    @Override
                    public String getView() {
                        return "people";
                    }

                    @Test
                    void startsAtPeople() {
                        assertPath("people");
                    }

                    @Test
                    void navigatesItself() {
                        navigate(PeopleView.class);
                        assertQueryParameter("sort", "name");
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.UnifiedUITest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedUITest
                class PeopleTest {

                    @Test
                    void startsAtPeople(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        assertThat(page).hasPath("people");
                    }

                    @Test
                    void navigatesItself(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        assertThat(page).hasQueryParameter("sort", "name");
                    }
                }
                """));
    }

    @Test
    void helpersAndBeforeEachTakeTheUnifiedPage() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.ButtonElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {

                    @BeforeEach
                    void open() {
                        navigate(PeopleView.class);
                    }

                    @Test
                    void saves() {
                        save();
                    }

                    private void save() {
                        ButtonElement.getByText(getPage(), "Save").click();
                    }
                }
                """,
                """
                package com.example;

                import com.microsoft.playwright.Page;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.ButtonElement;

                @UnifiedSpringBootTest
                class PeopleTest {

                    void open(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                    }

                    @Test
                    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
                    void saves(UnifiedPage page) {
                        page.navigate("");
                        open(page);
                        save(page);
                    }

                    private void save(UnifiedPage page) {
                        Page playwright = page.unwrap(Page.class);
                        // TODO unified: Dramafinder Escape Hatch: ButtonElement.getByText(playwright, "Save").click()
                        ButtonElement.getByText(playwright, "Save").click();
                    }
                }
                """));
    }

    @Test
    void anEscapeHatchMarkerCarriesOnlyTheCallNotTheCommentAboveIt() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void saves() {
                        navigate(PeopleView.class);
                        // the button has no text:
                        // only its tag finds it
                        this.page.locator("vaadin-button").click();
                    }
                }
                """,
                """
                package com.example;

                import com.microsoft.playwright.Page;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
                    void saves(UnifiedPage page) {
                        Page playwright = page.unwrap(Page.class);
                        page.navigate(PeopleView.class);
                        // the button has no text:
                        // only its tag finds it
                        // TODO unified: Dramafinder Escape Hatch: playwright.locator("vaadin-button").click()
                        playwright.locator("vaadin-button").click();
                    }
                }
                """));
    }

    @Test
    void rawPlaywrightAndPageObjectsGetThePageFromTheEscapeHatch() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import com.microsoft.playwright.Page;

                public class PeoplePage {
                    public PeoplePage(Page page) {
                    }

                    public void save() {
                    }
                }
                """), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void saves() {
                        navigate(PeopleView.class);
                        new PeoplePage(page).save();
                        this.page.locator("vaadin-button").click();
                    }
                }
                """,
                """
                package com.example;

                import com.microsoft.playwright.Page;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
                    void saves(UnifiedPage page) {
                        Page playwright = page.unwrap(Page.class);
                        page.navigate(PeopleView.class);
                        // TODO unified: Dramafinder Escape Hatch: new PeoplePage(playwright)
                        new PeoplePage(playwright).save();
                        // TODO unified: Dramafinder Escape Hatch: playwright.locator("vaadin-button").click()
                        playwright.locator("vaadin-button").click();
                    }
                }
                """));
    }

    /** A view that sets ids and test ids, as the application's sources do. */
    private static final String ID_VIEW = """
            package com.example;

            import com.vaadin.flow.component.button.Button;
            import com.vaadin.flow.component.checkbox.Checkbox;
            import com.vaadin.flow.component.html.Div;
            import com.vaadin.flow.component.textfield.TextField;
            import com.vaadin.flow.router.Route;

            @Route("plan")
            public class PlanView extends Div {
                public PlanView() {
                    Button next = new Button("Next week");
                    next.setId("week-next");
                    TextField meal = new TextField();
                    meal.setId("meal-friday");
                    Checkbox cooked = new Checkbox("Cooked");
                    cooked.getElement().setAttribute("data-testid", "cooked");
                    Div card = new Div();
                    card.setId("recipe-card");
                    add(next, meal, cooked, card);
                }
            }
            """;

    @Test
    void rawIdAndTestIdLocatorsBecomeLookupsOfTheWrapperTheViewSetsTheIdOn() {
        // issue #356: locator("#id"), locator("[data-testid=..]") and getByTestId(..) name a component by id
        rewriteRun(java(VIEW), java(ID_VIEW), java(
                """
                package com.example;

                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                import com.microsoft.playwright.Locator;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PlanTest extends SpringPlaywrightIT {
                    @Test
                    void plans() {
                        navigate(PlanView.class);
                        page.locator("#week-next").click();
                        Locator meal = page.locator("#meal-friday");
                        meal.fill("Pancakes");
                        assertThat(meal).hasValue("Pancakes");
                        page.getByTestId("cooked").check();
                        assertThat(page.locator("[data-testid='cooked']")).isChecked();
                        page.locator("#recipe-card").click();
                        assertThat(page.locator("#week-next")).isEnabled();
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedCheckbox;
                import com.vaadin.testing.unified.component.UnifiedContainer;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PlanTest {
                    @Test
                    void plans(UnifiedPage page) {
                        page.navigate(PlanView.class);
                        page.find(UnifiedButton.class).withId("week-next").click();
                        UnifiedTextField meal = page.find(UnifiedTextField.class).withId("meal-friday");
                        meal.setValue("Pancakes");
                        assertThat(meal).hasValue("Pancakes");
                        page.find(UnifiedCheckbox.class).withTestId("cooked").check();
                        assertThat(page.find(UnifiedCheckbox.class).withTestId("cooked")).isChecked();
                        page.find(UnifiedContainer.class).withId("recipe-card").click();
                        assertThat(page.find(UnifiedButton.class).withId("week-next")).isEnabled();
                    }
                }
                """));
    }

    @Test
    void aRawLocatorOnAnIdNoViewSetsBecomesAContainerWithAMarker() {
        rewriteRun(java(VIEW), java(ID_VIEW), java(
                """
                package com.example;

                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PlanTest extends SpringPlaywrightIT {
                    @Test
                    void plans() {
                        navigate(PlanView.class);
                        page.locator("#mise-undo").click();
                        assertThat(page.getByTestId("toast")).isVisible();
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedContainer;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PlanTest {
                    @Test
                    void plans(UnifiedPage page) {
                        page.navigate(PlanView.class);
                        // TODO unified: was locator("#mise-undo"): no view in the sources sets this id; UnifiedContainer finds any component, use the component's own Wrapper if it has one
                        page.find(UnifiedContainer.class).withId("mise-undo").click();
                        // TODO unified: was getByTestId("toast"): no view in the sources sets this test id; UnifiedContainer finds any component, use the component's own Wrapper if it has one
                        assertThat(page.find(UnifiedContainer.class).withTestId("toast")).isVisible();
                    }
                }
                """));
    }

    @Test
    void aRawLocatorWithACallItsWrapperLacksStaysBehindTheEscapeHatch() {
        rewriteRun(java(VIEW), java(ID_VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PlanTest extends SpringPlaywrightIT {
                    @Test
                    void plans() {
                        navigate(PlanView.class);
                        page.locator("#meal-friday").press("Enter");
                        page.locator("#mise-undo").fill("x");
                        page.locator("#week-next span").click();
                    }
                }
                """,
                """
                package com.example;

                import com.microsoft.playwright.Page;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PlanTest {
                    @Test
                    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
                    void plans(UnifiedPage page) {
                        Page playwright = page.unwrap(Page.class);
                        page.navigate(PlanView.class);
                        // TODO unified: Dramafinder Escape Hatch: playwright.locator("#meal-friday").press("Enter")
                        playwright.locator("#meal-friday").press("Enter");
                        // TODO unified: Dramafinder Escape Hatch: playwright.locator("#mise-undo").fill("x")
                        playwright.locator("#mise-undo").fill("x");
                        // TODO unified: Dramafinder Escape Hatch: playwright.locator("#week-next span").click()
                        playwright.locator("#week-next span").click();
                    }
                }
                """));
    }

    @Test
    void textAttributeClassVisibilityAndAttachedChecksOnRawLocatorsBecomeUnifiedChecks() {
        // issue #510: the Playwright checks a real suite makes most, on the raw locators #356 converts
        rewriteRun(java(VIEW), java(ID_VIEW), java(
                """
                package com.example;

                import com.microsoft.playwright.Locator;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                import java.util.regex.Pattern;

                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                class PlanTest extends SpringPlaywrightIT {
                    @Test
                    void plans() {
                        navigate(PlanView.class);
                        assertThat(page.locator("#recipe-card")).containsText("Pancakes");
                        assertThat(page.locator("#recipe-card")).hasText("Pancakes on Friday");
                        assertThat(page.locator("#week-next")).hasAttribute("disabled", "");
                        assertThat(page.locator("#week-next")).not().hasAttribute("disabled", "");
                        assertThat(page.locator("#week-next")).not().hasAttribute("aria-disabled", "true");
                        assertThat(page.locator("#recipe-card")).hasClass("recipe-card--future");
                        assertThat(page.locator("#recipe-card")).hasClass(Pattern.compile(".*recipe-card--past.*"));
                        assertThat(page.getByTestId("cooked")).not().isVisible();
                        assertThat(page.locator("#recipe-card")).isAttached();
                        Locator meal = page.locator("#meal-friday");
                        assertThat(meal).not().hasAttribute("disabled", "");
                        assertThat(meal).hasClass("filled");
                        assertThat(meal).not().isVisible();
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedCheckbox;
                import com.vaadin.testing.unified.component.UnifiedContainer;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PlanTest {
                    @Test
                    void plans(UnifiedPage page) {
                        page.navigate(PlanView.class);
                        assertThat(page.find(UnifiedContainer.class).withId("recipe-card")).hasTextContaining("Pancakes");
                        assertThat(page.find(UnifiedContainer.class).withId("recipe-card")).hasText("Pancakes on Friday");
                        assertThat(page.find(UnifiedButton.class).withId("week-next")).isDisabled();
                        assertThat(page.find(UnifiedButton.class).withId("week-next")).isEnabled();
                        assertThat(page.find(UnifiedButton.class).withId("week-next")).isEnabled();
                        assertThat(page.find(UnifiedContainer.class).withId("recipe-card").withClassName("recipe-card--future")).exists();
                        assertThat(page.find(UnifiedContainer.class).withId("recipe-card").withClassName("recipe-card--past")).exists();
                        assertThat(page.find(UnifiedCheckbox.class).withTestId("cooked").visible()).doesNotExist();
                        assertThat(page.find(UnifiedContainer.class).withId("recipe-card")).exists();
                        UnifiedTextField meal = page.find(UnifiedTextField.class).withId("meal-friday");
                        assertThat(meal).isEnabled();
                        assertThat(meal.withClassName("filled")).exists();
                        assertThat(meal.visible()).doesNotExist();
                    }
                }
                """));
    }

    @Test
    void aTextOrClassCheckOnARawLocatorWithAPatternKeepsItsMarker() {
        // issue #510: a pattern is not a literal text, and only ".*x.*" is one class name
        rewriteRun(java(VIEW), java(ID_VIEW), java(
                """
                package com.example;

                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                import java.util.regex.Pattern;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PlanTest extends SpringPlaywrightIT {
                    @Test
                    void plans() {
                        navigate(PlanView.class);
                        assertThat(page.locator("#recipe-card")).hasText(Pattern.compile("Pan.*"));
                        assertThat(page.locator("#recipe-card")).containsText(Pattern.compile("Pan"));
                        assertThat(page.locator("#recipe-card")).hasClass(Pattern.compile("^a|b$"));
                    }
                }
                """,
                """
                package com.example;

                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                import java.util.regex.Pattern;

                import com.microsoft.playwright.Page;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PlanTest {
                    @Test
                    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
                    void plans(UnifiedPage page) {
                        Page playwright = page.unwrap(Page.class);
                        page.navigate(PlanView.class);
                        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.locator("#recipe-card")).hasText(Pattern.compile("Pan.*"))
                        assertThat(playwright.locator("#recipe-card")).hasText(Pattern.compile("Pan.*"));
                        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.locator("#recipe-card")).containsText(Pattern.compile("Pan"))
                        assertThat(playwright.locator("#recipe-card")).containsText(Pattern.compile("Pan"));
                        // TODO unified: Dramafinder Escape Hatch: assertThat(playwright.locator("#recipe-card")).hasClass(Pattern.compile("^a|b$"))
                        assertThat(playwright.locator("#recipe-card")).hasClass(Pattern.compile("^a|b$"));
                    }
                }
                """));
    }

    @Test
    void queryApiLookupsLocalsAndElementAssertionsBecomeWrappersAndUnifiedAssertions() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import static org.vaadin.addons.dramafinder.element.query.ElementQueries.find;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.AutoWait;
                import org.vaadin.addons.dramafinder.element.CheckboxElement;
                import org.vaadin.addons.dramafinder.element.DialogElement;
                import org.vaadin.addons.dramafinder.element.TextFieldElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void edits() {
                        navigate(PeopleView.class);
                        TextFieldElement name = find(page, TextFieldElement.class).withLabel("Name").single();
                        name.setValue("Alan");
                        AutoWait.waitForVaadinIdle(page);
                        name.assertValue("Alan");
                        find(page, CheckboxElement.class).withLabel("Active").first().check();
                        find(page, TextFieldElement.class).get(1).assertDisabled();
                        DialogElement.getByHeaderText(page, "Edit").assertOpen();
                    }
                }
                """,
                """
                package com.example;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedCheckbox;
                import com.vaadin.testing.unified.component.UnifiedDialog;
                import com.vaadin.testing.unified.component.UnifiedTextField;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    void edits(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
                        name.setValue("Alan");
                        assertThat(name).hasValue("Alan");
                        // TODO unified: was first(); the lookup fails when several components match
                        page.find(UnifiedCheckbox.class).withLabel("Active").check();
                        assertThat(page.find(UnifiedTextField.class).all().get(1)).isDisabled();
                        assertThat(page.find(UnifiedDialog.class).withHeaderTitle("Edit")).isOpen();
                    }
                }
                """));
    }

    @Test
    void aLookupWithACallTheWrapperLacksStaysBehindTheEscapeHatch() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.TextFieldElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void checksThePattern() {
                        navigate(PeopleView.class);
                        TextFieldElement name = TextFieldElement.getByLabel(page, "Name");
                        name.setValue("Alan");
                        name.assertPattern("[A-Z].*");
                    }
                }
                """,
                """
                package com.example;

                import com.microsoft.playwright.Page;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.TextFieldElement;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
                    void checksThePattern(UnifiedPage page) {
                        Page playwright = page.unwrap(Page.class);
                        page.navigate(PeopleView.class);
                        // TODO unified: Dramafinder Escape Hatch: TextFieldElement.getByLabel(playwright, "Name")
                        TextFieldElement name = TextFieldElement.getByLabel(playwright, "Name");
                        // TODO unified: Dramafinder Escape Hatch: name.setValue("Alan")
                        name.setValue("Alan");
                        // TODO unified: Dramafinder Escape Hatch: name.assertPattern("[A-Z].*")
                        name.assertPattern("[A-Z].*");
                    }
                }
                """));
    }

    @Test
    void gridRowsCellsAndWaitsBecomeUnifiedGridCalls() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.GridElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void readsTheGrid() {
                        navigate(PeopleView.class);
                        GridElement people = GridElement.get(page);
                        people.waitForGridToStopLoading();
                        people.assertRowCount(3);
                        Assertions.assertEquals(3, people.getTotalRowCount());
                        GridElement.RowElement ada = people.findRow(people.findRowIndexesWithColumnText(0, "Ada").get(0)).get();
                        Assertions.assertEquals("ada@example.com", ada.getCell(1).getText());
                        ada.select();
                        ada.assertSelected();
                        GridElement.RowElement first = people.findRow(0).orElseThrow();
                        first.getCell("Email").assertText("alan@example.com");
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedGrid;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    void readsTheGrid(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        UnifiedGrid people = page.find(UnifiedGrid.class);
                        assertThat(people).hasRowCount(3);
                        Assertions.assertEquals(3, people.getRowCount());
                        // TODO unified: was findRowIndexesWithColumnText(0, ..).get(0): any column matches now; the row lookup fails when several rows match
                        UnifiedGrid.UnifiedGridRow ada = people.row(people.row().withCellText("Ada").getIndex());
                        Assertions.assertEquals("ada@example.com", ada.getCellText(1));
                        ada.select();
                        assertThat(ada).isSelected();
                        UnifiedGrid.UnifiedGridRow first = people.row(0);
                        assertThat(first).hasCellText("Email", "alan@example.com");
                    }
                }
                """));
    }

    @Test
    void aGridWithACallNoRuleConvertsStaysBehindTheEscapeHatch() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.GridElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Test
                    void sorts() {
                        navigate(PeopleView.class);
                        GridElement people = GridElement.get(page);
                        people.findHeaderCellByText("Name").orElseThrow().clickSort();
                    }
                }
                """,
                """
                package com.example;

                import com.microsoft.playwright.Page;
                import com.vaadin.testing.unified.RequiresTestingTool;
                import com.vaadin.testing.unified.TestingTool;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.GridElement;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @Test
                    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
                    void sorts(UnifiedPage page) {
                        Page playwright = page.unwrap(Page.class);
                        page.navigate(PeopleView.class);
                        // TODO unified: Dramafinder Escape Hatch: GridElement.get(playwright)
                        GridElement people = GridElement.get(playwright);
                        // TODO unified: Dramafinder Escape Hatch: people.findHeaderCellByText("Name").orElseThrow().clickSort()
                        people.findHeaderCellByText("Name").orElseThrow().clickSort();
                    }
                }
                """));
    }

    @Test
    void navigatingToTheBaseUrlPlusAPathNavigatesToThePath() {
        // issue #351: getUrl() + "/path" is how Dramafinder tests open a view with query parameters
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import java.time.LocalDate;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PlanTest extends SpringPlaywrightIT {
                    @Test
                    void opensTheWeek() {
                        LocalDate monday = LocalDate.of(2026, 11, 16);
                        page.navigate(getUrl() + "/plan?week=" + monday);
                        assertPath("plan");
                    }

                    @Test
                    void opensPeople() {
                        page.navigate(getUrl() + "people");
                        openPlan();
                    }

                    @Test
                    void opensTheStart() {
                        this.page.navigate(getUrl());
                    }

                    private void openPlan() {
                        getPage().navigate(getUrl() + "/" + "plan");
                    }
                }
                """,
                """
                package com.example;

                import java.time.LocalDate;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PlanTest {
                    @Test
                    void opensTheWeek(UnifiedPage page) {
                        page.navigate("");
                        LocalDate monday = LocalDate.of(2026, 11, 16);
                        page.navigate("plan?week=" + monday);
                        assertThat(page).hasPath("plan");
                    }

                    @Test
                    void opensPeople(UnifiedPage page) {
                        page.navigate("people");
                        openPlan(page);
                    }

                    @Test
                    void opensTheStart(UnifiedPage page) {
                        page.navigate("");
                    }

                    private void openPlan(UnifiedPage page) {
                        page.navigate("plan");
                    }
                }
                """));
    }

    @Test
    void theBaseUrlUsedOtherThanToNavigateStillKeepsTheClass() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class LinkTest extends SpringPlaywrightIT {
                    @Test
                    void opens() {
                        String url = getUrl() + "people";
                        page.navigate(url);
                    }
                }
                """,
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                // TODO unified: not migrated: calls getUrl() of SpringPlaywrightIT; convert them by hand, then run the migration again
                class LinkTest extends SpringPlaywrightIT {
                    @Test
                    void opens() {
                        String url = getUrl() + "people";
                        page.navigate(url);
                    }
                }
                """));
    }

    @Test
    void aTestUsingABaseClassMemberNoRuleConvertsStaysAndSaysWhy() {
        // issue #350: a skipped class says what kept it from migrating
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import com.microsoft.playwright.BrowserContext;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class AdminTest extends SpringPlaywrightIT {
                    @Override
                    protected void prepareBrowserContext(BrowserContext context) {
                    }

                    @Test
                    void opensPeopleAsAdmin() {
                        loginAs("admin", "ADMIN");
                        navigate(PeopleView.class);
                    }
                }
                """,
                """
                package com.example;

                import com.microsoft.playwright.BrowserContext;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                // TODO unified: not migrated: overrides prepareBrowserContext(..) of SpringPlaywrightIT, calls loginAs(..) of SpringPlaywrightIT; convert them by hand, then run the migration again
                class AdminTest extends SpringPlaywrightIT {
                    @Override
                    protected void prepareBrowserContext(BrowserContext context) {
                    }

                    @Test
                    void opensPeopleAsAdmin() {
                        loginAs("admin", "ADMIN");
                        navigate(PeopleView.class);
                    }
                }
                """));
    }

    @Test
    void aSetupTestOverrideThatSeedsBeforeTheBaseClassBecomesAPlainBeforeEach() {
        // issue #350: seeding data before the base class navigates
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.ButtonElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @BeforeEach
                    @Override
                    public void setupTest() throws Exception {
                        seed();
                        super.setupTest();
                    }

                    private void seed() {
                    }

                    @Override
                    public String getView() {
                        return "people";
                    }

                    @Test
                    void saves() {
                        ButtonElement.getByText(page, "Save").click();
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PeopleTest {
                    @BeforeEach
                    public void setupTest() throws Exception {
                        seed();
                    }

                    private void seed() {
                    }

                    @Test
                    void saves(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void aSetupTestOverrideThatOnlyCallsTheBaseClassGoes() {
        rewriteRun(java(VIEW), java(
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.element.ButtonElement;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PeopleTest extends SpringPlaywrightIT {
                    @Override
                    public void setupTest() throws Exception {
                        super.setupTest();
                    }

                    @Test
                    void saves() {
                        navigate(PeopleView.class);
                        ButtonElement.getByText(page, "Save").click();
                    }
                }
                """,
                """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                @UnifiedSpringBootTest
                class PeopleTest {

                    @Test
                    void saves(UnifiedPage page) {
                        page.navigate(PeopleView.class);
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """));
    }

    @Test
    void aSetupTestOverrideThatWorksAfterTheBaseClassStaysAndSaysWhy() {
        rewriteRun(java(VIEW), java(
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
                    }
                }
                """,
                """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                // TODO unified: not migrated: overrides setupTest() of AbstractBasePlaywrightIT; convert them by hand, then run the migration again
                class PeopleTest extends SpringPlaywrightIT {
                    @Override
                    public void setupTest() throws Exception {
                        super.setupTest();
                        navigate(PeopleView.class);
                    }

                    @Test
                    void opens() {
                    }
                }
                """));
    }

    private static final String PLAN_VIEW = """
            package com.example.views;

            import com.vaadin.flow.component.Component;
            import com.vaadin.flow.router.Route;

            @Route("plan")
            public class PlanView extends Component {}
            """;

    /** A test that starts at {@code view}, as {@code getView()} returns it. */
    private static String startingAt(String view) {
        return """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

                class PlanTest extends SpringPlaywrightIT {

                    @Override
                    public String getView() {
                        return "%s";
                    }

                    @Test
                    void opens() {
                        assertPath("plan");
                    }
                }
                """.formatted(view);
    }

    @Test
    void aViewPathOneRouteHasBecomesTheRouteClass() {
        // issue #370: the class form survives renaming the route, and its User Step reads "open Plan"
        rewriteRun(java(PLAN_VIEW), java(startingAt("/plan"),
                """
                package com.example;

                import com.example.views.PlanView;
                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PlanTest {

                    @Test
                    void opens(UnifiedPage page) {
                        page.navigate(PlanView.class);
                        assertThat(page).hasPath("plan");
                    }
                }
                """));
    }

    @Test
    void aViewPathTwoRoutesHaveStaysAPath() {
        rewriteRun(java(PLAN_VIEW), java("""
                package com.example;

                import com.vaadin.flow.component.Component;
                import com.vaadin.flow.router.Route;

                @Route(value = "plan")
                public class OtherPlanView extends Component {}
                """), java(startingAt("plan"), keptAsPath("plan")));
    }

    @Test
    void aViewPathNoRouteHasStaysAPath() {
        rewriteRun(java(PLAN_VIEW), java(startingAt("plan?week=2026-11-02"), keptAsPath("plan?week=2026-11-02")));
    }

    @Test
    void aViewPathUnderALayoutWithAPrefixStaysAPath() {
        // the route's full path is "app/plan": the recipe doesn't follow layouts, so it doesn't guess
        rewriteRun(java("""
                package com.example.views;

                import com.vaadin.flow.component.Component;
                import com.vaadin.flow.router.RoutePrefix;
                import com.vaadin.flow.router.RouterLayout;

                @RoutePrefix("app")
                public class AppLayout extends Component implements RouterLayout {}
                """), java("""
                package com.example.views;

                import com.vaadin.flow.component.Component;
                import com.vaadin.flow.router.Route;

                @Route(value = "plan", layout = AppLayout.class)
                public class PlanView extends Component {}
                """), java(startingAt("plan"), keptAsPath("plan")));
    }

    private static String keptAsPath(String view) {
        return """
                package com.example;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
                import org.junit.jupiter.api.Test;

                import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

                @UnifiedSpringBootTest
                class PlanTest {

                    @Test
                    void opens(UnifiedPage page) {
                        page.navigate("%s");
                        assertThat(page).hasPath("plan");
                    }
                }
                """.formatted(view);
    }
}
