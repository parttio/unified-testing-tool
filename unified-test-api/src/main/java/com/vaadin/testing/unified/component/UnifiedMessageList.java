package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spi.MessageListAccess;
import java.util.List;

/**
 * A {@link MessageList}: the messages of a chat. Each message's text is the text the application gave it (the
 * Markdown source when the list renders Markdown), the same on every Testing Tool.
 */
@UnifiedComponent(MessageList.class)
public interface UnifiedMessageList extends UnifiedWrapper, CommonFilters<UnifiedMessageList> {

    /** One message of the list: who wrote it and its text. */
    record Message(String userName, String text) {}

    // getters (immediate); assert with assertThat(list).hasMessageCount(n) to wait for a reply
    default List<Message> getMessages() { return element().unwrap(MessageListAccess.class).messages(); }
    default List<String> getMessageTexts() { return getMessages().stream().map(Message::text).toList(); }
    default int getMessageCount() { return getMessages().size(); }
}
