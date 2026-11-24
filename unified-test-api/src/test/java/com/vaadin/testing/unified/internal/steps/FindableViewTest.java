package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Findable View ({@code describe()}) on a fake page. Public for its custom Wrapper. */
public class FindableViewTest {

    private final FakePage fake = new FakePage();
    private final UnifiedPage page = fake.page;

    @Test
    void eachComponentWithItsNameStateAndLookup() {
        fake.add("vaadin-text-field", "label", "Name", "value", "Alan Turing")
                .add("vaadin-text-field", "label", "Email")
                .add("vaadin-button", "text", "Save", "enabled", "false")
                .add("vaadin-button", "text", "Cancel")
                .add("vaadin-checkbox", "label", "Active", "checked", "true");

        assertEquals("""
                Findable view of “/people” on browserless: 5 components
                text field “Name” = “Alan Turing”  page.find(UnifiedTextField.class).withLabel("Name")
                text field “Email”                 page.find(UnifiedTextField.class).withLabel("Email")
                checkbox “Active” (checked)        page.find(UnifiedCheckbox.class).withLabel("Active")
                button “Save” (disabled)           page.find(UnifiedButton.class).withText("Save")
                button “Cancel”                    page.find(UnifiedButton.class).withText("Cancel")
                """, page.describe());
    }

    @Test
    void aMessageListShowsItsMessageCount() {
        fake.add("vaadin-message-list", "messages", "Assistant:Hi!|You:Soup|Assistant:Here is a recipe.");

        assertEquals(List.of("message list, 3 messages  page.find(UnifiedMessageList.class)"), lines(page.describe()));
    }

    @Test
    void aComponentsOwnPartIsLeftOutButAFieldTheApplicationAddsIsListed() {
        fake.add("vaadin-message-input")
                .add("vaadin-text-area", "label", "Message", "internalPart", "true")
                .add("vaadin-text-area", "label", "Notes");

        assertEquals(List.of(
                "text area “Notes”  page.find(UnifiedTextArea.class).withLabel(\"Notes\")",
                "message input      page.find(UnifiedMessageInput.class)"), lines(page.describe()));
    }

    @Test
    void aComponentWithoutANameIsFoundByItsTestId() {
        fake.add("vaadin-button", "text", "Save").add("vaadin-button", "data-testid", "close");

        assertEquals(List.of(
                "button “Save”                      page.find(UnifiedButton.class).withText(\"Save\")",
                "button (no name; test id “close”)  page.find(UnifiedButton.class).withTestId(\"close\")"),
                lines(page.describe()));
    }

    @Test
    void twoAlikeAreToldApartByAnotherFilterOrShownAsNotUnique() {
        fake.add("vaadin-button", "text", "Save", "data-testid", "billing-save")
                .add("vaadin-button", "text", "Save", "data-testid", "shipping-save")
                .add("vaadin-text-field", "label", "City")
                .add("vaadin-text-field", "label", "City");

        assertEquals(List.of(
                "text field “City”  not unique: 2 alike",
                "text field “City”  not unique: 2 alike",
                "button “Save”      page.find(UnifiedButton.class).withTestId(\"billing-save\")",
                "button “Save”      page.find(UnifiedButton.class).withTestId(\"shipping-save\")"),
                lines(page.describe()));
    }

