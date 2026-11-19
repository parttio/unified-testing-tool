package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import java.time.Duration;

/**
 * The Bad and Good pairs of docs/guide/common-mistakes.md. CI compiles them but doesn't run them; the Bad ones are
 * mistakes on purpose. The class has no test methods, so the style rules (which would fail the Bad ones) skip it.
 */
final class CommonMistakes {

    private CommonMistakes() {
    }

    static void getterBad(UnifiedTextField name) {
        // --8<-- [start:getterBad]
        assertEquals("Alice", name.getValue());          // reads once: fails if the value arrives a moment later
        // --8<-- [end:getterBad]
    }

    static void getterGood(UnifiedTextField name) {
        // --8<-- [start:getterGood]
        assertThat(name).hasValue("Alice");              // retries until the timeout, and is a `see` User Step
        // --8<-- [end:getterGood]
    }

    static void countBad(UnifiedGrid people, UnifiedButton add) {
        // --8<-- [start:countBad]
        int before = people.getRowCount();
        add.click();
        assertEquals(before + 1, people.getRowCount());
        // --8<-- [end:countBad]
    }

    static void countGood(UnifiedGrid people, UnifiedButton add) {
        // --8<-- [start:countGood]
        int before = people.getRowCount();               // a read is fine for a value you need
        add.click();
        assertThat(people).hasRowCount(before + 1);      // the check retries
        // --8<-- [end:countGood]
    }

    static void sleepBad(UnifiedButton save, UnifiedNotification notification) throws InterruptedException {
        // --8<-- [start:sleepBad]
        save.click();
        Thread.sleep(2000);                              // too long on Browserless, maybe too short on CI
        assertThat(notification).hasText("Saved Alice");
        // --8<-- [end:sleepBad]
    }

    static void sleepGood(UnifiedButton save, UnifiedNotification notification) {
        // --8<-- [start:sleepGood]
        save.click();                                    // waits for the server's answer (Auto-Wait)
        assertThat(notification).hasText("Saved Alice"); // waits until it holds, up to the timeout
        // --8<-- [end:sleepGood]
    }

    static void timeoutBad(UnifiedPage page) {
        // --8<-- [start:timeoutBad]
        UnifiedNotification saved = (UnifiedNotification) page.find(UnifiedNotification.class)
                .withTimeout(Duration.ofSeconds(60));    // it failed once at 10 s
        assertThat(saved).hasText("Saved Alice");
        // --8<-- [end:timeoutBad]
    }

    static void firstOfSeveralBad(UnifiedPage page) {
        // --8<-- [start:firstOfSeveralBad]
        page.find(UnifiedButton.class).withText("Delete").all().get(0).click();   // which one is first?
        // --8<-- [end:firstOfSeveralBad]
    }

    static void firstOfSeveralGood(UnifiedPage page) {
        // --8<-- [start:firstOfSeveralGood]
        page.find(UnifiedGrid.class).row().withCellText("Name", "Alice").cell("Actions")
                .find(UnifiedButton.class).withText("Delete").click();             // the one in Alice's row
        // --8<-- [end:firstOfSeveralGood]
    }

    static void testIdBad(UnifiedPage page) {
        // --8<-- [start:testIdBad]
        page.find(UnifiedTextField.class).withTestId("name-field").setValue("Alice");
        // --8<-- [end:testIdBad]
    }

    static void testIdGood(UnifiedPage page) {
        // --8<-- [start:testIdGood]
        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");   // what the user reads
        // --8<-- [end:testIdGood]
    }

    static void noCheckBad(UnifiedPage page) {
        // --8<-- [start:noCheckBad]
        page.navigate(ProfileView.class);
        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");
        page.find(UnifiedButton.class).withText("Save").click();
        // passes whether or not the save works
        // --8<-- [end:noCheckBad]
    }

    static void noCheckGood(UnifiedPage page) {
        // --8<-- [start:noCheckGood]
        page.navigate(ProfileView.class);
        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");
        page.find(UnifiedButton.class).withText("Save").click();
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alice");  // what the user sees after
        // --8<-- [end:noCheckGood]
    }

    static void escapeHatchBad(UnifiedPage page) {
        // --8<-- [start:escapeHatchBad]
        page.unwrap(Page.class).reload();                // Playwright only: fails on Browserless and Selenium
        // --8<-- [end:escapeHatchBad]
    }

    static void escapeHatchGood(UnifiedPage page) {
        // --8<-- [start:escapeHatchGood]
        page.reload();                                   // the Unified API has it, on every Testing Tool
        // --8<-- [end:escapeHatchGood]
    }
}
