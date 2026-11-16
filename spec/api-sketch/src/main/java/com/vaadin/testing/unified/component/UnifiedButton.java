package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;

/** No label filter: a button is found by text or accessible name (ADR 0001). */
@UnifiedComponent(Button.class)
public interface UnifiedButton extends UnifiedWrapper, CommonFilters<UnifiedButton>, TextFilter<UnifiedButton> {

    default void click() { element().click(); }
    default String getText() { return element().getText(); }
}
