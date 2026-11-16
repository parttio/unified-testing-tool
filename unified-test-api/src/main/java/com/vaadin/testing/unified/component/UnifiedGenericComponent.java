package com.vaadin.testing.unified.component;

import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.TextFilter;

/**
 * Wrapper for any component found with {@code findComponent(..)}: the shared filters, Unified Element
 * operations, and a Scope to search inside it
 * ({@code page.findComponent(FormLayout.class).find(UnifiedTextField.class)}).
 */
public interface UnifiedGenericComponent extends UnifiedWrapper, Scope, CommonFilters<UnifiedGenericComponent>,
        LabelFilter<UnifiedGenericComponent>, TextFilter<UnifiedGenericComponent> {

    /** {@link com.vaadin.testing.unified.UnifiedElement#getText()}: the DOM text, the same on every tool. */
    default String getText() { return element().getText(); }
}
