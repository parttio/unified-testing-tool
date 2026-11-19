package com.vaadin.testing.unified.browserless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.browserless.internal.PrettyPrintTreeKt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.testing.unified.internal.steps.TreePage;
import org.junit.jupiter.api.Test;

/** The component tree Browserless evidence writes: Karibu's, with the step's component marked. */
class BrowserlessTreeTest {

    private final TextField name = new TextField("Name");
    private final Button save = new Button("Save");
    private final VerticalLayout root = new VerticalLayout(new H1("People"), new HorizontalLayout(name), save);

    @Test
    void isKaribusTreeWhenTheStepActedOnNothing() {
        assertEquals(PrettyPrintTreeKt.toPrettyTree(root), BrowserlessSession.tree(root, null).print());
    }

    @Test
    void marksTheLineOfTheComponentTheStepActedOn() {
        String tree = BrowserlessSession.tree(root, name).print();

        assertTrue(tree.contains("TextField[label='Name', value='', manualValidation='true']" + TreePage.TARGET_MARK
                + "\n"), tree);
        assertEquals(1, tree.split(TreePage.TARGET_MARK, -1).length - 1, tree);
        // the rest as Karibu writes it
        assertEquals(PrettyPrintTreeKt.toPrettyTree(root), tree.replace(TreePage.TARGET_MARK, ""));
    }
}
