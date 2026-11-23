package com.vaadin.testing.unified.internal.steps;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedMessageList.UnifiedMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A message list's lazy messages ({@code message()}, {@code message(int)}): lookups, checks and their User Steps. */
class MessageLookupTest {

    private final FakePage fake = new FakePage()
            .add("vaadin-message-list", "messages", "Assistant:Hi! Ask me for a recipe.|You:Soup"
                    + "|Assistant:Here is a recipe for **Soup**.|You:Pancakes|Assistant:Here is a recipe for **Pancakes**.");
    private final UnifiedMessageList chat = fake.page.find(UnifiedMessageList.class);

    private List<String> texts() {
        return fake.steps.entries().stream()
                .map(e -> e instanceof StepRecorder.Step s ? s.phrase().render() : e.toString()).toList();
    }

    @Test
    void aMessageIsFoundByItsIndexFromZero() {
        assertThat(chat.message(2)).hasTextContaining("recipe for **Soup**").hasUserName("Assistant");

        assertEquals(List.of(
                "see message at index 2 with text containing “recipe for **Soup**” in message list",
                "see message at index 2 with user name “Assistant” in message list"), texts());
        assertEquals("Here is a recipe for **Soup**.", chat.message(2).getText());
        assertEquals("You", chat.message(1).getUserName());
        assertEquals(2, chat.message(2).getIndex());
    }

    @Test
    void theLatestReplyIsTheLastMessageFromItsUserName() {
        UnifiedMessage reply = chat.message().withUserName("Assistant").last();

        assertThat(reply).hasText("Here is a recipe for **Pancakes**.");

        assertEquals(4, reply.getIndex());
        assertEquals(List.of("see last message “Here is a recipe for **Pancakes**.” from “Assistant” in message list"),
                texts());
        assertEquals("message().withUserName(\"Assistant\").last()", reply.toString());
    }

    @Test
    void lastAppliesToTheMessagesMatchingSoFar() {
        assertEquals(3, chat.message().withUserName("You").last().getIndex());
        assertEquals(4, chat.message().last().getIndex());
        assertEquals(2, chat.message().withTextContaining("SOUP").last().getIndex());
    }

    @Test
    void absentAndCountedMessagesReadAsSeeNoAndSeeN() {
        assertThat(chat.message().withTextContaining("secret prompt")).doesNotExist();
        assertThat(chat.message().withUserName("You")).hasCount(2);
        assertThat(chat.message().withText("Soup")).exists();

        assertEquals(List.of(
                "see no message containing “secret prompt” in message list",
                "see 2 messages from “You” in message list",
                "see message with text “Soup” in message list"), texts());
    }

    @Test
    void anIndexPastTheEndDoesNotExist() {
        assertThat(chat.message(5)).doesNotExist();

        AssertionError error = assertThrows(AssertionError.class, () -> assertThat(chat.message(5)).exists());
        assertTrue(error.getMessage().contains("message at index 5 in message list to exist"), error.getMessage());
    }

    @Test
    void aCheckOnSeveralMatchesSaysHowManyMatched() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> assertThat(chat.message().withUserName("You")).hasText("Soup"));

        assertTrue(error.getMessage().contains("message from “You” in message list to have text “Soup”"),
                error.getMessage());
        assertTrue(error.getMessage().contains("2 matches"), error.getMessage());
    }

    @Test
    void aGetterOnSeveralMatchesFailsNamingTheCount() {
        AssertionError error = assertThrows(AssertionError.class, () -> chat.message().withUserName("You").getText());

        assertTrue(error.getMessage().contains("exactly one message from “You” in message list"), error.getMessage());
    }

    @Test
    void aCheckWaitsForAReplyTheApplicationAddsLater() throws Exception {
        Map<String, String> list = fake.components.get(0);
        list.put("messages", "Assistant:Hi!");
        Thread reply = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
            list.put("messages", "Assistant:Hi!|You:Soup|Assistant:Here is a recipe for **Soup**.");
        });
        reply.start();

        assertThat(chat.message().withUserName("Assistant").last()).hasTextContaining("Soup");
        reply.join();
    }

    @Test
    void allListsTheMatchesByIndexForLooping() {
        List<UnifiedMessage> mine = chat.message().withUserName("You").all();

        assertEquals(List.of(1, 3), mine.stream().map(UnifiedMessage::getIndex).toList());
        assertEquals("message(3)", mine.get(1).toString());
    }

    @Test
    void lookingUpAMessageIsNoUserStep() {
        chat.message().withUserName("You").last();

        assertEquals(List.of(), texts());
    }
}
