package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.vaadin.testbench.TestBenchElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TemplateScope;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.component.UnifiedTemplate;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import demo.template.OrdersPanel;
import demo.template.OrdersView;
import demo.template.SearchBar;
import org.junit.jupiter.api.DisplayName;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Components in a Template's shadow root, found by entering the Template (#347), on every Testing Tool. */
@UnifiedSpringBootTest
public class TemplateTest {

    // --8<-- [start:wrapper]
    /** A Wrapper for a Template: its lookups search inside it. */
    @UnifiedComponent(SearchBar.class)
    public interface UnifiedSearchBar extends UnifiedWrapper, CommonFilters<UnifiedSearchBar>, TemplateScope {
    }
    // --8<-- [end:wrapper]

    // --8<-- [start:entersNestedTemplates]
    @Test
    @DisplayName("A button in a Template nested in another is found by entering both")
    void entersNestedTemplates(UnifiedPage page) {
        page.navigate(OrdersView.class);
        UnifiedTemplate panel = page.findTemplate(OrdersPanel.class);

        panel.find(UnifiedSearchBar.class).find(UnifiedButton.class).withId("new").click();
        page.find(UnifiedDialog.class).withHeaderTitle("New order")
                .find(UnifiedButton.class).withText("Create").click();

        assertThat(panel.find(UnifiedSpan.class).withId("status")).hasText("Order created");
    }
    // --8<-- [end:entersNestedTemplates]

    @Test
    @DisplayName("Typing into a field in a Template runs the server's listener")
    void fillsAFieldInATemplate(UnifiedPage page) {
        page.navigate(OrdersView.class);
        UnifiedTemplate panel = page.findTemplate(OrdersPanel.class);

        panel.findTemplate(SearchBar.class).find(UnifiedTextField.class).withId("filter").setValue("open");

        assertThat(panel.find(UnifiedSpan.class).withId("status")).hasText("Filtered by open");
    }

    @Test
    @DisplayName("An id repeated in two Templates is compared within the Template entered")
    void sameIdInTwoTemplates(UnifiedPage page) {
        page.navigate(OrdersView.class);

        page.findTemplate(OrdersPanel.class).find(UnifiedButton.class).withId("new").click();

        assertThat(page.findTemplate(OrdersPanel.class).find(UnifiedSpan.class).withId("status"))
                .hasText("New customer");
    }

    @Test
    @DisplayName("A lookup doesn't enter a Template by itself")
    void lookupsDontEnterTemplates(UnifiedPage page) {
        page.navigate(OrdersView.class);

        assertThat(page.find(UnifiedButton.class).withId("new")).doesNotExist();
        assertThat(page.find(UnifiedTextField.class).withId("filter")).doesNotExist();
    }

    @Test
    @DisplayName("What the view adds to a Template is found inside it, once")
    void findsTheTemplatesLightDom(UnifiedPage page) {
        page.navigate(OrdersView.class);
        UnifiedTemplate panel = page.findTemplate(OrdersPanel.class);

        panel.find(UnifiedButton.class).withText("Help").click();

        assertThat(panel.find(UnifiedSpan.class).withId("status")).hasText("Help shown");
        assertThat(page.find(UnifiedButton.class).withText("Help")).exists();
    }

    @Test
    @DisplayName("Inside a Template, what has no id isn't found, and text written in the template is browser-only")
    void findsWhatTheServerKnows(UnifiedPage page) {
        page.navigate(OrdersView.class);
        UnifiedTemplate searchBar = page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class);

