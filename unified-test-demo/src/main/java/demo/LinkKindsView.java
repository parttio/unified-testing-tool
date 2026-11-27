package demo;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Two RouterLinks and an Anchor, as in Bakery's menu (#562): all three are an {@code <a>} in the browser, and a
 * lookup by class finds the same ones on every Testing Tool.
 */
@AnonymousAllowed
@Route("link-kinds")
public class LinkKindsView extends HorizontalLayout {
    public LinkKindsView() {
        Anchor logout = new Anchor("logout", "Logout");
        logout.setId("logout-tab");
        add(new RouterLink("Profile", ProfileView.class), new RouterLink("People", PeopleView.class), logout);
    }
}
