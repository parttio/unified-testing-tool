package com.vaadin.testing.unified.browserless;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.spi.LinkAccess;
import java.net.URI;
import java.util.Optional;

/**
 * Following a link on Browserless, which has no browser to do it: a link to a route of the application navigates to
 * that route, as Browserless Test's {@code RouterLinkTester} and {@code AnchorTester} do. A link that leaves the
 * application or opens another window can't be followed. The one place the adapter follows a link: a Wrapper whose
 * component holds a link reaches it through {@link LinkAccess} on the link.
 */
record BrowserlessLinks(BrowserlessSession session, Component link) implements LinkAccess {

    /** The {@code href} as the application set it: {@code getHref()}, which a disabled Anchor still keeps. */
    private String href() {
        if (link instanceof RouterLink routerLink) {
            return routerLink.getHref();
        }
        if (link instanceof Anchor anchor) {
            return anchor.getHref();
        }
        String href = link.getElement().getAttribute("href");
        return href == null ? "" : href;
    }

    @Override
    public Optional<String> unfollowable() {
        String target = link.getElement().getAttribute("target");
        if (target != null && !target.isBlank() && !java.util.List.of("_self", "_top", "_parent").contains(target)) {
            return Optional.of("opens another window (target=“" + target + "”)");
        }
        String href = href();
        URI uri;
        try {
            uri = URI.create(href);
        } catch (IllegalArgumentException e) {
            return Optional.of("has an href that is not a URL (“" + href + "”)");
        }
        if (uri.getScheme() != null || uri.getRawAuthority() != null) {
            return Optional.of("leaves the application (“" + href + "”)");
        }
        if (RouteConfiguration.forSessionScope().getRoute(path(uri)).isEmpty()) {
            return Optional.of("goes to “" + href + "”, which is not a route of the application (a servlet path,"
                    + " a file or a download)");
        }
        return Optional.empty();
    }

    @Override
    public void follow() {
        URI uri = URI.create(href());
        session.act(link, () -> {
            // what RouterLinkTester.navigate() and AnchorTester.navigate() do, with an absolute path ("/users") too
            session.window().getUI().navigate(path(uri), QueryParameters.fromString(uri.getRawQuery()));
            session.window().roundTrip();
        });
    }

    /** The route path: relative to the application, as {@code UI.navigate(..)} takes it. */
    private static String path(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
