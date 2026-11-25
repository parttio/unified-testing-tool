package demo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.breadcrumbs.Breadcrumbs;
import com.vaadin.flow.component.breadcrumbs.BreadcrumbsItem;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * One component per accessible-name rule (#641), each with a test id: {@code aria-labelledby} (one id, two ids)
 * before {@code aria-label}, a field's label, {@code setAriaLabel} and {@code setAriaLabelledBy}, and the text of
 * the components named by their content (a button, a link, a heading), which a {@code Div} is not; and the names
 * Vaadin's web components render in the browser, which Browserless computes from the server-side elements.
 */
@AnonymousAllowed
@Route("accessible-names")
public class AccessibleNamesView extends VerticalLayout {

    public AccessibleNamesView() {
        Span billing = title("title-billing", "Billing");
        Span address = title("title-address", "address");
        Span contacts = title("title-contacts", "Contacts");
        Span street = title("title-street", "Street");
        add(billing, address, contacts, street);

        Div ariaLabel = new Div("Shown text");
        ariaLabel.getElement().setAttribute("aria-label", "Shipping");
        Div labelledByOne = new Div("Shown text");
        labelledByOne.getElement().setAttribute("aria-labelledby", "title-billing");
        Div labelledByTwo = new Div("Shown text");
        labelledByTwo.getElement().setAttribute("aria-labelledby", "title-billing title-address");
        Div both = new Div("Shown text");
        both.getElement().setAttribute("aria-label", "Ignored");
        both.getElement().setAttribute("aria-labelledby", "title-contacts");
        add(withTestId(ariaLabel, "aria-label"), withTestId(labelledByOne, "labelledby-one"),
                withTestId(labelledByTwo, "labelledby-two"), withTestId(both, "labelledby-and-aria-label"));

        TextField city = new TextField("City");
        TextField postalCode = new TextField();
        postalCode.setAriaLabel("Postal code");
        TextField streetField = new TextField();
        streetField.setAriaLabelledBy("title-street");
        TextField country = new TextField("Country");
        country.setAriaLabel("Country of residence");
        TextField placeholderOnly = new TextField();
        placeholderOnly.setPlaceholder("Search");
        add(withTestId(city, "field-label"), withTestId(postalCode, "field-aria-label"),
                withTestId(streetField, "field-labelledby"), withTestId(country, "field-label-and-aria-label"),
                withTestId(placeholderOnly, "field-placeholder"));

        Checkbox terms = new Checkbox("Accept terms");
        RadioButtonGroup<String> size = new RadioButtonGroup<>("Size");
        size.setItems("S", "M", "L");
        add(withTestId(terms, "checkbox-label"), withTestId(size, "group-label"));

        Button save = new Button("Save", VaadinIcon.CHECK.create());
        save.setTooltipText("Save the draft");
        Button delete = new Button("Del");
        delete.setAriaLabel("Delete draft");
        add(withTestId(save, "button-text-and-icon"), withTestId(delete, "button-aria-label"));

        add(withTestId(new Anchor("#help", "Help"), "anchor"),
                withTestId(new RouterLink("Profile", ProfileView.class), "router-link"),
                withTestId(new NativeButton("Native"), "native-button"),
                withTestId(new H2("Heading"), "heading"),
                withTestId(new Div("Just text"), "div-text"));

        // what Vaadin's web components render: a select's value, an avatar's abbreviation, a side nav's or a card's
        // title, a dialog's header title, a badge's and a breadcrumb's own text
        Select<String> plan = new Select<>();
        plan.setLabel("Plan");
        plan.setItems("Free", "Pro");
        plan.setItemLabelGenerator(item -> item + " plan");
        plan.setValue("Pro");
        Select<String> region = new Select<>();
        region.setLabel("Region");
        region.setItems("North", "South");
        region.setPlaceholder("Pick one");
        Select<String> unlabelledSelect = new Select<>();
        unlabelledSelect.setAriaLabel("Currency");
        unlabelledSelect.setItems("EUR", "USD");
        CheckboxGroup<String> extras = new CheckboxGroup<>("Extras");
        extras.setItems("Lunch");
        DateTimePicker departure = new DateTimePicker("Departure");
        Avatar avatar = new Avatar("Ada Lovelace");
        SideNav nav = new SideNav("Sections");
        nav.addItem(new SideNavItem("Home", "home"));
        Card card = new Card();
        card.setTitle("Summary");
        card.add(new Span("Card content"));
        Badge badge = new Badge("Paid", VaadinIcon.CHECK.create());
        Breadcrumbs breadcrumbs = new Breadcrumbs(Breadcrumbs.Mode.MANUAL);
        BreadcrumbsItem crumb = new BreadcrumbsItem("Orders", "orders");
        breadcrumbs.add(crumb);
        add(withTestId(plan, "select-value"), withTestId(region, "select-placeholder"),
                withTestId(unlabelledSelect, "select-aria-label"), withTestId(extras, "checkbox-group-label"),
                withTestId(departure, "date-time-picker-label"), withTestId(avatar, "avatar"),
                withTestId(nav, "side-nav"), withTestId(card, "card"), withTestId(badge, "badge"), breadcrumbs);
        withTestId(crumb, "breadcrumb");

        Dialog dialog = new Dialog("Shipping options");
        dialog.setModal(false);
        dialog.add(new Span("Pick a carrier."));
        add(dialog);
        dialog.open();
        Notification saved = new Notification("Draft saved");
        saved.setDuration(0);
        saved.open();
    }

    private static Span title(String id, String text) {
        Span title = new Span(text);
        title.setId(id);
        return title;
    }

    private static <C extends Component> C withTestId(C component, String testId) {
        component.getElement().setAttribute("data-testid", testId);
        return component;
    }
}
