package demo;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** A combo box that turns down every pick the user makes, staying empty: the pick doesn't take (#377). */
@AnonymousAllowed
@Route("rejecting-pick")
public class RejectingPickView extends Div {

    public RejectingPickView() {
        ComboBox<String> size = new ComboBox<>("Size", "Small", "Large");
        size.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                size.clear();
            }
        });
        add(size);
    }
}
