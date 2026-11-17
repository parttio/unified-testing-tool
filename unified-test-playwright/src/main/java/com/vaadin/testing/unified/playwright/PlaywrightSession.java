package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.internal.CurrentView;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.vaadin.addons.dramafinder.AutoWait;
import org.vaadin.addons.dramafinder.RouteNavigation;
import org.vaadin.addons.dramafinder.VaadinClient;
import org.vaadin.addons.dramafinder.element.GenericElement;
import org.vaadin.addons.dramafinder.element.VaadinElement;
import org.vaadin.addons.dramafinder.element.query.ElementQueries;
import org.vaadin.addons.dramafinder.element.query.TagQuery;

/** One test on Playwright: one browser context and page. */
final class PlaywrightSession implements AdapterSession {

    private final BrowserContext context;
    private final Page page;
    private final String baseUrl;

    private final boolean ownsContext;
    private final java.util.List<PlaywrightSession> children = new ArrayList<>();

    private final double timeoutMillis;

    PlaywrightSession(BrowserContext context, Page page, String baseUrl, double timeoutMillis) {
        this(context, page, baseUrl, timeoutMillis, true);
    }

    private PlaywrightSession(BrowserContext context, Page page, String baseUrl, double timeoutMillis,
            boolean ownsContext) {
        this.context = context;
        this.page = page;
        this.baseUrl = baseUrl;
        this.timeoutMillis = timeoutMillis;
        this.ownsContext = ownsContext;
    }

    /** A new page in the same browser context: same cookies, so the same HTTP and Vaadin session. */
    @Override
    public AdapterSession openWindow() {
        Page second = context.newPage();
        return new PlaywrightSession(context, second, baseUrl, timeoutMillis, false);
    }

    /** A new browser context (own cookies, own session), logged in through the test-only endpoint. */
    @Override
    public AdapterSession openAsUser(String username, String... roles) {
        String loginUrl = LoginSupport.userUrl(baseUrl, username, roles);
        BrowserContext other = context.browser().newContext();
        other.setDefaultTimeout(timeoutMillis);
        PlaywrightSession session = new PlaywrightSession(other, other.newPage(), baseUrl, timeoutMillis, true);
        children.add(session);
        session.logIn(loginUrl);
        return session;
    }

    /** Opens the test-only login URL: the browser context's session belongs to that user afterwards. */
    void logIn(String loginUrl) {
        com.microsoft.playwright.Response response = page.navigate(loginUrl);
        if (response == null || !response.ok()) {
            throw new IllegalStateException("Test login failed: " + (response == null ? "no response" : response.status()
                    + " " + response.text()));
        }
    }

    Page page() {
        return page;
    }

    // ---------------------------------------------------------------- finding

    @Override
    public List<UnifiedElement> find(QuerySpec spec) {
        PlaywrightComponents.Support support = PlaywrightComponents.of(spec.componentType());
        Locator scope = spec.parent() == null ? null : ((PlaywrightElement) spec.parent()).locator();
        if (support.dramafinderElement() != null) {
            return findWithDramafinder(support, scope, spec.filters());
        }
        if (spec.tag() == null) {
            throw new IllegalArgumentException(spec.componentType().getName()
                    + " has no @Tag; set @UnifiedComponent(tag = \"...\")");
        }
        // Dramafinder's tag query takes the filters CSS can express; the rest are checked in the page with the
        // Unified API's matching, for every candidate in one round trip: no browser call per element, and no
        // element detaching between two calls.
        // "*" (VaadinElement.ANY_TAG) queries every light-DOM element, whatever its tag
        TagQuery<GenericElement> query = scope == null ? ElementQueries.find(page, spec.tag())
                : ElementQueries.find(scope, spec.tag());
        for (Filter f : spec.filters()) {
            if (BrowserQueries.selectorPart(f) != null) {
                query = switch (f.kind()) {
                    case ID -> query.withId(f.value());
                    case TEST_ID -> query.withTestId(f.value());
                    case CLASS_NAME -> query.withClassName(f.value());
                    case THEME -> query.withTheme(f.value());
                    case ATTRIBUTE -> query.withAttribute(f.name(), f.value());
                    default -> throw new IllegalStateException("Not a CSS filter: " + f);
                };
            }
        }
        Locator candidates = query.getLocator();
        List<Map<String, Object>> remaining = BrowserQueries.remaining(spec.filters());
        List<Integer> indices = new ArrayList<>();
        for (Object i : (List<?>) candidates.evaluateAll(BrowserQueries.MATCH, remaining)) {
            indices.add(((Number) i).intValue());
        }
        List<UnifiedElement> result = new ArrayList<>();
        for (int i : indices) {
            Locator locator = candidates.nth(i);
            if (support.include(locator)) {
                result.add(new PlaywrightElement(this, locator, null, support, candidates, i));
            }
        }
        return result;
    }

