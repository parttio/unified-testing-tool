package com.vaadin.testing.unified.spi;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.testing.unified.UnifiedElement;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One test on one Testing Tool. The core handles laziness, waiting and retries; the session finds
 * components as they are now and acts on them.
 */
public interface AdapterSession extends AutoCloseable {

    /** The components matching the spec right now, in document order. No waiting. */
    List<UnifiedElement> find(QuerySpec spec);

    void navigate(Class<? extends Component> view);
    void navigate(Class<? extends Component> view, Object urlParameter);
    void navigate(Class<? extends Component> view, RouteParameters parameters);
    void navigate(String path);
    void reload();

    boolean isAt(Class<? extends Component> view);
    /** Current path relative to the base URL, without leading slash or query string. */
    String currentPath();
    Optional<String> queryParameter(String name);

    /** Called between retries: lets pending server work run (Browserless: UI.access tasks and signals). */
    void sync();

    /**
     * Called after every user action, once the action has waited for its round trip: waits for what the round
     * trip set going in the page and doesn't wait for. In a browser, the address bar change of a server-side
     * navigation, which Flow makes after handling the response (spec 05, Auto-Wait). The default does nothing.
     */
    default void settle() {
    }

    void pressKey(String key);
    void screenshot(Path file);

    /**
     * Writes what a developer needs to see why a test failed (browsers: a screenshot; Browserless: the
     * component tree) into {@code directory}, with file names starting with {@code name}.
     *
     * @return the files written
     */
    List<Path> writeFailureEvidence(Path directory, String name) throws java.io.IOException;

    /**
     * Evidence of the page right after one User Step, when {@code vaadin.testing.user-steps.snapshots} is on:
     * written into {@code directory}, with file names starting with {@code name}. Browsers: a screenshot of the
     * viewport; Browserless: the component tree. The default writes nothing.
     *
     * @return the files written
     */
    default List<Path> writeStepSnapshot(Path directory, String name) throws java.io.IOException {
        return List.of();
    }

    /**
     * Whether this session records a trace of its own whose actions it can group by User Step
     * ({@link #stepStarted}, {@link #stepEnded}): Playwright with {@code vaadin.testing.playwright.trace} on.
     */
    default boolean tracesSteps() {
        return false;
    }

    /** A User Step or User Task starts, in its words; only called when {@link #tracesSteps()}. */
    default void stepStarted(String text) {
    }

    /** The User Step or User Task last started has ended, passed or failed. */
    default void stepEnded() {
    }

    /**
     * The test has ended, before {@link #close()}: keep what the session recorded for it, such as Playwright's
     * trace, in the test's evidence directory. The default keeps nothing.
     *
     * @param failed whether the test failed
     * @return the files written
     */
    default List<Path> testEnded(Path testEvidenceDirectory, boolean failed) throws java.io.IOException {
        return List.of();
    }

    /** Another window of the same user and session. This session closes it when it closes. */
    AdapterSession openWindow();

    /** A window of another user, in a session of its own. This session closes it when it closes. */
    AdapterSession openAsUser(String username, String... roles);

    /**
     * For failure messages only (Near Misses, several matches): what each element shows a user, in the order
     * given. Testing Tools with a round trip per read should read them all at once. The default reads each value
     * through the Unified Element and knows no ancestors.
     */
    default List<ElementFacts> facts(List<UnifiedElement> elements) {
        return elements.stream().map(ElementFacts::read).toList();
    }

    /** The browser, as a failure message names it ({@code chromium}), or null without one. */
    default String browserName() {
        return null;
    }

    /** The Testing Tool's own page-level object of the given type, if it has one. */
    <T> Optional<T> unwrap(Class<T> type);

    @Override
    void close();
}
