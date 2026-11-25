package demo;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * The pencil a {@code LitRenderer} draws in the {@code editable} grid, by its tag: it has no Flow class, as Crud's
 * {@code vaadin-crud-edit} has none (#608).
 */
@UnifiedComponent(value = Component.class, tag = "demo-edit", kind = "edit pencil")
public interface DemoEdit extends UnifiedWrapper, CommonFilters<DemoEdit> {

    /** Clicks it as a user does; Auto-Wait. */
    default void click() { element().click(); }
}
