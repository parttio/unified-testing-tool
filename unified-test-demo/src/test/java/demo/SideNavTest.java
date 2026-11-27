package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedSideNav;
import com.vaadin.testing.unified.component.UnifiedSideNavItem;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import demo.ShopLayout.ShopArchiveView;
import demo.ShopLayout.ShopHomeView;
import demo.ShopLayout.ShopOrdersView;
import demo.ShopLayout.ShopUsersView;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code UnifiedSideNav} and {@code UnifiedSideNavItem} (#658): side navigation items found by their label, clicked
 * as a user does, and read the same on every Testing Tool.
 */
@UnifiedSpringBootTest
class SideNavTest {

    // --8<-- [start:clicksASideNavItem]
    @Test
    @DisplayName("The user opens Orders from the side navigation")
    void clicksAnItem(UnifiedPage page) {
        page.navigate(ShopHomeView.class);

        page.find(UnifiedSideNavItem.class).withLabel("Orders").click();

        assertThat(page).isAt(ShopOrdersView.class);
        assertThat(page.find(UnifiedSideNavItem.class).withLabel("Orders")).isCurrent();
    }
    // --8<-- [end:clicksASideNavItem]

    // --8<-- [start:clicksANestedItem]
    @Test
    @DisplayName("The user opens Admin › Users: the collapsed Admin item is expanded first")
    void clicksANestedItemInACollapsedItem(UnifiedPage page) {
        page.navigate(ShopHomeView.class);
        UnifiedSideNavItem admin = page.find(UnifiedSideNavItem.class).withLabel("Admin");
        assertThat(admin).isCollapsed();

        admin.find(UnifiedSideNavItem.class).withLabel("Users").click();

        assertThat(page).isAt(ShopUsersView.class);
        assertThat(admin).isExpanded();
        assertThat(admin.find(UnifiedSideNavItem.class).withLabel("Users")).isCurrent().hasPath("shop/admin/users");
    }
    // --8<-- [end:clicksANestedItem]

    @Test
    @DisplayName("The user opens Orders › Archive: Orders, which has a path, is expanded by its toggle, not followed")
    void clicksANestedItemOfAnItemWithAPath(UnifiedPage page) {
        page.navigate(ShopHomeView.class);

        page.find(UnifiedSideNavItem.class).withLabel("Archive").click();

        assertThat(page).isAt(ShopArchiveView.class);
        assertThat(page.find(UnifiedSideNavItem.class).withLabel("Orders")).isExpanded().isNotCurrent();
    }

    @Test
    @DisplayName("The current item says so, and the others don't")
    void tellsTheCurrentItem(UnifiedPage page) {
        page.navigate(ShopOrdersView.class);

        assertThat(page.find(UnifiedSideNavItem.class).withLabel("Orders")).isCurrent();
        assertThat(page.find(UnifiedSideNavItem.class).withLabel("Home")).isNotCurrent();
        assertTrue(page.find(UnifiedSideNavItem.class).withLabel("Orders").isCurrent());
    }

    @Test
    @DisplayName("The user expands and collapses Admin, by its toggle and by clicking it")
    void expandsAndCollapses(UnifiedPage page) {
        page.navigate(ShopHomeView.class);
        UnifiedSideNavItem admin = page.find(UnifiedSideNavItem.class).withLabel("Admin");

        admin.expand();
        assertThat(admin).isExpanded().hasItems("Users", "Roles");
        assertThat(admin.find(UnifiedSideNavItem.class).withLabel("Roles")).isVisible();

        admin.collapse();
        assertThat(admin).isCollapsed();
        assertThat(admin.find(UnifiedSideNavItem.class).withLabel("Roles")).isHidden();

        admin.click(); // an item without a path: a click toggles it
        assertThat(admin).isExpanded();
        assertThat(page).isAt(ShopHomeView.class);
    }

    @Test
    @DisplayName("Each item reads its label, path and child items; each side nav its label and items")
    void readsItemsAndNavs(UnifiedPage page) {
        page.navigate(ShopHomeView.class);
        UnifiedSideNav shop = page.find(UnifiedSideNav.class).withLabel("Shop");
        UnifiedSideNavItem admin = shop.find(UnifiedSideNavItem.class).withLabel("Admin");

        assertEquals("Shop", shop.getLabel());
        assertEquals(List.of("Home", "Orders", "Admin", "Vaadin", "Reports"), shop.getItems());
        assertThat(shop).hasItems("Home", "Orders", "Admin", "Vaadin", "Reports");
        assertEquals("Admin", admin.getLabel());
        assertNull(admin.getPath());
        assertEquals(List.of("Users", "Roles"), admin.getItems());
        assertThat(page.find(UnifiedSideNavItem.class).withPath("shop/orders")).hasPath("shop/orders");
    }

    @Test
    @DisplayName("Two items labelled Users are told apart by the side nav or item they are in")
    void findsAlikeItemsThroughTheirScope(UnifiedPage page) {
        page.navigate(ShopHomeView.class);

        page.find(UnifiedSideNav.class).withLabel("Help").find(UnifiedSideNavItem.class).withLabel("Users").click();

        assertThat(page).isAt(UsersView.class);
    }

    @Test
    @DisplayName("The user can't click the disabled Reports item")
    void disabledItemCantBeClicked(UnifiedPage page) {
        page.navigate(ShopHomeView.class);
        UnifiedSideNavItem reports = page.find(UnifiedSideNavItem.class).withLabel("Reports")
                .withTimeout(Duration.ofMillis(500));

        AssertionError e = assertThrows(AssertionError.class, reports::click);

        assertTrue(e.getMessage().contains("disabled"), e.getMessage());
        assertThat(page).isAt(ShopHomeView.class);
    }

    @Test
    @DisplayName("On a page of a nested item, its parent is expanded and the item is current, as a browser shows it")
    void expandsTheParentOfTheCurrentItem(UnifiedPage page) {
        page.navigate(ShopUsersView.class);
        UnifiedSideNavItem admin = page.find(UnifiedSideNavItem.class).withLabel("Admin");

        assertThat(admin).isExpanded().isNotCurrent();
        assertThat(admin.find(UnifiedSideNavItem.class).withLabel("Users")).isVisible().isCurrent();
    }

    @Test
    @DisplayName("Orders, current and holding an item, is expanded once the user opens it")
    void expandsTheCurrentItemWithChildItems(UnifiedPage page) {
        page.navigate(ShopHomeView.class);
        UnifiedSideNavItem orders = page.find(UnifiedSideNavItem.class).withLabel("Orders");
        assertThat(orders).isCollapsed();

        orders.click();

        assertThat(page).isAt(ShopOrdersView.class);
        assertThat(orders).isCurrent().isExpanded().hasItems("Archive");
    }

    @Test
    @DisplayName("Expanding an item without child items fails, saying so")
    void expandingAnItemWithoutChildItemsFails(UnifiedPage page) {
        page.navigate(ShopHomeView.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> page.find(UnifiedSideNavItem.class).withLabel("Home").expand());

        assertTrue(e.getMessage().contains("the item has no child items"), e.getMessage());
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @DisplayName("Clicking an item out of the application fails on Browserless, asking for a browser")
    void itemOutOfTheApplicationNeedsABrowser(UnifiedPage page) {
        page.navigate(ShopHomeView.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> page.find(UnifiedSideNavItem.class).withLabel("Vaadin").click());

        assertTrue(e.getMessage().contains("leaves the application (“https://vaadin.com”)"), e.getMessage());
        assertTrue(e.getMessage().contains("@RequiresBrowser"), e.getMessage());
        assertThat(page).isAt(ShopHomeView.class);
    }

    @Test
    @RequiresBrowser
    @DisplayName("An item out of the application goes where the application says")
    void readsAnItemOutOfTheApplication(UnifiedPage page) {
        page.navigate(ShopHomeView.class);

        assertThat(page.find(UnifiedSideNavItem.class).withLabel("Vaadin")).hasPath("https://vaadin.com");
    }

    @Test
    @DisplayName("The Findable View lists the side navs and their items, with the current and collapsed ones, and the side nav that tells the two Users items apart")
    void listsSideNavItems(UnifiedPage page) {
        page.navigate(ShopHomeView.class);

        assertEquals(List.of(
                "side nav “Shop”  page.find(UnifiedSideNav.class).withLabel(\"Shop\")",
                "side nav “Help”  page.find(UnifiedSideNav.class).withLabel(\"Help\")",
                "side nav item “Home” (current)  page.find(UnifiedSideNavItem.class).withLabel(\"Home\")",
                "side nav item “Orders” (collapsed)  page.find(UnifiedSideNavItem.class).withLabel(\"Orders\")",
                "side nav item “Archive” (hidden)  page.find(UnifiedSideNavItem.class).withLabel(\"Archive\")",
                "side nav item “Admin” (collapsed)  page.find(UnifiedSideNavItem.class).withLabel(\"Admin\")",
                "side nav item “Users” (hidden)  not unique: 2 alike; one in side nav “Shop”: page.find(UnifiedSideNav.class).withLabel(\"Shop\").find(UnifiedSideNavItem.class).withLabel(\"Users\")",
                "side nav item “Roles” (hidden)  page.find(UnifiedSideNavItem.class).withLabel(\"Roles\")",
                "side nav item “Vaadin”  page.find(UnifiedSideNavItem.class).withLabel(\"Vaadin\")",
                "side nav item “Reports” (disabled)  page.find(UnifiedSideNavItem.class).withLabel(\"Reports\")",
                "side nav item “Users”  not unique: 2 alike; one in side nav “Help”: page.find(UnifiedSideNav.class).withLabel(\"Help\").find(UnifiedSideNavItem.class).withLabel(\"Users\")"),
                page.describe().lines().skip(1).filter(l -> l.startsWith("side nav"))
                        .map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }
}