    /**
     * One round trip per query's candidates: elements found together are read by their index in one
     * {@code evaluateAll}; any other element is read on its own.
     */
    @Override
    public List<com.vaadin.testing.unified.spi.ElementFacts> facts(List<UnifiedElement> elements) {
        com.vaadin.testing.unified.spi.ElementFacts[] facts = new com.vaadin.testing.unified.spi.ElementFacts[elements.size()];
        java.util.Map<Locator, List<Integer>> groups = new java.util.IdentityHashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof PlaywrightElement e && e.candidates() != null) {
                groups.computeIfAbsent(e.candidates(), k -> new ArrayList<>()).add(i);
            } else if (elements.get(i) instanceof PlaywrightElement e) {
                facts[i] = BrowserQueries.facts(e.locator().evaluate(BrowserQueries.FACTS));
            } else {
                facts[i] = com.vaadin.testing.unified.spi.ElementFacts.read(elements.get(i));
            }
        }
        groups.forEach((candidates, positions) -> {
            List<Integer> indices = positions.stream().map(p -> ((PlaywrightElement) elements.get(p)).index()).toList();
            List<?> read = (List<?>) candidates.evaluateAll(
                    "(els, idx) => idx.map(i => els[i] ? (" + BrowserQueries.FACTS + ")(els[i]) : null)", indices);
            for (int j = 0; j < positions.size(); j++) {
                Object one = j < read.size() ? read.get(j) : null;
                facts[positions.get(j)] = one == null ? null : BrowserQueries.facts(one);
            }
        });
        return java.util.Arrays.asList(facts);
    }

    @Override
    public String browserName() {
        return context.browser() == null ? null : context.browser().browserType().name();
    }

    private List<UnifiedElement> findWithDramafinder(PlaywrightComponents.Support support, Locator scope,
            List<Filter> filters) {
        Class<? extends VaadinElement> type = support.dramafinderElement();
        Object query = scope == null
                ? invokeStatic("find", new Class<?>[] { Page.class, Class.class }, page, type)
                : invokeStatic("find", new Class<?>[] { Locator.class, Class.class }, scope, type);
        for (Filter f : filters) {
            String method = switch (f.kind()) {
                case LABEL -> "withLabel";
                case ACCESSIBLE_NAME -> "withAccessibleName";
                case TEXT -> "withText";
                case PLACEHOLDER -> "withPlaceholder";
                case TEST_ID -> "withTestId";
                case ID -> "withId";
                case CLASS_NAME -> "withClassName";
                case THEME, ATTRIBUTE -> null;
            };
            if (f.kind() == Filter.Kind.THEME) {
                query = invoke(query, "withTheme", new Class<?>[] { String.class }, f.value());
            } else if (f.kind() == Filter.Kind.ATTRIBUTE) {
                query = invoke(query, "withAttribute", new Class<?>[] { String.class, String.class }, f.name(), f.value());
            } else {
                query = invoke(query, f.exact() ? method : method + "Containing", new Class<?>[] { String.class },
                        f.value());
            }
        }
        // all() returns the query locator's matches in order: element i is candidates.nth(i)
        Locator candidates = (Locator) invoke(query, "getLocator", new Class<?>[0]);
        @SuppressWarnings("unchecked")
        List<VaadinElement> found = (List<VaadinElement>) invoke(query, "all", new Class<?>[0]);
        List<UnifiedElement> result = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            VaadinElement e = found.get(i);
            if (support.include(e.getLocator())) {
                result.add(new PlaywrightElement(this, e.getLocator(), e, support, candidates, i));
            }
        }
        return result;
    }

    private static Object invokeStatic(String name, Class<?>[] types, Object... args) {
        try {
            return ElementQueries.class.getMethod(name, types).invoke(null, args);
        } catch (InvocationTargetException e) {
            throw unwrapped(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, types);
            return method.invoke(target, args);
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException(target.getClass().getSimpleName() + " has no " + name
                    + " filter in Dramafinder", e);
        } catch (InvocationTargetException e) {
            throw unwrapped(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RuntimeException unwrapped(InvocationTargetException e) {
        return e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
    }

    // ---------------------------------------------------------------- navigation

    @Override
    public void navigate(Class<? extends Component> view) {
        RouteNavigation.navigate(page, baseUrl, view);
    }

    @Override
    public void navigate(Class<? extends Component> view, Object urlParameter) {
        RouteNavigation.navigate(page, baseUrl, view, urlParameter);
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters) {
        RouteNavigation.open(page, baseUrl, RouteNavigation.pathOf(view, parameters));
    }

    @Override
    public void navigate(String path) {
        if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            page.navigate(path);
            AutoWait.waitForVaadinIdle(page);
        } else {
            RouteNavigation.open(page, baseUrl, path.startsWith("/") ? path.substring(1) : path);
        }
    }

    @Override
    public void reload() {
        page.reload();
        AutoWait.waitForVaadinIdle(page);
    }

    @Override
    public boolean isAt(Class<? extends Component> view) {
        if (!CurrentView.hasRoute(view)) {
            return CurrentView.shows(currentViews(), view);
        }
        return RouteNavigation.isAt(page, baseUrl, view);
    }

    /** The current-view endpoint's answer for the page's UI, or null (no Vaadin UI, or no endpoint). */
    private String currentViews() {
        Optional<Integer> ui = VaadinClient.uiId(page);
        if (ui.isEmpty()) {
            return null;
        }
        APIResponse response = page.request().get(baseUrl + CurrentView.PATH + "?ui=" + ui.get());
        try {
            return response.ok() ? response.text() : null;
        } finally {
            response.dispose();
        }
    }

    /** The path in the page's location: it already sees a client-side navigation. Null outside the app. */
    @Override
    public String currentPath() {
        return RouteNavigation.currentPath(page, baseUrl);
    }

    @Override
    public Optional<String> queryParameter(String name) {
        return RouteNavigation.queryParameter(page, name);
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
            page.evaluate(BrowserQueries.SETTLE_NAVIGATION, BrowserQueries.SETTLE_NAVIGATION_CAP);
        } catch (com.microsoft.playwright.PlaywrightException e) {
            // the action loaded another page: nothing of Flow's left to wait for in this one
        }
        AutoWait.waitForVaadinIdle(page);
    }

    /** A full-page screenshot. */
    @Override
    public List<Path> writeFailureEvidence(Path directory, String name) throws java.io.IOException {
        java.nio.file.Files.createDirectories(directory);
        Path file = directory.resolve(name + ".png");
        page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true));
        return List.of(file);
    }

    @Override
    public void pressKey(String key) {
        page.keyboard().press(key);
        AutoWait.afterAction(page);
    }

    @Override
    public void screenshot(Path file) {
        page.screenshot(new Page.ScreenshotOptions().setPath(file));
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type.isInstance(page)) {
            return Optional.of(type.cast(page));
        }
        if (type.isInstance(context)) {
            return Optional.of(type.cast(context));
        }
        if (type.isInstance(context.browser())) {
            return Optional.of(type.cast(context.browser()));
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        for (PlaywrightSession child : children) {
            child.close();
        }
        if (ownsContext) {
            context.close(); // closes the windows opened from this one too
        } else {
            page.close();
        }
    }

    /** Exactly this text, surrounding whitespace aside. Escaped by hand: Playwright runs it as a JS regex. */
    static Pattern exactText(String text) {
        return Pattern.compile("^\\s*" + text.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0") + "\\s*$");
    }
}
