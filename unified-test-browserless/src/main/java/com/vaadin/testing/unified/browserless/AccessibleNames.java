package com.vaadin.testing.unified.browserless;

import com.vaadin.browserless.internal.LocatorKt;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.dom.Element;
import com.vaadin.testing.unified.internal.BrowserQueries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The accessible name on Browserless, as {@link BrowserQueries#ACCESSIBLE_NAME} computes it in a browser (#641): from
 * the server-side elements, with the ARIA attributes each Vaadin web component renders in the browser (a field's
 * label, {@code accessibleName} and {@code accessibleNameRef} on its input or host, a select's value, a dialog's
 * header title, ...).
 */
final class AccessibleNames {
    private AccessibleNames() {}

    /** Fields whose web component names its host, not an input: a group, as the browser renders it. */
    private static final Set<String> NAMED_HOST_FIELDS = Set.of("vaadin-checkbox-group", "vaadin-radio-group",
            "vaadin-custom-field", "vaadin-date-time-picker");

    /** Tags whose element is labelable by a native {@code <label>}, as in Playwright's computation. */
    private static final Set<String> LABELABLE = Set.of("button", "meter", "output", "progress", "select", "textarea",
            "input");

    /**
     * The component's accessible name, whitespace normalized, or null.
     *
     * @param text the component's text, as {@code getText()} reads it, for a component whose text is not in its
     *        elements (a notification's renderer); empty to read its elements
     */
    static String of(Component c, Supplier<Optional<String>> text) {
        Element e = c.getElement();
        if (e.isTextNode()) {
            return null;
        }
        String tag = e.getTag();
        if (BrowserQueries.namedByContent(tag)) {
            if (e.hasAttribute("aria-label") || e.hasAttribute("aria-labelledby")) {
                return join(labels(c, e.getAttribute("aria-labelledby"), e.getAttribute("aria-label"), e));
            }
            return text.get().map(t -> join(List.of(t))).orElseGet(() -> ownText(e));
        }
        String host = join(labels(c, e.getAttribute("aria-labelledby"), hostAriaLabel(e), e));
        if (host != null) {
            return host;
        }
        return rendered(c, e);
    }

    /** The {@code aria-label} the web component renders on its host: a dialog's header, else the application's. */
    private static String hostAriaLabel(Element e) {
        String rendered = switch (e.getTag()) {
            case "vaadin-dialog" -> e.getProperty("headerTitle");
            case "vaadin-confirm-dialog" -> e.getProperty("header");
            default -> null;
        };
        return rendered != null && !rendered.isEmpty() ? rendered : e.getAttribute("aria-label");
    }

    /** The name a web component renders from its properties, when the application put none on the host. */
    private static String rendered(Component c, Element e) {
        String tag = e.getTag();
        switch (tag) {
            case "vaadin-avatar" -> {
                String name = e.getProperty("name");
                String abbr = e.getProperty("abbr");
                if (name != null && !name.isEmpty()) {
                    // the web component abbreviates the name to each word's first letter
                    String shown = abbr != null && !abbr.isEmpty() ? abbr
                            : Arrays.stream(name.split(" ")).map(w -> w.isEmpty() ? "" : w.substring(0, 1))
                                    .collect(Collectors.joining());
                    return join(List.of(name + " (" + shown + ")"));
                }
                return abbr == null ? null : join(List.of(abbr));
            }
            // labelled by its label slot, or its title
            case "vaadin-side-nav" -> {
                return join(e.getChildren().filter(l -> "label".equals(l.getAttribute("slot")))
                        .map(ElementText::of).limit(1).toList());
            }
            case "vaadin-card" -> {
                String title = e.getProperty("cardTitle");
                return title == null ? null : join(List.of(title));
            }
            default -> { }
        }
        if (!(c instanceof HasValue<?, ?>) || !tag.startsWith("vaadin-")) {
            return null;
        }
        // a field: aria-labelledby is the accessibleNameRef, else (without an accessibleName) the label; aria-label
        // is the accessibleName. On its input, the label is also the input's native <label>.
        String ref = e.getProperty("accessibleNameRef");
        String accessibleName = e.getProperty("accessibleName");
        String label = e.getProperty("label");
        List<String> name = new ArrayList<>();
        if (ref != null && !ref.isBlank()) {
            List<String> targets = targetTexts(c, ref);
            if (!targets.isEmpty()) {
                name.addAll(targets);
            } else if (accessibleName != null && !accessibleName.isBlank()) {
                name.add(accessibleName);
            } else if (!NAMED_HOST_FIELDS.contains(tag) && !"vaadin-select".equals(tag) && label != null) {
                name.add(label);
            }
        } else if (accessibleName != null && !accessibleName.isBlank()) {
            name.add(accessibleName);
        } else if (label != null) {
            name.add(label);
        }
        if ("vaadin-select".equals(tag)) {
            // the value button is labelled by the label and itself: what it shows, the item or the placeholder
            name.add(selectValue(e));
        }
        return join(name);
    }

    /** What a select's value button shows: the selected item's label or text, else the placeholder. */
    private static String selectValue(Element select) {
        String value = select.getProperty("value");
        if (value != null && !value.isEmpty()) {
            Optional<Element> item = descendants(select)
                    .filter(i -> "vaadin-select-item".equals(i.getTag()) && value.equals(i.getAttribute("value")))
                    .findFirst();
            if (item.isPresent()) {
                String label = item.get().getAttribute("label");
                return label != null ? label : ElementText.of(item.get());
            }
        }
        String placeholder = select.getProperty("placeholder");
        return placeholder == null ? "" : placeholder;
    }

    /**
     * A host's label texts: those of its {@code aria-labelledby} targets, else a non-blank {@code aria-label}, else
     * its native {@code <label>}s when it is labelable.
     */
    private static List<String> labels(Component c, String labelledBy, String ariaLabel, Element e) {
        if (labelledBy != null) {
            List<String> targets = targetTexts(c, labelledBy);
            if (!targets.isEmpty()) {
                return targets;
            }
        }
        if (ariaLabel != null && !ariaLabel.isBlank()) {
            return List.of(ariaLabel);
        }
        if (!LABELABLE.contains(e.getTag()) || "hidden".equals(e.getAttribute("type"))) {
            return List.of();
        }
        String id = BrowserlessSession.idOf(e);
        List<String> labels = new ArrayList<>();
        for (Element a = e.getParent(); a != null; a = a.getParent()) {
            if ("label".equals(a.getTag())) {
                labels.add(ElementText.of(a));
            }
        }
        if (id != null) {
            uiElements(c).filter(l -> "label".equals(l.getTag()) && id.equals(l.getAttribute("for")))
                    .map(ElementText::of).forEach(labels::add);
        }
        return labels;
    }

    /** The texts of the elements with these ids (space-separated), each once, in the ids' order. */
    private static List<String> targetTexts(Component c, String ids) {
        List<Element> all = uiElements(c).toList();
        List<Element> targets = new ArrayList<>();
        for (String id : ids.split(" ")) {
            if (id.isEmpty()) {
                continue;
            }
            all.stream().filter(el -> id.equals(BrowserlessSession.idOf(el))).findFirst()
                    .filter(t -> !targets.contains(t)).ifPresent(targets::add);
        }
        return targets.stream().map(ElementText::of).toList();
    }

    /**
     * The UI's elements: those of every component, as Browserless's lookups walk them (the route's content is a
     * virtual child of the UI's element), and the plain elements under each.
     */
    private static Stream<Element> uiElements(Component c) {
        return c.getUI().map(ui -> StreamSupport.stream(LocatorKt._walkAll(ui).spliterator(), false)
                .flatMap(component -> descendants(component.getElement()))).orElseGet(Stream::empty);
    }

    /**
     * Its own text: without the children in a named slot (an icon, a tooltip) when one of them has text, the other
     * children's texts joined with a space; else all its text.
     */
    private static String ownText(Element e) {
        List<Element> children = e.getChildren().toList();
        if (children.stream().noneMatch(ch -> !ch.isTextNode() && ch.hasAttribute("slot") && hasText(ch))) {
            return join(List.of(ElementText.of(e)));
        }
        return join(children.stream().filter(ch -> ch.isTextNode() || !ch.hasAttribute("slot"))
                .map(ElementText::of).toList());
    }

    /** Whether a browser has text in it: a tooltip shows its text in its own element there. */
    private static boolean hasText(Element e) {
        if ("vaadin-tooltip".equals(e.getTag())) {
            String text = e.getProperty("text");
            return text != null && !text.isBlank();
        }
        return !ElementText.of(e).isBlank();
    }

    /** The element and its descendant elements, text nodes left out. */
    private static Stream<Element> descendants(Element e) {
        return Stream.concat(Stream.of(e), e.getChildren().filter(ch -> !ch.isTextNode())
                .flatMap(AccessibleNames::descendants));
    }

    /** The texts whitespace-normalized, the blank ones left out, joined with a space; null when none is left. */
    static String join(List<String> texts) {
        String joined = texts.stream().filter(Objects::nonNull).map(BrowserQueries::normalize)
                .filter(t -> !t.isEmpty()).collect(Collectors.joining(" "));
        return joined.isEmpty() ? null : joined;
    }
}
