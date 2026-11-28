package com.vaadin.testing.unified.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.microsoft.playwright.Browser;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of one run on Playwright running on several threads at once, as with JUnit's parallel execution: Playwright
 * for Java is single-threaded, so no two threads may use one {@code Playwright} at the same time (#686).
 */
class PlaywrightParallelRunTest {

    private final Run run = new Run();
    private final ExecutorService threads = Executors.newFixedThreadPool(2);

    @AfterEach
    void endRun() throws Exception {
        threads.shutdownNow();
        run.close();
    }

    @Test
    void testsRunningAtTheSameTimeOnTwoThreadsGetABrowserEach() throws Exception {
        CyclicBarrier bothOpen = new CyclicBarrier(2);
        Supplier<Browser> test = () -> {
            AdapterSession session = new PlaywrightAdapter().open(run.context());
            try {
                bothOpen.await(30, TimeUnit.SECONDS); // the other test is open too
                return session.unwrap(Browser.class).orElseThrow();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                session.close();
            }
        };
        Future<Browser> first = threads.submit(test::get);
        Future<Browser> second = threads.submit(test::get);
        assertNotSame(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
    }

    @Test
    void aSequentialRunUsesOneBrowser() {
        Browser first = browserOfATest();
        Browser second = browserOfATestOn(threads);
        assertSame(first, second);
    }

    @Test
    void everyBrowserIsClosedWhenTheRunEnds() throws Exception {
        List<Browser> browsers = new ArrayList<>();
        CyclicBarrier bothOpen = new CyclicBarrier(2);
        List<Future<Browser>> tests = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tests.add(threads.submit(() -> {
                AdapterSession session = new PlaywrightAdapter().open(run.context());
                try {
                    bothOpen.await(30, TimeUnit.SECONDS);
                    return session.unwrap(Browser.class).orElseThrow();
                } finally {
                    session.close();
                }
            }));
        }
        for (Future<Browser> test : tests) {
            browsers.add(test.get(60, TimeUnit.SECONDS));
        }
        run.close(); // on this thread, not on the ones that launched them
        for (Browser browser : browsers) {
            assertFalse(browser.isConnected());
        }
    }

    @Test
    void aSecondUserOfAParallelTestUsesThatTestsBrowser() throws Exception {
        CyclicBarrier bothOpen = new CyclicBarrier(2);
        List<Future<Browser[]>> tests = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tests.add(threads.submit(() -> {
                AdapterSession session = new PlaywrightAdapter().open(run.context());
                try {
                    bothOpen.await(30, TimeUnit.SECONDS);
                    AdapterSession second = session.openNewSession(); // openAsUser(..) opens its context the same way
                    return new Browser[] {session.unwrap(Browser.class).orElseThrow(),
                            second.unwrap(Browser.class).orElseThrow()};
                } finally {
                    session.close();
                }
            }));
        }
        for (Future<Browser[]> test : tests) {
            Browser[] browsers = test.get(60, TimeUnit.SECONDS);
            assertSame(browsers[0], browsers[1]);
        }
    }

    @Test
    void testsNavigatingAndLookingUpInParallelDontDisturbEachOther() throws Exception {
        List<Future<?>> tests = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            int test = t;
            tests.add(threads.submit(() -> {
                for (int i = 0; i < 5; i++) {
                    AdapterSession session = new PlaywrightAdapter().open(run.context());
                    try {
                        com.microsoft.playwright.Page page = session.unwrap(com.microsoft.playwright.Page.class)
                                .orElseThrow();
                        for (int n = 0; n < 5; n++) {
                            String text = "test " + test + " page " + n;
                            page.setContent("<p id='t'>" + text + "</p>");
                            assertEquals(text, page.locator("#t").textContent());
                        }
                    } finally {
                        session.close();
                    }
                }
                return null;
            }));
        }
        for (Future<?> test : tests) {
            test.get(120, TimeUnit.SECONDS);
        }
    }

    private Browser browserOfATest() {
        AdapterSession session = new PlaywrightAdapter().open(run.context());
        try {
            return session.unwrap(Browser.class).orElseThrow();
        } finally {
            session.close();
        }
    }

    /** A test on another thread, after the first ended: JUnit's threads take whichever test is next. */
    private Browser browserOfATestOn(ExecutorService thread) {
        try {
            return thread.submit(this::browserOfATest).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A run's scope, as the Test Runner keeps it: run-scoped resources made once, closed when the run ends. */
    static final class Run implements AutoCloseable {
        private final Map<Class<?>, AutoCloseable> scoped = new LinkedHashMap<>();

        AdapterContext context() {
            return new AdapterContext() {
                @Override
                public Optional<String> configuration(String key) {
                    return Optional.empty();
                }

                @Override
                public Duration timeout() {
                    return Duration.ofSeconds(10);
                }

                @Override
                public Optional<Class<?>> testClass() {
                    return Optional.of(PlaywrightParallelRunTest.class);
                }

                @Override
                public Optional<Method> testMethod() {
                    return Optional.empty();
                }

                @Override
                public <T extends AutoCloseable> T runScoped(Class<T> key, Supplier<T> create) {
                    synchronized (scoped) {
                        return key.cast(scoped.computeIfAbsent(key, k -> create.get()));
                    }
                }

                @Override
                public Optional<Object> springContext() {
                    return Optional.empty();
                }

                @Override
                public String baseUrl() {
                    return "about:blank";
                }
            };
        }

        @Override
        public void close() throws Exception {
            List<AutoCloseable> resources;
            synchronized (scoped) {
                resources = new ArrayList<>(scoped.values());
                scoped.clear();
            }
            for (AutoCloseable resource : resources) {
                resource.close();
            }
        }
    }
}
