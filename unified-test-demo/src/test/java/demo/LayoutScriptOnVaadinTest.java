package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.spi.LayoutScript;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The layout script's contract on real Vaadin components (ADR 0011), where the static pages of its own tests can't
 * go: AppLayout's overlay drawer and MenuBar's overflow, explained by name. Read through Playwright's Escape Hatch,
 * since the Unified API shows these only through later features (Reach in the Findable View, the Viewport Comparison).
 */
@UnifiedSpringBootTest
@RequiresTestingTool(TestingTool.PLAYWRIGHT)
@Viewport(width = 375, height = 667, name = "phone")
class LayoutScriptOnVaadinTest {

    private static final List<LayoutScript.Target> TARGETS = List.of(
            LayoutScript.Target.interactive("vaadin-side-nav-item"),
            LayoutScript.Target.interactive("vaadin-menu-bar-button"),
            LayoutScript.Target.interactive("vaadin-button"));

    private static JsonObject measure(UnifiedPage page) {
        String json = (String) page.unwrap(Page.class).evaluate(LayoutScript.SOURCE,
                Arrays.asList(LayoutScript.argument(TARGETS), null));
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String reachesAndBy(JsonObject result, int target) {
        JsonArray out = new JsonArray();
        result.getAsJsonArray("matches").get(target).getAsJsonArray().forEach(m -> {
            JsonObject match = m.getAsJsonObject();
            out.add(match.get("reach").getAsString() + (match.has("by") ? " " + match.get("by").getAsString() : ""));
        });
        return out.toString();
    }

    @Test
    @DisplayName("At 375 px the drawer's items are hidden in the closed AppLayout drawer, menu items in the overflow")
    void closedDrawerAndMenuOverflow(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);

        JsonObject result = measure(page);

        assertEquals("[\"hidden the AppLayout drawer\",\"hidden the AppLayout drawer\",\"hidden the AppLayout drawer\"]",
                reachesAndBy(result, 0));
        assertEquals(true, reachesAndBy(result, 1).contains("hidden the MenuBar overflow"), reachesAndBy(result, 1));
    }

    @Test
    @DisplayName("With the drawer opened at 375 px, the content's button is behind the AppLayout drawer, and no overlap")
    void openedDrawerCoversTheContent(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        Page playwright = page.unwrap(Page.class);
        playwright.click("vaadin-drawer-toggle");
        // set as the drawer starts to slide in: the script itself waits for the slide to end
        playwright.waitForSelector("vaadin-app-layout[drawer-opened]");

        JsonObject result = measure(page);

        // "Open the drawer" and "Save", in the content
        assertEquals("[\"behind the AppLayout drawer\",\"behind the AppLayout drawer\"]", reachesAndBy(result, 2),
                result.toString());
        assertEquals("[]", result.getAsJsonArray("findings").toString().replaceAll("\\{\"kind\":\"scroll\"[^}]*},?", "")
                .replace(",]", "]"));
    }
}
