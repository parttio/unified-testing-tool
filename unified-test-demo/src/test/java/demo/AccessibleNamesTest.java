package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.breadcrumbs.BreadcrumbsItem;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedLink;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.vaadin.addons.dramafinder.element.ButtonElement;
import org.vaadin.addons.dramafinder.element.GenericElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;
import org.vaadin.addons.dramafinder.element.query.AccessibleNames;

/**
 * The accessible name follows the browser's order on every Testing Tool (#641): {@code aria-labelledby} before
 * {@code aria-label}, then a field's own label, then the own text of a component named by its content; nothing
 * else names a component. {@code getAccessibleName()} and {@code withAccessibleName(..)} agree on each.
 */
@UnifiedSpringBootTest
class AccessibleNamesTest {

    @Test
    @DisplayName("Components named by aria-label and aria-labelledby, labelledby first")
    void ariaLabelledByComesBeforeAriaLabel(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);

        named(page, Div.class, "aria-label", "Shipping");
        named(page, Div.class, "labelledby-one", "Billing");
        named(page, Div.class, "labelledby-two", "Billing address");
        named(page, Div.class, "labelledby-and-aria-label", "Contacts");
    }

    // --8<-- [start:aDivHasNoAccessibleName]
    @Test
    @DisplayName("A div with only text has no accessible name: it is found by its text")
    void aDivWithTextHasNoAccessibleName(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);

        assertThat(page.find(UnifiedDiv.class).withAccessibleName("Just text")).doesNotExist();
        assertThat(page.find(UnifiedDiv.class).withText("Just text")).exists();
    }
    // --8<-- [end:aDivHasNoAccessibleName]

    @Test
    @DisplayName("A div with only text reads no accessible name")
    void aDivWithTextReadsNoAccessibleName(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);

        named(page, Div.class, "div-text", null);
        assertThat(page.findComponent(Div.class).withTestId("div-text").withAccessibleName("Just text"))
                .doesNotExist();
    }

    @Test
    @DisplayName("Fields named by their label, setAriaLabel or setAriaLabelledBy, never their placeholder")
    void fieldsAreNamedByTheirLabel(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);

        named(page, TextField.class, "field-label", "City");
        named(page, TextField.class, "field-aria-label", "Postal code");
        named(page, TextField.class, "field-labelledby", "Street");
        named(page, TextField.class, "field-label-and-aria-label", "Country of residence");
        named(page, TextField.class, "field-placeholder", null);
        named(page, Checkbox.class, "checkbox-label", "Accept terms");
        named(page, RadioButtonGroup.class, "group-label", "Size");
        named(page, CheckboxGroup.class, "checkbox-group-label", "Extras");
        named(page, DateTimePicker.class, "date-time-picker-label", "Departure");
        assertThat(page.find(UnifiedTextField.class).withAccessibleName("Postal code")).exists();
    }

    @Test
    @DisplayName("A select is named by its label and the value it shows")
    void aSelectIsNamedByItsLabelAndValue(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);

        named(page, Select.class, "select-value", "Plan Pro plan");
        named(page, Select.class, "select-placeholder", "Region Pick one");
        named(page, Select.class, "select-aria-label", "Currency");
    }

    @Test
    @DisplayName("Buttons, links and headings are named by their own text, without icons and tooltips")
    void contentNamesButtonsLinksAndHeadings(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);

        named(page, Button.class, "button-text-and-icon", "Save");
        named(page, Button.class, "button-aria-label", "Delete draft");
        named(page, Anchor.class, "anchor", "Help");
        named(page, RouterLink.class, "router-link", "Profile");
        named(page, NativeButton.class, "native-button", "Native");
        named(page, H2.class, "heading", "Heading");
        named(page, Badge.class, "badge", "Paid");
        named(page, BreadcrumbsItem.class, "breadcrumb", "Orders");
        assertThat(page.find(UnifiedButton.class).withAccessibleName("Delete draft")).hasText("Del");
        assertThat(page.find(UnifiedLink.class).withAccessibleName("Help")).exists();
    }

    @Test
    @DisplayName("Avatars, side navs, cards, dialogs and notifications are named as their web components render them")
    void webComponentsNameTheirHosts(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);

        named(page, Avatar.class, "avatar", "Ada Lovelace (AL)");
        named(page, SideNav.class, "side-nav", "Sections");
        named(page, Card.class, "card", "Summary");
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withAccessibleName("Shipping options");
        assertEquals("Shipping options", dialog.element().getAccessibleName());
        UnifiedNotification notification = page.find(UnifiedNotification.class).withAccessibleName("Draft saved");
        assertEquals("Draft saved", notification.element().getAccessibleName());
    }

    /** On Playwright, the name is Dramafinder's {@code getAccessibleName()}. */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("Playwright reads the accessible name Dramafinder reads")
    void playwrightReadsDramafindersName(UnifiedPage page) {
        page.navigate(AccessibleNamesView.class);
        Page browser = page.unwrap(Page.class);

        for (String testId : new String[] { "aria-label", "labelledby-one", "labelledby-two",
                "labelledby-and-aria-label", "div-text" }) {
            assertEquals(new GenericElement(byTestId(browser, testId)).getAccessibleName(),
                    page.findComponent(Div.class).withTestId(testId).element().getAccessibleName(), testId);
        }
        for (String testId : new String[] { "field-label", "field-aria-label", "field-labelledby",
                "field-label-and-aria-label", "field-placeholder" }) {
            assertEquals(new TextFieldElement(byTestId(browser, testId)).getAccessibleName(),
                    page.findComponent(TextField.class).withTestId(testId).element().getAccessibleName(), testId);
        }
        for (String testId : new String[] { "button-text-and-icon", "button-aria-label" }) {
            assertEquals(new ButtonElement(byTestId(browser, testId)).getAccessibleName(),
                    page.findComponent(Button.class).withTestId(testId).element().getAccessibleName(), testId);
        }
        // Dramafinder has no link element: a link is read as it reads a button
        assertEquals(AccessibleNames.labelledOrOwnText(byTestId(browser, "anchor")),
                page.findComponent(Anchor.class).withTestId("anchor").element().getAccessibleName());
    }

    /** The name read, and a lookup by it finding the same component; with no name, none to look up. */
    private static void named(UnifiedPage page, Class<? extends Component> type, String testId, String name) {
        assertEquals(name, page.findComponent(type).withTestId(testId).element().getAccessibleName(), testId);
        if (name != null) {
            assertThat(page.findComponent(type).withAccessibleName(name).withTestId(testId)).exists();
        }
    }

    private static Locator byTestId(Page browser, String testId) {
        return browser.locator("[data-testid='" + testId + "']");
    }
}
