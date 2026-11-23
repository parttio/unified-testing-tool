package demo;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A chat with an assistant, built from a MessageList and a MessageInput as an AI app is: the reply is streamed
 * into its message piece by piece. With {@code ?reply-delay=<ms>} the reply comes that much later, from a
 * background thread, as a model's would. A "Notes" text area sits next to the message input.
 */
@AnonymousAllowed
@Route("chat")
public class ChatView extends VerticalLayout implements BeforeEnterObserver {

    private int replyDelayMillis;

    public ChatView() {
        MessageList messages = new MessageList();
        messages.setMarkdown(true);
        List<MessageListItem> items = new ArrayList<>();
        items.add(new MessageListItem("Hi! Ask me for a recipe.", null, "Assistant"));
        messages.setItems(items);

        MessageInput input = new MessageInput();
        input.addSubmitListener(e -> {
            messages.addItem(new MessageListItem(e.getValue(), null, "You"));
            String dish = e.getValue();
            if (replyDelayMillis <= 0) {
                reply(messages, dish);
                return;
            }
            UI ui = UI.getCurrent();
            ui.setPollInterval(100); // a browser fetches the later reply without push
            CompletableFuture.runAsync(() -> ui.access(() -> {
                reply(messages, dish);
                ui.setPollInterval(-1);
            }), CompletableFuture.delayedExecutor(replyDelayMillis, TimeUnit.MILLISECONDS));
        });

        // a field of the application's own next to the message input, which has a text area of its own inside
        TextArea notes = new TextArea("Notes");

        add(messages, input, notes);
    }

    private static void reply(MessageList messages, String dish) {
        MessageListItem reply = new MessageListItem("", null, "Assistant");
        messages.addItem(reply);
        reply.appendText("Here is a recipe for ");
        reply.appendText("**" + dish + "**.");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        replyDelayMillis = event.getLocation().getQueryParameters().getSingleParameter("reply-delay")
                .map(Integer::parseInt).orElse(0);
    }
}
