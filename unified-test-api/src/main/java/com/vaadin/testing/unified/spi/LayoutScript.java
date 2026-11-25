package com.vaadin.testing.unified.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The one script that measures a page's layout (ADR 0011), shipped by the core and run unchanged by every browser
 * adapter in {@link AdapterSession#measureLayout}: a JavaScript function expression that takes {@link #argument} and
 * returns a JSON string. Playwright: {@code page.evaluate(SOURCE, argument)}; Selenium:
 * {@code executeScript("return (" + SOURCE + ")(arguments[0])", argument)}.
 */
public final class LayoutScript {
    private LayoutScript() {}

    /** The script's source. */
    public static final String SOURCE = load();

    /**
     * What to report on: the elements a Wrapper type's lookup finds, by its CSS selector, outside shadow roots as
     * {@code find()} searches. {@code interactive}: they take user actions, so overlapping counts.
     */
    public record Target(String css, boolean interactive) {
        public static Target of(String css) {
            return new Target(css, false);
        }

        public static Target interactive(String css) {
            return new Target(css, true);
        }
    }

    /** The script's argument for these targets, as JSON. */
    public static String argument(List<Target> targets) {
        StringBuilder json = new StringBuilder("{\"targets\":[");
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            json.append(i == 0 ? "" : ",").append("{\"css\":\"").append(t.css().replace("\\", "\\\\")
                    .replace("\"", "\\\"")).append("\",\"interactive\":").append(t.interactive()).append('}');
        }
        return json.append("]}").toString();
    }

    private static String load() {
        try (InputStream in = LayoutScript.class.getResourceAsStream("/META-INF/unified/layout-measure.js")) {
            if (in == null) {
                throw new IllegalStateException("layout-measure.js is missing from unified-test-api");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
