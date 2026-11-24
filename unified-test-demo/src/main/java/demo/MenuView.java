package demo;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A {@code Tabs} used as a navigation menu, as Vaadin's Bakery builds it (#531): tabs holding a {@code RouterLink}
 * each, a plain tab whose selection listener shows a note, and a tab holding an {@code Anchor} to an absolute URL,
 * which Browserless can't follow. A {@code TabSheet} below has a tab holding a link too.
 */
@AnonymousAllowed
@Route("menu")
public class MenuView extends VerticalLayout {
    public MenuView() {
        Tab notes = new Tab("Notes");
        Span note = new Span();
        note.setId("note");
        Tabs menu = new Tabs(new Tab(new RouterLink("Profile", ProfileView.class)),
                new Tab(new RouterLink("People", PeopleView.class)), notes,
                new Tab(new Anchor(absoluteUrl("profile"), "Account")));
        menu.setId("menu");
        menu.addSelectedChangeListener(e -> note.setText(e.getSelectedTab() == notes ? "Notes opened" : ""));

        TabSheet sheet = new TabSheet();
        sheet.add("Summary", new Span("Summary content"));
        sheet.add(new Tab(new RouterLink("People", PeopleView.class)), new Span("People content"));
        add(menu, note, sheet);
    }

    /**
     * The page as an absolute URL, with this server's host and port: a browser loads it anew, Browserless sees a
     * link that leaves the application.
     */
    private static String absoluteUrl(String path) {
        if (VaadinRequest.getCurrent() instanceof VaadinServletRequest request) {
            return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                    + request.getContextPath() + "/" + path;
        }
        return "http://localhost/" + path;
    }
}
