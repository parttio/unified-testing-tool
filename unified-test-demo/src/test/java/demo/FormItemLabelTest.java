package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** withLabel(..) matches a FormLayout item's label, on every Testing Tool (#93). */
@UnifiedSpringBootTest
class FormItemLabelTest {

    // --8<-- [start:formItemLabel]
    @Test
    @DisplayName("The user signs up with a nickname and a plan in a form of labelled items")
    void findsFieldsByTheirFormItemLabel(UnifiedPage page) {
        page.navigate(SignupView.class);

        page.find(UnifiedTextField.class).withLabel("Nickname").setValue("Ada");
        page.find(UnifiedComboBox.class).withLabel("Plan").selectItem("Pro");
        page.find(UnifiedButton.class).withText("Sign up").click();

        assertThat(page.find(UnifiedSpan.class).withId("welcome")).hasText("Welcome Ada on Pro");
    }
    // --8<-- [end:formItemLabel]

    @Test
    @DisplayName("A part of a form item's label finds its field too")
    void findsAFieldByPartOfItsFormItemLabel(UnifiedPage page) {
        page.navigate(SignupView.class);

        assertThat(page.find(UnifiedTextField.class).withLabelContaining("nick")).exists();
        assertThat(page.find(UnifiedTextField.class).withLabel("Plan")).doesNotExist();
    }

    @Test
    @DisplayName("A misspelled label fails, naming the field by its form item's label")
    void aMisspelledLabelNamesTheFormItemLabel(UnifiedPage page) {
        page.navigate(SignupView.class);
        UnifiedTextField nickname = page.find(UnifiedTextField.class).withLabel("Nick name")
                .withTimeout(Duration.ofMillis(500));

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> nickname.setValue("Ada"));

        assertEquals(List.of("text field “Nickname”"), e.getNearMisses(), e.getMessage());
    }
}
