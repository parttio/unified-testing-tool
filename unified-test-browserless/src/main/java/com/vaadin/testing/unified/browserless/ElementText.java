package com.vaadin.testing.unified.browserless;

import com.vaadin.flow.dom.Element;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * An element's text on Browserless, as a browser's {@code textContent} holds it: Flow's
 * {@link Element#getTextRecursively()}, plus the text of each {@code innerHTML} property in it (#691). Flow's
 * {@code Html} and {@code setProperty("innerHTML", ..)} put markup there, which a browser renders as text; the
 * markup is parsed with jsoup, which ships with Flow, and its text taken as {@code textContent} takes it: text inside
 * {@code <style>} and {@code <script>} counts, comments don't. Not normalized: callers collapse whitespace.
 */
final class ElementText {
    private ElementText() {}

    static String of(Element e) {
        StringBuilder text = new StringBuilder();
        append(e, text);
        return text.toString();
    }

    private static void append(Element e, StringBuilder text) {
        if (e.isTextNode()) {
            text.append(e.getText());
            return;
        }
        String html = e.getProperty("innerHTML");
        if (html != null) {
            appendMarkup(Jsoup.parseBodyFragment(html).body(), text);
        }
        e.getChildren().forEach(child -> append(child, text));
    }

    private static void appendMarkup(Node node, StringBuilder text) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode t) {
                text.append(t.getWholeText());
            } else if (child instanceof DataNode d) {
                text.append(d.getWholeData());
            } else {
                appendMarkup(child, text);
            }
        }
    }
}
