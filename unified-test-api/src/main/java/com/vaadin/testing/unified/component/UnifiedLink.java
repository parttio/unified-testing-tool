package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.links.LinkSupport;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;
import java.time.Duration;

/**
 * A link: a {@code RouterLink} or an {@code Anchor}, or any other {@code <a>}. Found by its text or accessible
 * name, or by where it goes with {@link #withHref(String)}:
 *
 * <pre>{@code
 * page.find(UnifiedLink.class).withText("Users").click();
 * assertThat(page).isAt(UsersView.class);
 * }</pre>
 */
@UnifiedComponent(value = Component.class, tag = "a")
public interface UnifiedLink extends UnifiedWrapper, CommonFilters<UnifiedLink>, TextFilter<UnifiedLink> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedLink withTimeout(Duration timeout);

    /** Links whose {@code href} is this, exactly, as the application set it ({@link #getHref()}). */
    default UnifiedLink withHref(String href) { return withAttribute("href", href); }

    /**
     * Follows the link as a user's click does, and waits for the navigation (Auto-Wait). On Browserless, which has
     * no browser, a link to a route of the application navigates to it; a link that leaves the application (another
     * host, a servlet path such as {@code /logout}, a download) or opens another window fails: the test needs
     * {@code @RequiresBrowser}.
     */
    default void click() { LinkSupport.click(this); }

    // getters (immediate)
    /**
     * The {@code href} as the application set it, the same on every Testing Tool: relative for a {@code RouterLink}
     * ({@code "users"}), not the absolute URL a browser resolves it to. Null for a disabled {@code Anchor}, which
     * has none.
     */
    default String getHref() { return element().getAttribute("href"); }

    /** {@link com.vaadin.testing.unified.UnifiedElement#getText()}: the DOM text, the same on every tool. */
    default String getText() { return element().getText(); }
}
