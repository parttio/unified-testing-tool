package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;
import java.time.Duration;

/** No label filter: a button is found by text or accessible name (ADR 0001). */
@UnifiedComponent(Button.class)
public interface UnifiedButton extends UnifiedWrapper, CommonFilters<UnifiedButton>, TextFilter<UnifiedButton> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedButton withTimeout(Duration timeout);

    default void click() { element().click(); }
    default String getText() { return element().getText(); }
}
