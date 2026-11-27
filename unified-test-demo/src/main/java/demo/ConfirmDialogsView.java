package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Two ConfirmDialogs, each recording the answer the user gave: "Delete order" asks with Confirm, Reject and Cancel
 * enabled and the application's own button texts; "Leave page" asks without a header, with Confirm and Cancel and
 * the web component's default texts.
 */
@AnonymousAllowed
@Route("confirm-dialogs")
public class ConfirmDialogsView extends VerticalLayout {

    private final Span answer = new Span("No answer");

    public ConfirmDialogsView() {
        answer.setId("answer");
        Button delete = new Button("Delete order", e -> {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Delete order?");
            dialog.setText("The order can't be restored.");
            dialog.setConfirmText("Delete");
            dialog.setRejectable(true);
            dialog.setRejectText("Archive instead");
            dialog.setCancelable(true);
            dialog.setCancelText("Keep order");
            record(dialog);
            dialog.open();
        });
        Button leave = new Button("Leave page", e -> {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setText("Leave without saving?");
            dialog.setCancelable(true);
            record(dialog);
            dialog.open();
        });
        add(answer, delete, leave);
    }

    private void record(ConfirmDialog dialog) {
        dialog.addConfirmListener(e -> answer.setText("Confirmed"));
        dialog.addRejectListener(e -> answer.setText("Rejected"));
        dialog.addCancelListener(e -> answer.setText("Cancelled"));
    }
}
