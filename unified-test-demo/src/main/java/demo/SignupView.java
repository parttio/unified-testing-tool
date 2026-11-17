package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A form whose fields have no label of their own: {@code FormLayout.addFormItem(field, label)} labels them, as
 * many forms do. {@code withLabel(..)} finds them by that label (#93).
 */
@AnonymousAllowed
@Route("signup")
public class SignupView extends FormLayout {

    public SignupView() {
        TextField nickname = new TextField();
        ComboBox<String> plan = new ComboBox<>();
        plan.setItems("Free", "Pro");
        Span welcome = new Span();
        welcome.setId("welcome");
        addFormItem(nickname, "Nickname");
        addFormItem(plan, "Plan");
        add(new Button("Sign up", e -> welcome.setText("Welcome " + nickname.getValue() + " on " + plan.getValue())),
                welcome);
    }
}
