package com.vaadin.testing.unified.selenium;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.testbench.TestBenchDriverProxy;
import com.vaadin.testbench.TestBenchElement;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.internal.CurrentView;
import com.vaadin.testing.unified.internal.RoutePaths;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import com.vaadin.testing.unified.spi.StaleElementException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.interactions.Actions;

/**
 * One window of one browser on Selenium. The page injected into the test owns the driver; windows opened
 * from it are tabs of the same browser (same cookies, same session) and close with it.
 */
final class SeleniumSession implements AdapterSession {

    /** The browser and the window its commands currently go to, shared by the test's windows. */
    private static final class Browser {
        final TestBenchDriverProxy driver;
        String current;

        /** False once the browser refused its log: Firefox's driver has none. */
        private boolean consoleReadable = true;

        Browser(TestBenchDriverProxy driver) {
            this.driver = driver;
            this.current = driver.getWindowHandle();
            // a browser shared by the run's tests: what an earlier test left in the console is not this one's
            consoleErrors();
        }

        /**
         * The errors in the browser's console since they were last read, for all its windows: Selenium's browser
         * log, which Chrome's driver has. Without the browser's own request for {@code /favicon.ico}.
         */
        List<String> consoleErrors() {
            if (!consoleReadable) {
                return List.of();
            }
            try {
                List<String> errors = new ArrayList<>();
                for (org.openqa.selenium.logging.LogEntry entry : driver.manage().logs()
                        .get(org.openqa.selenium.logging.LogType.BROWSER)) {
                    if (entry.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()
                            && !entry.getMessage().contains("/favicon.ico")) {
                        errors.add(entry.getMessage());
                    }
                }
                return errors;
            } catch (RuntimeException e) {
                consoleReadable = false;
                return List.of();
            }
        }
    }

    private final Browser browser;
    private final java.util.function.Supplier<TestBenchDriverProxy> browsers;
    private final List<SeleniumSession> children = new ArrayList<>();
    private final TestBenchDriverProxy driver;
    private final String baseUrl;
    private final Duration timeout;
    private final String handle;
    private final boolean ownsDriver;
    /** Instead of quitting the driver on close: gives a shared browser back. Null: quit it. */
    private final Runnable release;

    /** A page in a new browser from {@code browsers}; {@code openAsUser} starts its own browser the same way. */
    SeleniumSession(java.util.function.Supplier<TestBenchDriverProxy> browsers, String baseUrl, Duration timeout) {
        this(browsers, new Browser(browsers.get()), baseUrl, timeout, true, null);
    }

    /** A page in a browser shared by the run's tests: {@code release} gives it back when the test ends. */
    SeleniumSession(java.util.function.Supplier<TestBenchDriverProxy> browsers, TestBenchDriverProxy shared,
            Runnable release, String baseUrl, Duration timeout) {
        this(browsers, new Browser(shared), baseUrl, timeout, true, release);
    }

    private SeleniumSession(java.util.function.Supplier<TestBenchDriverProxy> browsers, Browser browser,
            String baseUrl, Duration timeout, boolean ownsDriver, Runnable release) {
        this(browsers, browser, baseUrl, timeout, browser.current, ownsDriver, release);
    }

    private SeleniumSession(java.util.function.Supplier<TestBenchDriverProxy> browsers, Browser browser,
            String baseUrl, Duration timeout, String handle, boolean ownsDriver, Runnable release) {
        this.browsers = browsers;
        this.browser = browser;
        this.driver = browser.driver;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.handle = handle;
        this.ownsDriver = ownsDriver;
        this.release = release;
    }

    /** The driver, switched to this session's window first: several windows can live in one test. */
    TestBenchDriverProxy driver() {
        if (!handle.equals(browser.current)) {
            driver.switchTo().window(handle);
            browser.current = handle;
        }
        return driver;
    }

    Duration timeout() {
        return timeout;
    }

    Object js(String script, Object... args) {
        return stale(() -> ((JavascriptExecutor) driver()).executeScript(script, args));
    }

