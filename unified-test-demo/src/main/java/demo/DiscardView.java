package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** "Discard draft" asks for confirmation in a ConfirmDialog, an overlay host other than Dialog. */
@AnonymousAllowed
@Route("discard")
public class DiscardView extends VerticalLayout {

    public DiscardView() {
        Span status = new Span("Draft kept");
        status.setId("discard-status");
        Button discard = new Button("Discard draft", e -> {
            ConfirmDialog confirm = new ConfirmDialog();
            confirm.setHeader("Discard draft?");
            confirm.setText("The draft can't be restored.");
            // a Flow button, so that every Testing Tool can click it (UnifiedConfirmDialog is #532)
            confirm.setConfirmButton(new Button("Discard", confirmed -> {
                status.setText("Draft discarded");
                confirm.close();
            }));
            confirm.open();
        });
        add(status, discard);
    }
}
