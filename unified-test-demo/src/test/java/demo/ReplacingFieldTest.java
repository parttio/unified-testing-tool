package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A pick in a field the app replaces as it takes the value goes through, on every Testing Tool (#377). */
@UnifiedSpringBootTest
class ReplacingFieldTest {

    @Test
    @DisplayName("The user picks a product in a field the app replaces with one holding the pick")
    void picksInAFieldTheAppReplaces(UnifiedPage page) {
        page.navigate(ReplacingFieldView.class);

        page.find(UnifiedComboBox.class).withAttribute("data-generation", "0").selectItem("Bun");

        assertThat(page.find(UnifiedComboBox.class).withAttribute("data-generation", "1")).hasSelectedItem("Bun");
    }
}
