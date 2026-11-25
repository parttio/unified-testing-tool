package com.vaadin.testing.unified.internal.sidenav;

import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.LookupFailures;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.links.LinkSupport;
import com.vaadin.testing.unified.internal.steps.Phrases;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.SideNavItemAccess;

/**
 * SideNavItem behaviour (#658): a click as a user's, which first expands the collapsed items it is inside, then
 * follows the item's path as {@code UnifiedLink.click()} does, or toggles an item without a path; expanding and
 * collapsing by its toggle button.
 */
public final class SideNavSupport {
    private SideNavSupport() {}

    public static SideNavItemAccess access(UnifiedWrapper item) {
        return item.element().unwrap(SideNavItemAccess.class);
    }

    public static void click(UnifiedWrapper item) {
        LazyElement element = Wrappers.lazyElementOf(item);
        TestRun run = element.run();
        String before = run.session().currentPath();
        element.act(() -> Phrases.forElementAction("click", element, null), "click",
                e -> e.isEnabled() && (LazyElement.usable(e) || e.unwrap(SideNavItemAccess.class).inCollapsedItem()),
                e -> {
                    SideNavItemAccess access = e.unwrap(SideNavItemAccess.class);
                    if (access.inCollapsedItem()) {
                        // a user expands the items it is inside before they can click it
                        access.expandParents();
                        run.session().settle();
                        run.pollFor(() -> LazyElement.usable(e) ? Boolean.TRUE : null,
                                () -> LookupFailures.notUsable(element));
                    }
                    if (e.getAttribute("path") != null) {
                        LinkSupport.follow(run, StepTarget.of(element).renderWithScope(), access.link(), () -> {});
                    } else if (!access.items().isEmpty()) {
                        access.toggle(); // the web component toggles an item without a path on a click
                    } else {
                        e.click();
                    }
                });
        LinkSupport.recordNavigation(run, before);
    }

    /** Shows its child items, by its toggle button; nothing when they are shown already. */
    public static void expand(UnifiedWrapper item) {
        setExpanded(item, true);
    }

    /** Hides its child items, by its toggle button; nothing when they are hidden already. */
    public static void collapse(UnifiedWrapper item) {
        setExpanded(item, false);
    }

    private static void setExpanded(UnifiedWrapper item, boolean expanded) {
        LazyElement element = Wrappers.lazyElementOf(item);
        TestRun run = element.run();
        String action = expanded ? "expand" : "collapse";
        element.act(() -> Phrases.forElementAction(action, element, null), action, LazyElement::usable, e -> {
            SideNavItemAccess access = e.unwrap(SideNavItemAccess.class);
            if (access.items().isEmpty()) {
                throw run.failure(FailureParts.of("child items to " + action + " in "
                        + StepTarget.of(element).renderWithScope(), "none: the item has no child items"), null);
            }
            if (access.isExpanded() != expanded) {
                access.toggle();
            }
        });
    }
}
