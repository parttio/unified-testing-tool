package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spi.MessageInputAccess;

/** A {@link MessageInput}: where a user writes a chat message and sends it. */
@UnifiedComponent(MessageInput.class)
public interface UnifiedMessageInput extends UnifiedWrapper, CommonFilters<UnifiedMessageInput> {

    /**
     * Types the text and sends it, as a user would (the input's submit event), then waits for the server
     * round-trip. A reply the application adds later, e.g. from a background thread, is not waited for: check it
     * with {@code assertThat(messageList).hasMessageCount(n)} or {@code .hasMessageContaining(..)}.
     */
    default void send(String text) {
        Wrappers.lazyElementOf(this).act("send", e -> e.unwrap(MessageInputAccess.class).send(text));
    }
}
