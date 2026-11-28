package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.spi.Filter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What the browser adapters share to find elements in one round trip: filters that CSS can express go into
 * the selector; the others are checked by {@link #MATCH} in the page, over every candidate at once.
 */
public final class BrowserQueries {
    private BrowserQueries() {}

    /**
     * Resolves once Flow has changed the address bar for a navigation it has handled (#233), waiting for the
     * navigation flag at most {@code cap} ms. Without React, Flow's {@code History} sends
     * {@code setTimeout(() => history.pushState(..))}: the first timeout runs after it. In React mode, Flow.tsx
     * sets {@code Vaadin.Flow.navigation} on {@code vaadin-navigate} and deletes it in a timeout once React Router
     * has moved: wait while it is set, then one more timeout. The flag is Flow's internal, not an API.
     */
    public static final String SETTLE_NAVIGATION = """
            async (cap) => {
              const tick = (ms) => new Promise(r => setTimeout(r, ms));
              await tick(0);
              for (let t = 0; window.Vaadin?.Flow?.navigation && t < cap; t += 10) {
                await tick(10);
              }
              await tick(0);
            }""";

    /** How long {@link #SETTLE_NAVIGATION} waits for the navigation flag at most, in ms. */
    public static final int SETTLE_NAVIGATION_CAP = 2000;

    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[A-Za-z_][-A-Za-z0-9_]*");

    /** The selector part for a filter CSS can express, or null. */
    public static String selectorPart(Filter f) {
        if (!f.exact()) {
            return null;
        }
        return switch (f.kind()) {
            case ID -> "[id=" + quote(f.value()) + "]";
            case TEST_ID -> "[data-testid=" + quote(f.value()) + "]";
            case CLASS_NAME -> f.value().isBlank() || f.value().matches(".*\\s.*") ? null
                    : "[class~=" + quote(f.value()) + "]";
            case THEME -> f.value().isBlank() || f.value().matches(".*\\s.*") ? null
                    : "[theme~=" + quote(f.value()) + "]";
            // without a value, "has the attribute": checked in the page (MATCH), as Dramafinder's queries take a value
            case ATTRIBUTE -> f.value() != null && ATTRIBUTE_NAME.matcher(f.name()).matches()
                    ? "[" + f.name() + "=" + quote(f.value()) + "]" : null;
            // checked in the page (MATCH): Dramafinder's queries have no negation
            case WITHOUT_ATTRIBUTE -> null;
            default -> null;
        };
    }

    /** The selector with every filter CSS can express. */
    public static String selector(String tag, List<Filter> filters) {
        StringBuilder selector = new StringBuilder(tagSelector(tag));
        for (Filter f : filters) {
            String part = selectorPart(f);
            if (part != null) {
                selector.append(part);
            }
        }
        return selector.toString();
    }

    /**
     * Whether {@code tag} lists several tags, comma-separated, as a Wrapper bound to several components declares
     * it ({@code UnifiedLogin}: {@code "vaadin-login-overlay, vaadin-login-form"}).
     */
    public static boolean isTagList(String tag) {
        return tag != null && tag.indexOf(',') >= 0;
    }

    /** The tags of {@code tag}: itself, or those it lists ({@link #isTagList}). */
    public static List<String> tags(String tag) {
        return isTagList(tag) ? java.util.Arrays.stream(tag.split(",")).map(String::strip).filter(t -> !t.isEmpty())
                .toList() : List.of(tag);
    }

    /** The CSS selector for {@code tag}: itself, or {@code :is(a,b)} for a tag list, so filters can follow it. */
    public static String tagSelector(String tag) {
        return isTagList(tag) ? ":is(" + String.join(",", tags(tag)) + ")" : tag;
    }

    /** The filters the selector can't express, as {@link #MATCH} takes them. */
    public static List<Map<String, Object>> remaining(List<Filter> filters) {
        List<Map<String, Object>> rest = new ArrayList<>();
        for (Filter f : filters) {
            if (selectorPart(f) == null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", f.kind().name());
                m.put("name", f.name() == null ? "" : f.name());
                m.put("value", f.value());
                m.put("exact", f.exact());
                rest.add(m);
            }
        }
        return rest;
    }

    /**
     * XPath for light-DOM children ({@code descendants}: every level) with this tag, {@code *} for any. XPath,
     * unlike CSS in Playwright, never enters shadow roots.
     */
    public static String lightDomXPath(String tag, boolean descendants) {
        String axis = descendants ? ".//*" : "./*";
        if ("*".equals(tag)) {
            return axis;
        }
        if (!tag.matches("[A-Za-z][-A-Za-z0-9_.]*")) {
            throw new IllegalArgumentException("Not a tag name: " + tag);
        }
        return axis + "[local-name()='" + tag.toLowerCase(java.util.Locale.ROOT) + "']";
    }

    /** Dash-separated CSS property name ({@code backgroundColor} becomes {@code background-color}). */
    public static String cssName(String name) {
        return name.startsWith("--") ? name : name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
    }

    /** Browser function {@code (element, name) => value}: the inline style's declared value, or null. */
    public static final String INLINE_STYLE = """
            (e, n) => {
              const v = e.style.getPropertyValue(n);
              if (v && v.trim() !== '') return v.trim();
              for (const part of (e.getAttribute('style') || '').split(';')) {
                const i = part.indexOf(':');
                if (i > 0 && part.substring(0, i).trim() === n) return part.substring(i + 1).trim();
              }
              return null;
            }""";

    /**
     * The text a user types for a value: a BigDecimal as it is ({@code 54.20}, its scale is what the user
     * typed), other numbers without trailing zeros ({@code 3.0} as {@code 3}), null as empty.
     */
    public static String typedText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Number n) {
            return new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /** Whitespace collapsed and trimmed, as {@link #TEXT} does in the browser. */
    public static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * The element's text: its DOM text ({@code textContent}), whitespace collapsed, leaving out the text of a
     * tooltip ({@code setTooltipText}: a {@code <vaadin-tooltip slot="tooltip">} child, its text in the DOM
     * only in a browser). The same on every Testing Tool: Browserless reads the same text from the server-side
     * elements.
     */
    public static final String TEXT = """
            e => {
              // as textContent: text nodes only, comments (Lit's markers) left out
              const tooltip = n => n.localName === 'vaadin-tooltip' && n.getAttribute('slot') === 'tooltip';
              const textOf = n => n.nodeType === 3 ? n.data
                  : n.nodeType === 1 && !tooltip(n) ? [...n.childNodes].map(textOf).join('') : '';
              return [...e.childNodes].map(textOf).join('').replace(/\\s+/g, ' ').trim();
            }""";

    /**
     * A confirm dialog's buttons as {@code [[role, text], ...]}: the button in each role's slot of the host, the
     * application's own or the web component's, with its text as {@link #TEXT} reads it. Reject and Cancel only
     * while the web component shows them ({@code rejectButtonVisible}, {@code cancelButtonVisible}).
     */
    public static final String CONFIRM_DIALOG_BUTTONS = """
            e => {
              const textOf = %s;
              return ['confirm', 'reject', 'cancel']
                  .filter(role => role === 'confirm' || e[role + 'ButtonVisible'] === true)
                  .map(role => {
                    const button = e.querySelector(':scope > [slot="' + role + '-button"]');
                    return [role, button ? textOf(button) : (e[role + 'Text'] || '')];
                  });
            }""".formatted(TEXT.strip());

    /** {@link #CONFIRM_DIALOG_BUTTONS}' result, by role. */
    public static java.util.Map<com.vaadin.testing.unified.spi.ConfirmDialogAccess.Role, String> confirmDialogButtons(
            Object result) {
        java.util.Map<com.vaadin.testing.unified.spi.ConfirmDialogAccess.Role, String> buttons =
                new java.util.EnumMap<>(com.vaadin.testing.unified.spi.ConfirmDialogAccess.Role.class);
        for (Object pair : (List<?>) result) {
            List<?> roleAndText = (List<?>) pair;
            buttons.put(com.vaadin.testing.unified.spi.ConfirmDialogAccess.Role.valueOf(
                    String.valueOf(roleAndText.get(0)).toUpperCase(java.util.Locale.ROOT)),
                    String.valueOf(roleAndText.get(1)));
        }
        return buttons;
    }

    /** The CSS selector, from the confirm dialog's host, of the button in the role's slot. */
    public static String confirmDialogButton(com.vaadin.testing.unified.spi.ConfirmDialogAccess.Role role) {
        return ":scope > [slot='" + role.name().toLowerCase(java.util.Locale.ROOT) + "-button']";
    }

    /**
     * The Item Name of a tab or a menu item: its text, whitespace collapsed, leaving out children in a named
     * slot (a tooltip); when it shows no text, its {@code aria-label}, or that of the component it holds (a
     * menu-bar button holds the app's item). Browserless reads the same from the server-side elements.
     */
    /**
     * A message list's messages as {@code [userName, text]} pairs, from its {@code items}: the text the server gave
     * each one, as Browserless reads it, rather than the rendered Markdown.
     */
    /**
     * A DateTimePicker's date in the text its date half shows for it, from the date picker's own
     * {@code i18n.formatDate}: the format of the field's locale. Takes {@code [year, month, day]}.
     */
    public static final String FORMAT_DATE = """
            (e, [year, month, day]) => {
              const datePicker = e.querySelector('[slot="date-picker"]');
              const i18n = datePicker.__effectiveI18n || datePicker.i18n;
              return i18n.formatDate({ year, month: month - 1, day });
            }""";

    /** The {@code [year, month, day]} argument of {@link #FORMAT_DATE}. */
    public static java.util.List<Integer> formatDateArgument(java.time.LocalDate date) {
        return java.util.List.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    public static final String MESSAGES = """
            e => (e.items || []).map(i => [i.userName == null ? null : String(i.userName),
                i.text == null ? '' : String(i.text)])""";

    /** {@link #MESSAGES}, read as a list of messages. */
    public static java.util.List<com.vaadin.testing.unified.component.UnifiedMessageList.Message> messages(Object read) {
        return ((java.util.List<?>) read).stream().map(m -> (java.util.List<?>) m)
                .map(m -> new com.vaadin.testing.unified.component.UnifiedMessageList.Message(
                        (String) m.get(0), (String) m.get(1)))
                .toList();
    }

    public static final String ITEM_NAME = """
            e => {
              // text and elements only: comments (Lit's markers) have a textContent too
              const text = [...e.childNodes]
                  .filter(n => n.nodeType === 3 || (n.nodeType === 1 && !n.hasAttribute('slot')))
                  .map(n => n.textContent).join('').replace(/\\s+/g, ' ').trim();
              if (text) return text;
              const named = e.hasAttribute('aria-label') ? e : e.querySelector('[aria-label]');
              return named ? named.getAttribute('aria-label').trim() : '';
            }""";

    /**
     * A {@code <vaadin-menu-bar>}'s top-level items as Selenium's {@code MenuBarAccess} reads them (#493; TestBench's
     * {@code getButtons()} leaves the folded ones out, and Playwright reads Dramafinder's, #666): {@code {items,
     * overflow, overflowButton}}, the Item Names ({@link #ITEM_NAME}) of all its items, of those it has moved into its
     * overflow menu, which it hides with {@code visibility: hidden}, and of the overflow button itself
     * ({@code slot="overflow"}, "More options" in English), which is none of its items.
     */
    public static final String MENU_BAR_ITEMS = """
            e => {
              const label = %s;
              // opening the overflow menu moves a folded item's content into it, until the next relayout: the
              // button is empty then, so the name is read from the item's content, wherever it sits
              const name = b => label(b) || (b.item && b.item.component ? label(b.item.component) : '')
                  || (b.item && b.item.text) || '';
              const buttons = [...e.querySelectorAll(':scope > vaadin-menu-bar-button:not([slot="overflow"]):not([hidden])')];
              return {
                items: buttons.map(name),
                overflow: buttons.filter(b => b.style.visibility === 'hidden').map(name),
                overflowButton: label(e.querySelector(':scope > vaadin-menu-bar-button[slot="overflow"]'))
              };
            }""".formatted(ITEM_NAME);

    /**
     * A {@code <vaadin-side-nav>} as {@code SideNavAccess} reads it (#658): {@code {label, items}}, its label the
     * text of its {@code slot="label"} child or null, its items its top-level items' labels ({@link #ITEM_NAME}).
     */
    public static final String SIDE_NAV = """
            e => {
              const name = %s;
              const label = e.querySelector(':scope > [slot="label"]');
              return {
                label: label ? label.textContent.replace(/\\s+/g, ' ').trim() : null,
                items: [...e.querySelectorAll(':scope > vaadin-side-nav-item')].map(name)
              };
            }""".formatted(ITEM_NAME);

    /**
     * A {@code <vaadin-side-nav-item>} as {@code SideNavItemAccess} reads it (#658): {@code {label, items, current,
     * expanded, inCollapsedItem}}.
     */
    public static final String SIDE_NAV_ITEM = """
            e => {
              const name = %s;
              let inCollapsedItem = false;
              for (let p = e.parentElement; p && p.localName === 'vaadin-side-nav-item'; p = p.parentElement) {
                if (!p.expanded) inCollapsedItem = true;
              }
              return {
                label: name(e),
                items: [...e.querySelectorAll(':scope > vaadin-side-nav-item')].map(name),
                current: !!e.current,
                expanded: !!e.expanded,
                inCollapsedItem
              };
            }""".formatted(ITEM_NAME);

    /**
     * The tags of the components named by their content (#641): ARIA's name-from-content roles among what Vaadin
     * renders, as Dramafinder reads them ({@code ButtonElement}, {@code TabElement}, {@code BadgeElement},
     * {@code MenuItemElement}, {@code BreadcrumbsItemElement}, {@code NotificationElement}), plus links, native
     * buttons and headings. A Notification is its card in a browser and its host on Browserless. Any other
     * component has no accessible name unless it is labelled.
     */
    public static final java.util.Set<String> NAMED_BY_CONTENT = java.util.Set.of("vaadin-button", "vaadin-tab",
            "vaadin-badge", "vaadin-menu-bar-button", "vaadin-menu-bar-item", "vaadin-context-menu-item",
            "vaadin-breadcrumbs-item", "vaadin-notification-card", "vaadin-notification", "a", "button", "h1", "h2",
            "h3", "h4", "h5", "h6");

    /** Whether a component with this tag is named by its content ({@link #NAMED_BY_CONTENT}). */
    public static boolean namedByContent(String tag) {
        return tag != null && NAMED_BY_CONTENT.contains(tag.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * The accessible name, as browsers and Dramafinder's {@code getAccessibleName()} compute it (#641), the same on
     * every Testing Tool. Dramafinder's {@code AccessibleNames} scripts, verbatim, chosen by tag:
     * <ul>
     * <li>every component: the texts of its {@code aria-labelledby} targets (several ids: joined with a space),
     * else a non-blank {@code aria-label}, else its native {@code <label>}s; read on the host, then on its own
     * input ({@code slot="input"}, {@code "textarea"} or {@code "value"}), so a field's label,
     * {@code accessibleName} and {@code accessibleNameRef} count. A placeholder doesn't.</li>
     * <li>a component named by its content ({@link #NAMED_BY_CONTENT}): its {@code aria-labelledby} or
     * {@code aria-label} name when either is set, else its own text, without slotted children such as an icon or a
     * tooltip.</li>
     * </ul>
     * Whitespace normalized; null when nothing names it. Playwright reads the same with Dramafinder's
     * {@code AccessibleNames}; Browserless computes it from the server-side elements.
     */
    public static final String ACCESSIBLE_NAME = """
            e => {
              const text = node => {
                let full = '';
                for (let child = node.firstChild; child; child = child.nextSibling) {
                  if (child.nodeType === Node.TEXT_NODE) {
                    full += child.nodeValue || '';
                  } else if (child.nodeType === Node.ELEMENT_NODE
                      && !['SCRIPT', 'NOSCRIPT', 'STYLE'].includes(child.nodeName)) {
                    full += child instanceof HTMLInputElement
                        && ['submit', 'button', 'reset'].includes(child.type) ? child.value : text(child);
                  }
                }
                if (node.shadowRoot) {
                  full += text(node.shadowRoot);
                }
                return full;
              };
              const labels = el => {
                const ref = el.getAttribute('aria-labelledby');
                if (ref !== null) {
                  const root = el.getRootNode();
                  const targets = [];
                  for (const id of ref.split(' ').filter(id => !!id)) {
                    const target = root.querySelector('#' + CSS.escape(id));
                    if (target && !targets.includes(target)) {
                      targets.push(target);
                    }
                  }
                  if (targets.length) {
                    return targets.map(text);
                  }
                }
                const ariaLabel = el.getAttribute('aria-label');
                if (ariaLabel !== null && ariaLabel.trim()) {
                  return [ariaLabel];
                }
                const labelable = ['BUTTON', 'METER', 'OUTPUT', 'PROGRESS', 'SELECT', 'TEXTAREA'].includes(el.nodeName)
                    || (el.nodeName === 'INPUT' && el.type !== 'hidden');
                return labelable && el.labels ? [...el.labels].map(text) : [];
              };
              const join = texts => texts.map(t => t.replace(/\\s+/g, ' ').trim()).filter(t => t).join(' ') || null;
              if (!%s.includes(e.localName)) {
                const own = [...e.children].find(c => ['input', 'textarea', 'value'].includes(c.getAttribute('slot')));
                return join(labels(e)) ?? (own ? join(labels(own)) : null);
              }
              if (e.hasAttribute('aria-label') || e.hasAttribute('aria-labelledby')) {
                return join(labels(e));
              }
              const children = [...e.childNodes];
              const hasText = node => !!(node.textContent || '').trim();
              if (!children.some(c => c.nodeType === Node.ELEMENT_NODE && c.hasAttribute('slot') && hasText(c))) {
                return join([e.textContent || '']);
              }
              return join(children
                  .filter(c => c.nodeType === Node.TEXT_NODE
                      || (c.nodeType === Node.ELEMENT_NODE && !c.hasAttribute('slot')))
                  .map(c => c.textContent || ''));
            }""".formatted(NAMED_BY_CONTENT.stream().sorted().map(t -> "'" + t + "'")
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]")));

    /**
     * The label of the FormLayout item holding the element ({@code formLayout.addFormItem(field, "Name")}): the
     * text of the {@code <vaadin-form-item>}'s {@code slot="label"} child; null outside a form item. Browserless
     * reads the same from the server-side elements.
     */
    public static final String FORM_ITEM_LABEL = """
            e => {
              const item = e.parentElement;
              if (!item || item.localName !== 'vaadin-form-item') return null;
              const label = item.querySelector(':scope > [slot="label"]');
              return label ? (label.textContent || '').replace(/\s+/g, ' ').trim() : null;
            }""";

    /** The label {@code withLabel(..)} matches and a failure shows: the {@code label} property, else the form item's. */
    public static final String LABEL = """
            e => {
              const formItemLabel = %s;
              return e.label != null && String(e.label).trim() !== '' ? String(e.label) : formItemLabel(e);
            }""".formatted(FORM_ITEM_LABEL);

    /**
     * Browser function {@code e => internalPart}: whether the element is an internal part of another component,
     * one that component rendered itself rather than the application: Flow's client has bound no server-side
     * element to it, but has bound one of its ancestors in the same DOM tree (the {@code <vaadin-text-area>} a
     * {@code <vaadin-message-input>} puts in its light DOM). The UI's own element, {@code <body>}, doesn't count as
     * that ancestor: it is the page, not a component rendering its parts, so an element a component's client side
     * renders straight under it (the {@code <vaadin-notification-card>} in {@code <vaadin-notification-container>})
     * is not one (#740). An element in an application's own template, whose shadow root holds no bound element
     * above it, is not one either; without Flow's client, nothing is.
     */
    public static final String INTERNAL_PART = """
            e => {
              const clients = window.Vaadin && window.Vaadin.Flow && window.Vaadin.Flow.clients;
              const bound = n => Object.values(clients || {}).some(c => c && typeof c.getNodeId === 'function'
                  && c.getNodeId(n) >= 0);
              if (!clients || bound(e)) return false;
              for (let a = e.parentElement; a && a !== document.body; a = a.parentElement) {
                if (bound(a)) return true;
              }
              return false;
            }""";

    /**
     * Browser function
     * {@code e => [label, text, placeholder, testId, accessibleName, ancestorTestId, ancestorId, internalPart, data,
     * id]}: what {@link com.vaadin.testing.unified.spi.ElementFacts} holds, for failure messages; the text is
     * {@link #TEXT}'s, without a tooltip. The ancestor is the
     * nearest one with a {@code data-testid} or an {@code id}, crossing shadow roots to their hosts. Whether it is
     * an internal part is {@link #INTERNAL_PART}'s.
     */
    public static final String FACTS = """
            e => {
              const text = %s;
              const accessibleName = %s;
              const label = %s;
              const internalPart = %s;
              const str = v => v == null ? null : String(v);
              const up = n => n.parentElement || (n.getRootNode() instanceof ShadowRoot ? n.getRootNode().host : null);
              let a = up(e);
              while (a && !a.getAttribute('data-testid') && !a.getAttribute('id')) a = up(a);
              return [str(label(e)), text(e), str(e.placeholder), e.getAttribute('data-testid'), accessibleName(e),
                  a ? a.getAttribute('data-testid') : null, a && !a.getAttribute('data-testid') ? a.getAttribute('id') : null,
                  internalPart(e),
                  Object.fromEntries([...e.attributes].filter(x => x.name.startsWith('data-') && x.name !== 'data-testid')
                      .map(x => [x.name, x.value])),
                  e.getAttribute('id')];
            }""".formatted(TEXT, ACCESSIBLE_NAME, LABEL, INTERNAL_PART);

    /** {@link #FACTS}' result as {@link com.vaadin.testing.unified.spi.ElementFacts}. */
    public static com.vaadin.testing.unified.spi.ElementFacts facts(Object result) {
        List<?> values = (List<?>) result;
        String[] s = new String[8];
        for (int i = 0; i < s.length && i < values.size(); i++) {
            s[i] = values.get(i) == null ? null : String.valueOf(values.get(i));
        }
        java.util.Map<String, String> data = new java.util.HashMap<>();
        if (values.size() > 8 && values.get(8) instanceof java.util.Map<?, ?> m) {
            m.forEach((k, v) -> data.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        }
        String id = values.size() > 9 && values.get(9) != null ? String.valueOf(values.get(9)) : null;
        return new com.vaadin.testing.unified.spi.ElementFacts(s[0], s[1], s[2], s[3], s[4], s[5], s[6],
                Boolean.parseBoolean(s[7]), data, id);
    }

    /**
     * Browser function {@code (host, selector) => elements}: where a lookup inside a Template searches (spec 04,
     * "Templates"). First the matches in the Template's shadow root that have an {@code id} or are under an element
     * that has one, as the {@code @Id} components the server knows are; {@code closest} stays in the shadow root,
     * and {@code querySelectorAll} doesn't enter the shadow roots of Templates nested in it. Then the matches in its
     * light DOM.
     */
    public static final String IN_TEMPLATE = """
            (host, selector) => {
              const shadow = host.shadowRoot
                  ? Array.from(host.shadowRoot.querySelectorAll(selector)).filter(e => e.closest('[id]'))
                  : [];
              return shadow.concat(Array.from(host.querySelectorAll(selector)));
            }""";

    /**
     * Whether an element is the app's content: false inside the shadow root of Vaadin's own components and dev
     * tools (Copilot), at any depth. Lookups don't enter shadow roots by themselves; a test enters a Template
     * explicitly (ADR 0012).
     */
    public static final String APP_CONTENT = """
            e => {
              for (let r = e.getRootNode(); r instanceof ShadowRoot; r = r.host.getRootNode()) {
                if (/^(vaadin-|copilot-)/.test(r.host.localName)) return false;
              }
              return true;
            }""";

    /**
     * Browser function {@code (elements, filters) => indices}: the indices of the elements matching every
     * filter of {@link #remaining}, leaving out the shadow DOM of Vaadin's components and dev tools. Matching
     * is as in {@link Filter#matches}.
     */
    public static final String MATCH = """
            (elements, filters) => {
              const text = %s;
              const accessibleName = %s;
              const formItemLabel = %s;
              const norm = v => v == null ? null : String(v).replace(/\\s+/g, ' ').trim();
              const matches = (actual, f) => {
                const a = norm(actual);
                if (a == null) return false;
                return f.exact ? a === f.value : a.toLowerCase().includes(f.value.toLowerCase());
              };
              const check = (e, f) => {
                switch (f.kind) {
                  case 'TEXT': return matches(text(e), f);
                  // its own label, or the label of the FormLayout item holding it
                  case 'LABEL': return matches(e.label, f) || matches(formItemLabel(e), f);
                  case 'PLACEHOLDER': return matches(e.placeholder, f);
                  case 'ACCESSIBLE_NAME': return matches(accessibleName(e), f);
                  case 'TEST_ID': return e.getAttribute('data-testid') === f.value;
                  case 'ID': return e.getAttribute('id') === f.value;
                  case 'CLASS_NAME': return e.classList.contains(f.value);
                  case 'THEME': return (e.getAttribute('theme') || '').split(/\\s+/).includes(f.value);
                  case 'ATTRIBUTE': return f.value == null ? e.hasAttribute(f.name) : e.getAttribute(f.name) === f.value;
                  case 'WITHOUT_ATTRIBUTE': return !e.hasAttribute(f.name);
                  default: return false;
                }
              };
              const appContent = %s;
              const found = [];
              elements.forEach((e, i) => { if (appContent(e) && filters.every(f => check(e, f))) found.push(i); });
              return found;
            }""".formatted(TEXT, ACCESSIBLE_NAME, FORM_ITEM_LABEL, APP_CONTENT);

    /**
     * Browser function {@code (elements, {host, filters}) => indices}: {@link #MATCH} for candidates a query found
     * under a Template with CSS that enters every shadow root (Playwright's), keeping first only those
     * {@link #IN_TEMPLATE} would find: in the Template's own shadow root with an {@code id} on them or an ancestor
     * there, or in its light DOM.
     */
    public static final String MATCH_IN_TEMPLATE = """
            (elements, arg) => {
              const host = arg.host;
              const inside = e => e !== host && (host.shadowRoot && e.getRootNode() === host.shadowRoot
                  ? e.closest('[id]') !== null : host.contains(e));
              const kept = [];
              elements.forEach((e, i) => { if (inside(e)) kept.push(i); });
              const match = %s;
              return match(kept.map(i => elements[i]), arg.filters).map(j => kept[j]);
            }""".formatted(MATCH);

    /** Whether an element is a Template: an element of the application's own with a shadow root. */
    private static final String IS_TEMPLATE = """
            e => !!e.shadowRoot && e.localName.includes('-') && !/^(vaadin-|copilot-)/.test(e.localName)""";

    /**
     * Browser function {@code (scope, inTemplate) => elements}: the Templates in a scope, not entering them; the
     * whole page when {@code scope} is null. {@code querySelectorAll} doesn't enter shadow roots, so Templates nested
     * in another are not found.
     */
    public static final String TEMPLATES = """
            (scope, inTemplate) => {
              const all = scope === null || scope === undefined ? Array.from(document.querySelectorAll('*'))
                  : inTemplate ? (%s)(scope, '*') : Array.from(scope.querySelectorAll('*'));
              return all.filter(%s);
            }""".formatted(IN_TEMPLATE, IS_TEMPLATE);

    /**
     * Browser function {@code (elements, {scope, inTemplate}) => indices}: {@link #TEMPLATES} for candidates found
     * with CSS that enters every shadow root (Playwright's): those in the scope's own tree, or as
     * {@link #MATCH_IN_TEMPLATE} keeps them inside a Template.
     */
    public static final String TEMPLATES_AMONG = """
            (elements, arg) => {
              const scope = arg.scope;
              const inside = e => scope === null || scope === undefined ? e.getRootNode() === document
                  : arg.inTemplate ? e !== scope && (scope.shadowRoot && e.getRootNode() === scope.shadowRoot
                      ? e.closest('[id]') !== null : scope.contains(e))
                  : e !== scope && scope.contains(e);
              const isTemplate = %s;
              const found = [];
              elements.forEach((e, i) => { if (inside(e) && isTemplate(e)) found.push(i); });
              return found;
            }""".formatted(IS_TEMPLATE);
}
