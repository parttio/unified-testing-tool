package com.vaadin.testing.unified.internal.steps;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.UserStepVerb;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDateTimePicker;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.component.UnifiedUpload;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Gap;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Group;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Status;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Step;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/** User Steps in words (spec chapter 12, "User Steps"): kinds, verbs, names, brackets, scope, masking, gaps. */
public class UserStepWordingTest {

    private final FakePage fake = new FakePage();

    /** An add-on Wrapper with its own kind and verb. */
    @UnifiedComponent(value = Button.class, kind = "star rating")
    public interface UnifiedStars extends UnifiedWrapper, CommonFilters<UnifiedStars>, LabelFilter<UnifiedStars> {
        @UserStepVerb("give {value} stars to")
        default void rate(int stars) { element().click(); }

        @UserStepVerb("reset")
        default void clearRating() { element().click(); }

        /** No act(): a void non-getter, so still a User Step, with the verb from its name. */
        default void setFavourite(boolean favourite) { }
    }

    /** An add-on Wrapper without overrides: kind and verb from its names. */
    @UnifiedComponent(Button.class)
    public interface UnifiedThumbsUp extends UnifiedWrapper, CommonFilters<UnifiedThumbsUp> {
        default void giveThumb() { element().click(); }
    }

    /** A Wrapper for a password field: what is typed into it never reaches the reports. */
    @UnifiedComponent(PasswordField.class)
    public interface UnifiedSecretField extends UnifiedWrapper, CommonFilters<UnifiedSecretField>,
            LabelFilter<UnifiedSecretField> {
        default void setValue(String value) { element().setValueAsUser(value); }
    }

    private List<String> texts() {
        return fake.steps.entries().stream().map(e -> switch (e) {
            case Step s -> s.phrase().render();
            case Group g -> "group " + g.name();
            case Gap g -> g.text();
        }).toList();
    }

    @Test
    void wrapperActionsReadAsVerbKindNameValue() {
        fake.add("vaadin-text-field", "label", "Name")
                .add("vaadin-button", "text", "Save")
                .add("vaadin-checkbox", "label", "Terms");

        fake.page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
        fake.page.find(UnifiedTextField.class).withLabel("Name").clear();
        fake.page.find(UnifiedButton.class).withText("Save").click();
        fake.page.find(UnifiedCheckbox.class).withLabel("Terms").check();
        fake.page.find(UnifiedCheckbox.class).withLabel("Terms").setChecked(false);
        fake.page.find(UnifiedButton.class).withText("Save").element().click();

        assertEquals(List.of(
                "fill text field “Name” with “Alan M. Turing”",
                "clear text field “Name”",
                "click button “Save”",
                "check checkbox “Terms”",
                "uncheck checkbox “Terms”",
                "click button “Save”"), texts());
    }

    @Test
    void nestedWrapperCallsAndGettersAreNotSteps() {
        fake.add("vaadin-checkbox", "label", "Terms", "checked", "false");
        UnifiedCheckbox terms = fake.page.find(UnifiedCheckbox.class).withLabel("Terms");

        terms.check(); // calls setChecked(true), then element().setValueAsUser(true)
        assertTrue(terms.isChecked()); // a getter: not a step
        terms.getLabel();

        assertEquals(List.of("check checkbox “Terms”"), texts());
        assertEquals(1, fake.steps.stepCount());
    }

    @Test
    void otherFiltersGoInBracketsAndTheScopeNamesTheContainer() {
        fake.add("vaadin-button", "text", "Save", "testId", "billing")
                .add("vaadin-dialog", "headerTitle", "Edit person")
                .add("vaadin-text-field", "label", "Email", "placeholder", "you@example.com");

        fake.page.find(UnifiedButton.class).withText("Save").withTestId("billing").click();
        fake.page.find(UnifiedButton.class).withTestId("billing").visible().click();
        UnifiedDialog dialog = fake.page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
        dialog.find(UnifiedButton.class).withText("Save").click();
        dialog.find(UnifiedTextField.class).withPlaceholder("you@example.com").setValue("a@b.c");
        fake.page.find(UnifiedButton.class).withTextContaining("Sav").click();
        fake.page.find(UnifiedButton.class).all().get(0).click();

        assertEquals(List.of(
                "click button “Save” (test id “billing”)",
                "click button (test id “billing”, visible)",
                "click button “Save” in dialog “Edit person”",
                "fill text field (placeholder “you@example.com”) with “a@b.c” in dialog “Edit person”",
                "click button (text containing “Sav”)",
                "click the 1st button"), texts());
    }