    /** An asynchronous script: its last argument is the callback that ends it. */
    Object jsAsync(String script, Object... args) {
        return stale(() -> ((JavascriptExecutor) driver()).executeAsyncScript(script, args));
    }

    /** Runs a WebDriver call; an element that went stale meanwhile becomes the core's retryable exception. */
    static <T> T stale(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (StaleElementReferenceException e) {
            throw new StaleElementException("The element went stale (re-rendered by Vaadin)", e);
        }
    }

    static void stale(Runnable call) {
        stale(() -> {
            call.run();
            return null;
        });
    }

    /** Waits until Vaadin has handled every pending request (TestBench's waitForVaadin). */
    void waitForVaadin() {
        driver().getCommandExecutor().waitForVaadin();
    }

    /** The element as a TestBench element: what TestBench's own element classes wrap. */
    TestBenchElement testBench(WebElement element) {
        return element instanceof TestBenchElement tb ? tb
                : com.vaadin.testbench.TestBench.createElement(element, driver().getCommandExecutor());
    }

    // ---------------------------------------------------------------- finding

    @Override
    public List<UnifiedElement> find(QuerySpec spec) {
        return findElements(spec);
    }

    /** All the elements in one script call: WebDriver passes a list of elements as one argument. */
    @Override
    public List<com.vaadin.testing.unified.spi.ElementFacts> facts(List<UnifiedElement> elements) {
        if (!elements.stream().allMatch(SeleniumElement.class::isInstance)) {
            return AdapterSession.super.facts(elements);
        }
        List<WebElement> web = elements.stream().map(e -> ((SeleniumElement) e).webElement()).toList();
        List<?> read = (List<?>) js("return arguments[0].map(e => (" + BrowserQueries.FACTS + ")(e));", web);
        return read.stream().map(BrowserQueries::facts).toList();
    }

    @Override
    public List<String> consoleErrors() {
        return browser.consoleErrors();
    }

    @Override
    public String browserName() {
        Object wrapped = driver.getWrappedDriver();
        return wrapped instanceof org.openqa.selenium.HasCapabilities c ? c.getCapabilities().getBrowserName() : null;
    }

    private List<UnifiedElement> findElements(QuerySpec spec) {
        SeleniumComponents.Support support = SeleniumComponents.of(spec.componentType());
        String tag = spec.tag() == null ? null : support.selector(spec.tag());
        if (tag == null) {
            throw new IllegalArgumentException(spec.componentType().getName()
                    + " has no @Tag; set @UnifiedComponent(tag = \"...\")");
        }
        // what CSS can express goes into the selector; the rest is checked in the page, for every candidate in
        // one round trip
        String selector = BrowserQueries.selector(tag, spec.filters());
        List<Map<String, Object>> remaining = BrowserQueries.remaining(spec.filters());
        List<SearchContext> roots = spec.parent() == null ? List.of(driver())
                : spec.inTemplate() ? List.of(((SeleniumElement) spec.parent()).webElement())
                : new ArrayList<>(((SeleniumElement) spec.parent()).roots());
        List<UnifiedElement> result = new ArrayList<>();
        try {
            for (SearchContext root : roots) {
                List<WebElement> candidates = new ArrayList<>();
                for (WebElement candidate : spec.inTemplate() ? inTemplate((WebElement) root, selector)
                        : root.findElements(By.cssSelector(selector))) {
                    if (support.include(candidate, this)) {
                        candidates.add(candidate);
                    }
                }
                List<WebElement> matching = candidates;
                if (!remaining.isEmpty() && !candidates.isEmpty()) {
                    matching = new ArrayList<>();
                    // the rest of the filters check what the user sees, e.g. a notification's card
                    List<WebElement> contents = candidates.stream().map(c -> support.content(c, this)).toList();
                    // no TestBench filter on textContent or accessible name, nor in one round trip: #165, #21
                    for (Object i : (List<?>) js("return (" + BrowserQueries.MATCH + ")(arguments[0], arguments[1]);",
                            contents, remaining)) {
                        matching.add(candidates.get(((Number) i).intValue()));
                    }
                }
                for (WebElement candidate : matching) {
                    result.add(new SeleniumElement(this, candidate, support));
                }
            }
        } catch (StaleElementReferenceException | StaleElementException e) {
            // Vaadin re-rendered a candidate while it was checked: not ready yet, the core queries again
            throw new StaleElementException("An element went stale while finding " + selector, e);
        }
        return result;
    }

