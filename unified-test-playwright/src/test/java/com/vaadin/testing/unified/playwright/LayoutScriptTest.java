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

    @Test
    void aContainerThatScrollsSidewaysIsNamedWithHowFar() {
        JsonObject result = measure("""
                <div id="scroller" style="overflow-x:auto;width:300px"><div style="width:500px;height:20px">x</div></div>
                """);

        assertEquals("[{\"kind\":\"scroll\",\"container\":\"div#scroller\",\"by\":200}]",
                result.getAsJsonArray("findings").toString());
    }

    /** A custom element whose shadow root holds {@code inner}, defined before it is used. */
    private static String component(String tag, String inner) {
        return "<script>customElements.define('" + tag + "', class extends HTMLElement { constructor() { super(); "
                + "this.attachShadow({mode: 'open'}).innerHTML = `" + inner + "`; } });</script>";
    }

    @Test
    void aScrollerInAShadowRootIsNamedByItsHostAndPart() {
        JsonObject result = measure(component("x-layout", "<div part='content' style='overflow-x:auto;width:300px'>"
                + "<div style='width:500px;height:20px'>x</div></div>")
                + component("x-old-layout", "<div content style='overflow-x:auto;width:300px'>"
                + "<div style='width:400px;height:20px'>x</div></div>")
                + "<x-layout></x-layout><x-old-layout></x-old-layout>");

        assertEquals("[{\"kind\":\"scroll\",\"container\":\"x-layout [part=content]\",\"by\":200},"
                + "{\"kind\":\"scroll\",\"container\":\"x-old-layout [content]\",\"by\":100}]",
                result.getAsJsonArray("findings").toString());
    }

    @Test
    void gridTabsTabSheetAndVirtualListScrollSidewaysByDesignButAScrollerDoesNot() {
        String scrolls = "<div style='overflow-x:auto;width:300px'><div style='width:500px;height:20px'>x</div></div>";
        JsonObject result = measure(component("vaadin-grid", scrolls) + component("vaadin-tabs", scrolls)
                + component("vaadin-tabsheet", "<vaadin-tabs></vaadin-tabs>")
                + "<vaadin-grid></vaadin-grid><vaadin-tabs></vaadin-tabs><vaadin-tabsheet></vaadin-tabsheet>"
                + "<vaadin-virtual-list style='display:block;overflow-x:auto;width:300px'>"
                + "<div style='width:500px;height:20px'>x</div></vaadin-virtual-list>"
                + "<vaadin-scroller style='display:block;overflow-x:auto;width:300px'>"
                + "<div style='width:400px;height:20px'>x</div></vaadin-scroller>");

        assertEquals("[{\"kind\":\"scroll\",\"container\":\"vaadin-scroller\",\"by\":100}]",
                result.getAsJsonArray("findings").toString());
    }

    // ---------------------------------------------------------------- clipped

    @Test
    void aButtonPushedOutOfAHiddenOverflowBoxIsClippedByIt() {
        JsonObject result = measure("""
                <div id="box" style="overflow:hidden;width:200px;height:60px;white-space:nowrap">
                  <span style="display:inline-block;width:250px">spacer</span><button>Save</button>
                </div>
                <button>Cancel</button>
                """, LayoutScript.Target.interactive("button"));

        assertEquals("[{\"kind\":\"clipped\",\"target\":0,\"index\":0,\"by\":\"div#box\"}]",
                result.getAsJsonArray("findings").toString());
        assertEquals("clipped", matches(result, 0).get(0).getAsJsonObject().get("reach").getAsString());
        assertEquals("div#box", matches(result, 0).get(0).getAsJsonObject().get("by").getAsString());
        assertEquals("visible", matches(result, 0).get(1).getAsJsonObject().get("reach").getAsString());
    }

    @Test
    void escapingABoxOrBeingReachableByScrollingIsNotClipped() {
        JsonObject result = measure("""
                <div style="overflow:hidden;width:50px;height:20px">
                  <button style="position:absolute;left:100px;top:100px">Escapes a static box</button>
                  <button style="position:fixed;left:150px;top:200px">Fixed</button>
                </div>
                <div style="overflow:auto;width:200px;height:60px;white-space:nowrap">
                  <span style="display:inline-block;width:250px">spacer</span><button>Scroll to me</button>
                </div>
                """, LayoutScript.Target.interactive("button"));

        assertEquals("[\"visible\",\"visible\",\"after scroll\"]", reaches(result, 0));
    }

    @Test
    void thePageScrollsToWhatIsBelowButClipsWhatIsLeftOfItOrBeyondAHiddenEdge() {
        JsonObject scrolls = measure("""
                <div style="height:1000px"></div><button>Below the fold</button>
                <button style="position:absolute;left:-300px;top:10px">Left of the page</button>
                """, LayoutScript.Target.interactive("button"));
        JsonObject hides = measure("""
                <style>body{overflow-x:hidden}</style>
                <button style="position:absolute;left:500px;top:10px">Beyond the edge</button>
                """, LayoutScript.Target.interactive("button"));

        assertEquals("[\"after scroll\",\"clipped\"]", reaches(scrolls, 0));
        assertEquals("the page", matches(scrolls, 0).get(1).getAsJsonObject().get("by").getAsString());
        assertEquals("[\"clipped\"]", reaches(hides, 0));
        assertEquals("the page", matches(hides, 0).get(0).getAsJsonObject().get("by").getAsString());
    }

    @Test
    void whatIsBeforeAScrollingBoxsOriginIsClippedButWhatItScrolledPastIsNot() {
        JsonObject result = measure("""
                <div id="box" style="overflow:auto;position:relative;width:200px;height:60px">
                  <div style="width:600px;height:20px"></div>
                  <button style="position:absolute;left:-120px;top:0">Before the origin</button>
                  <button style="position:absolute;left:10px;top:0">Scrolled past</button>
                </div>
                <script>document.getElementById('box').scrollLeft = 300</script>
                """, LayoutScript.Target.interactive("button"));

        assertEquals("[\"clipped\",\"after scroll\"]", reaches(result, 0));
        assertEquals("div#box", matches(result, 0).get(0).getAsJsonObject().get("by").getAsString());
    }

    // ---------------------------------------------------------------- behind

    @Test
    void aButtonUnderAFixedFooterIsBehindItButNotUnderABadgeThatLetsClicksThrough() {
        JsonObject result = measure("""
                <button style="position:absolute;left:10px;top:620px;width:120px;height:30px">Under the footer</button>
                <footer id="bar" style="position:fixed;left:0;bottom:0;width:100%;height:60px;background:#eee"></footer>
                <button style="position:absolute;left:10px;top:10px;width:120px;height:30px">Badged</button>
                <span style="position:absolute;left:10px;top:10px;width:120px;height:30px;pointer-events:none">3</span>
                """, LayoutScript.Target.interactive("button"));

        assertEquals("[\"behind\",\"visible\"]", reaches(result, 0));
        assertEquals("footer#bar", matches(result, 0).get(0).getAsJsonObject().get("by").getAsString());
    }

    // ---------------------------------------------------------------- overlap

    @Test
    void twoOverlappingButtonsAreOneFindingNamingTheCoveredOneAndWhatCoversIt() {
        JsonObject result = measure("""
                <button style="position:absolute;left:10px;top:10px;width:120px;height:40px">Save</button>
                <button style="position:absolute;left:70px;top:20px;width:120px;height:40px">Delete</button>
                """, LayoutScript.Target.interactive("button"));

        assertEquals("[{\"kind\":\"overlap\",\"target\":0,\"index\":0,\"byTarget\":0,\"byIndex\":1}]",
                result.getAsJsonArray("findings").toString());
        JsonObject save = matches(result, 0).get(0).getAsJsonObject();
        assertEquals("behind", save.get("reach").getAsString());
        assertEquals("[0,1]", save.get("byMatch").toString());
    }

    @Test
    void componentsOverlapOnceAndADialogOverAButtonIsNoOverlap() {
        JsonObject components = measure(component("x-button", "<button style='width:100%;height:100%'><slot></slot></button>")
                + "<x-button style='position:absolute;display:block;left:10px;top:10px;width:120px;height:40px'>Save</x-button>"
                + "<x-button style='position:absolute;display:block;left:70px;top:20px;width:120px;height:40px'>Delete</x-button>",
                LayoutScript.Target.interactive("x-button"));
        JsonObject dialog = measure("""
                <button style="position:absolute;left:10px;top:10px;width:120px;height:40px">Under the dialog</button>
                <dialog id="d" style="margin:0;padding:0;left:0;top:0;width:300px;height:200px"><button style="width:300px;height:200px">OK</button></dialog>
                <script>document.getElementById('d').showModal()</script>
                """, LayoutScript.Target.interactive("button"));

        assertEquals(1, components.getAsJsonArray("findings").size(), components.toString());
        assertEquals("[]", dialog.getAsJsonArray("findings").toString());
        assertEquals("behind", matches(dialog, 0).get(0).getAsJsonObject().get("reach").getAsString());
    }

    private static String reaches(JsonObject result, int target) {
        JsonArray reaches = new JsonArray();
        matches(result, target).forEach(m -> reaches.add(m.getAsJsonObject().get("reach")));
        return reaches.toString();
    }

    /** x, y, width of a box, rounded, and 0 for the height so fonts don't matter. */
    private static String roundedXyW(JsonObject match) {
        JsonArray box = match.getAsJsonArray("box");
        return "[" + Math.round(box.get(0).getAsDouble()) + "," + Math.round(box.get(1).getAsDouble()) + ","
                + Math.round(box.get(2).getAsDouble()) + ",0]";
    }
}
