package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.TemplateScope;
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
    @DisplayName("A lookup that finds nothing names the Templates where it would, and how to enter them")
    void failureNamesTheTemplate(UnifiedPage page) {
        page.navigate(OrdersView.class);
        page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class).describe(); // the test knows both classes

        UnifiedTextField filter = (UnifiedTextField) page.find(UnifiedTextField.class).withId("filter")
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
}
