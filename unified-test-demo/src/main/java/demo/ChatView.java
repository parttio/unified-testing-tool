package demo;

import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.ArrayList;
import java.util.List;

/**
 * A chat with an assistant, built from a MessageList and a MessageInput as an AI app is: the reply is streamed
 * into its message piece by piece.
 */
@AnonymousAllowed
@Route("chat")
public class ChatView extends VerticalLayout {

    public ChatView() {
        MessageList messages = new MessageList();
        messages.setMarkdown(true);
        List<MessageListItem> items = new ArrayList<>();
        items.add(new MessageListItem("Hi! Ask me for a recipe.", null, "Assistant"));
        messages.setItems(items);

        MessageInput input = new MessageInput();
        input.addSubmitListener(e -> {
            messages.addItem(new MessageListItem(e.getValue(), null, "You"));
            MessageListItem reply = new MessageListItem("", null, "Assistant");
            messages.addItem(reply);
            reply.appendText("Here is a recipe for ");
            reply.appendText("**" + e.getValue() + "**.");
        });

        add(messages, input);
    }
}
