package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Texts an application builds from more than plain text read the same on every Testing Tool (#690, #691). */
@UnifiedSpringBootTest
class NoticesTest {

    @Test
    @DisplayName("A notification built from components reads the text of its components")
    void notificationFromComponentsReadsItsComponentsText(UnifiedPage page) {
        page.navigate(NoticesView.class);

        page.find(UnifiedButton.class).withText("Save").click();

        // the components' text as a browser's textContent joins it: no space between a span and a button
        assertThat(page.find(UnifiedNotification.class).withTextContaining("Saved")).hasText("SavedUndo");
    }

    @Test
    @DisplayName("Text written as HTML reads as its text, without the markup")
    void htmlReadsAsItsText(UnifiedPage page) {
        page.navigate(NoticesView.class);

        assertThat(page.find(UnifiedDiv.class).withTestId("total")).hasText("Total 42");
        assertThat(page.find(UnifiedSpan.class).withText("Please check the input data")).isVisible();
    }

    @Test
    @DisplayName("HTML is found by the Wrapper of its root tag, as a browser finds it")
    void htmlIsFoundByItsRootTag(UnifiedPage page) {
        page.navigate(NoticesView.class);

        assertThat(page.find(UnifiedParagraph.class).withText("Prices include VAT")).isVisible();
    }

    @Test
    @DisplayName("A layout's text includes the text of HTML set inside it")
    void layoutTextIncludesHtmlInside(UnifiedPage page) {
        page.navigate(NoticesView.class);

        assertThat(page.find(UnifiedContainer.class).withTestId("welcome")).hasText("WelcomeHello");
    }

    @Test
    @DisplayName("A notification built from HTML reads its text")
    void notificationFromHtmlReadsItsText(UnifiedPage page) {
        page.navigate(NoticesView.class);

        page.find(UnifiedButton.class).withText("Import").click();

        assertThat(page.find(UnifiedNotification.class).withText("3 rows imported")).isVisible();
    }

    @Test
    @DisplayName("The Findable View names a button by the text of the HTML in it")
    void findableViewNamesAButtonByItsHtmlText(UnifiedPage page) {
        page.navigate(NoticesView.class);

        String view = page.describe();
        assertTrue(view.lines().anyMatch(l -> l.startsWith("button “Export all”")), view);
        assertThat(page.find(UnifiedButton.class).withText("Export all")).isVisible();
    }
}
