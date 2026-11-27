package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** A view that fits a phone (ADR 0011): no Layout Findings at 375 px, one button only reachable by scrolling down. */
@AnonymousAllowed
@Route("layout-clean")
public class LayoutCleanView extends VerticalLayout {

    public LayoutCleanView() {
        TextField name = new TextField("Name");
        name.setWidthFull();
        FlexLayout buttons = new FlexLayout(new Button("Save"), new Button("Cancel"), new Button("Delete"));
        buttons.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        Div spacer = new Div();
        spacer.setHeight("1200px");
        add(name, buttons, spacer, new Button("Below the fold"));
    }
}
