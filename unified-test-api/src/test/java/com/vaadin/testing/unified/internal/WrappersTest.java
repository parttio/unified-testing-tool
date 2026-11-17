package com.vaadin.testing.unified.internal;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** Wrappers carry their filters (ADR 0007): immutable, lazy, "exactly one" on every use. */
class WrappersTest {

    /** Buttons on a fake page; {@code find} applies TEXT filters. */
    private final List<String> buttons = new CopyOnWriteArrayList<>();
    private final List<String> clicked = new CopyOnWriteArrayList<>();
    /** The filters of every query the fake page received. */
    private final List<List<Filter>> queried = new CopyOnWriteArrayList<>();

    private UnifiedPageImpl page() {
        AdapterSession session = (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> {
                    if (method.getName().equals("find")) {
                        QuerySpec spec = (QuerySpec) args[0];
                        queried.add(spec.filters());
                        return buttons.stream()
                                .filter(text -> spec.filters().stream()
                                        .allMatch(f -> f.kind() == Filter.Kind.TEXT && f.matches(text)))
                                .map(this::button)
                                .toList();
                    }
                    return null;
                });
        return new UnifiedPageImpl(new TestRun(TestingTool.BROWSERLESS, session, Duration.ofMillis(300)));
    }

    private UnifiedElement button(String text) {
        return (UnifiedElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { UnifiedElement.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "isVisible", "isEnabled" -> true;
                    case "getText" -> text;
                    case "click" -> {
                        clicked.add(text);
                        yield null;
                    }
                    default -> null;
                });
    }

    @Test
    void filtersReturnANewWrapperAndLeaveTheReceiverUnchanged() {
        buttons.addAll(List.of("Save", "Cancel"));
        UnifiedButton any = page().find(UnifiedButton.class);
        UnifiedButton save = any.withText("Save");

        assertNotSame(any, save);
        assertEquals("UnifiedButton[UnifiedButton]", any.toString());
        assertEquals("UnifiedButton[UnifiedButton.withText(\"Save\")]", save.toString());
        save.click();
        assertEquals(List.of("Save"), clicked);
        assertThat(any).hasCount(2);
        assertThat(save).hasCount(1);
    }

    @Test
    void takesOneClassNamePerWithClassNameAsFlowDoes() {
        UnifiedButton any = page().find(UnifiedButton.class);

        IllegalArgumentException spaces = assertThrows(IllegalArgumentException.class,
                () -> any.withClassName("primary large"));
        assertEquals("Class name cannot contain spaces: \"primary large\"; use withClassNames(..) to require several",
                spaces.getMessage());
        assertThrows(IllegalArgumentException.class, () -> any.withClassName(" "));
        assertThrows(IllegalArgumentException.class, () -> any.withClassNames());
    }

    @Test
    void withClassNamesRequiresEachNameSplitAsFlowDoes() {
        UnifiedButton both = page().find(UnifiedButton.class).withClassNames("primary", " large  wide ");

        assertEquals("UnifiedButton[UnifiedButton.withClassName(\"primary\").withClassName(\"large\")"
                + ".withClassName(\"wide\")]", both.toString());
        both.all();
        assertEquals(List.of(new Filter(Filter.Kind.CLASS_NAME, null, "primary", true),
                new Filter(Filter.Kind.CLASS_NAME, null, "large", true),
                new Filter(Filter.Kind.CLASS_NAME, null, "wide", true)), queried.get(queried.size() - 1));
    }

    @Test
    void checksExactlyOneOnEveryUse() {
        buttons.addAll(List.of("Save", "Save"));
        UnifiedButton save = page().find(UnifiedButton.class).withText("Save");

        AssertionError several = assertThrows(AssertionError.class, save::click);
        assertEquals("exactly one button with text “Save”", ((UnifiedAssertionError) several).getExpected());
        assertTrue(((UnifiedAssertionError) several).getFound().startsWith("2: button “Save”, button “Save”"),
                several.getMessage());

        buttons.remove("Save");
        save.click();
        assertEquals(List.of("Save"), clicked);
    }

    @Test
    void assertsExistenceAndCount() {
        buttons.addAll(List.of("Save", "Delete", "Delete"));
        UnifiedPageImpl page = page();

        assertThat(page.find(UnifiedButton.class).withText("Save")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Archive")).doesNotExist();
        assertThat(page.find(UnifiedButton.class).withText("Delete")).exists().hasCount(2);
        AssertionError missing = assertThrows(AssertionError.class,
                () -> assertThat(page.find(UnifiedButton.class).withText("Archive")).exists());
        assertTrue(missing.getMessage().contains("Expected      button “Archive” to exist\n  Found         0 matches"),
                missing.getMessage());
        assertThrows(AssertionError.class,
                () -> assertThat(page.find(UnifiedButton.class).withText("Save")).doesNotExist());
    }

    @Test
    void allReturnsLazyWrappersByPosition() {
        buttons.addAll(List.of("A", "B"));
        List<UnifiedButton> all = page().find(UnifiedButton.class).all();

        assertEquals(2, all.size());
        assertEquals("B", all.get(1).getText());
        buttons.remove("A");
        assertEquals("B", all.get(0).getText());
        assertThat(all.get(1)).doesNotExist();
    }

    @Test
    void findComponentCarriesTheSharedFilters() {
        buttons.addAll(List.of("Save", "Cancel"));
        UnifiedGenericComponent cancel = page()
                .findComponent(com.vaadin.flow.component.button.Button.class).withText("Cancel");

        assertEquals("Cancel", cancel.getText());
    }
}
