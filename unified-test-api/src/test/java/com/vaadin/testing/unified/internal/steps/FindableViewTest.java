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

    // ---------------------------------------------------------------- Reach, in a browser (ADR 0011)

    private static final com.vaadin.testing.unified.ViewportSize PHONE =
            new com.vaadin.testing.unified.ViewportSize("phone", 375, 667);

    /** The layout script's result with these matches for the first target, the Findable View's first type. */
    private static String layout(String matchesOfEveryTarget) {
        return "{\"findings\":[],\"matches\":" + matchesOfEveryTarget + ",\"appLayouts\":[],\"menuBars\":[]}";
    }

    @Test
    void inABrowserTheViewSaysWhichViewportItDescribes() {
        FakePage browser = new FakePage(com.vaadin.testing.unified.TestingTool.PLAYWRIGHT, PHONE);
        browser.add("vaadin-button", "text", "Save");
        browser.layout = layout("[]");

        assertEquals("Findable view of “/people” on playwright at Viewport phone 375 × 667: 1 component",
                browser.page.describe().lines().findFirst().orElseThrow());
    }

    @Test
    void inABrowserEachLineSaysWhatClipsCoversOrHidesItsComponent() {
        FakePage browser = new FakePage(com.vaadin.testing.unified.TestingTool.PLAYWRIGHT, PHONE);
        browser.add("vaadin-button", "text", "Save").add("vaadin-button", "text", "Delete")
                .add("vaadin-button", "text", "Share", "visible", "false").add("vaadin-button", "text", "Help");
        browser.reaches.put("vaadin-button", List.of("behind:the AppLayout drawer", "clipped:div#box",
                "hidden:the MenuBar overflow", "after scroll"));

        assertEquals(List.of(
                "button “Save” (behind the AppLayout drawer)  page.find(UnifiedButton.class).withText(\"Save\")",
                "button “Delete” (clipped)               page.find(UnifiedButton.class).withText(\"Delete\")",
                "button “Share” (hidden in the MenuBar overflow)  page.find(UnifiedButton.class).withText(\"Share\")",
                "button “Help”                           page.find(UnifiedButton.class).withText(\"Help\")"),
                lines(browser.page.describe()));
    }

    @Test
    void whenTheScriptAndTheLookupDisagreeOnHowManyThereAreTheLinesSayNothingOfReach() {
        FakePage browser = new FakePage(com.vaadin.testing.unified.TestingTool.PLAYWRIGHT, PHONE);
        browser.add("vaadin-button", "text", "Save").add("vaadin-button", "text", "Delete");
        browser.reaches.put("vaadin-button", List.of("clipped:div#box"));

        assertEquals(List.of("button “Save”    page.find(UnifiedButton.class).withText(\"Save\")",
                "button “Delete”  page.find(UnifiedButton.class).withText(\"Delete\")"), lines(browser.page.describe()));
    }

    @Test
    void clickingAComponentSomethingCoversFailsSayingWhatCoversIt() {
        FakePage browser = new FakePage(com.vaadin.testing.unified.TestingTool.PLAYWRIGHT, PHONE);
        browser.add("vaadin-button", "text", "Save");
        RuntimeException intercepted = new RuntimeException("<div part=\"backdrop\"> intercepts pointer events");
        browser.clickError = intercepted;
        browser.subjectReach = "behind:the AppLayout drawer";

        com.vaadin.testing.unified.UnifiedAssertionError e = org.junit.jupiter.api.Assertions.assertThrows(
                com.vaadin.testing.unified.UnifiedAssertionError.class,
                () -> browser.page.find(com.vaadin.testing.unified.component.UnifiedButton.class).withText("Save").click());

        assertEquals("a reachable button with text “Save”", e.getExpected());
        assertEquals("behind the AppLayout drawer", e.getFound());
        org.junit.jupiter.api.Assertions.assertSame(intercepted, e.getCause());
    }

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
    void aKindWhoseLookupFailsIsNamedWithTheErrorRatherThanLeftOut() {
        fake.add("vaadin-button", "text", "Save");
        fake.findErrors.put("a", new UnsupportedOperationException("Browserless can't read this link's text\nmore"));

        assertEquals("""
                Findable view of “/people” on browserless: 1 component
                button “Save”  page.find(UnifiedButton.class).withText("Save")
                Not listed: link (UnsupportedOperationException: Browserless can't read this link's text)
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
    void aComponentWhoseOnlyUniqueValueIsItsIdIsFoundByIt() {
        fake.add("vaadin-button", "id", "save")
                .add("vaadin-button", "id", "cancel")
                .add("vaadin-button", "text", "Edit", "id", "edit")
                .add("vaadin-button", "text", "Edit", "id", "edit-2");

        assertEquals(List.of(
                "button (no name; id “save”)  page.find(UnifiedButton.class).withId(\"save\")",
                "button (no name; id “cancel”)  page.find(UnifiedButton.class).withId(\"cancel\")",
                "button “Edit”  page.find(UnifiedButton.class).withId(\"edit\")",
                "button “Edit”  page.find(UnifiedButton.class).withId(\"edit-2\")"),
                lines(page.describe()).stream().map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }

    @Test
    void anIdComesAfterTheNamesAndTheTestIdOutsideATemplate() {
        fake.add("vaadin-button", "text", "Save", "id", "save")
                .add("vaadin-button", "data-testid", "close", "id", "close-button");

        assertEquals(List.of(
                "button “Save”  page.find(UnifiedButton.class).withText(\"Save\")",
                "button (no name; test id “close”)  page.find(UnifiedButton.class).withTestId(\"close\")"),
                lines(page.describe()).stream().map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }

    @Test
    void flowsRootIdIsNeverSuggested() {
        fake.add("vaadin-button", "id", "ROOT-2521314").add("vaadin-button", "id", "save");

        assertEquals(List.of(
                "button (no name)  not unique: 2 alike",
                "button (no name; id “save”)  page.find(UnifiedButton.class).withId(\"save\")"),
                lines(page.describe()).stream().map(l -> l.replaceAll(" {2,}", "  ")).toList());
    }

    @Tag("order-details")
    public static class OrderDetails extends Component {
    }

    @Test
    void insideATemplateTheIdComesFirst() {
        // the fake page ignores scopes but for a test id: the template's components are "in" it
        fake.add("order-details", "testId", "details")
                .add("vaadin-button", "text", "Save", "id", "save", "in", "details")
                .add("vaadin-button", "id", "cancel", "in", "details")
                .add("vaadin-button", "text", "Help", "in", "details");

        assertEquals(List.of(
                "button “Save”  find(UnifiedButton.class).withId(\"save\")",
                "button (no name; id “cancel”)  find(UnifiedButton.class).withId(\"cancel\")",
                "button “Help”  find(UnifiedButton.class).withText(\"Help\")"),
                lines(page.findTemplate(OrderDetails.class).describe()).stream()
                        .map(l -> l.replaceAll(" {2,}", "  ")).toList());
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
