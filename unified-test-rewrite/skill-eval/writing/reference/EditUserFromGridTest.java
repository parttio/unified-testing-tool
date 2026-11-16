package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class EditUserFromGridTest {

    @Test
    @DisplayName("The Edit button in a user's row starts editing that user")
    void editsAUserFromTheirRow(UnifiedPage page) {
        // given
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users");

        // when
        users.row().withCellText("Name", "User 3").cell("Actions").find(UnifiedButton.class).withText("Edit").click();

        // then
        assertThat(page.find(UnifiedNotification.class)).hasText("Editing User 3");
    }
}
