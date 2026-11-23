package com.vaadin.testing.unified.spi;

import java.util.Optional;

/**
 * Adapter access to a link ({@code RouterLink}, {@code Anchor}), reached by the core with
 * {@code element.tryUnwrap(LinkAccess.class)}. Only a Testing Tool whose click on the element doesn't follow the link
 * provides it: Browserless, which has no browser to follow it. A browser's click on the {@code <a>} follows it.
 */
public interface LinkAccess {

    /**
     * Why this Testing Tool can't follow the link as a user's click does, in words that complete "the link …", e.g.
     * "leaves the application (“https://vaadin.com”)"; empty when it can.
     */
    Optional<String> unfollowable();

    /** Follows the link, as a user's click does, and waits for the navigation. */
    void follow();
}
