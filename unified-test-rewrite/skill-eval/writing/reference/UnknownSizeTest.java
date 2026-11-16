package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class UnknownSizeTest {

    @Test
    @DisplayName("Choosing a size the order doesn't offer fails, listing the sizes there are")
    void unknownSizeFailsWithTheOfferedSizes(UnifiedPage page) {
        // given
        page.navigate(OrderView.class);
        UnifiedRadioButtonGroup size = page.find(UnifiedRadioButtonGroup.class).withLabel("Size");

        // when
        UnifiedAssertionError failure = assertThrows(UnifiedAssertionError.class, () -> size.selectItem("Extra large"));

        // then
        assertEquals(Set.of("item “Small”", "item “Medium”", "item “Large”"), Set.copyOf(failure.getNearMisses()),
                failure.getMessage());
    }
}
