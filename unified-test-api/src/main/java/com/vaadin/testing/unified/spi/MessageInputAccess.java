package com.vaadin.testing.unified.spi;

/** Adapter access to a MessageInput, reached by the core with {@code element.unwrap(MessageInputAccess.class)}. */
public interface MessageInputAccess {

    /** Types the text and submits it, as a user would, and waits for the server round-trip. */
    void send(String text);
}
