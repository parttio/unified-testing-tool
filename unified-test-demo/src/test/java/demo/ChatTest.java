package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedMessageList.Message;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A chat built from a MessageList and a MessageInput, as an app on Vaadin's AI components has, on every Testing Tool. */
@UnifiedSpringBootTest
class ChatTest {

    // --8<-- [start:sendsAMessage]
    @Test
    @DisplayName("The user asks the assistant for a recipe and sees the reply")
    void sendsAMessage(UnifiedPage page) {
        page.navigate(ChatView.class);
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);

        page.find(UnifiedMessageInput.class).send("Pancakes");

        assertThat(messages).hasMessageCount(3).hasMessageContaining("recipe for **Pancakes**");
    }
    // --8<-- [end:sendsAMessage]

    @Test
    @DisplayName("Each message is read with its user name and the text the app gave it")
    void readsTheMessages(UnifiedPage page) {
        page.navigate(ChatView.class);
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);
        assertThat(messages).hasMessageTexts("Hi! Ask me for a recipe.");

        page.find(UnifiedMessageInput.class).send("Soup");

        assertThat(messages).hasMessageTexts("Hi! Ask me for a recipe.", "Soup", "Here is a recipe for **Soup**.");
        assertEquals(List.of(
                new Message("Assistant", "Hi! Ask me for a recipe."),
                new Message("You", "Soup"),
                new Message("Assistant", "Here is a recipe for **Soup**.")), messages.getMessages());
        assertEquals(3, messages.getMessageCount());
    }
}
