package com.vaadin.testing.unified;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.query.CommonFilters;

/** Something a test can search in: the page, a dialog, a container. */
public interface Scope {

    /**
     * Typed lookup. Returns the Wrapper itself, lazy: filters are its methods and each returns a new Wrapper,
     * {@code find(UnifiedTextField.class).withLabel("Name").setValue("Alice")}. Every use finds the component
     * again and waits up to the timeout for exactly one match.
     */
    <W extends UnifiedWrapper & CommonFilters<W>> W find(Class<W> wrapperType);

    /**
     * Lookup of any Flow component without a wrapper; the tag comes from its {@code @Tag}. Returns a lazy
     * {@link UnifiedGenericComponent} with the shared filters.
     */
    <C extends Component> UnifiedGenericComponent findComponent(Class<C> componentType);

    /**
     * The Findable View: the components a test can find in here as they are now, one line each, with the lookup
     * that singles each out. For a person or an agent writing the next lookup; print it, don't assert on it.
     *
     * <pre>{@code
     * System.out.println(page.describe());
     * // text field “Name” = “Alan Turing”     page.find(UnifiedTextField.class).withLabel("Name")
     * // button “Save” (disabled)              page.find(UnifiedButton.class).withText("Save")
     * }</pre>
     *
     * Lists the Wrappers of the Unified API and the custom Wrappers the test has looked up; not a User Step.
     * Values marked {@link UnifiedPage#secret} show as {@code ••••}. On a container, waits as any use of it does,
     * and its lookups are on the container.
     */
    String describe();
}
