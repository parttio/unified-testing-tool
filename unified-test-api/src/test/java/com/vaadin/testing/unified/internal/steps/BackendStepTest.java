package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** {@code page.backendStep(..)}: work outside the UI, shown among the User Steps but not one of them (ADR 0023). */
public class BackendStepTest {

    private final FakePage fake = new FakePage().add("vaadin-button", "text", "Place order");

    private UserStepsReport report(String status, AssertionError failure) {
        fake.steps.finish();
        return new UserStepsReport("demo.OrderTest", "placesAnOrder", "Placing an order stores it", "browserless",
                status, failure, fake.steps);
    }

    @Test
    void theReportsShowABackendStepAmongTheUserStepsWithoutCountingIt() {
        UnifiedPage page = fake.page;
        page.find(UnifiedButton.class).withText("Place order").click();
        page.backendStep("order is stored", () -> { });

        List<String> lines = report("passed", null).text().lines().toList();

        assertEquals(List.of("   1  click button “Place order”", "   2  backend: order is stored"),
                lines.subList(1, 3));
    }

    @Test
    void jsonWritesABackendEntryWithTheUserStepsCalledInsideIt() throws Exception {
        UnifiedPage page = fake.page;
        page.backendStep("order is stored", () -> page.find(UnifiedButton.class).withText("Place order").click());

        JsonNode run = new ObjectMapper().readTree(report("passed", null).json());
        JsonNode backend = run.get("steps").get(0);

        assertEquals("backend", backend.get("type").asString());
        assertEquals("1", backend.get("index").asString());
        assertEquals("order is stored", backend.get("name").asString());
        assertEquals("backend: order is stored", backend.get("text").asString());
        assertEquals("passed", backend.get("status").asString());
        assertEquals("click button “Place order”", backend.get("steps").get(0).get("text").asString());
        assertEquals(1, run.get("stepCount").asInt(), "the Backend Step is no User Step");
    }

    @Test
    void workThatThrowsFailsTheBackendStepAndTheTest() throws Exception {
        AssertionError thrown = new AssertionError("expected 1 order, found 0");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> fake.page.backendStep("order is stored", () -> { throw thrown; }));
        assertSame(thrown, failure);

        UserStepsReport report = report("failed", failure);
        assertEquals("   1  backend: order is stored   ✘", report.text().lines().toList().get(1));
        JsonNode backend = new ObjectMapper().readTree(report.json()).get("steps").get(0);
        assertEquals("failed", backend.get("status").asString());
    }

    @Test
    void theRunSummaryReadsABackendStepBackAsTheStepListShowsIt() {
        UnifiedPage page = fake.page;
        page.backendStep("order is stored", () -> page.find(UnifiedButton.class).withText("Place order").click());
        AssertionError failure = assertThrows(AssertionError.class,
                () -> page.backendStep("mail is sent", () -> { throw new AssertionError("no mail"); }));

        ScenarioResult read = ScenarioResult.parse(report("failed", failure).json(), "OrderTest/placesAnOrder/");

        assertEquals("""
                1  backend: order is stored
                   1.1  click button “Place order”
                2  backend: mail is sent   ✘
                """, read.stepsText());
        assertEquals(List.of("backend: order is stored", "   click button “Place order”", "backend: mail is sent"),
                read.stepLines());
    }
}
