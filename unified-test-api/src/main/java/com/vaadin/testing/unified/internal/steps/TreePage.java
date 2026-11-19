package com.vaadin.testing.unified.internal.steps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A component tree, as Browserless evidence writes it ({@code <name>.tree.txt}: a {@code Location:} line, then
 * Karibu's pretty tree), turned into a page a person can read: {@code <name>.tree.html}, with one foldable node per
 * component, its type and the attributes that name it first, the rest muted. One file with its CSS and script
 * inline, like {@code report.html}, which shows it under a step.
 * <p>
 * The text is the source: the page shows what it says, masked passwords included, and a line it can't read as a
 * component shows as it is.
 */
public final class TreePage {

    /** The text evidence's suffix, and the page's. */
    public static final String TEXT_SUFFIX = ".tree.txt";
    public static final String PAGE_SUFFIX = ".tree.html";

    /** The attributes that say which component it is, shown before the others. */
    private static final Set<String> NAMING = Set.of("label", "caption", "text", "value", "placeholder", "title",
            "header", "helperText", "errorMessage", "ariaLabel", "@aria-label", "href", "src", "alt");

    private TreePage() {
    }

    /** The page's file for a tree's text file: {@code 003.tree.txt} → {@code 003.tree.html}. */
    public static String pageFor(String textFile) {
        return textFile.substring(0, textFile.length() - TEXT_SUFFIX.length()) + PAGE_SUFFIX;
    }

    public static boolean isTree(String file) {
        return file.endsWith(TEXT_SUFFIX);
    }

    /** Writes the page beside the text file, unless a page as new as the text is already there. */
    public static Path writeBeside(Path textFile) throws IOException {
        Path page = textFile.resolveSibling(pageFor(textFile.getFileName().toString()));
        if (Files.isRegularFile(page)
                && !Files.getLastModifiedTime(page).toInstant().isBefore(Files.getLastModifiedTime(textFile).toInstant())) {
            return page;
        }
        Files.writeString(page, html(Files.readString(textFile, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        return page;
    }

    /** One component: its line's text without the tree's lines, and the components under it. */
    record Node(String label, List<Node> children) {
    }

    /** The tree's {@code Location:} line's value, or null; and its root components. */
    record Tree(String location, List<Node> roots) {
    }

    static Tree parse(String text) {
        String location = null;
        List<Node> roots = new ArrayList<>();
        List<Node> stack = new ArrayList<>();
        List<String> lines = text.replace("\r\n", "\n").lines().toList();
        int i = 0;
        if (!lines.isEmpty() && lines.get(0).startsWith("Location:")) {
            location = lines.get(0).substring("Location:".length()).trim();
            i = 1;
        }
        Node last = null;
        for (; i < lines.size(); i++) {
            String line = lines.get(i);
            int start = labelStart(line);
            if (start < 0) {
                if (last != null) {
                    // a value with a line break: the line goes on the component's label
                    Node joined = new Node(last.label() + "\n" + line, last.children());
                    replace(roots, stack, last, joined);
                    last = joined;
                } else if (!line.isBlank()) {
                    last = new Node(line.strip(), new ArrayList<>());
                    roots.add(last);
                    stack.clear();
                    stack.add(last);
                }
                continue;
            }
            int depth = start / 4 - 1; // the label comes after its own "├── "
            Node node = new Node(line.substring(start), new ArrayList<>());
            while (stack.size() > depth) {
                stack.removeLast();
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.getLast().children().add(node);
            }
            stack.add(node);
            last = node;
        }
        return new Tree(location, roots);
    }

    /** Where a component's line has its label, after {@code │   ├── }; -1 when the line is no component's. */
    private static int labelStart(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '│' || line.charAt(i) == '|')
                && !line.startsWith("|-- ", i)) {
            i++;
        }
        // Karibu draws with box characters, or with ASCII where it can't
        if (line.startsWith("├── ", i) || line.startsWith("└── ", i) || line.startsWith("|-- ", i)
                || line.startsWith("\\-- ", i)) {
            return i + 4;
        }
        return -1;
    }

    /** Swaps a node for its longer self, where it sits in the tree and on the stack. */
    private static void replace(List<Node> roots, List<Node> stack, Node old, Node now) {
        swap(roots, old, now);
        swap(stack, old, now);
        for (Node n : stack) {
            swap(n.children(), old, now);
        }
    }

    private static void swap(List<Node> list, Node old, Node now) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == old) {
                list.set(i, now);
            }
        }
    }

    /** A component's label read as {@code Type[attr, attr]}: its type and attributes; no attributes if it isn't. */
    record Label(String type, List<String> attributes) {

        static Label of(String label) {
            int open = label.indexOf('[');
            if (open <= 0 || !label.endsWith("]")) {
                return new Label(label, List.of());
            }
            return new Label(label.substring(0, open), split(label.substring(open + 1, label.length() - 1)));
        }

        /** {@code a, b='x, y', c} → its three attributes: a quoted value ends at a quote before a comma or the end. */
        private static List<String> split(String content) {
            List<String> parts = new ArrayList<>();
            StringBuilder part = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (!quoted && c == '\'' && i > 0 && content.charAt(i - 1) == '=') {
                    quoted = true;
                } else if (quoted && c == '\'' && (i + 1 == content.length() || content.startsWith(", ", i + 1))) {
                    quoted = false;
                } else if (!quoted && content.startsWith(", ", i)) {
                    parts.add(part.toString());
                    part.setLength(0);
                    i++;
                    continue;
                }
                part.append(c);
            }
            if (!part.isEmpty()) {
                parts.add(part.toString());
            }
            return parts;
        }
    }

    static String html(String text) {
        Tree tree = parse(text);
        int[] count = {0};
        StringBuilder body = new StringBuilder(text.length() * 2);
        body.append("<ul class=\"tree\">\n");
        tree.roots().forEach(n -> node(body, n, count));
        body.append("</ul>\n");

        StringBuilder h = new StringBuilder(body.length() + 4096);
        h.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>Component tree</title>\n<style>").append(CSS).append("</style>\n</head>\n<body>\n")
                .append("<header>\n<p class=\"where\">");
        if (tree.location() != null) {
            h.append("<span class=\"k\">Location</span> <code>").append(RunReport.esc(tree.location()))
                    .append("</code>");
        }
        h.append("<span class=\"count\">").append(count[0]).append(count[0] == 1 ? " component" : " components")
                .append("</span></p>\n<nav>")
                .append("<input type=\"search\" id=\"q\" placeholder=\"Find a component\" ")
                .append("aria-label=\"Find a component\">")
                .append("<button type=\"button\" id=\"open\">Expand all</button>")
                .append("<button type=\"button\" id=\"close\">Collapse all</button></nav>\n</header>\n<main>\n")
                .append(body).append("</main>\n<script>").append(SCRIPT).append("</script>\n</body>\n</html>\n");
        return h.toString();
    }

    private static void node(StringBuilder h, Node node, int[] count) {
        count[0]++;
        Label label = Label.of(node.label());
        boolean hidden = label.attributes().contains("INVIS");
        h.append("<li").append(hidden ? " class=\"invis\"" : "").append(">");
        if (node.children().isEmpty()) {
            h.append("<div class=\"row leaf\">");
            line(h, label);
            h.append("</div>");
        } else {
            h.append("<details open><summary class=\"row\">");
            line(h, label);
            h.append("<span class=\"n\">").append(node.children().size()).append("</span></summary>\n<ul>\n");
            node.children().forEach(c -> node(h, c, count));
            h.append("</ul></details>");
        }
        h.append("</li>\n");
    }

    /** The type, then the id, flags and naming attributes, then the others, muted. */
    private static void line(StringBuilder h, Label label) {
        h.append("<span class=\"type\">").append(RunReport.esc(label.type())).append("</span>");
        List<String> rest = new ArrayList<>();
        for (String a : label.attributes()) {
            int eq = a.indexOf('=');
            if (a.startsWith("#")) {
                h.append(" <span class=\"id\">").append(RunReport.esc(a)).append("</span>");
            } else if (eq < 0 && a.equals(a.toUpperCase(java.util.Locale.ROOT)) && !a.isBlank()) {
                h.append(" <span class=\"flag\">").append(RunReport.esc(a)).append("</span>");
            } else if (eq > 0 && NAMING.contains(a.substring(0, eq))) {
                attribute(h, a, eq, "attr");
            } else {
                rest.add(a);
            }
        }
        for (String a : rest) {
            int eq = a.indexOf('=');
            if (eq > 0) {
                attribute(h, a, eq, "attr more");
            } else {
                h.append(" <span class=\"attr more\">").append(RunReport.esc(a)).append("</span>");
            }
        }
    }

    private static void attribute(StringBuilder h, String a, int eq, String cls) {
        h.append(" <span class=\"").append(cls).append("\"><span class=\"k\">").append(RunReport.esc(a.substring(0, eq)))
                .append("</span>=<span class=\"v\">").append(RunReport.esc(a.substring(eq + 1))).append("</span></span>");
    }

    private static final String CSS = """
            :root{color-scheme:light dark;--bg:#fbfbfa;--surface:#fff;--text:#1d1d1f;--muted:#5f6368;--line:#e3e3e0;\
            --accent:#1f5fbf;--id:#8a3ffc;--flag:#9a6700;--flag-bg:#fff4d6;--hit:#fff1a8;--code:#f3f3f1}
            @media (prefers-color-scheme: dark){:root{--bg:#141416;--surface:#1c1c1f;--text:#e8e8ea;--muted:#a0a0a8;\
            --line:#2e2e33;--accent:#7aa7ff;--id:#c4a1ff;--flag:#f0c060;--flag-bg:#3a3018;--hit:#5a4d10;--code:#26262b}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font:13px/1.5 ui-monospace,SFMono-Regular,Menlo,\
            Consolas,monospace}
            header{position:sticky;top:0;z-index:1;background:var(--bg);border-bottom:1px solid var(--line);\
            padding:8px 16px;display:flex;flex-wrap:wrap;gap:6px 16px;align-items:center}
            .where{margin:0;flex:1 1 260px;min-width:0;overflow-wrap:anywhere}
            .where code{background:var(--code);padding:1px 4px;border-radius:4px}
            .count{color:var(--muted);margin-left:12px}
            nav{display:flex;flex-wrap:wrap;gap:6px}
            nav input,nav button{font:inherit;padding:3px 8px;border:1px solid var(--line);border-radius:6px;\
            background:var(--surface);color:var(--text)}
            nav input{min-width:0;flex:1 1 160px}nav button{cursor:pointer}
            main{padding:8px 16px 32px}
            ul.tree,ul.tree ul{list-style:none;margin:0;padding:0}
            ul.tree ul{margin-left:7px;padding-left:14px;border-left:1px solid var(--line)}
            .row{display:block;padding:1px 4px 1px calc(1.2em + 4px);text-indent:-1.2em;border-radius:4px;\
            overflow-wrap:anywhere;white-space:pre-wrap}
            summary.row{cursor:pointer;list-style:none}
            summary.row::-webkit-details-marker{display:none}
            summary.row::before{content:"▾";display:inline-block;width:1.2em;text-indent:0;color:var(--muted)}
            details:not([open])>summary.row::before{content:"▸"}
            .leaf::before{content:"";display:inline-block;width:1.2em;text-indent:0}
            .row:hover{background:var(--code)}
            .type{font-weight:700}
            .id{color:var(--id);font-weight:600}
            .flag{font-size:11px;padding:0 5px;border-radius:4px;background:var(--flag-bg);color:var(--flag)}
            .attr .k{color:var(--muted)}.attr .v{color:var(--accent)}
            .attr.more,.attr.more .k,.attr.more .v{color:var(--muted)}
            .n{color:var(--muted);font-size:11px;margin-left:8px}
            details[open]>summary .n{display:none}
            li.invis>.row,li.invis>details>.row{opacity:.55}
            .hit{background:var(--hit)}
            li.miss{display:none}
            """;

    /** Expand and collapse all; the search shows the components that match, and the ones they are in. */
    private static final String SCRIPT = """
            (function(){
              var all=function(o){document.querySelectorAll('details').forEach(function(d){d.open=o;});};
              document.getElementById('open').addEventListener('click',function(){all(true);});
              document.getElementById('close').addEventListener('click',function(){all(false);});
              var q=document.getElementById('q'),items=[].slice.call(document.querySelectorAll('ul.tree li'));
              q.addEventListener('input',function(){
                var s=q.value.trim().toLowerCase();
                items.forEach(function(li){li.classList.remove('miss');
                  var r=li.querySelector(':scope>.row,:scope>details>.row');if(r){r.classList.remove('hit');}});
                if(!s){return;}
                items.forEach(function(li){li.classList.add('miss');});
                items.forEach(function(li){
                  var r=li.querySelector(':scope>.row,:scope>details>.row');
                  if(r&&r.textContent.toLowerCase().indexOf(s)>=0){
                    r.classList.add('hit');
                    for(var p=li;p;p=p.parentElement&&p.parentElement.closest('li')){
                      p.classList.remove('miss');var d=p.querySelector(':scope>details');if(d){d.open=true;}
                    }
                  }
                });
              });
            })();
            """;
}
