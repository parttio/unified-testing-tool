package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Selection fields; written once, runs on Browserless and Playwright. */
@UnifiedSpringBootTest
class OrderTest {

    // --8<-- [start:selectsByVisibleLabel]
    @Test
    void selectsByVisibleLabel(UnifiedPage page) {
        page.navigate(OrderView.class);
        UnifiedComboBox country = page.find(UnifiedComboBox.class).withLabel("Country");
        UnifiedRadioButtonGroup size = page.find(UnifiedRadioButtonGroup.class).withLabel("Size");
        UnifiedMultiSelectComboBox toppings = page.find(UnifiedMultiSelectComboBox.class).withLabel("Toppings");

        country.selectItem("Germany");
        size.selectItem("Large");
        toppings.selectItems("Olives", "Cheese");
        page.find(UnifiedButton.class).withText("Order").click();

        assertThat(page.find(UnifiedNotification.class))
                .hasText("Order: DE, LARGE, Cheese+Olives, coupon SPRING");
        assertThat(country).hasSelectedItem("Germany");
        assertThat(size).hasSelectedItem("Large");
        assertThat(toppings).hasSelectedItems("Cheese", "Olives");
    }
    // --8<-- [end:selectsByVisibleLabel]

    /** One group on the page: found without a filter, on every Testing Tool (#133). */
    @Test
    void findsTheOnlyRadioButtonGroupWithoutFilter(UnifiedPage page) {
        page.navigate(OrderView.class);
        assertThat(page.find(UnifiedRadioButtonGroup.class)).hasSelectedItem("Medium");
    }

    @Test
    void readsInitialSelection(UnifiedPage page) {
        page.navigate(OrderView.class);

        assertThat(page.find(UnifiedComboBox.class).withLabel("Country")).hasNoSelectedItem();
        assertThat(page.find(UnifiedComboBox.class).withLabel("Coupon")).hasSelectedItem("SPRING");
        assertThat(page.find(UnifiedRadioButtonGroup.class).withLabel("Size")).hasSelectedItem("Medium");
        assertThat(page.find(UnifiedMultiSelectComboBox.class).withLabel("Toppings")).hasNoSelectedItems();
        assertNull(page.find(UnifiedComboBox.class).withLabel("Country").getSelectedItem());
    }

    @Test
    void listsItems(UnifiedPage page) {
        page.navigate(OrderView.class);

        assertThat(page.find(UnifiedComboBox.class).withLabel("Country"))
                .hasItems("Finland", "Germany", "France");
        assertThat(page.find(UnifiedRadioButtonGroup.class).withLabel("Size"))
                .hasItems("Small", "Medium", "Large");
        assertEquals(List.of("Cheese", "Ham", "Mushrooms", "Olives"),
                page.find(UnifiedMultiSelectComboBox.class).withLabel("Toppings").getItems());
    }

    /** Every item, not only the first page the drop-down loads. */
    @Test
    @RequiresTestingTool({ TestingTool.BROWSERLESS, TestingTool.PLAYWRIGHT }) // Selenium: first page only (#127)
    void listsEveryItemOfALazyComboBox(UnifiedPage page) {
        page.navigate(OrderView.class);

        List<String> airports = page.find(UnifiedComboBox.class).withLabel("Airport").getItems();

        assertEquals(OrderView.AIRPORT_COUNT, airports.size());
        assertEquals("Airport 1", airports.get(0));
        assertEquals("Airport 120", airports.get(OrderView.AIRPORT_COUNT - 1));
    }

    @Test
    void deselectsAndClears(UnifiedPage page) {
        page.navigate(OrderView.class);
        UnifiedMultiSelectComboBox toppings = page.find(UnifiedMultiSelectComboBox.class).withLabel("Toppings");
        UnifiedComboBox coupon = page.find(UnifiedComboBox.class).withLabel("Coupon");
        UnifiedComboBox country = page.find(UnifiedComboBox.class).withLabel("Country");
        toppings.selectItems("Ham", "Mushrooms", "Olives");
        country.selectItem("France");

        toppings.deselectItems("Mushrooms", "Cheese"); // Cheese is not selected: ignored
        coupon.clear();   // clear button shown
        country.clear();  // no clear button

        assertThat(toppings).hasSelectedItems("Ham", "Olives");
        assertThat(coupon).hasNoSelectedItem();
        assertThat(country).hasNoSelectedItem();

        toppings.clear();
        page.find(UnifiedButton.class).withText("Order").click();
        assertThat(page.find(UnifiedNotification.class)).hasText("Order: -, MEDIUM, , coupon null");
    }

    @Test
    void unknownItemFailsWithTheAvailableItems(UnifiedPage page) {
        page.navigate(OrderView.class);
        UnifiedComboBox country = page.find(UnifiedComboBox.class).withLabel("Country");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> country.selectItem("Spain"));

        assertEquals("item “Spain” in combo box “Country”", e.getExpected());
        assertEquals(java.util.Set.of("item “Finland”", "item “Germany”", "item “France”"),
                java.util.Set.copyOf(e.getNearMisses()), e.getMessage());
    }
}
