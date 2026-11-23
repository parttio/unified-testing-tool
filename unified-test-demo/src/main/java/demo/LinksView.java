package demo;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** Links a user follows (#561): a RouterLink, an Anchor to a route of the application, one out of it, a disabled one. */
@AnonymousAllowed
@Route("links")
public class LinksView extends VerticalLayout {
    public LinksView() {
        Anchor disabled = new Anchor("users", "Users");
        disabled.setEnabled(false);
        add(new RouterLink("Profile", ProfileView.class), new Anchor("people", "People"),
                new Anchor("https://vaadin.com", "Vaadin"), disabled);
    }
}
