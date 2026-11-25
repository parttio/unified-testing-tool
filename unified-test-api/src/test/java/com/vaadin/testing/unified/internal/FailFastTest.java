package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.assertion.UnifiedAssertions;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.StaleElementException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A wait that can't succeed stops at once and says why (#611). */
class FailFastTest {

    @Test
    @DisplayName("A lookup whose adapter can't do it fails at once, naming the error, instead of waiting out the timeout")
    void unsupportedLookupFailsFastWithItsCause() {
        UnifiedPageImpl page = pageWhoseFindThrows(
                () -> new UnsupportedOperationException("Browserless can't search inside a LitRenderer"));

        long start = System.nanoTime();
        UnifiedAssertionError error = assertThrows(UnifiedAssertionError.class,
                () -> UnifiedAssertions.assertThat(page.find(UnifiedButton.class).withText("Save")).exists());
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(took.compareTo(Duration.ofSeconds(2)) < 0, "took " + took);
        assertTrue(error.getMessage().contains(
                "\n  Cause         UnsupportedOperationException: Browserless can't search inside a LitRenderer\n"),
                error.getMessage());
        assertFalse(error.getMessage().contains("Waited"), error.getMessage());
    }

    @Test
    @DisplayName("An error that may go away, such as a grid row not yet rendered, is still retried until it passes")
    void otherErrorsAreRetried() {
        AtomicInteger finds = new AtomicInteger();
        UnifiedElement save = (UnifiedElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { UnifiedElement.class }, (proxy, method, args) -> method.getName().equals("getText")
                        ? "Save" : null);
        UnifiedPageImpl page = page(Duration.ofSeconds(10), () -> {
            switch (finds.incrementAndGet()) {
                case 1 -> throw new IllegalStateException("Row 3 was not rendered");
                case 2 -> throw new StaleElementException("re-rendered", null);
                default -> {
                    return List.of(save);
                }
            }
        });

        UnifiedAssertions.assertThat(page.find(UnifiedButton.class).withText("Save")).exists();

        assertTrue(finds.get() >= 3, "finds: " + finds.get());
    }

    @Test
    @DisplayName("A timeout whose last attempt threw names that error, besides how long it waited")
    void timeoutNamesTheLastError() {
        UnifiedPageImpl page = page(Duration.ofMillis(300), () -> {
            throw new IllegalArgumentException("No visible column at index 2 (2 columns)\nsecond line");
        });

        UnifiedAssertionError error = assertThrows(UnifiedAssertionError.class,
                () -> UnifiedAssertions.assertThat(page.find(UnifiedButton.class).withText("Save")).exists());

        assertTrue(error.getMessage().contains(
                "\n  Cause         IllegalArgumentException: No visible column at index 2 (2 columns)\n"),
                error.getMessage());
        assertTrue(error.getMessage().contains("\n  Waited        300 ms (withTimeout)\n"), error.getMessage());
        assertFalse(error.getMessage().contains("second line"), error.getMessage());
    }

    private UnifiedPageImpl pageWhoseFindThrows(Supplier<RuntimeException> error) {
        return page(Duration.ofSeconds(10), () -> {
            throw error.get();
        });
    }

    private UnifiedPageImpl page(Duration timeout, Supplier<List<UnifiedElement>> find) {
        AdapterSession session = (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { AdapterSession.class }, (proxy, method, args) ->
                        method.getName().equals("find") ? find.get() : null);
        return new UnifiedPageImpl(new TestRun(TestingTool.BROWSERLESS, session, Duration.ofSeconds(10))
                .withTimeout(timeout));
    }
}
