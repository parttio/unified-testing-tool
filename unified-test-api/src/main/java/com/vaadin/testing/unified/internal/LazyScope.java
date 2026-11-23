package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedTemplate;
import com.vaadin.testing.unified.query.CommonFilters;

/** A Scope inside a lazy element, e.g. a grid cell: lookups search inside it, found again on every use. */
public record LazyScope(LazyElement element) implements Scope {

    @Override
    public <W extends UnifiedWrapper & CommonFilters<W>> W find(Class<W> wrapperType) {
        return Wrappers.newWrapper(element.run(), wrapperType, element);
    }

    @Override
    public <C extends Component> UnifiedGenericComponent findComponent(Class<C> componentType) {
        return Wrappers.newComponent(element.run(), componentType, element);
    }

    @Override
    public <C extends Component> UnifiedTemplate findTemplate(Class<C> templateType) {
        return Wrappers.newTemplate(element.run(), templateType, element);
    }

    @Override
    public String describe() {
        return FindableView.ofScope(element);
    }

    @Override
    public String toString() {
        return element.describe();
    }
}
