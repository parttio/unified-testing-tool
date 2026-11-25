package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Auto-Wait waits for a busy Flow client before acting only for a while (#604, ADR 0014): an app whose client stays
 * busy, such as one with a short {@code @Poll} interval, can still be driven.
 */
@UnifiedSpringBootTest
class BusyFlowClientTest {

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user types into a field while the app stays busy")
    void actsWhenFlowStaysBusy(UnifiedPage page) {
        page.navigate(SlowLoadView.class);
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
        assertThat(name).isVisible();

        // a Flow client that is busy until the field gets input: only an action that doesn't wait for it ends it
        page.unwrap(Page.class).evaluate("""
                () => {
                  const field = document.querySelector('vaadin-text-field');
                  let busy = true;
                  field.addEventListener('input', () => busy = false);
                  window.Vaadin.Flow.clients.busyForever = { isActive: () => busy };
                }""");
        name.setValue("Typed while busy");

        assertThat(name).hasValue("Typed while busy");
    }
}