    @Test
    void customFiltersReadAsThemselvesInFailuresToo() {
        fake.add("vaadin-dialog", "headerTitle", "Edit person");
        UnifiedDialog dialog = fake.page.find(UnifiedDialog.class).withHeaderTitle("Edit person");

        assertEquals("UnifiedDialog[UnifiedDialog.withHeaderTitle(\"Edit person\")]", dialog.toString());
        assertEquals("dialog “Edit person”", StepTarget.of(Wrappers.lazyElementOf(dialog)).render(false));
    }

    @Test
    void kindsAreTheWrapperTypeInPlainWords() {
        assertEquals("combo box", StepTarget.kindOf(UnifiedComboBox.class));
        assertEquals("multi-select combo box", StepTarget.kindOf(UnifiedMultiSelectComboBox.class));
        assertEquals("date-time picker", StepTarget.kindOf(UnifiedDateTimePicker.class));
        assertEquals("text field", StepTarget.kindOf(UnifiedTextField.class));
        assertEquals("menu bar", StepTarget.kindOf(UnifiedMenuBar.class));
        assertEquals("thumbs up", StepTarget.kindOf(UnifiedThumbsUp.class));
        assertEquals("HTML view", StepTarget.kindOf(HTMLView.class));
        assertEquals(List.of("Multi", "Select", "Combo", "Box"), StepTarget.splitWords("MultiSelectComboBox"));
    }

    interface HTMLView {}

    @Test
    void findComponentReadsAsTheComponentClass() {
        fake.add("vaadin-button", "text", "Cancel");

        fake.page.findComponent(Button.class).withText("Cancel").element().click();

        assertEquals(List.of("click button “Cancel”"), texts());
    }

    @Test
    void addOnWrappersTakeKindAndVerbFromTheirNamesOrAnnotations() {
        fake.add("vaadin-button", "label", "Quality");

        fake.page.find(UnifiedStars.class).withLabel("Quality").rate(4);
        fake.page.find(UnifiedStars.class).withLabel("Quality").clearRating();
        fake.page.find(UnifiedStars.class).withLabel("Quality").setFavourite(true);
        fake.page.find(UnifiedThumbsUp.class).giveThumb();

        assertEquals(List.of(
                "give “4” stars to star rating “Quality”",
                "reset star rating “Quality”",
                "set favourite star rating “Quality” with “true”",
                "give thumb thumbs up"), texts());
    }

    @Test
    void theVerbTableCoversTheCoreSet() throws Exception {
        fake.add("vaadin-combo-box", "label", "Role")
                .add("vaadin-menu-bar", "testId", "menu")
                .add("vaadin-upload", "testId", "receipt")
                .add("vaadin-multi-select-combo-box", "label", "Tags")
                .add("vaadin-message-input", "testId", "chat");

        assertEquals("choose “Admin” in combo box “Role”",
                phrase(fake.page.find(UnifiedComboBox.class).withLabel("Role"), "selectItem", "Admin"));
        assertEquals("select “a”, “b” in multi-select combo box “Tags”",
                phrase(fake.page.find(UnifiedMultiSelectComboBox.class).withLabel("Tags"), "selectItems",
                        (Object) new String[] { "a", "b" }));
        assertEquals("click “File › Export” in menu bar (test id “menu”)",
                phrase(fake.page.find(UnifiedMenuBar.class).withTestId("menu"), "clickItem", "File",
                        new String[] { "Export" }));
        assertEquals("upload “receipt.pdf” to upload (test id “receipt”)",
                phrase(fake.page.find(UnifiedUpload.class).withTestId("receipt"), "upload", "receipt.pdf",
                        "application/pdf", new byte[1]));
        assertEquals("send “Hello” in message input (test id “chat”)",
                phrase(fake.page.find(UnifiedMessageInput.class).withTestId("chat"), "send", "Hello"));
    }

