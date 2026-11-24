package com.vaadin.testing.unified.selenium;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.testbench.DatePickerElement;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.testbench.NumberFieldElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testing.unified.spi.GridAccess;
import com.vaadin.testing.unified.spi.SelectionAccess;
import com.vaadin.testing.unified.spi.TabSheetAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.openqa.selenium.WebElement;

/**
 * The Selenium adapter's Core Set registrations: which tag stands for a component on the page, and how it
 * is read and acted on when the generic Unified Element behaviour is not enough. Actions go through
 * TestBench's own element classes where TestBench has one. Adding a Core Set component means one entry here.
 */
final class SeleniumComponents {
    private SeleniumComponents() {}

    /** How the Selenium adapter handles one component type. Every method has a generic default. */
    interface Support {
        /** CSS selector for the component on the page; by default its web component tag. */
        default String selector(String componentTag) {
            return componentTag;
        }

        /** Whether a found element counts as a match at all (e.g. only open dialogs). */
        default boolean include(WebElement element, SeleniumSession session) {
            return true;
        }

        default Optional<String> text(WebElement element, SeleniumSession session) {
            return Optional.empty();
        }

        default Optional<Boolean> visible(WebElement element, SeleniumSession session) {
            return Optional.empty();
        }

        default Optional<Boolean> booleanProperty(WebElement element, String name, SeleniumSession session) {
            return Optional.empty();
        }

        /** Sets the value as a user would; false = not handled (the generic input + change events). */
        default boolean setValue(WebElement element, Object value, SeleniumSession session) {
            return false;
        }

        /**
         * The element that holds what the user sees, when it isn't the component's own element (e.g. a
         * notification's card): text filters, the text and child queries go there.
         */
        default WebElement content(WebElement element, SeleniumSession session) {
            return element;
        }

        /** A component-specific access object for the core (e.g. GridAccess), reached through unwrap. */
        default Optional<Object> unwrap(Class<?> type, WebElement element, SeleniumSession session) {
            return Optional.empty();
        }
    }

    /**
     * An overlay host (spec 04, "What a lookup sees"): an element with no box of its own, whose content its
     * overlay shows. It is part of the page, and visible, while its overlay is open: its {@code opened}
     * property, as TestBench's DialogElement and NotificationElement read it. Closed ones (the app's or the dev
     * tools') are not part of the page. Another overlay component opts in by registering one.
     */
    static class OverlayHost implements Support {
        @Override
        public boolean include(WebElement element, SeleniumSession session) {
            return opened(element, session);
        }

        @Override
        public Optional<Boolean> visible(WebElement element, SeleniumSession session) {
            return Optional.of(opened(element, session));
        }

        @Override
        public Optional<Boolean> booleanProperty(WebElement element, String name, SeleniumSession session) {
            return "opened".equals(name) ? Optional.of(opened(element, session)) : Optional.empty();
        }

        static boolean opened(WebElement element, SeleniumSession session) {
            return Boolean.TRUE.equals(session.testBench(element).getPropertyBoolean("opened"));
        }
    }

    static final Support DEFAULT = new Support() {};
    private static final Map<Class<? extends Component>, Support> REGISTRY = new LinkedHashMap<>();

