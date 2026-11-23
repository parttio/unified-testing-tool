package com.vaadin.testing.unified;

/**
 * Mixin for the Wrapper of a Template (spec 04, "Templates"): a component whose own content a test reaches only
 * by entering it, such as a {@code LitTemplate} with {@code @Id} components in its shadow root. Lookups on the
 * Wrapper search the Template's shadow root and its light DOM, not the shadow roots of Templates nested in it.
 *
 * <pre>{@code
 * @UnifiedComponent(SearchBar.class)
 * public interface UnifiedSearchBar extends UnifiedWrapper, CommonFilters<UnifiedSearchBar>, TemplateScope {
 * }
 *
 * page.find(UnifiedSearchBar.class).find(UnifiedButton.class).withId("new").click();
 * }</pre>
 *
 * Inside a {@code LitTemplate}, what the server knows is found on every Testing Tool: the {@code @Id} components
 * and what is under them. Find them by id: text written in the template's HTML is empty on Browserless. A Template
 * that is not a {@code LitTemplate} renders its content in the browser only: entering it needs
 * {@link RequiresBrowser}.
 */
public interface TemplateScope extends Scope {
}
