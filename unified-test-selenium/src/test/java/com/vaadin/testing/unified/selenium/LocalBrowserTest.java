package com.vaadin.testing.unified.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vaadin.testbench.parallel.Browser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What {@code TestBenchEscapeHatch.getRunLocallyBrowser()} and {@code getRunLocallyBrowserVersion()} answer (#621).
 * The Escape Hatch itself loads TestBench, which checks its license when it loads: the demo's
 * {@code TestBenchEscapeHatchRunLocallyTest} calls them on Selenium.
 */
class LocalBrowserTest {

    @Test
    void theConfiguredBrowserIsTheLocalBrowser() {
        assertEquals(Browser.FIREFOX, LocalBrowser.of(Optional.of("firefox")).browser());
        assertEquals(Browser.CHROME, LocalBrowser.of(Optional.of("chrome")).browser());
        // the adapter has no browser version setting: any version, as TestBench's @RunLocally without one
        assertEquals("", LocalBrowser.of(Optional.of("firefox")).version());
    }

    @Test
    void anyOtherConfiguredBrowserIsChromeAsTheAdapterRunsChrome() {
        assertEquals(Browser.CHROME, LocalBrowser.of(Optional.of("edge")).browser());
    }

    @Test
    void withoutAConfiguredBrowserThereIsNoLocalBrowser() {
        LocalBrowser none = LocalBrowser.of(Optional.empty());

        assertNull(none.browser());
        assertEquals("", none.version());
    }
}
