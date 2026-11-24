package demo;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A link beside plain text, as {@code add(String)} and Bakery's menu make (#603): a {@code Text} component is a text
 * node, with no tag, and a lookup by tag skips it.
 */
@AnonymousAllowed
@Route("welcome")
public class WelcomeView extends Div {
    public WelcomeView() {
        setId("welcome");
        add(new Text("Welcome "), new RouterLink("Users", UsersView.class), new Text(" or "), new Span("New"));
    }
}
