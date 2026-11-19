package com.vaadin.testing.unified.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrowserInstallTest {

    @Test
    void headlessChromiumNeedsOnlyTheHeadlessShell() {
        assertEquals(List.of("install", "chromium", "--only-shell"), BrowserInstall.CHROMIUM.installArgs(true));
    }

    @Test
    void headedChromiumNeedsOnlyChromium() {
        assertEquals(List.of("install", "chromium", "--no-shell"), BrowserInstall.CHROMIUM.installArgs(false));
    }

    @Test
    void firefoxAndWebkitInstallThemselves() {
        assertEquals(List.of("install", "firefox"), BrowserInstall.FIREFOX.installArgs(true));
        assertEquals(List.of("install", "webkit"), BrowserInstall.WEBKIT.installArgs(false));
    }

    @Test
    void browserSettingPicksTheBrowser() {
        assertEquals(BrowserInstall.FIREFOX, BrowserInstall.of("firefox"));
        assertEquals(BrowserInstall.WEBKIT, BrowserInstall.of("webkit"));
        assertEquals(BrowserInstall.CHROMIUM, BrowserInstall.of("chromium"));
        // as before: anything else is Chromium
        assertEquals(BrowserInstall.CHROMIUM, BrowserInstall.of("chrome"));
    }

    @Test
    void installsUnlessTheEnvironmentSaysNotTo() {
        assertTrue(BrowserInstall.wanted(Map.of()));
        assertTrue(BrowserInstall.wanted(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "0")));
        assertTrue(BrowserInstall.wanted(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "false")));
        assertFalse(BrowserInstall.wanted(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        // Playwright connects to a Selenium Grid's browser then, and downloads nothing
        assertFalse(BrowserInstall.wanted(Map.of("SELENIUM_REMOTE_URL", "http://grid:4444")));
    }
}
