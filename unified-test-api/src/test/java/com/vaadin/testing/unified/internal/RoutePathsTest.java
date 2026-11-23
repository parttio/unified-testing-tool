package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RoutePrefix;
import com.vaadin.flow.router.RouterLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.vaadin.flow.router.RouteAlias;
import java.util.List;

class RoutePathsTest {

    @Tag("div") @Route("profile") static class ProfileView extends Component {}
    @Tag("div") @Route static class OrderHistoryView extends Component {}
    @Tag("div") @Route static class MainView extends Component {}
    @Tag("div") @RoutePrefix("app") static class AppLayout extends Component implements RouterLayout {}
    @Tag("div") @RoutePrefix("admin") @ParentLayout(AppLayout.class) static class AdminLayout extends Component implements RouterLayout {}
    @Tag("div") @Route(value = "users", layout = AdminLayout.class) static class UsersView extends Component {}
    @Tag("div") @Route(value = "login", layout = AdminLayout.class, absolute = true) static class LoginView extends Component {}
    @Tag("div") @Route("order/:id/:tab?") static class OrderView extends Component {}
    @Tag("div") @Route("files/:path*") static class FilesView extends Component {}
    @Tag("div") @Route("item/:id?([0-9]*)/details") static class ItemView extends Component {}
    @Tag("div") @Route(value = "storefront/:orderID?", layout = AdminLayout.class)
    @RouteAlias(value = "storefront/:orderID/edit", layout = AdminLayout.class) @RouteAlias("")
    @RouteAlias(value = "shop", layout = AdminLayout.class, absolute = true)
    static class StorefrontView extends Component {}

    @Test
    void routeValue() { assertEquals("profile", RoutePaths.pathOf(ProfileView.class)); }

    @Test
    void namingConvention() {
        assertEquals("orderhistory", RoutePaths.pathOf(OrderHistoryView.class));
        assertEquals("", RoutePaths.pathOf(MainView.class));
    }

    @Test
    void layoutPrefixes() {
        assertEquals("app/admin/users", RoutePaths.pathOf(UsersView.class));
        assertEquals("login", RoutePaths.pathOf(LoginView.class));
    }

    @Test
    void parameters() {
        assertEquals("profile/42", RoutePaths.pathOf(ProfileView.class, 42));
        assertEquals("order/7/items", RoutePaths.pathOf(OrderView.class, new RouteParameters(
                java.util.Map.of("id", "7", "tab", "items"))));
        assertEquals("order/7", RoutePaths.pathOf(OrderView.class, new RouteParameters("id", "7")));
    }

    @Test
    void queryParameters() {
        // issue #370: navigate(view, query) and navigate(view, parameters, query) on the browser Testing Tools
        assertEquals("order/7?week=2026-11-02", RoutePaths.pathOf(OrderView.class, new RouteParameters("id", "7"),
                QueryParameters.of("week", "2026-11-02")));
        assertEquals("profile?q=a%20b", RoutePaths.pathOf(ProfileView.class, RouteParameters.empty(),
                QueryParameters.of("q", "a b")));
        assertEquals("profile", RoutePaths.pathOf(ProfileView.class, RouteParameters.empty(), QueryParameters.empty()));
    }

    // issue #530: navigate(view) without parameters, and isAt matching the template and the aliases

    @Test
    void withoutParametersDropsOptionalAndWildcardOnes() {
        assertEquals("app/admin/storefront", RoutePaths.pathOf(StorefrontView.class, RouteParameters.empty()));
        assertEquals("files", RoutePaths.pathOf(FilesView.class, RouteParameters.empty()));
    }

    @Test
    void requiredParameters() {
        assertEquals(List.of("id"), RoutePaths.requiredParameters(OrderView.class));
        assertEquals(List.of(), RoutePaths.requiredParameters(StorefrontView.class));
        assertEquals(List.of(), RoutePaths.requiredParameters(FilesView.class));
        assertEquals(List.of(), RoutePaths.requiredParameters(ProfileView.class));
    }

    @Test
    void matchesPlainRoute() {
        assertTrue(RoutePaths.matches(ProfileView.class, "profile"));
        assertFalse(RoutePaths.matches(ProfileView.class, "profile/42"));
        assertFalse(RoutePaths.matches(ProfileView.class, "profiles"));
        assertTrue(RoutePaths.matches(MainView.class, ""));
        assertFalse(RoutePaths.matches(MainView.class, "profile"));
    }

    @Test
    void requiredParameterMatchesExactlyOneSegment() {
        assertTrue(RoutePaths.matches(OrderView.class, "order/7"));
        assertFalse(RoutePaths.matches(OrderView.class, "order"));
        assertTrue(RoutePaths.matches(OrderView.class, "order/7/items"));
        assertFalse(RoutePaths.matches(OrderView.class, "order/7/items/more"));
    }

    @Test
    void optionalParameterMatchesZeroOrOneSegment() {
        assertTrue(RoutePaths.matches(ItemView.class, "item/details"));
        assertTrue(RoutePaths.matches(ItemView.class, "item/5/details"));
        assertFalse(RoutePaths.matches(ItemView.class, "item/5/6/details"));
        assertFalse(RoutePaths.matches(ItemView.class, "item/5"));
    }

    @Test
    void wildcardParameterMatchesAnyRemainingSegments() {
        assertTrue(RoutePaths.matches(FilesView.class, "files"));
        assertTrue(RoutePaths.matches(FilesView.class, "files/a"));
        assertTrue(RoutePaths.matches(FilesView.class, "files/a/b/c"));
        assertFalse(RoutePaths.matches(FilesView.class, "other/a"));
    }

    @Test
    void matchesWithLayoutPrefixes() {
        assertTrue(RoutePaths.matches(UsersView.class, "app/admin/users"));
        assertFalse(RoutePaths.matches(UsersView.class, "users"));
        assertTrue(RoutePaths.matches(LoginView.class, "login"));
    }

    @Test
    void matchesTheAliases() {
        assertTrue(RoutePaths.matches(StorefrontView.class, "app/admin/storefront"));
        assertTrue(RoutePaths.matches(StorefrontView.class, "app/admin/storefront/42"));
        assertTrue(RoutePaths.matches(StorefrontView.class, "app/admin/storefront/42/edit"));
        assertTrue(RoutePaths.matches(StorefrontView.class, ""));
        assertTrue(RoutePaths.matches(StorefrontView.class, "shop"));
        assertFalse(RoutePaths.matches(StorefrontView.class, "app/admin/shop"));
        assertFalse(RoutePaths.matches(StorefrontView.class, "storefront"));
    }
}