        assertThat(searchBar.find(UnifiedButton.class).withText("Not bound")).doesNotExist();
        if (page.hasRealBrowser()) {
            assertThat(searchBar.find(UnifiedButton.class).withText("New order")).exists();
        } else {
            assertThat(searchBar.find(UnifiedButton.class).withText("New order")).doesNotExist();
        }
    }

    @Test
    @DisplayName("A Template's element is its host: findDescendants stays in the light DOM, on every Testing Tool")
    void templateElementStaysInTheLightDom(UnifiedPage page) {
        page.navigate(OrdersView.class);
        UnifiedTemplate searchBar = page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class);

        assertEquals(List.of(), searchBar.element().findDescendants("vaadin-button"));
    }

    // Template HTML with no @Id (#617, .out-of-scope/shadow-piercing-lookups.md): give it an @Id, or reach it
    // through the Escape Hatch, in a test for that one Testing Tool.
    // --8<-- [start:templateHtmlEscapeHatch]
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("Playwright: the Template's Locator reaches HTML in its shadow root that has no @Id")
    void templateHtmlThroughPlaywright(UnifiedPage page) {
        page.navigate(OrdersView.class);
        UnifiedTemplate searchBar = page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class);

        Locator buttons = searchBar.unwrap(Locator.class).locator("vaadin-button"); // CSS enters open shadow roots
        assertEquals(List.of("New order", "Not bound"), buttons.allTextContents());
    }

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    @DisplayName("Selenium: the Template's TestBench element reaches HTML in its shadow root that has no @Id")
    void templateHtmlThroughSelenium(UnifiedPage page) {
        page.navigate(OrdersView.class);
        UnifiedTemplate searchBar = page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class);

        List<TestBenchElement> buttons = searchBar.unwrap(TestBenchElement.class).$("vaadin-button").all();
        assertEquals(List.of("New order", "Not bound"), buttons.stream().map(TestBenchElement::getText).toList());
    }
    // --8<-- [end:templateHtmlEscapeHatch]

    @Test
    @DisplayName("A lookup that finds nothing names the Templates where it would, and how to enter them")
    void failureNamesTheTemplate(UnifiedPage page) {
        page.navigate(OrdersView.class);
        page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class).describe(); // the test knows both classes

        UnifiedTextField filter = page.find(UnifiedTextField.class).withId("filter")
                .withTimeout(Duration.ofMillis(200));
        UnifiedAssertionError failure = assertThrows(UnifiedAssertionError.class, () -> filter.setValue("x"));

        String message = failure.getMessage();
        assertTrue(message.contains("text field (id “filter”) in Template <demo-search-bar> in Template"
                + " <demo-orders-panel>: findTemplate(OrdersPanel.class).findTemplate(SearchBar.class)"
                + ".find(UnifiedTextField.class).withId(\"filter\")"), message);
        assertTrue(message.contains("Did you mean  findTemplate(OrdersPanel.class).findTemplate(SearchBar.class)"
                + ".find(UnifiedTextField.class).withId(\"filter\")"), message);
    }

    @Test
    @DisplayName("The Findable View lists a Template as one line, with the lookup that enters it")
    void findableViewListsTemplates(UnifiedPage page) {
        page.navigate(OrdersView.class);

        String view = page.describe();

        assertTrue(view.contains("Template <demo-orders-panel>"), view);
        assertTrue(view.contains("page.findTemplate(<the class with @Tag(\"demo-orders-panel\")>.class)"), view);
        String inside = page.findTemplate(OrdersPanel.class).describe();
        assertTrue(inside.contains("Template <demo-search-bar>"), inside);
        assertTrue(inside.contains("span (id “status”)") || inside.contains("button “New customer”"), inside);
    }

    @Test
    @DisplayName("Inside a Template, the Findable View finds an @Id component by its id, even where a browser reads"
            + " the text the template writes")
    void findableViewSuggestsTheIdInsideATemplate(UnifiedPage page) {
        page.navigate(OrdersView.class);

        // --8<-- [start:describeTemplate]
        String view = page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class).describe();
        // text field “Filter”     find(UnifiedTextField.class).withId("filter")
        // button “New order”      find(UnifiedButton.class).withId("new")    (a browser reads the template's text)
        // button (no name; id “new”)  find(UnifiedButton.class).withId("new")    (Browserless doesn't)
        // --8<-- [end:describeTemplate]

        List<String> lines = view.lines().skip(1).map(l -> l.replaceAll(" {2,}", "  ")).toList();
        assertEquals(2, lines.size(), view);
        assertTrue(lines.get(0).endsWith("  find(UnifiedTextField.class).withId(\"filter\")"), view);
        assertTrue(lines.get(1).endsWith("  find(UnifiedButton.class).withId(\"new\")"), view);
        if (view.contains(" on browserless")) {
            assertEquals("button (no name; id “new”)  find(UnifiedButton.class).withId(\"new\")", lines.get(1));
        }
        String panel = page.findTemplate(OrdersPanel.class).describe();
        assertTrue(panel.contains("find(UnifiedButton.class).withId(\"new\")"), panel);
        assertTrue(panel.contains("find(UnifiedButton.class).withText(\"Help\")"), panel);
    }
}
