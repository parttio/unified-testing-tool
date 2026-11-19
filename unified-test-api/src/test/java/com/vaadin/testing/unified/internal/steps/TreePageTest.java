package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Browserless's component tree, as {@code <name>.tree.txt} has it, as a page a person can read. */
class TreePageTest {

    /** What Browserless writes: the location, then Karibu's pretty tree. */
    static final String TREE = """
            Location: /people?sort=name

            └── VerticalLayout[#main, @style='width:100%', @theme='spacing padding']
                ├── H1[text='People']
                ├── HorizontalLayout[@theme='spacing']
                │   ├── TextField[label='Name', value='Alan, Turing', placeholder='Full name', manualValidation='true']
                │   ├── PasswordField[label='Password', value='••••', manualValidation='true']
                │   └── TextArea[label='Notes', value='line one
            line two', manualValidation='true']
                ├── Button[DISABLED, caption='Save']
                │   └── Text[DISABLED, text='Save']
                ├── Span[INVIS, text='hidden']
                └── Anchor[text='Link', href='/x']
            """;

    @Test
    void readsTheLocationAndTheNesting() {
        TreePage.Tree tree = TreePage.parse(TREE);

        assertEquals("/people?sort=name", tree.location());
        assertEquals(1, tree.roots().size());
        TreePage.Node root = tree.roots().get(0);
        assertEquals(List.of("H1", "HorizontalLayout", "Button", "Span", "Anchor"),
                root.children().stream().map(n -> TreePage.Label.of(n.label()).type()).toList());
        TreePage.Node form = root.children().get(1);
        assertEquals(3, form.children().size());
        // a value with a line break stays on its component
        assertEquals("TextArea[label='Notes', value='line one\nline two', manualValidation='true']",
                form.children().get(2).label());
        assertEquals(1, root.children().get(2).children().size());
    }

    @Test
    void readsKaribusAsciiTreeToo() {
        TreePage.Tree tree = TreePage.parse("""
                \\-- UI[]
                    |-- H1[text='People']
                    |   \\-- Text[text='People']
                    \\-- Button[caption='Save']
                """);

        TreePage.Node ui = tree.roots().get(0);
        assertEquals(2, ui.children().size());
        assertEquals("Text[text='People']", ui.children().get(0).children().get(0).label());
    }

    @Test
    void splitsAttributesButNotTheCommasInAValue() {
        TreePage.Label label = TreePage.Label.of("TextField[#name, RO, label='Name', value='Alan, Turing', x='a']");

        assertEquals("TextField", label.type());
        assertEquals(List.of("#name", "RO", "label='Name'", "value='Alan, Turing'", "x='a'"), label.attributes());
        assertEquals(new TreePage.Label("not a component", List.of()), TreePage.Label.of("not a component"));
    }

    @Test
    void showsEachComponentFoldableWithWhatNamesItFirst() {
        String html = TreePage.html(TREE);

        assertTrue(html.startsWith("<!doctype html>"), html);
        assertTrue(html.contains("<span class=\"k\">Location</span> <code>/people?sort=name</code>"), html);
        assertTrue(html.contains("10 components"), html);
        // a component with children folds; one without is a plain row
        assertTrue(html.contains("<details open><summary class=\"row\"><span class=\"type\">VerticalLayout</span>"
                + " <span class=\"id\">#main</span>"), html);
        assertTrue(html.contains("<div class=\"row leaf\"><span class=\"type\">H1</span> <span class=\"attr\">"
                + "<span class=\"k\">text</span>=<span class=\"v\">&#39;People&#39;</span></span></div>"), html);
        // naming attributes before the others, the others muted
        String field = html.substring(html.indexOf("<span class=\"type\">TextField</span>"));
        field = field.substring(0, field.indexOf("</div>"));
        assertTrue(field.indexOf("placeholder") < field.indexOf("manualValidation"), field);
        assertTrue(field.contains("<span class=\"attr more\"><span class=\"k\">manualValidation</span>"), field);
        assertTrue(html.contains("<span class=\"flag\">DISABLED</span>"), html);
        assertTrue(html.contains("<li class=\"invis\"><div class=\"row leaf\"><span class=\"type\">Span</span>"), html);
        // the masked password stays masked, and nothing of the page runs from elsewhere
        assertTrue(html.contains("&#39;••••&#39;"), html);
        assertFalse(html.contains("<script src"), html);
        assertFalse(html.contains("<link"), html);
    }

    @Test
    void escapesWhatTheComponentsSay() {
        String html = TreePage.html("└── Span[text='<b>&</b>']\n");

        assertTrue(html.contains("&lt;b&gt;&amp;&lt;/b&gt;"), html);
        assertFalse(html.contains("<b>&</b>"), html);
    }

    @Test
    void writesThePageBesideTheText(@TempDir Path dir) throws Exception {
        Path text = dir.resolve("003.tree.txt");
        Files.writeString(text, TREE);

        Path page = TreePage.writeBeside(text);

        assertEquals(dir.resolve("003.tree.html"), page);
        assertTrue(Files.readString(page).contains("TextArea"));
        assertEquals("steps/003.tree.html", TreePage.pageFor("steps/003.tree.txt"));
    }
}
