package demo;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/** The card a {@code LitRenderer} draws in each row of the {@code cards} grid, by its tag (#608). */
@UnifiedComponent(value = Component.class, tag = "demo-card", kind = "card")
public interface DemoCard extends UnifiedWrapper, CommonFilters<DemoCard> {

    /** Clicks it as a user does; Auto-Wait. */
    default void click() { element().click(); }
}
