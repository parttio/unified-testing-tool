package com.vaadin.testing.unified.browserless;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.spi.SideNavAccess;
import com.vaadin.testing.unified.spi.SideNavItemAccess;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SideNav and SideNavItem on Browserless (#658), as the web component behaves in a browser: an item is current when
 * its path or one of its aliases matches the page's location, and hidden while an item it is inside is collapsed.
 * Expanding is the {@code expanded} property the web component sends to the server. Following an item's path is
 * {@link BrowserlessLinks}' job.
 */
final class BrowserlessSideNav {
    private BrowserlessSideNav() {}

    record NavAccess(SideNav nav) implements SideNavAccess {
        @Override
        public String label() {
            return nav.getLabel();
        }

        @Override
        public List<String> items() {
            return nav.getItems().stream().map(BrowserlessSideNav::label).toList();
        }
    }

    record ItemAccess(BrowserlessSession session, SideNavItem item) implements SideNavItemAccess {
        @Override
        public String label() {
            return BrowserlessSideNav.label(item);
        }

        @Override
        public List<String> items() {
            return item.getItems().stream().map(BrowserlessSideNav::label).toList();
        }

        @Override
        public boolean isCurrent() {
            return BrowserlessSideNav.isCurrent(session.window().getUI(), item);
        }

        @Override
        public boolean isExpanded() {
            return item.isExpanded();
        }

        @Override
        public boolean inCollapsedItem() {
            return parents(item).stream().anyMatch(p -> !p.isExpanded());
        }

        @Override
        public void expandParents() {
            session.act(item, () -> {
                for (SideNavItem parent : parents(item).reversed()) {
                    if (!parent.isExpanded()) {
                        parent.setExpanded(true); // as the web component sends it after a click on the toggle
                    }
                }
                session.window().roundTrip();
            });
        }

        @Override
        public void toggle() {
            session.act(item, () -> {
                item.setExpanded(!item.isExpanded());
                session.window().roundTrip();
            });
        }

        @Override
        public UnifiedElement link() {
            return new BrowserlessElement(session, item);
        }
    }

    /**
     * What the web component does in a browser after each navigation: a current item's parents are expanded, unless
     * its side nav says not to ({@code setAutoExpand(false)}), and the item itself when it has child items. The
     * browser sends the {@code expanded} property to the server, so the server sees it too.
     */
    static void autoExpand(UI ui, Location location) {
        ui.getChildren().forEach(c -> autoExpand(c, location));
    }

    private static void autoExpand(Component component, Location location) {
        if (component instanceof SideNavItem item && isCurrent(location, item)) {
            boolean auto = item.getParent().isEmpty() || nav(item).map(SideNav::isAutoExpand).orElse(true);
            if (auto) {
                parents(item).forEach(p -> p.setExpanded(true));
            }
            item.setExpanded(!item.getItems().isEmpty());
        }
        component.getChildren().forEach(c -> autoExpand(c, location));
    }

    private static Optional<SideNav> nav(SideNavItem item) {
        Optional<Component> parent = item.getParent();
        while (parent.isPresent() && !(parent.get() instanceof SideNav)) {
            parent = parent.get().getParent();
        }
        return parent.map(SideNav.class::cast);
    }

    /** The item's own text, as the browser shows it: its text nodes, not its child items' or slotted parts'. */
    static String label(SideNavItem item) {
        return ItemNames.of(item);
    }

    /** Hidden while an item it is inside is collapsed, as the web component hides the collapsed item's children. */
    static boolean hiddenByParent(Component component) {
        return component instanceof SideNavItem item && parents(item).stream().anyMatch(p -> !p.isExpanded());
    }

    /** The items it is inside, innermost first. */
    private static List<SideNavItem> parents(SideNavItem item) {
        List<SideNavItem> parents = new ArrayList<>();
        Optional<Component> parent = item.getParent();
        while (parent.isPresent() && parent.get() instanceof SideNavItem p) {
            parents.add(p);
            parent = p.getParent();
        }
        return parents;
    }

    /**
     * What the web component's {@code current} says: the page's path is the item's path or one of its aliases (or
     * below it, with {@code matchNested}), and the page's query has the path's query parameters.
     */
    static boolean isCurrent(UI ui, SideNavItem item) {
        return isCurrent(ui.getInternals().getActiveViewLocation(), item);
    }

    private static boolean isCurrent(Location location, SideNavItem item) {
        String path = item.getPath();
        if (path == null) {
            return false;
        }
        if (location == null) {
            return false;
        }
        List<String> paths = new ArrayList<>();
        paths.add(path);
        paths.addAll(item.getPathAliases());
        return paths.stream().anyMatch(p -> matches(location, p, item.isMatchNested()));
    }

    private static boolean matches(Location location, String path, boolean nested) {
        URI uri;
        try {
            uri = URI.create(path);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (uri.getScheme() != null || uri.getRawAuthority() != null) {
            return false; // another site: never the page the user is on in the application
        }
        String wanted = trim(uri.getPath());
        String actual = trim(location.getPath());
        boolean pathMatches = wanted.equals(actual) || nested && (wanted.isEmpty() || actual.startsWith(wanted + "/"));
        if (!pathMatches) {
            return false;
        }
        Map<String, List<String>> query = QueryParameters.fromString(uri.getRawQuery()).getParameters();
        Map<String, List<String>> actualQuery = location.getQueryParameters().getParameters();
        return query.entrySet().stream()
                .allMatch(q -> actualQuery.getOrDefault(q.getKey(), List.of()).containsAll(q.getValue()));
    }

    private static String trim(String path) {
        String p = path == null ? "" : path;
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
