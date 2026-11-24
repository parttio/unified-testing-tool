package com.vaadin.testing.unified.selenium;

import com.vaadin.testbench.parallel.Browser;
import java.util.Optional;

/**
 * The browser a TestBench test calls its local one ({@code getRunLocallyBrowser()}), as the Escape Hatch answers
 * it: the browser {@code vaadin.testing.selenium.browser} configures, the way TestBench's {@code @RunLocally}
 * picks one. Null when the setting is absent and the adapter runs its default browser.
 */
record LocalBrowser(Browser browser) {

    static LocalBrowser of(Optional<String> configured) {
        // the adapter runs Firefox for "firefox", Chrome for any other value
        return new LocalBrowser(configured.map(name -> "firefox".equals(name) ? Browser.FIREFOX : Browser.CHROME)
                .orElse(null));
    }

    /** Always {@code ""}, any version, as a {@code @RunLocally} without one: the adapter has no version setting. */
    String version() {
        return "";
    }
}
