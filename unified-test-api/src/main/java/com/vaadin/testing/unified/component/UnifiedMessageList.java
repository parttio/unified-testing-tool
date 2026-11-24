package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.messages.MessageSupport;
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

    /**
     * The Findable View of the list: its messages, each with the lookup on this list that finds it
     * ({@code message().withUserName("Assistant").last()}). The first messages only; print it, don't assert on it.
     */
    default String describe() { return com.vaadin.testing.unified.internal.ItemsView.ofMessageList(this); }

    // messages
    /**
     * A lazy message found by what it shows: {@code message().withUserName("Assistant").last()}. Every use finds
     * the message again and waits up to the timeout for exactly one match.
     */
    default UnifiedMessage message() { return MessageSupport.message(this); }
    /** The message at this index among all messages, from 0. */
    default UnifiedMessage message(int index) { return MessageSupport.message(this, index); }

    /**
     * A lazy message. From {@link #message()}, its filters narrow the messages it stands for; each returns a new
     * lookup and leaves this one unchanged. Check it with {@code assertThat(message)}, which waits.
     */
    interface UnifiedMessage {
        // filters
        /** Messages from this user name, exactly. */
        UnifiedMessage withUserName(String userName);
        /** Messages whose text is exactly this. */
        UnifiedMessage withText(String text);
        /** Messages whose text contains this part, ignoring case. */
        UnifiedMessage withTextContaining(String part);
        /** The last of the messages matching so far: {@code withUserName("Assistant").last()} is the latest reply. */
        UnifiedMessage last();
        /**
         * Every matching message right now, by its index. Only for looping over messages; to check a count, use
         * {@code assertThat(message).hasCount(n)}, which waits.
         */
        List<UnifiedMessage> all();

        UnifiedMessageList list();
        // getters (immediate, once exactly one message matches)
        int getIndex();
        String getText();
        String getUserName();
    }
}