    private static String phrase(UnifiedWrapper wrapper, String method, Object... args) throws Exception {
        Class<?> type = wrapper.getClass().getInterfaces()[0];
        Method m = java.util.Arrays.stream(type.getMethods())
                .filter(x -> x.getName().equals(method) && x.getParameterCount() == args.length)
                .findFirst().orElseThrow();
        return Phrases.forWrapperCall(type, Wrappers.lazyElementOf(wrapper), m, args).render();
    }

    @Test
    void assertionsReadSeeOrSeeNo() {
        fake.add("vaadin-text-field", "label", "Name", "value", "Alan")
                .add("vaadin-notification", "text", "Saved Alan")
                .add("vaadin-button", "text", "Delete")
                .add("vaadin-button", "text", "Delete")
                .add("vaadin-checkbox", "label", "Terms", "checked", "true");

        assertThat(fake.page.find(UnifiedTextField.class).withLabel("Name")).hasValue("Alan");
        assertThat(fake.page.find(UnifiedNotification.class)).hasText("Saved Alan");
        assertThat(fake.page.find(UnifiedButton.class).withText("Archive")).doesNotExist();
        assertThat(fake.page.find(UnifiedButton.class).withText("Delete")).hasCount(2);
        assertThat(fake.page.find(UnifiedCheckbox.class).withLabel("Terms")).isChecked();
        assertThat(fake.page).isAt(com.vaadin.flow.component.html.Div.class);

        assertEquals(List.of(
                "see text field “Name” with value “Alan”",
                "see notification “Saved Alan”",
                "see no button “Archive”",
                "see 2 buttons “Delete”",
                "see checkbox “Terms” checked",
                "see Div"), texts());
    }

    @Test
    void aMissingColumnReadsAsWithout() {
        fake.add("vaadin-grid", "testId", "archived");
        StepTarget grid = StepTarget.of(Wrappers.lazyElementOf(
                fake.page.find(com.vaadin.testing.unified.component.UnifiedGrid.class).withTestId("archived")));

        assertEquals("see grid (test id “archived”) with column “Archived on”",
                Phrases.forCheck(grid, "to have column", "Archived on").render());
        assertEquals("see grid (test id “archived”) without column “Archived on”",
                Phrases.forCheck(grid, "not to have column", "Archived on").render());
    }

    @Test
    void pageStepsReadAsTheUserWouldSayThem() {
        fake.page.navigate(PeopleView.class);
        fake.page.navigate("profile?tab=2");
        fake.page.pressKey("Escape");

        assertEquals(List.of("open People", "open “profile?tab=2”", "press “Escape”"), texts());
    }

    static class PeopleView extends com.vaadin.flow.component.html.Div {}

    @Test
    void passwordValuesAndSecretsAreMasked() {
        fake.add("vaadin-password-field", "label", "Password")
                .add("vaadin-text-field", "label", "API key", "value", "k-123");

        fake.page.find(UnifiedSecretField.class).withLabel("Password").setValue("hunter2");
        fake.page.find(UnifiedTextField.class).withLabel("API key").setValue(fake.page.secret("k-123"));
        assertThat(fake.page.find(UnifiedTextField.class).withLabel("API key")).hasValue("k-123");

        String lines = fake.lines();
        assertTrue(lines.contains("fill secret field “Password” with “••••”"), lines);
        assertTrue(lines.contains("fill text field “API key” with “••••”"), lines);
        assertTrue(lines.contains("see text field “API key” with value “••••”"), lines);
        assertFalse(lines.contains("hunter2") || lines.contains("k-123"), lines);
        String json = new UserStepsReport("demo.FakeTest", "test", "test", "browserless", "passed", null,
                fake.steps).json();
        assertFalse(json.contains("hunter2") || json.contains("k-123"), json);
    }

    @Test
    void longValuesAreCut() {
        fake.add("vaadin-text-field", "label", "Notes");
        String value = "x".repeat(100);

        fake.page.find(UnifiedTextField.class).withLabel("Notes").setValue(value);

        assertEquals(List.of("fill text field “Notes” with “" + "x".repeat(79) + "…”"), texts());
        assertEquals(80, Phrase.cut(value).length());
        assertEquals("short", Phrase.cut("short"));
    }

