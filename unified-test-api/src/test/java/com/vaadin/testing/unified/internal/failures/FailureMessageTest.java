package com.vaadin.testing.unified.internal.failures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.UnifiedAssertionError;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The layout of a Unified failure's message (spec chapter 12, "Failures"). */
class FailureMessageTest {

    @Test
    void listsEveryPartWithFixedLabelsInAFixedOrder() {
        int[] steps = { 5 };
        FailureParts parts = FailureParts.of("exactly one text field labelled “Email” in dialog “Edit person”", "none")
                .nearMisses(List.of("text field “E-mail”", "text field “Name”", "combo box “Email”"))
                .suggestion("find(UnifiedTextField.class).withLabel(\"E-mail\")")
                .waited(Duration.ofSeconds(10), "vaadin.testing.timeout")
                .tool("playwright (chromium)");
        parts.failedStep(5, () -> steps[0], "fill text field “Email” with “alan@bletchley.uk”");
        parts.stepChain(List.of("Edit Alan Turing", "Fill in the form"));
        parts.codePath(List.of("EditPersonTest.java:13 (editsAPersonInTheDialog)", "EditPersonTest.java:24 (editPerson)"));
        parts.scenario("Editing a person in the dialog updates the grid");
        parts.evidenceDir("target/surefire-reports/unified-evidence/EditPersonTest/editsAPersonInTheDialog/");
        UnifiedAssertionError error = new UnifiedAssertionError(parts, null);

        steps[0] = 10; // the test went on (assertThrows) or ended: the total is read when the message is
        assertEquals("""
                Editing a person in the dialog updates the grid
                  Failed at     User Step 5 of 10: fill text field “Email” with “alan@bletchley.uk”
                  In step       Edit Alan Turing › Fill in the form
                  Expected      exactly one text field labelled “Email” in dialog “Edit person”
                  Found         none
                  Nearby        text field “E-mail”, text field “Name”, combo box “Email”
                  Did you mean  find(UnifiedTextField.class).withLabel("E-mail")
                  Waited        10 s (vaadin.testing.timeout)
                  Tool          playwright (chromium)
                  Code          EditPersonTest.java:13 (editsAPersonInTheDialog) ← EditPersonTest.java:24 (editPerson)
                  Evidence      target/surefire-reports/unified-evidence/EditPersonTest/editsAPersonInTheDialog/""",
                error.getMessage());
        assertEquals("Expected exactly one text field labelled “Email” in dialog “Edit person”, found none",
                error.getSummary());
        assertEquals(Optional.of(5), error.getFailedStepNumber());
        assertEquals(Optional.of(10), error.getStepCount());
        assertEquals(List.of("Edit Alan Turing", "Fill in the form"), error.getStepChain());
        assertEquals(Optional.of("find(UnifiedTextField.class).withLabel(\"E-mail\")"), error.getSuggestion());
        assertEquals(Optional.of(Duration.ofSeconds(10)), error.getWaited());
        assertEquals("playwright (chromium)", error.getTool());
    }

    @Test
    void leavesOutLinesWithNothingToSayAndStartsWithTheSummaryWithoutAScenario() {
        FailureParts parts = FailureParts.of("button “Save” to be enabled", "false")
                .waited(Duration.ofMillis(1500), "withTimeout").tool("browserless");

        assertEquals("""
                Expected button “Save” to be enabled, found false
                  Expected      button “Save” to be enabled
                  Found         false
                  Waited        1.5 s (withTimeout)
                  Tool          browserless""", new UnifiedAssertionError(parts, null).getMessage());
    }

    @Test
    void masksSecretValuesInEveryPart() {
        FailureParts parts = FailureParts.of("password field to have value “hunter2”", "“hunter”");
        parts.mask(text -> text.replace("hunter2", "••••"));

        assertEquals("Expected password field to have value “••••”, found “hunter”",
                new UnifiedAssertionError(parts, null).getSummary());
    }

    @Test
    void listsTheValuesSeenWhileWaiting() {
        assertEquals("“Saving…”, then “Saved Alan Turing”", FailureMessage.sequence(List.of("“Saving…”",
                "“Saved Alan Turing”")));
        assertEquals("1, then …, then 4, then 5, then 6", FailureMessage.sequence(List.of("1", "2", "3", "4", "5", "6")));
        assertEquals("300 ms", FailureMessage.duration(Duration.ofMillis(300)));
        assertEquals("10 s", FailureMessage.duration(Duration.ofSeconds(10)));
    }
}
