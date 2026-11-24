package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

/** Temporary (#601): fails on purpose, to check that CI re-runs it with Playwright's trace. */
@UnifiedSpringBootTest
class RetraceProbeTest {

    @Test
    void failsOnPurpose(UnifiedPage page) {
        page.navigate(DashboardView.class);
        assertThat(page.find(UnifiedMetricCard.class)).hasCount(3);
    }
}
