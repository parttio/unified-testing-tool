package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * Any component used to narrow a search, typically a layout (FormLayout, VerticalLayout, Div, …), which has
 * no wrapper of its own. Find it by test id or id, then search inside it:
 *
 * <pre>{@code
 * UnifiedContainer billing = page.find(UnifiedContainer.class).withTestId("billing");
 * billing.find(UnifiedTextField.class).withLabel("City").setValue("Turku");
 * }</pre>
 * A layout with a click listener, such as a card, is clicked with {@link #click()}.
 */
@UnifiedComponent(value = Component.class, tag = "*")
public interface UnifiedContainer extends UnifiedWrapper, Scope, CommonFilters<UnifiedContainer> {

    /** Clicks it as a user does, for a click listener ({@code addClickListener}); Auto-Wait. */
    default void click() { element().click(); }
}
