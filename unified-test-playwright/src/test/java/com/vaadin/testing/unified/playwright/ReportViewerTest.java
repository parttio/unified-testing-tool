package com.vaadin.testing.unified.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.vaadin.testing.unified.internal.steps.RunReport;
import com.vaadin.testing.unified.internal.steps.RunSummary;
import com.vaadin.testing.unified.internal.steps.ScenarioResult;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code report.html} in a browser: a step's screenshot opens in the viewer with its Step Target spotlighted. */
class ReportViewerTest {

    /** Two steps with screenshots, one boxed and one whose target is gone, and a boxed failure screenshot. */
    private static final String SCENARIO = """
            {
              "schemaVersion": 1,
              "testClass": "demo.CheckoutTest",
              "testMethod": "pays",
              "displayName": "A guest pays",
              "tool": "playwright",
              "status": "failed",
              "startedAt": "2026-11-20T10:00:00Z",
              "durationMs": 2000,
              "stepCount": 2,
              "failure": {
                "type": "java.lang.AssertionError",
                "message": "no receipt",
                "stepIndex": "2",
                "stepNumber": 2,
                "codePath": [],
                "highlight": {"x": 0.1, "y": 0.25, "width": 0.3, "height": 0.05}
              },
              "steps": [
                {"type": "step", "index": "1", "number": 1, "text": "open Checkout", "verb": "open",
                 "status": "passed", "durationMs": 10, "snapshot": "steps/001.png",
                 "highlight": {"x": 0.5, "y": 0.125, "width": 0.25, "height": 0.0625}},
                {"type": "step", "index": "2", "number": 2, "text": "click button “Pay”", "verb": "click",
                 "status": "failed", "durationMs": 1800, "error": "AssertionError: no receipt",
                 "snapshot": "steps/002.png", "highlight": {"missing": "gone"}}
              ]
            }
            """;

    private static Playwright playwright;
    private static Browser browser;
    private Page page;

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void close() {
        playwright.close();
    }

    @BeforeEach
    void open(@TempDir Path dir) throws Exception {
        Path scenarioDir = dir.resolve("CheckoutTest/pays");
        Files.createDirectories(scenarioDir.resolve("steps"));
        png(scenarioDir.resolve("steps/001.png"));
        png(scenarioDir.resolve("steps/002.png"));
        png(dir.resolve("demo.CheckoutTest.pays.png"));
        ScenarioResult scenario = ScenarioResult.parse(SCENARIO, "CheckoutTest/pays/");
        String html = new RunReport(new RunSummary("Playwright", List.of(scenario), 0, null),
                s -> List.of("demo.CheckoutTest.pays.png")).html();
        Files.writeString(dir.resolve("report.html"), html);
        page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1200, 900));
        page.navigate(dir.resolve("report.html").toUri().toString());
    }

    private static void png(Path file) throws Exception {
        ImageIO.write(new BufferedImage(800, 480, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
    }

    @Test
    void aStepsScreenshotOpensInTheViewerWithItsStepTargetSpotlighted() {
        page.locator("a.snap[data-image]").first().click();
        Locator viewer = page.locator("#viewer");
        assertTrue(viewer.isVisible());
        assertTrue(viewer.locator("header").textContent().contains("open Checkout"));
        assertSpotAt(viewer, 0.5, 0.125, 0.25, 0.0625);

        page.keyboard().press("ArrowRight");
        assertTrue(viewer.locator("header").textContent().contains("click button “Pay”"));
        assertFalse(viewer.locator(".spot").isVisible(), "no box when the target is gone");
        assertEquals("No box: the component is no longer on the page.", viewer.locator(".note").textContent());

        page.keyboard().press("ArrowRight");
        assertTrue(viewer.locator("header").textContent().contains("✘"), "the failure's frame comes last");
        assertSpotAt(viewer, 0.1, 0.25, 0.3, 0.05);

        page.keyboard().press("Escape");
        assertFalse(viewer.isVisible());
    }

    @Test
    void theSwitchTurnsEverySpotlightOffAndHFlipsIt() {
        Locator inline = page.locator(".evidence .spot");
        assertTrue(inline.isVisible(), "the failure's screenshot shows its spotlight inline");

        page.locator("#highlight").uncheck();
        assertFalse(inline.isVisible());

        page.locator(".evidence a.shot").click(); // opens the viewer on the failure's frame
        Locator viewer = page.locator("#viewer");
        assertFalse(viewer.locator(".spot").isVisible(), "the viewer follows the switch");
        page.keyboard().press("h");
        assertTrue(viewer.locator(".spot").isVisible());
        assertTrue(page.locator("#highlight").isChecked(), "h flips the same switch");
    }

    /** The spotlight sits at these fractions of the viewer's image, give or take a pixel. */
    private static void assertSpotAt(Locator viewer, double x, double y, double width, double height) {
        BoundingBox image = viewer.locator(".stage img").boundingBox();
        BoundingBox spot = viewer.locator(".spot").boundingBox();
        assertEquals(image.x + x * image.width, spot.x, 1.5, "left");
        assertEquals(image.y + y * image.height, spot.y, 1.5, "top");
        assertEquals(width * image.width, spot.width, 1.5, "width");
        assertEquals(height * image.height, spot.height, 1.5, "height");
    }
}
