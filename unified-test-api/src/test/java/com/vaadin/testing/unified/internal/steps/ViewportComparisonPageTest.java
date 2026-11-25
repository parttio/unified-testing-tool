package com.vaadin.testing.unified.internal.steps;

import static com.vaadin.testing.unified.internal.steps.ViewportComparisonTest.component;
import static com.vaadin.testing.unified.internal.steps.ViewportComparisonTest.layout;
import static com.vaadin.testing.unified.internal.steps.ViewportComparisonTest.parse;
import static com.vaadin.testing.unified.internal.steps.ViewportComparisonTest.scenario;
import static com.vaadin.testing.unified.internal.steps.ViewportComparisonTest.step;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code viewport-comparison.html} (#495; the prototype's variant B on {@code prototype/viewport-comparison-report}):
 * a matrix of scenarios against Viewports, a detail panel per cell, the User Steps behind a toggle.
 */
class ViewportComparisonPageTest {

    private static String page() {
        String desktopOpen = step(1, "open People", layout(1, "",
                component("button “Save”", "visible", null) + "," + component("button “Menu”", "visible", null) + ","
                        + component("button “Export”", "visible", null)));
        ScenarioResult desktop = parse(scenario("desktop", 1280, 720, false, "passed", desktopOpen + ","
                + step(2, "click side nav item “Plan”", null)), "/runs/desktop/EditPersonTest/editsAPerson/");
        ScenarioResult phone = parse(scenario("phone", 375, 667, false, "failed", step(1, "open People", layout(1,
                "{\"kind\": \"scroll\", \"text\": \"the page scrolls sideways by 40 px\", \"box\": [0, 0, 375, 667]}",
                component("button “Save”", "visible", null) + "," + component("button “Menu”", "hidden",
                        "the AppLayout drawer"))) + "," + step(2, "open the drawer", null)),
                "/runs/phone/EditPersonTest/editsAPerson/");
        return ViewportComparisonReport.html(ViewportComparison.of("playwright", List.of(desktop, phone)),
                Path.of("/runs/comparison"));
    }

    @Test
    void theMatrixHasACellPerScenarioAndViewportThatOpensItsDetail() {
        String html = page();

        assertTrue(html.contains("<table class=\"matrix\">"), html);
        assertTrue(html.contains("<td class=\"cell\" data-detail=\"s0-1\"><span class=\"pill x\">✘ fails</span>"
                + "<span class=\"pill f\">1 ⚠</span><span class=\"pill l\">1 lost</span>"
                + "<span class=\"pill n\">⑂ parts at step 2</span></td>"), html);
    }

    @Test
    void aCellsDetailShowsBothScreenshotsTheFindingsAndWhatIsLostOrExplained() {
        String html = page();

        assertTrue(html.contains("<section class=\"detail\" id=\"s0-1\" hidden>"), html);
        assertTrue(html.contains("<img src=\"../desktop/EditPersonTest/editsAPerson/layout/001.png\""), html);
        assertTrue(html.contains("<img src=\"../phone/EditPersonTest/editsAPerson/layout/001.png\""), html);
        assertTrue(html.contains("<div class=\"finding\">⇿ the page scrolls sideways by 40 px</div>"), html);
        assertTrue(html.contains("<div class=\"lost\">− button “Export” <small>missing, needs review</small></div>"), html);
        assertTrue(html.contains("<div class=\"lost explained\">◌ button “Menu” <small>hidden in the AppLayout drawer"
                + "</small></div>"), html);
    }

    @Test
    void theUserStepsAreBehindAToggleOnceAndForkWhereTheRunsPart() {
        String html = page();

        assertTrue(html.contains("<details class=\"steps\"><summary>User Steps</summary>"), html);
        assertFalse(html.contains("<details class=\"steps\" open>"), html);
        assertTrue(html.contains("<li>open People"), html);
        assertTrue(html.contains("<div class=\"fork\"><div><h4>desktop 1280 × 720</h4><ol start=\"2\">"
                + "<li>click side nav item “Plan”</li></ol></div><div><h4>phone 375 × 667</h4><ol start=\"2\">"
                + "<li>open the drawer</li></ol></div></div>"), html);
    }
}
