package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import java.time.Duration;

/** A {@link TextArea}: multi-line text. */
@UnifiedComponent(TextArea.class)
public interface UnifiedTextArea extends UnifiedTextInput,
        CommonFilters<UnifiedTextArea>, LabelFilter<UnifiedTextArea>, PlaceholderFilter<UnifiedTextArea> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedTextArea withTimeout(Duration timeout);
}
