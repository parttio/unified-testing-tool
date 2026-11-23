package com.vaadin.testing.unified.internal.messages;

import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedMessageList.Message;
import com.vaadin.testing.unified.component.UnifiedMessageList.UnifiedMessage;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.MessageListAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Core implementation of a message list's lazy messages, on {@link MessageListAccess}: the same on every Testing
 * Tool, as the access reads every message's user name and text.
 */
public final class MessageSupport {
    private MessageSupport() {}

    public static UnifiedMessage message(UnifiedMessageList list) {
        return new LazyMessage(list, null, List.of());
    }

    public static UnifiedMessage message(UnifiedMessageList list, int index) {
        return new LazyMessage(list, index, List.of());
    }

    public static TestRun runOf(UnifiedMessageList list) {
        return Wrappers.lazyElementOf(list).run();
    }

    /** The message in user words, for the User Step reports. */
    public static StepTarget targetOf(UnifiedMessage message) {
        return message instanceof LazyMessage m ? m.target() : StepTarget.of(message.toString());
    }

    /** How many messages match right now; no waiting. */
    public static int countNow(UnifiedMessage message) {
        return ((LazyMessage) message).matchingNow().size();
    }

    /**
     * The one matching message's text or user name right now, or, when not exactly one message matches, what was
     * found instead ({@code none}, {@code 2 matches}), so that a check waits for it and its failure says so.
     */
    public static Object readNow(UnifiedMessage message, Function<Message, String> part) {
        LazyMessage lazy = (LazyMessage) message;
        List<Message> messages = lazy.messages();
        List<Integer> matching = lazy.matching(messages);
        return matching.size() == 1 ? part.apply(messages.get(matching.get(0))) : new Found(matching.size());
    }

    /** What a lookup found when it found other than one message. */
    private record Found(int count) {
        @Override
        public String toString() {
            return count == 0 ? "none" : count + " matches";
        }
    }

    /** One narrowing of the messages, applied in the order the test wrote them. */
    private sealed interface Step permits UserName, Text, Last {
        List<Integer> apply(List<Integer> indexes, List<Message> messages);

        /** {@code from “Assistant”}. */
        String words();
    }

    private record UserName(String userName) implements Step {
        @Override
        public List<Integer> apply(List<Integer> indexes, List<Message> messages) {
            Filter filter = new Filter(Filter.Kind.TEXT, null, userName, true);
            return indexes.stream().filter(i -> filter.matches(messages.get(i).userName())).toList();
        }

        @Override
        public String words() {
            return "from “" + userName + "”";
        }

        @Override
        public String toString() {
            return "withUserName(\"" + userName + "\")";
        }
    }

    private record Text(Filter filter) implements Step {
        @Override
        public List<Integer> apply(List<Integer> indexes, List<Message> messages) {
            return indexes.stream().filter(i -> filter.matches(messages.get(i).text())).toList();
        }

        @Override
        public String words() {
            return (filter.exact() ? "with text “" : "containing “") + filter.value() + "”";
        }

        @Override
        public String toString() {
            return (filter.exact() ? "withText" : "withTextContaining") + "(\"" + filter.value() + "\")";
        }
    }

    private record Last() implements Step {
        @Override
        public List<Integer> apply(List<Integer> indexes, List<Message> messages) {
            return indexes.isEmpty() ? indexes : List.of(indexes.get(indexes.size() - 1));
        }

        @Override
        public String words() {
            return null;
        }

        @Override
        public String toString() {
            return "last()";
        }
    }

    /**
     * A lazy message: {@code message(n)} when {@code fixedIndex} is set, otherwise the one message the steps leave,
     * found again on every call.
     */
    record LazyMessage(UnifiedMessageList list, Integer fixedIndex, List<Step> steps) implements UnifiedMessage {

        private UnifiedMessage with(Step step) {
            List<Step> copy = new ArrayList<>(steps);
            copy.add(step);
            return new LazyMessage(list, fixedIndex, List.copyOf(copy));
        }

        @Override public UnifiedMessage withUserName(String userName) { return with(new UserName(userName)); }
        @Override public UnifiedMessage withText(String text) { return with(new Text(new Filter(Filter.Kind.TEXT, null, text, true))); }
        @Override public UnifiedMessage withTextContaining(String part) { return with(new Text(new Filter(Filter.Kind.TEXT, null, part, false))); }
        @Override public UnifiedMessage last() { return with(new Last()); }

        @Override
        public List<UnifiedMessage> all() {
            return matchingNow().stream().map(i -> MessageSupport.message(list, i)).toList();
        }

        List<Message> messages() {
            return list.element().unwrap(MessageListAccess.class).messages();
        }

        List<Integer> matchingNow() {
            return matching(messages());
        }

        /** The indexes of the matching messages. */
        List<Integer> matching(List<Message> messages) {
            List<Integer> indexes;
            if (fixedIndex != null) {
                indexes = fixedIndex >= 0 && fixedIndex < messages.size() ? List.of(fixedIndex) : List.of();
            } else {
                indexes = IntStream.range(0, messages.size()).boxed().toList();
            }
            for (Step step : steps) {
                indexes = step.apply(indexes, messages);
            }
            return indexes;
        }

        /** Waits until exactly one message matches; its index and the message as it was then. */
        private Object[] one() {
            int[] last = { 0 };
            return runOf(list).pollFor(() -> {
                List<Message> messages = messages();
                List<Integer> indexes = matching(messages);
                last[0] = indexes.size();
                return indexes.size() == 1 ? new Object[] { indexes.get(0), messages.get(indexes.get(0)) } : null;
            }, () -> FailureParts.of("exactly one " + target().renderWithScope(),
                    last[0] == 0 ? "none" : String.valueOf(last[0])));
        }

        @Override public int getIndex() { return (Integer) one()[0]; }
        @Override public String getText() { return ((Message) one()[1]).text(); }
        @Override public String getUserName() { return ((Message) one()[1]).userName(); }

        /** {@code last message from “Assistant”} in the message list, for the User Step reports. */
        StepTarget target() {
            boolean last = steps.stream().anyMatch(Last.class::isInstance);
            List<String> words = new ArrayList<>();
            if (fixedIndex != null) {
                words.add("at index " + fixedIndex);
            }
            steps.stream().map(Step::words).filter(w -> w != null).forEach(words::add);
            StepTarget target = StepTarget.of(last ? "last message" : "message")
                    .withScope(StepTarget.scopeOf(Wrappers.lazyElementOf(list)));
            return words.isEmpty() ? target : target.withQualifier(String.join(" ", words));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(fixedIndex == null ? "message()" : "message(" + fixedIndex + ")");
            steps.forEach(s -> sb.append('.').append(s));
            return sb.toString();
        }
    }
}
