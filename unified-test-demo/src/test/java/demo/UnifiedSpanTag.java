package demo;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;

/** Any {@code <span>}, by tag rather than by class, as {@code UnifiedLink} is any {@code <a>} (#603). */
@UnifiedComponent(value = Component.class, tag = "span")
public interface UnifiedSpanTag extends UnifiedWrapper, CommonFilters<UnifiedSpanTag>, TextFilter<UnifiedSpanTag> {
}
