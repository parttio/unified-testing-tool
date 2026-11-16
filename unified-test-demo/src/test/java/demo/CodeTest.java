package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** setValueAsUser on a native input: the server sees the value on every Testing Tool. */
@UnifiedSpringBootTest
class CodeTest {

    @Test
    @DisplayName("The user types a code into a plain Flow input")
    void typesIntoFlowInput(UnifiedPage page) {
        page.navigate(CodeView.class);

        page.findComponent(Input.class).withTestId("code").element().setValueAsUser("ABC");

        assertThat(page.findComponent(Span.class).withId("code-value")).hasText("ABC");
    }

    @Test
    @DisplayName("The user types into a bare HTML input element")
    void typesIntoBareInputElement(UnifiedPage page) {
        page.navigate(CodeView.class);
        UnifiedContainer holder = page.find(UnifiedContainer.class).withTestId("raw");

        holder.element().findChildren("input").get(0).setValueAsUser("XYZ");

        assertThat(page.findComponent(Span.class).withId("raw-value")).hasText("XYZ");
    }
}
