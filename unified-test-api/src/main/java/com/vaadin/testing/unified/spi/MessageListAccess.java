package com.vaadin.testing.unified.spi;

import com.vaadin.testing.unified.component.UnifiedMessageList;
import java.util.List;

/** Adapter access to a MessageList, reached by the core with {@code element.unwrap(MessageListAccess.class)}. */
public interface MessageListAccess {

    /**
     * The list's messages, in order: each one's user name and the text the application gave it (the Markdown
     * source when the list renders Markdown), so that every Testing Tool reads the same.
     */
    List<UnifiedMessageList.Message> messages();
}
