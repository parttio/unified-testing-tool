package demo;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.checkbox.testbench.CheckboxElement;
import com.vaadin.flow.component.textfield.testbench.NumberFieldElement;
import com.vaadin.testbench.HasElementQuery;

/**
 * A TestBench page object of the preferences view, used by {@code PreferencesPageObjectTestBenchTest}. It has no
 * converted twin: the skill evaluation's agent can only resolve its marker by converting it, or keep it (#134).
 */
class PreferencesTestBenchPage {

    private final HasElementQuery test;

    PreferencesTestBenchPage(HasElementQuery test) {
        this.test = test;
    }

    void height(double centimetres) {
        test.$(NumberFieldElement.class).withLabel("Height (cm)").single().setValue(String.valueOf(centimetres));
    }

    void subscribe() {
        test.$(CheckboxElement.class).withLabel("Subscribe to newsletter").single().setChecked(true);
    }

    void save() {
        test.$(ButtonElement.class).withText("Save").single().click();
    }
}
