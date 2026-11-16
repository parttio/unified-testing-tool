package com.vaadin.testing.unified.internal.steps;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The failures of lookups, actions and assertions, with Near Misses, on a fake page. */
class LookupFailureMessagesTest {

    private final FakePage fake = new FakePage();
    private final UnifiedPage page = fake.page;

    @Test
    void aRenamedLabelListsNearMissesAndSuggestsTheClosest() {
        fake.add("vaadin-text-field", "label", "Name").add("vaadin-text-field", "label", "E-mail")
                .add("vaadin-combo-box", "label", "Role");
        fake.steps.describeTest("Editing a person updates the grid", "target/evidence/EditPersonTest/edits/");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> page.step("Edit Alan Turing",
                () -> page.find(UnifiedTextField.class).withLabel("Email").setValue("alan@bletchley.uk")));

        String[] lines = e.getMessage().split("\n");
        assertEquals(List.of(
                "Editing a person updates the grid",
                "  Failed at     User Step 1 of 1: fill text field “Email” with “alan@bletchley.uk”",
                "  In step       Edit Alan Turing",
                "  Expected      exactly one text field labelled “Email”",
                "  Found         none",
                "  Nearby        text field “E-mail”, text field “Name”",
                "  Did you mean  find(UnifiedTextField.class).withLabel(\"E-mail\")",
                "  Waited        200 ms (vaadin.testing.timeout)",
                "  Tool          browserless"), List.of(lines).subList(0, 9));
        assertTrue(lines[9].matches("  Code          LookupFailureMessagesTest\\.java:\\d+ \\(aRenamed\\w+\\)"
                + " ← LookupFailureMessagesTest\\.java:\\d+ \\(aRenamed\\w+\\)"), lines[9]);
        assertEquals("  Evidence      target/evidence/EditPersonTest/edits/", lines[10]);
        assertEquals(11, lines.length, e.getMessage());
    }

    @Test
    void suggestsAnotherKindWithTheExactName() {
        fake.add("vaadin-text-field", "label", "Name").add("vaadin-combo-box", "label", "Role");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> page.find(UnifiedTextField.class).withLabel("Role").setValue("Admin"));

        assertEquals(List.of("text field “Name”", "combo box “Role”"), e.getNearMisses());
        assertEquals(Optional.of("find(UnifiedComboBox.class).withLabel(\"Role\")"), e.getSuggestion());
    }

    @Test
    void severalMatchesAreListedWithoutASuggestion() {
        fake.add("vaadin-button", "text", "Save").add("vaadin-button", "text", "Save");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> page.find(UnifiedButton.class).withText("Save").click());

        assertEquals("exactly one button with text “Save”", e.getExpected());
        assertEquals("2: button “Save”, button “Save”", e.getFound());
        assertEquals(List.of(), e.getNearMisses());
        assertEquals(Optional.empty(), e.getSuggestion());
    }

    @Test
    void aDisabledButtonIsFoundButNotUsable() {
        fake.add("vaadin-button", "text", "Save", "enabled", "false");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> page.find(UnifiedButton.class).withText("Save").click());

        assertEquals("a visible and enabled button with text “Save”", e.getExpected());
        assertEquals("1, but disabled", e.getFound());
    }

    @Test
    void anAssertionTimeoutListsTheValuesSeenWhileWaiting() {
        fake.add("vaadin-notification", "text", "Saving…");
        new Thread(() -> {
            try {
                Thread.sleep(80);
            } catch (InterruptedException ignored) {
                return;
            }
            fake.components.get(0).put("text", "Saved Alan Turing");
        }).start();

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing"));

        assertEquals("notification to have text “Saved Alan M. Turing”", e.getExpected());
        assertEquals("“Saving…”, then “Saved Alan Turing”", e.getFound());
        assertEquals("see notification “Saved Alan M. Turing”", e.getFailedStep().orElseThrow());
    }
}
