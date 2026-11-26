package demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.List;

/**
 * An AppLayout that doesn't quite fit a phone (ADR 0011; the "applayout-375" fixture of the layout-script research):
 * a row wider than the content area scrolls it sideways; a wide Grid and five Tabs scroll by design.
 */
@AnonymousAllowed
@Route("layout-app-layout")
public class LayoutAppLayoutView extends AppLayout {

    public LayoutAppLayoutView() {
        Span chosen = new Span();
        chosen.setId("chosen");
        MenuBar menu = new MenuBar();
        for (String item : List.of("File", "Edit", "View", "Insert", "Format", "Share", "Print")) {
            menu.addItem(item, e -> chosen.setText("Chose " + item));
        }
        addToNavbar(new DrawerToggle(), menu);
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard", "dashboard"), new SideNavItem("Persons", "persons"),
                new SideNavItem("Plan", "plan"));
        addToDrawer(nav);

        Grid<Integer> grid = new Grid<>();
        for (int c = 1; c <= 8; c++) {
            int column = c;
            grid.addColumn(row -> "Cell " + row + "." + column).setHeader("Column " + c).setWidth("150px")
                    .setFlexGrow(0);
        }
        grid.setItems(1, 2, 3);
        grid.setAllRowsVisible(true);
        Div wideRow = new Div("A row wider than a phone");
        wideRow.setWidth("560px");
        wideRow.getStyle().set("flex-shrink", "0");
        Tabs tabs = new Tabs(new Tab("Overview"), new Tab("Details"), new Tab("History"), new Tab("Files"),
                new Tab("Settings"));
        // opens the drawer from the content, the same way on every Testing Tool
        Button openDrawer = new Button("Open the drawer", e -> setDrawerOpened(true));
        VerticalLayout content = new VerticalLayout(openDrawer, new TextField("Search"), tabs, grid, wideRow,
                new Button("Save"), chosen);
        setContent(content);
    }
}