    @Test
    void aContainerHintCountsWhatItHoldsAndIsLeftOutWhenEveryRowSharesIt() {
        fake.add("vaadin-form-layout", "testId", "billing")
                .add("vaadin-button", "text", "Save", "in", "billing")
                .add("vaadin-button", "text", "Save", "in", "billing")
                .add("vaadin-button", "text", "Save")
                // three rows with the same test id, a Pin button in each (#522)
                .add("vaadin-horizontal-layout", "testId", "meal-row")
                .add("vaadin-horizontal-layout", "testId", "meal-row")
                .add("vaadin-horizontal-layout", "testId", "meal-row")
                .add("vaadin-text-field", "label", "Pin", "in", "meal-row")
                .add("vaadin-text-field", "label", "Pin", "in", "meal-row")
                .add("vaadin-text-field", "label", "Pin", "in", "meal-row");

        assertEquals(List.of(
                "text field “Pin”  not unique: 3 alike",
                "text field “Pin”  not unique: 3 alike",
                "text field “Pin”  not unique: 3 alike",
                "button “Save”     not unique: 3 alike; 2 in container (test id “billing”)",
                "button “Save”     not unique: 3 alike; 2 in container (test id “billing”)",
                "button “Save”     not unique: 3 alike"), lines(page.describe()));
    }

