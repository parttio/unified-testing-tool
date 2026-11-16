package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class FilterUsersTest {

    @Test
    @DisplayName("Filtering the users by name shows only the matching users")
    void filtersUsersByName(UnifiedPage page) {
        // given
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users");

        // when
        users.column("Name").header().find(UnifiedTextField.class).withPlaceholder("Filter by name")
                .setValue("User 11");

        // then
        assertThat(users).hasRowCount(11);
        assertThat(users.row().withCellText("Name", "User 11")).exists();
        assertThat(users.row().withCellText("Name", "User 12")).doesNotExist();
    }
}