    static {
        register(TextField.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                session.testBench(element).wrap(TextFieldElement.class).setValue(value == null ? "" : value.toString());
                return true;
            }
        });
        register(NumberField.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                String text = value == null ? ""
                        : new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
                session.testBench(element).wrap(NumberFieldElement.class).setValue(text);
                return true;
            }
        });
        register(DatePicker.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                DatePickerElement picker = session.testBench(element).wrap(DatePickerElement.class);
                if (value instanceof String text) { // UnifiedDatePicker.typeText: as it is, unparsed
                    picker.setInputValue(text); // replaces the input's text, then Enter
                    return true;
                }
                if (value == null) {
                    picker.clear();
                } else {
                    picker.setDate((LocalDate) value);
                }
                return true;
            }
        });
        register(Checkbox.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                // TestBench's setChecked: the property, then change; plus what a click does to indeterminate,
                // in the same script so it costs no extra round trip
                session.js("if (arguments[0].checked !== arguments[1]) { arguments[0].indeterminate = false; }"
                        + " arguments[0].checked = arguments[1];", element, Boolean.TRUE.equals(value));
                session.testBench(element).dispatchEvent("change", Map.of("bubbles", true));
                return true;
            }
        });
    }

    static {
        register(Notification.class, new OverlayHost() {
            /**
             * The card the notification shows its content in. Not NotificationElement.getContext(), which waits
             * for the card to show its content, polling every 500 ms.
             */
            @Override
            public WebElement content(WebElement element, SeleniumSession session) {
                return card(element, session);
            }

            /**
             * The card's DOM text, as the Playwright adapter reads it: not NotificationElement.getText(), the
             * visible text, which is empty while the card's open animation runs, and which TestBench waits for
             * in 500 ms polls (#447). getVisibleText() still reads what is shown.
             */
            @Override
            public Optional<String> text(WebElement element, SeleniumSession session) {
                return Optional.of((String) session.js("const card = arguments[0]._card;"
                        + " return card ? (" + com.vaadin.testing.unified.internal.BrowserQueries.TEXT + ")(card) : '';",
                        element));
            }

            private WebElement card(WebElement element, SeleniumSession session) {
                return SeleniumSession.stale(() -> session.testBench(element).getPropertyElement("_card"));
            }
        });
        register(Dialog.class, new OverlayHost());
        register(ConfirmDialog.class, new OverlayHost() {
            @Override
            public Optional<Object> unwrap(Class<?> type, WebElement element, SeleniumSession session) {
                return type == com.vaadin.testing.unified.spi.ConfirmDialogAccess.class
                        ? Optional.of(new SeleniumCoreSetAccess.ConfirmDialogAccessImpl(session, element))
                        : Optional.empty();
            }
        });
    }

    static {
        register(com.vaadin.flow.component.textfield.TextArea.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                session.testBench(element).wrap(com.vaadin.flow.component.textfield.testbench.TextAreaElement.class)
                        .setValue(value == null ? "" : value.toString());
                return true;
            }
        });
        register(com.vaadin.flow.component.textfield.EmailField.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                session.testBench(element).wrap(com.vaadin.flow.component.textfield.testbench.EmailFieldElement.class)
                        .setValue(value == null ? "" : value.toString());
                return true;
            }
        });
        register(com.vaadin.flow.component.textfield.PasswordField.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                session.testBench(element).wrap(com.vaadin.flow.component.textfield.testbench.PasswordFieldElement.class)
                        .setValue(value == null ? "" : value.toString());
                return true;
            }
        });
        register(com.vaadin.flow.component.textfield.IntegerField.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                session.testBench(element).wrap(com.vaadin.flow.component.textfield.testbench.IntegerFieldElement.class)
                        .setValue(com.vaadin.testing.unified.internal.BrowserQueries.typedText(value));
                return true;
            }
        });
        register(com.vaadin.flow.component.textfield.BigDecimalField.class, new Support() {
            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                session.testBench(element).wrap(com.vaadin.flow.component.textfield.testbench.BigDecimalFieldElement.class)
                        .setValue(com.vaadin.testing.unified.internal.BrowserQueries.typedText(value));
                return true;
            }
        });
        register(com.vaadin.flow.component.datetimepicker.DateTimePicker.class, new Support() {
            @Override
            public Optional<Object> unwrap(Class<?> type, WebElement element, SeleniumSession session) {
                return type == com.vaadin.testing.unified.spi.DateTimePickerAccess.class
                        ? Optional.of(new SeleniumCoreSetAccess.DateTimePickerAccessImpl(session, element))
                        : Optional.empty();
            }

            @Override
            public boolean setValue(WebElement element, Object value, SeleniumSession session) {
                session.testBench(element)
                        .wrap(com.vaadin.flow.component.datetimepicker.testbench.DateTimePickerElement.class)
                        .setDateTime((java.time.LocalDateTime) value);
                return true;
            }
        });
        register(com.vaadin.flow.component.tabs.Tabs.class, access(TabSheetAccess.class,
                (element, session) -> SeleniumTabSheetAccess.tabs(session, element)));
        register(com.vaadin.flow.component.details.Details.class, access(com.vaadin.testing.unified.spi.DetailsAccess.class,
                (element, session) -> new SeleniumCoreSetAccess.DetailsAccessImpl(session, element)));
        register(com.vaadin.flow.component.menubar.MenuBar.class, access(com.vaadin.testing.unified.spi.MenuBarAccess.class,
                (element, session) -> new SeleniumCoreSetAccess.MenuBarAccessImpl(session, element)));
        register(com.vaadin.flow.component.upload.Upload.class, access(com.vaadin.testing.unified.spi.UploadAccess.class,
                (element, session) -> new SeleniumCoreSetAccess.UploadAccessImpl(session, element)));
        register(com.vaadin.flow.component.messages.MessageInput.class, access(com.vaadin.testing.unified.spi.MessageInputAccess.class,
                (element, session) -> new SeleniumCoreSetAccess.MessageInputAccessImpl(session, element)));
        register(com.vaadin.flow.component.messages.MessageList.class, access(com.vaadin.testing.unified.spi.MessageListAccess.class,
                (element, session) -> new SeleniumCoreSetAccess.MessageListAccessImpl(session, element)));
    }

    static {
        register(Grid.class, access(GridAccess.class, (element, session) -> new SeleniumGridAccess(session, element)));
        register(ComboBox.class, access(SelectionAccess.class,
                (element, session) -> new SeleniumSelectionAccess(session, element, SeleniumSelectionAccess.Kind.COMBO_BOX)));
        register(MultiSelectComboBox.class, access(SelectionAccess.class, (element, session) ->
                new SeleniumSelectionAccess(session, element, SeleniumSelectionAccess.Kind.MULTI_SELECT_COMBO_BOX)));
        register(RadioButtonGroup.class, access(SelectionAccess.class, (element, session) ->
                new SeleniumSelectionAccess(session, element, SeleniumSelectionAccess.Kind.RADIO_BUTTON_GROUP)));
        register(TabSheet.class, access(TabSheetAccess.class,
                (element, session) -> SeleniumTabSheetAccess.tabSheet(session, element)));
    }

    private static Support access(Class<?> accessType,
            java.util.function.BiFunction<WebElement, SeleniumSession, Object> factory) {
        return new Support() {
            @Override
            public Optional<Object> unwrap(Class<?> type, WebElement element, SeleniumSession session) {
                return type == accessType ? Optional.of(factory.apply(element, session)) : Optional.empty();
            }
        };
    }

    static void register(Class<? extends Component> type, Support support) {
        REGISTRY.put(type, support);
    }

    /** The support for the most specific registered supertype of {@code type}, or the generic default. */
    static Support of(Class<?> type) {
        Support found = DEFAULT;
        Class<?> best = null;
        for (Map.Entry<Class<? extends Component>, Support> e : REGISTRY.entrySet()) {
            if (e.getKey().isAssignableFrom(type) && (best == null || best.isAssignableFrom(e.getKey()))) {
                best = e.getKey();
                found = e.getValue();
            }
        }
        return found;
    }
}
