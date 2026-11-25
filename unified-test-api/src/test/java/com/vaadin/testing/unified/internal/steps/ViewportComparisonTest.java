package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.ViewportSize;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Viewport Comparison (ADR 0011, #495): the same scenarios run at several Viewports on one Testing Tool, compared
 * with the widest, measurement point by measurement point.
 */
class ViewportComparisonTest {

    /** A {@code user-steps.json} of {@code editsAPerson} at a Viewport, with these steps. */
    static String scenario(String viewport, int width, int height, boolean fixed, String status, String steps) {
        return """
                {"schemaVersion": 1, "testClass": "demo.EditPersonTest", "testMethod": "editsAPerson",
                 "displayName": "Editing a person", "tool": "playwright", "status": "%s",
                 "viewport": {"name": "%s", "width": %d, "height": %d, "fixed": %s},
                 "startedAt": "2026-11-16T10:00:01Z", "durationMs": 1200, "stepCount": 2, "steps": [%s]}
                """.formatted(status, viewport, width, height, fixed, steps);
    }

    /** A User Step, measured when {@code layout} is not null. */
    static String step(int number, String text, String layout) {
        return "{\"type\": \"step\", \"index\": \"" + number + "\", \"number\": " + number + ", \"text\": \"" + text
                + "\", \"verb\": \"open\", \"status\": \"passed\"" + (layout == null ? "" : ", \"layout\": " + layout)
                + "}";
    }

    static String layout(int point, String findings, String components) {
        return "{\"point\": " + point + ", \"screenshot\": \"layout/00" + point + ".png\", \"findings\": [" + findings
                + "], \"components\": [" + components + "]}";
    }

    static String component(String line, String reach, String by) {
        return "{\"line\": \"" + line.replace("\"", "\\\"") + "\", \"reach\": \"" + reach + "\""
                + (by == null ? "" : ", \"by\": \"" + by + "\"") + "}";
    }

    static ScenarioResult parse(String json, String dir) {
        return ScenarioResult.parse(json, dir);
    }

    static final String DESKTOP_OPEN = step(1, "open People", layout(1, "",
            component("button “Save”", "visible", null) + "," + component("button “Menu”", "visible", null) + ","
                    + component("button “Export”", "visible", null)));

    @Test
    void theWidestRunIsTheReferenceAndEachPointSaysWhatIsLostAtEverySize() {
        ScenarioResult desktop = parse(scenario("desktop", 1280, 720, false, "passed",
                DESKTOP_OPEN + "," + step(2, "click button “Save”", null)), "desktop/EditPersonTest/editsAPerson/");
        ScenarioResult phone = parse(scenario("phone", 375, 667, false, "failed", step(1, "open People", layout(1,
                "{\"kind\": \"scroll\", \"text\": \"the page scrolls sideways by 40 px\", \"box\": [0, 0, 375, 667]}",
                component("button “Save”", "visible", null) + "," + component("button “Menu”", "hidden",
                        "the AppLayout drawer"))) + "," + step(2, "click button “Save”", null)),
                "phone/EditPersonTest/editsAPerson/");

        ViewportComparison comparison = ViewportComparison.of("playwright", List.of(phone, desktop));

        assertEquals(new ViewportSize("desktop", 1280, 720), comparison.reference());
        assertEquals(List.of(new ViewportSize("desktop", 1280, 720), new ViewportSize("phone", 375, 667)),
                comparison.sizes());
        ViewportComparison.Scenario editing = comparison.scenarios().get(0);
        assertEquals(Map.of("desktop 1280 × 720", "passed", "phone 375 × 667", "failed"), editing.status());
        ViewportComparison.AtSize atPhone = editing.points().get(0).atSize().get("phone 375 × 667");
        assertEquals(List.of("the page scrolls sideways by 40 px"), atPhone.findings());
        // in the reference's order: Save, Menu, Export
        assertEquals(List.of(new ViewportComparison.Lost("button “Menu”", "hidden in the AppLayout drawer", true),
                new ViewportComparison.Lost("button “Export”", "missing", false)), atPhone.lost());
        assertEquals("phone/EditPersonTest/editsAPerson/layout/001.png", atPhone.screenshot());
    }
}
