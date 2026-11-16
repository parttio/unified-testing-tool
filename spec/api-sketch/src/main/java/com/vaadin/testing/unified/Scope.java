package com.vaadin.testing.unified;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.query.CommonFilters;

/** Something a test can search in: the page, a dialog, a container. */
public interface Scope {

    /**
     * Typed lookup. Returns the Wrapper itself, lazy: filters are its methods and each returns a new Wrapper,
     * {@code find(UnifiedTextField.class).withLabel("Name").setValue("Alice")}. Every use finds the component
     * again and waits up to the timeout for exactly one match (ADR 0007).
     */
    <W extends UnifiedWrapper & CommonFilters<W>> W find(Class<W> wrapperType);

    /**
     * Lookup of any Flow component without a wrapper; the tag comes from its {@code @Tag}. Returns a lazy
     * {@link UnifiedGenericComponent} with the shared filters.
     */
    <C extends Component> UnifiedGenericComponent findComponent(Class<C> componentType);
}
