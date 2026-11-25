package demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A layout with side navigation (#658), as most Vaadin apps have in AppLayout's drawer: a "Shop" SideNav with an
 * Admin item that has no path and holds nested items, an Orders item with a path that holds an Archive item, a
 * link out of the application, a disabled Reports item, and a
 * "Help" SideNav whose "Users" item has the same label as the nested Admin › Users.
 */
@AnonymousAllowed
public class ShopLayout extends HorizontalLayout implements RouterLayout {
    public ShopLayout() {
        SideNav shop = new SideNav("Shop");
        SideNavItem admin = new SideNavItem("Admin");
        admin.addItem(new SideNavItem("Users", ShopUsersView.class), new SideNavItem("Roles", ShopRolesView.class));
        SideNavItem orders = new SideNavItem("Orders", ShopOrdersView.class);
        orders.addItem(new SideNavItem("Archive", ShopArchiveView.class));
        // top-level: a disabled item's parent gives it its own enabled state in a browser
        SideNavItem reports = new SideNavItem("Reports", "shop/reports");
        reports.setEnabled(false);
        shop.addItem(new SideNavItem("Home", ShopHomeView.class), orders, admin,
                new SideNavItem("Vaadin", "https://vaadin.com"), reports);

        SideNav help = new SideNav("Help");
        help.addItem(new SideNavItem("Users", UsersView.class));
        add(new Div(shop, help));
    }

    @AnonymousAllowed
    @Route(value = "shop", layout = ShopLayout.class)
    public static class ShopHomeView extends Div {
        public ShopHomeView() {
            add(new H2("Shop home"));
        }
    }

    @AnonymousAllowed
    @Route(value = "shop/orders", layout = ShopLayout.class)
    public static class ShopOrdersView extends Div {
        public ShopOrdersView() {
            add(new H2("Orders"));
        }
    }

    @AnonymousAllowed
    @Route(value = "shop/orders/archive", layout = ShopLayout.class)
    public static class ShopArchiveView extends Div {
        public ShopArchiveView() {
            add(new H2("Archive"));
        }
    }

    @AnonymousAllowed
    @Route(value = "shop/admin/users", layout = ShopLayout.class)
    public static class ShopUsersView extends Div {
        public ShopUsersView() {
            add(new H2("Shop users"));
        }
    }

    @AnonymousAllowed
    @Route(value = "shop/admin/roles", layout = ShopLayout.class)
    public static class ShopRolesView extends Div {
        public ShopRolesView() {
            add(new H2("Roles"));
        }
    }
}
