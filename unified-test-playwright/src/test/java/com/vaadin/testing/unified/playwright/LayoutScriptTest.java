package com.vaadin.testing.unified.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.vaadin.testing.unified.spi.LayoutScript;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The layout script (ADR 0011) in a real Chromium, on small static pages at 375 × 667: the findings and the Reach of
 * each match it returns for the targets it is given. The fixtures and their tricky cases come from the prior-art
 * research (docs/research/layout-script-prior-art.md, §5).
 */
class LayoutScriptTest {

    static Playwright playwright;
    static Browser browser;
    Page page;

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
    void open() {
        page = browser.newPage(new Browser.NewPageOptions().setViewportSize(375, 667));
    }

    @AfterEach
    void closePage() {
        page.close();
    }

    private JsonObject measure(String body, LayoutScript.Target... targets) {
        page.setContent("<!doctype html><html><head><style>body{margin:0;font:16px sans-serif}</style></head><body>"
                + body + "</body></html>");
        String json = (String) page.evaluate(LayoutScript.SOURCE, LayoutScript.argument(List.of(targets)));
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonArray matches(JsonObject result, int target) {
        return result.getAsJsonArray("matches").get(target).getAsJsonArray();
    }

    // ---------------------------------------------------------------- clean

    @Test
    void aCleanPageHasNoFindingsAndEveryMatchIsVisibleInDocumentOrder() {
        JsonObject result = measure("""
                <div style="display:flex;flex-wrap:wrap">
                  <button style="width:100.4px">One</button><button style="width:100.4px">Two</button>
                </div>
                <p>Text</p><button style="width:120px;height:30px">Three</button>
                """, LayoutScript.Target.interactive("button"), LayoutScript.Target.of("p"));

        assertEquals("[]", result.getAsJsonArray("findings").toString());
        JsonArray buttons = matches(result, 0);
        assertEquals(3, buttons.size());
        assertEquals("visible", buttons.get(0).getAsJsonObject().get("reach").getAsString());
        assertEquals("visible", buttons.get(2).getAsJsonObject().get("reach").getAsString());
        assertEquals("[0,0,100,0]", roundedXyW(buttons.get(0).getAsJsonObject()));
        assertEquals(1, matches(result, 1).size());
    }

    // ---------------------------------------------------------------- sideways scroll

    @Test
    void aPageWiderThanTheViewportScrollsSidewaysByTheDifference() {
        JsonObject result = measure("<div style='width:700px;height:20px'>wide</div>");

        assertEquals("[{\"kind\":\"scroll\",\"container\":\"the page\",\"by\":325}]",
                result.getAsJsonArray("findings").toString());
    }

    @Test
    void lessThanAPixelTooWideIsNoSidewaysScroll() {
        JsonObject result = measure("<div style='width:375.6px;height:20px'>almost</div>");

        assertEquals("[]", result.getAsJsonArray("findings").toString());
    }

    @Test
    void aPageThatHidesItsSidewaysOverflowDoesNotScroll() {
        JsonObject result = measure("<style>body{overflow-x:hidden}</style><div style='width:700px;height:20px'>wide</div>");

        assertEquals("[]", result.getAsJsonArray("findings").toString());
    }

    /** x, y, width of a box, rounded, and 0 for the height so fonts don't matter. */
    private static String roundedXyW(JsonObject match) {
        JsonArray box = match.getAsJsonArray("box");
        return "[" + Math.round(box.get(0).getAsDouble()) + "," + Math.round(box.get(1).getAsDouble()) + ","
                + Math.round(box.get(2).getAsDouble()) + ",0]";
    }
}
