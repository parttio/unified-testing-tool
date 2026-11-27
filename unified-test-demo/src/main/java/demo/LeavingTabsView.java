package demo;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A tab bar whose "Profile" tab sends the user to another page from its selection listener with
 * {@code Page.setLocation}, as the "Logout" tab of Vaadin's Bakery does (#554). The tabs are gone once it loads.
 */
@AnonymousAllowed
@Route("leaving-tabs")
public class LeavingTabsView extends VerticalLayout {
    public LeavingTabsView() {
        Tab profile = new Tab("Profile");
        Tabs tabs = new Tabs(new Tab("Orders"), profile);
        tabs.addSelectedChangeListener(e -> {
            if (e.getSelectedTab() == profile) {
                getUI().ifPresent(ui -> ui.getPage().setLocation("profile"));
            }
        });
        add(tabs);
    }
}
