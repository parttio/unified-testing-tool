package demo;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A contact form whose labels differ slightly from what a test might guess ("E-mail"), and a save that reports
 * "Saving…" first and "Saved …" a moment later, from a background thread: tests of failure messages use it.
 */
@AnonymousAllowed
@Route("contact")
public class ContactView extends VerticalLayout {

    public ContactView() {
        TextField name = new TextField("Name");
        TextField email = new TextField("E-mail");
        ComboBox<String> role = new ComboBox<>("Role");
        role.setItems("Admin", "User");
        Span status = new Span();
        status.setId("status");

        Button save = new Button("Save", e -> {
            status.setText("Saving…");
            UI ui = UI.getCurrent();
            ui.setPollInterval(100); // a browser fetches the later change without push
            String saved = "Saved " + name.getValue();
            CompletableFuture.runAsync(() -> ui.access(() -> {
                status.setText(saved);
                ui.setPollInterval(-1);
            }), CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS));
        });
        add(name, email, role, save, status);
    }
}