    @Test
    void callsTheRecorderCantSeeAreOneGapLinePerRun() {
        fake.add("vaadin-button", "text", "Save");
        UnifiedButton save = fake.page.find(UnifiedButton.class).withText("Save");

        save.tryUnwrap(Button.class);
        fake.page.tryUnwrap(Object.class);
        save.element().tryUnwrap(Button.class);
        save.click();
        fake.page.waitUntil(() -> save.isVisible());
        fake.page.waitUntil(() -> true);
        fake.page.find(UnifiedButton.class).where(e -> true).click();

        assertEquals(List.of(
                "… 3 calls outside the Unified API (Escape Hatch)",
                "click button “Save”",
                "… 2 calls outside the Unified API (waitUntil)",
                "… 1 call outside the Unified API (where(..) condition)",
                "click button (matching a condition)"), texts());
    }

    @Test
    void theApiReachingItsOwnTypesIsNoGap() {
        fake.add("vaadin-button", "text", "Save");

        fake.page.find(UnifiedButton.class).withText("Save").tryUnwrap(com.vaadin.testing.unified.spi.GridAccess.class);

        assertEquals(List.of(), texts());
    }

    @Test
    void stepsNestAndNumberTheirUserSteps() {
        fake.add("vaadin-button", "text", "Save");
        UnifiedButton save = fake.page.find(UnifiedButton.class).withText("Save");

        save.click();
        fake.page.step("Edit Alan Turing", () -> {
            save.click();
            fake.page.step("Save the form", save::click);
            save.click();
        });
        save.click();

        assertEquals("""
                   1  click button “Save”
                   2  Edit Alan Turing
                      2.1  click button “Save”
                      2.2  Save the form
                         2.2.1  click button “Save”
                      2.3  click button “Save”
                   3  click button “Save”
                """, fake.lines());
        Group group = (Group) fake.steps.entries().get(1);
        assertEquals("UserStepWordingTest.java", group.callSite().file());
        assertEquals("stepsNestAndNumberTheirUserSteps", group.callSite().method());
        Step inner = (Step) ((Group) group.children().get(1)).children().get(0);
        assertEquals("2.2.1", inner.index());
        assertEquals(3, inner.number());
    }

    @Test
    void aFailureInsideAStepPointsAtTheStepFirstThenTheInnerLine() {
        UnifiedButton missing = fake.page.find(UnifiedButton.class).withText("Missing");

        AssertionError error = assertThrows(AssertionError.class,
                () -> fake.page.step("Outer", () -> fake.page.step("Inner", missing::click)));

        Step failed = fake.steps.stepThatThrew(error);
        assertEquals("1.1.1", failed.index());
        assertEquals(Status.FAILED, failed.status());
        assertSame(error, failed.error());
        List<CallSite> path = failed.codePath();
        assertEquals(3, path.size(), path.toString());
        assertTrue(path.get(0).line() < path.get(1).line() || path.get(0).line() == path.get(1).line(),
                path.toString());
        path.forEach(site -> assertEquals("UserStepWordingTest.java", site.file()));
        assertEquals(2, fake.steps.groupsThatThrew(error).size());
        assertTrue(fake.lines().contains("1.1.1  click button “Missing”   ✘"), fake.lines());
    }

    /** The expected items in the test's order, so two runs of a scenario word the step alike. */
    @Test
    void selectedItemsAreNamedInTheTestsOrder() {
        FakePage fake = new FakePage().add("vaadin-multi-select-combo-box", "label", "Toppings");
        var toppings = fake.page.find(com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox.class)
                .withLabel("Toppings");

        // the fake page has no selection to read, so the check fails; its step is recorded all the same
        org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> com.vaadin.testing.unified.assertion
                .UnifiedAssertions.assertThat(toppings).hasSelectedItems("Olives", "Ham", "Cheese", "Basil", "Corn", "Egg"));

        org.junit.jupiter.api.Assertions.assertTrue(fake.lines().contains("see multi-select combo box “Toppings” with "
                + "selected items “Olives”, “Ham”, “Cheese”, “Basil”, “Corn”, “Egg”"), fake.lines());
    }
}
