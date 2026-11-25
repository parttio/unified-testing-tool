package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A slow server answer (#604): "Load" takes a second on the server, and its answer starts another round
 * trip that puts the stored name in the field, as an app does that navigates to the entity and then reads it. A test
 * that clicks it through the Escape Hatch and types a new name at once must not have the answer overwrite it.
 */
@AnonymousAllowed
@Route("slow-load")
public class SlowLoadView extends VerticalLayout {

    static final long LOAD_MILLIS = 1000;

    public SlowLoadView() {
        TextField name = new TextField("Name");
        Button load = new Button("Load", e -> {
            try {
                Thread.sleep(LOAD_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            // as an app that navigates and then reads the entity: the answer starts another round trip
            name.getElement().executeJs("return 'Stored name'")
                    .then(String.class, name::setValue);
        });
        load.setId("load");
        Button save = new Button("Save", e -> {
            Notification saved = new Notification("Saved " + name.getValue());
            saved.setDuration(0);
            saved.open();
        });
        add(name, load, save);
    }
}
