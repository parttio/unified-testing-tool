package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedMessageList.Message;
import com.vaadin.testing.unified.internal.FindableView.Line;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.GridAccess;
import com.vaadin.testing.unified.spi.MessageListAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * The Findable View of a Wrapper's items (#534): {@code grid.describe()} lists the grid's columns and rows, and
 * {@code messageList.describe()} its messages, each with the lookup on the Wrapper that finds it, so that a person or an agent can write the next row lookup. Like the
 * page's view, it is a read, not a User Step, and its format may change.
 */
public final class ItemsView {
    private ItemsView() {}

    /** At most this many rows are listed; the rest are counted. */
    static final int MAX_ITEMS = 20;
    /** A row's lookup is checked unique only up to this many rows: above, reading every column costs too much. */
    static final int MAX_ROWS_COMPARED = 200;
    /** A cell is cut to this many characters on its row's line. */
    static final int MAX_CELL = 20;
    /** A message's text is cut to this many characters on its line. */
    static final int MAX_TEXT = 40;

    public static String ofGrid(UnifiedGrid grid) {
        LazyElement element = Wrappers.lazyElementOf(grid);
        return grid(element.run(), grid.element(), StepTarget.scopeOf(element));
    }

    /**
     * The items of the grid or message list a failed User Step was about (#590): its target was a row or a message,
     * and the element it singled out is that grid or list. Null for any other failure.
     */
    public static String ofFailedStep(TestRun run, StepRecorder steps, Throwable failure) {
        StepRecorder.Step step = steps.stepThatThrew(failure);
        UnifiedElement element = steps.targetOf(failure);
        if (step == null || element == null || step.phrase().target() == null) {
            return null;
        }
        StepTarget target = step.phrase().target();
        String kind = target.kind();
        String scope = target.scope() == null ? kind : target.scope();
        try {
            if ((kind.equals("row") || kind.equals("selected row"))
                    && element.tryUnwrap(GridAccess.class).isPresent()) {
                return grid(run, element, scope);
            }
            if ((kind.equals("message") || kind.equals("last message"))
                    && element.tryUnwrap(MessageListAccess.class).isPresent()) {
                return messageList(run, element, scope);
            }
        } catch (RuntimeException | AssertionError e) {
            // the evidence is written without the items
        }
        return null;
    }

    private static String grid(TestRun run, UnifiedElement element, String scope) {
        return run.steps().mask(run.steps().unrecorded(() -> {
            GridAccess access = element.unwrap(GridAccess.class);

            int count = access.rowCount();
            List<String> headers = access.columnHeaders();
            List<Line> lines = new ArrayList<>();
            for (int c = 0; c < headers.size(); c++) {
                String header = normalize(headers.get(c));
                lines.add(header.isEmpty() ? new Line("column at index " + c, "column(" + c + ")")
                        : new Line("column “" + header + "”", "column(\"" + header + "\")"));
            }
            List<List<String>> columns = new ArrayList<>();
            boolean compared = count <= MAX_ROWS_COMPARED;
            for (int c = 0; c < headers.size() && compared; c++) {
                columns.add(access.columnTexts(c).stream().map(ItemsView::normalize).toList());
            }
            int shown = Math.min(count, MAX_ITEMS);
            for (int r = 0; r < shown; r++) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < headers.size(); c++) {
                    cells.add(compared ? columns.get(c).get(r) : normalize(access.cellText(r, c)));
                }
                lines.add(new Line("row " + r + ": " + String.join(" | ", cells.stream().map(ItemsView::cut).toList()),
                        rowLookup(headers, columns, cells, r)));
            }
            if (count > shown) {
                lines.add(new Line("… " + (count - shown) + " more rows", "row()  (" + count + " rows in all)"));
            }
            StringBuilder view = new StringBuilder("Findable view of " + scope + " on "
                    + run.toolName() + ", " + count + (count == 1 ? " row" : " rows") + "; lookups are on it\n");
            FindableView.appendLines(view, lines);
            return view.toString();
        }));
    }

    /**
     * The message list's messages, each with the lookup on the list that finds it: a user name only one message has,
     * the last message of a user name, or its index.
     */
    public static String ofMessageList(UnifiedMessageList list) {
        LazyElement element = Wrappers.lazyElementOf(list);
        return messageList(element.run(), list.element(), StepTarget.scopeOf(element));
    }

    private static String messageList(TestRun run, UnifiedElement element, String scope) {
        return run.steps().mask(run.steps().unrecorded(() -> {
            List<Message> messages = element.unwrap(MessageListAccess.class).messages();

            int count = messages.size();
            int shown = Math.min(count, MAX_ITEMS);
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < shown; i++) {
                String user = normalize(messages.get(i).userName());
                lines.add(new Line("message " + i + " from “" + user + "”: " + cut(normalize(messages.get(i).text()),
                        MAX_TEXT), messageLookup(messages, i, user)));
            }
            if (count > shown) {
                lines.add(new Line("… " + (count - shown) + " more messages",
                        "message()  (" + count + " messages in all)"));
            }
            StringBuilder view = new StringBuilder("Findable view of " + scope + " on "
                    + run.toolName() + ", " + count + (count == 1 ? " message" : " messages")
                    + "; lookups are on it\n");
            FindableView.appendLines(view, lines);
            return view.toString();
        }));
    }

    private static String messageLookup(List<Message> messages, int index, String user) {
        List<Integer> same = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (normalize(messages.get(i).userName()).equals(user)) {
                same.add(i);
            }
        }
        if (user.isEmpty() || same.isEmpty()) {
            return "message(" + index + ")";
        }
        String byUser = "message().withUserName(\"" + user + "\")";
        return same.size() == 1 ? byUser : same.get(same.size() - 1) == index ? byUser + ".last()"
                : "message(" + index + ")";
    }

    /** The first column, with a header, whose text in this row no other row has; else the row's index. */
    private static String rowLookup(List<String> headers, List<List<String>> columns, List<String> cells, int row) {
        for (int c = 0; c < columns.size(); c++) {
            String header = normalize(headers.get(c));
            String value = cells.get(c);
            if (!header.isEmpty() && !value.isEmpty()
                    && columns.get(c).stream().filter(value::equals).count() == 1) {
                return "row().withCellText(\"" + header + "\", \"" + value + "\")";
            }
        }
        return "row(" + row + ")";
    }

    private static String cut(String text) {
        return cut(text, MAX_CELL);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** Stripped, each run of whitespace one space, as the exact filters compare. */
    static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }
}