    @Test
    void plainHtmlThatSaysItIsClickableIsListedWithWhy() {
        fake.add("span", "text", "Week of 2026-11-16", "role", "button")
                .add("div", "text", "Mon", "role", "tab")
                .add("div", "text", "Tue", "role", "Tab ")
                .add("article", "text", "Pancakes", "tabindex", "0")
                // not listed: focusable by script only, a role nobody clicks, and plain ones
                .add("span", "text", "Skip", "tabindex", "-1")
                .add("div", "text", "Layout", "role", "presentation")
                .add("div", "text", "Just text")
                // a role and a tabindex: listed once, for its role
                .add("span", "text", "Help", "role", "link", "tabindex", "0");

        assertEquals(List.of(
                "div “Mon” (role tab)  page.find(UnifiedDiv.class).withText(\"Mon\")",
                "div “Tue” (role tab)  page.find(UnifiedDiv.class).withText(\"Tue\")",
                "span “Week of 2026-11-16” (role button)  page.find(UnifiedSpan.class).withText(\"Week of 2026-11-16\")",
                "span “Help” (role link)  page.find(UnifiedSpan.class).withText(\"Help\")",
                "article “Pancakes” (focusable)  page.find(UnifiedArticle.class).withText(\"Pancakes\")"),
                lines(page.describe()).stream().map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }

    @Test
    void aClickableOnesTextSharedWithAPlainOneIsNoLookupOfItsOwn() {
        fake.add("span", "text", "Save", "role", "button")
                .add("span", "text", "Save");

        assertEquals(List.of("span “Save” (role button)  not unique: 2 alike"), lines(page.describe()).stream().map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }

    @Test
    void alikeComponentsAreToldApartByADataAttributeOnlyTheirsHas() {
        fake.add("vaadin-button", "text", "Pin", "data-pin-date", "2026-11-23", "data-kind", "meal")
                .add("vaadin-button", "text", "Pin", "data-pin-date", "2026-11-24", "data-kind", "meal")
                .add("vaadin-button", "text", "Save", "data-testid", "save", "data-kind", "form")
                .add("vaadin-button", "text", "Save", "data-testid", "save", "data-kind", "form");

        assertEquals(List.of(
                // data-kind is shared, data-pin-date is not: the first attribute only this one has
                "button “Pin”  page.find(UnifiedButton.class).withText(\"Pin\").withAttribute(\"data-pin-date\", \"2026-11-23\")",
                "button “Pin”  page.find(UnifiedButton.class).withText(\"Pin\").withAttribute(\"data-pin-date\", \"2026-11-24\")",
                // the test id is a name of its own, and no data-* attribute tells these apart
                "button “Save”  not unique: 2 alike",
                "button “Save”  not unique: 2 alike"),
                lines(page.describe()).stream().map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }

    @Test
    void aDataAttributeStillTellsThemApartUnderFlowsRoot() {
        // a browser sees Flow's root, ROOT-<n>, as the nearest ancestor with an id: never a container to name
        fake.add("vaadin-button", "text", "Pin", "data-pin-date", "2026-11-23", "inId", "ROOT-2521314")
                .add("vaadin-button", "text", "Pin", "data-pin-date", "2026-11-24", "inId", "ROOT-2521314");

        assertEquals(List.of(
                "button “Pin”  page.find(UnifiedButton.class).withText(\"Pin\").withAttribute(\"data-pin-date\", \"2026-11-23\")",
                "button “Pin”  page.find(UnifiedButton.class).withText(\"Pin\").withAttribute(\"data-pin-date\", \"2026-11-24\")"),
                lines(page.describe()).stream().map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }

    @Test
    void aSecretValueIsMasked() {
        fake.add("vaadin-text-field", "label", "API key", "value", page.secret("s3cr3t-key"));

        assertEquals(List.of("text field “API key” = “••••”  page.find(UnifiedTextField.class).withLabel(\"API key\")"),
                lines(page.describe()));
    }

    @Test
    void anEmptyPageSaysSo() {
        assertEquals("Findable view of “/people” on browserless: no component a Unified UI Test can find\n",
                page.describe());
    }

    @Test
    void aContainersViewHasLookupsOnTheContainer() {
        fake.add("vaadin-dialog", "headerTitle", "Edit person").add("vaadin-text-field", "label", "Name");

        String view = page.find(UnifiedDialog.class).withHeaderTitle("Edit person").describe();

        // the fake page ignores scopes, so the dialog is in its own view here
        assertEquals("""
                Findable view inside dialog “Edit person” on browserless; lookups are on it: 2 components
                dialog “Edit person”  find(UnifiedDialog.class).withHeaderTitle("Edit person")
                text field “Name”     find(UnifiedTextField.class).withLabel("Name")
                """, view);
    }

    @Test
    void aGridOrADialogWithoutANameIsNoAccessibilityFinding() {
        fake.add("vaadin-dialog", "text", "CancelSave").add("vaadin-grid").add("vaadin-button");

        assertEquals(List.of(
                "dialog            page.find(UnifiedDialog.class)",
                "button (no name)  page.find(UnifiedButton.class)",
                "grid              page.find(UnifiedGrid.class)"), lines(page.describe()));
    }

    @Test
    void aComponentInAnOpenDialogIsShownWithTheDialogsLookup() {
        fake.add("vaadin-dialog", "headerTitle", "Change email")
                .add("vaadin-button", "text", "Save").add("vaadin-button", "text", "Save");

        // the fake page ignores scopes: both buttons are "in" the dialog, so it can't tell them apart either
        assertEquals(List.of(
                "dialog “Change email”  page.find(UnifiedDialog.class).withHeaderTitle(\"Change email\")",
                "button “Save”          not unique: 2 alike",
                "button “Save”          not unique: 2 alike"), lines(page.describe()));
    }

    @Test
    void describingIsNotAUserStep() {
        fake.add("vaadin-button", "text", "Save");

        page.describe();

        assertEquals("(no User Steps)", fake.lines().strip());
    }

    @Tag("star-rating")
    public static class StarRating extends Component {
    }

    @UnifiedComponent(value = StarRating.class, kind = "star rating")
    public interface UnifiedStarRating extends UnifiedWrapper, CommonFilters<UnifiedStarRating>,
            TextFilter<UnifiedStarRating> {
    }

    @Test
    void aCustomWrapperIsListedOnceTheRunHasFoundIt() {
        fake.add("star-rating", "text", "Quality");
        page.find(UnifiedStarRating.class);

        assertEquals(List.of("star rating “Quality”  page.find(UnifiedStarRating.class).withText(\"Quality\")"), lines(page.describe()));
    }

    @Test
    void aLargeViewIsCut() {
        for (int i = 0; i < 105; i++) {
            fake.add("vaadin-button", "text", "Button " + i);
        }

        List<String> lines = lines(page.describe());

        assertEquals(101, lines.size());
        assertEquals("… and 5 more: describe a container to see them", lines.get(100));
    }

    /** The view without its header line. */
    private static List<String> lines(String view) {
        List<String> lines = List.of(view.split("\n"));
        return lines.subList(1, lines.size());
    }
}
