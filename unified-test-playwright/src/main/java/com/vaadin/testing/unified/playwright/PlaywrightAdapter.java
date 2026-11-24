package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Playwright adapter on Dramafinder 2.0. One browser per test run (JVM), one fresh browser context and page
 * per test.
 * <p>
 * Settings (from the Test Runner's configuration; on JUnit, configuration parameters or system properties):
 * <ul>
 * <li>{@code vaadin.testing.base-url}: app URL when the test has no Spring context; default
 * {@code http://localhost:8080}</li>
 * <li>{@code vaadin.testing.playwright.browser}: {@code chromium} (default), {@code firefox}, {@code webkit}:
 * the one browser Playwright downloads, when it isn't installed yet</li>
 * <li>{@code vaadin.testing.playwright.headless}: {@code true} (default)</li>
 * <li>{@code vaadin.testing.playwright.executable}: path to a browser executable, instead of Playwright's
 * Playwright's own browser</li>
 * <li>{@code vaadin.testing.playwright.trace}: {@code off} (default), {@code on} or {@code on-failure}: record
 * Playwright's trace, grouped by User Step, and keep it as {@code trace.zip} in the test's evidence directory
 * (always, or when the test failed)</li>
 * </ul>
 */
public final class PlaywrightAdapter implements TestingToolAdapter {

    static final String BASE_URL = "vaadin.testing.base-url";
    static final String BROWSER = "vaadin.testing.playwright.browser";
    static final String HEADLESS = "vaadin.testing.playwright.headless";
    static final String EXECUTABLE = "vaadin.testing.playwright.executable";
    static final String TRACE = "vaadin.testing.playwright.trace";

    @Override
    public TestingTool tool() {
        return TestingTool.PLAYWRIGHT;
    }

    @Override
    public AdapterSession open(AdapterContext context) {
        Browser browser = context.runScoped(SharedBrowser.class, () -> SharedBrowser.launch(context)).browser();
        String baseUrl = baseUrl(context);
        BrowserContext browserContext = browser.newContext();
        browserContext.setDefaultTimeout(context.timeout().toMillis()); // also for windows opened later
        PlaywrightSession.TraceMode trace = PlaywrightSession.TraceMode.parse(context.configuration(TRACE).orElse("off"));
        if (trace != PlaywrightSession.TraceMode.OFF) {
            // screenshots and DOM snapshots per action, and the test's sources aren't needed: the steps say where
            browserContext.tracing().start(new com.microsoft.playwright.Tracing.StartOptions().setScreenshots(true)
                    .setSnapshots(true));
        }
        Page page = browserContext.newPage();
        PlaywrightSession session = new PlaywrightSession(browserContext, page, baseUrl, context.timeout().toMillis(),
                trace);
        // @WithMockUser / @WithUserDetails: log the browser in before the test body
        if (LoginSupport.isAvailable()) {
            LoginSupport.testUserUrl(baseUrl).ifPresent(session::logIn);
        }
        return session;
    }

    private static String baseUrl(AdapterContext context) {
        Optional<String> fromSpring = context.springContext().flatMap(SpringSupport::baseUrl);
        String url = fromSpring.or(() -> context.configuration(BASE_URL)).orElse("http://localhost:8080");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Playwright and its browser, shared by the run's tests and closed when the run ends. */
    record SharedBrowser(Playwright playwright, Browser browser) implements AutoCloseable {

        static SharedBrowser launch(AdapterContext context) {
            Optional<String> executable = context.configuration(EXECUTABLE);
            BrowserInstall browser = BrowserInstall.of(context.configuration(BROWSER).orElse("chromium"));
            boolean headless = !"false".equalsIgnoreCase(context.configuration(HEADLESS).orElse("true"));
            // not every browser Playwright supports: the one the run launches, if not an executable
            Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of(BrowserInstall.SKIP_DOWNLOAD, "1")));
            try {
                if (executable.isEmpty()) {
                    browser.install(headless);
                }
                BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
                executable.ifPresent(path -> options.setExecutablePath(Path.of(path))
                        .setArgs(List.of("--no-sandbox")));
                return new SharedBrowser(playwright, browser.type(playwright).launch(options));
            } catch (RuntimeException e) {
                playwright.close();
                throw e;
            }
        }

        @Override
        public void close() {
            try {
                browser.close();
            } finally {
                playwright.close();
            }
        }
    }
}