    /** The candidates inside a Template the test entered, in one round trip ({@link BrowserQueries#IN_TEMPLATE}). */
    @SuppressWarnings("unchecked")
    private List<WebElement> inTemplate(WebElement host, String selector) {
        return (List<WebElement>) js("return (" + BrowserQueries.IN_TEMPLATE + ")(arguments[0], arguments[1]);",
                host, selector);
    }

    // ---------------------------------------------------------------- navigation

    @Override
    public void navigate(Class<? extends Component> view) {
        open(RoutePaths.pathOf(view));
    }

    @Override
    public void navigate(Class<? extends Component> view, Object urlParameter) {
        open(RoutePaths.pathOf(view, urlParameter));
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters) {
        open(RoutePaths.pathOf(view, parameters));
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters, QueryParameters query) {
        open(RoutePaths.pathOf(view, parameters, query));
    }

    @Override
    public void navigate(String path) {
        if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            driver().get(path);
            waitForVaadin();
        } else {
            open(path.startsWith("/") ? path.substring(1) : path);
        }
    }

    private void open(String path) {
        driver().get(baseUrl + "/" + path);
        waitForVaadin();
    }

    @Override
    public void reload() {
        driver().navigate().refresh();
        waitForVaadin();
    }

    @Override
    public boolean isAt(Class<? extends Component> view) {
        if (!CurrentView.hasRoute(view)) {
            // the UI id from TestBench: #166
            Object answer = ((JavascriptExecutor) driver()).executeAsyncScript(
                    "const done = arguments[arguments.length - 1];"
                            + "(" + CurrentView.SCRIPT + ")(arguments[0]).then(done, () => done(null));",
                    baseUrl + CurrentView.PATH);
            return CurrentView.shows((String) answer, view);
        }
        return currentPath().equals(RoutePaths.pathOf(view));
    }

    @Override
    public String currentPath() {
        String path = URI.create(driver().getCurrentUrl()).getPath();
        String basePath = URI.create(baseUrl).getPath();
        if (basePath != null && !basePath.isEmpty() && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    public Optional<String> queryParameter(String name) {
        String query = URI.create(driver().getCurrentUrl()).getRawQuery();
        if (query == null) {
            return Optional.empty();
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (URLDecoder.decode(kv[0], StandardCharsets.UTF_8).equals(name)) {
                return Optional.of(kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
        return Optional.empty();
    }

    @Override
    public String title() {
        return driver().getTitle();
    }

    // ---------------------------------------------------------------- windows

    /** A new tab of the same browser: same cookies, so the same HTTP and Vaadin session. */
    @Override
    public AdapterSession openWindow() {
        driver().switchTo().newWindow(WindowType.TAB);
        browser.current = driver.getWindowHandle();
        return new SeleniumSession(browsers, browser, baseUrl, timeout, browser.current, false, null);
    }

    /** A browser of its own (own cookies, own session), logged in through the test-only endpoint. */
    @Override
    public AdapterSession openAsUser(String username, String... roles) {
        String loginUrl = LoginSupport.userUrl(baseUrl, username, roles);
        SeleniumSession session = new SeleniumSession(browsers, new Browser(browsers.get()), baseUrl, timeout, true,
                null);
        children.add(session);
        session.logIn(loginUrl);
        return session;
    }

    /** Opens the test-only login URL: the browser's session belongs to that user afterwards. */
    void logIn(String loginUrl) {
        driver().get(loginUrl);
        String body = driver().findElement(By.tagName("body")).getText();
        if (!body.startsWith("Logged in as ")) {
            throw new IllegalStateException("Test login failed: " + body);
        }
    }

    // ---------------------------------------------------------------- the rest

    @Override
    public void sync() {
        // nothing to flush: the browser runs on its own
    }

    /** Waits for Flow to change the address bar after a navigation it handled, then for the round trip that may start. */
    @Override
    public void settle() {
        try {
            ((JavascriptExecutor) driver()).executeAsyncScript("const done = arguments[arguments.length - 1];"
                    + "(" + BrowserQueries.SETTLE_NAVIGATION + ")(arguments[0]).then(() => done(null), () => done(null));",
                    BrowserQueries.SETTLE_NAVIGATION_CAP);
        } catch (org.openqa.selenium.WebDriverException e) {
            // the action loaded another page: nothing of Flow's left to wait for in this one
        }
        waitForVaadin();
    }

    @Override
    public void pressKey(String key) {
        new Actions(driver()).sendKeys(SeleniumKeys.chord(key)).perform();
        waitForVaadin();
    }

    @Override
    public void screenshot(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, driver().getScreenshotAs(OutputType.BYTES));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A screenshot of the window, with where it shows the failed step's Step Target. */
    @Override
    public com.vaadin.testing.unified.spi.Evidence writeFailureEvidence(Path directory, String name, com.vaadin.testing.unified.UnifiedElement target)
            throws java.io.IOException {
        return new com.vaadin.testing.unified.spi.Evidence(List.of(screenshotInto(directory, name)), highlight(target));
    }

    private Path screenshotInto(Path directory, String name) throws java.io.IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(name + ".png");
        Files.write(file, driver().getScreenshotAs(OutputType.BYTES));
        return file;
    }

    /** A screenshot of the window after the step: a WebDriver round trip each, so only when asked for. */
    @Override
    public com.vaadin.testing.unified.spi.Evidence writeStepSnapshot(Path directory, String name,
            com.vaadin.testing.unified.UnifiedElement target) throws java.io.IOException {
        return new com.vaadin.testing.unified.spi.Evidence(List.of(screenshotInto(directory, name)), highlight(target));
    }

    /** The Step Target's box in the window's screenshot, in fractions of it; null without a target. */
    private com.vaadin.testing.unified.spi.Highlight highlight(com.vaadin.testing.unified.UnifiedElement target) {
        if (!(target instanceof SeleniumElement element)) {
            return null;
        }
        Object result;
        try {
            result = ((JavascriptExecutor) driver()).executeScript(TARGET_BOX, element.webElement());
        } catch (org.openqa.selenium.StaleElementReferenceException e) {
            return com.vaadin.testing.unified.spi.Highlight.gone(); // removed from the page
        } catch (RuntimeException e) {
            return null; // the screenshot is kept without a box: a highlight never costs the snapshot
        }
        if (!(result instanceof List<?> box) || box.size() != 6) {
            return com.vaadin.testing.unified.spi.Highlight.gone();
        }
        return com.vaadin.testing.unified.spi.Highlight.of(number(box.get(0)), number(box.get(1)), number(box.get(2)),
                number(box.get(3)), number(box.get(4)), number(box.get(5)));
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    /**
     * The element's box in the viewport, in pixels, and the viewport's size: [left, top, width, height, viewport
     * width, viewport height]; null when it is no longer on the page or not shown.
     */
    private static final String TARGET_BOX = """
            const e = arguments[0];
            if (!e.isConnected || !e.checkVisibility()) return null;
            const r = e.getBoundingClientRect();
            return [r.left, r.top, r.width, r.height, innerWidth, innerHeight];""";

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type.isInstance(driver())) {
            return Optional.of(type.cast(driver()));
        }
        Object wrapped = driver.getWrappedDriver();
        return type.isInstance(wrapped) ? Optional.of(type.cast(wrapped)) : Optional.empty();
    }

    @Override
    public void close() {
        for (SeleniumSession child : children) {
            child.close();
        }
        if (ownsDriver && release != null) {
            release.run(); // a shared browser: cleaned for the next test
        } else if (ownsDriver) {
            driver.quit(); // closes the windows opened from this one too
        } else {
            driver().close();
            browser.current = null;
        }
    }
}
