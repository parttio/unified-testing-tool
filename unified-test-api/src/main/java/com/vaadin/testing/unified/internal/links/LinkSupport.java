package com.vaadin.testing.unified.internal.links;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.steps.Phrases;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.LinkAccess;
import java.util.Objects;
import java.util.Optional;

/**
 * Following a link as a user's click does, on every Testing Tool: a browser clicks the {@code <a>}; a Testing Tool
 * without one follows it through {@link LinkAccess}, or fails when it can't (a link leaving the application). A
 * click that navigated is followed by an {@code open} User Step, as {@code navigate(..)} records it.
 */
public final class LinkSupport {
    private LinkSupport() {}

    public static void click(UnifiedWrapper link) {
        LazyElement element = Wrappers.lazyElementOf(link);
        TestRun run = element.run();
        String before = run.session().currentPath();
        element.act("click", e -> follow(element, e));
        String after = run.session().currentPath();
        if (after != null && !Objects.equals(before, after)) {
            run.steps().followedBy(() -> Phrases.open(after), run.window());
        }
    }

    /**
     * Follows the link {@code e}, found by {@code element}: the one place the core follows a link, for any Wrapper
     * whose component is or holds one.
     */
    public static void follow(LazyElement element, UnifiedElement e) {
        Optional<LinkAccess> access = e.tryUnwrap(LinkAccess.class);
        if (access.isEmpty()) {
            e.click(); // a browser follows the link
            return;
        }
        Optional<String> unfollowable = access.get().unfollowable();
        if (unfollowable.isPresent()) {
            TestRun run = element.run();
            throw run.failure(FailureParts.of("to follow " + StepTarget.of(element).renderWithScope(),
                    "a link that " + unfollowable.get() + ", which " + run.tool() + " can't follow without a browser:"
                            + " the test needs @RequiresBrowser")
                    .suggestion("@RequiresBrowser on the test, so it is skipped on " + run.tool()), null);
        }
        access.get().follow();
    }
}
