package com.vaadin.testing.unified.component;

import com.vaadin.testing.unified.TemplateScope;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * Wrapper for a Template found with {@code findTemplate(..)}, for one without a Wrapper of its own: the shared
 * filters, Unified Element operations, and a {@link TemplateScope} to search inside it.
 *
 * <pre>{@code
 * page.findTemplate(SearchBar.class).find(UnifiedButton.class).withId("new").click();
 * }</pre>
 */
public interface UnifiedTemplate extends UnifiedWrapper, TemplateScope, CommonFilters<UnifiedTemplate> {
}
