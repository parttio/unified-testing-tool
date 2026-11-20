package demo.diag;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import demo.ProfileView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/** Temporary diagnostic for #447: times each line of the Unified smallForm on Selenium. Not to be merged. */
@UnifiedSpringBootTest
@EnabledIfSystemProperty(named = "vaadin.testing.tool", matches = "selenium")
class UnifiedDiagTest {

    static long t;

    static String lap() {
        long now = System.nanoTime();
        String s = String.format("%5d", (now - t) / 1_000_000);
        t = now;
        return s;
    }

    @Test
    void smallFormLines(UnifiedPage page) {
        for (int round = 0; round < 12; round++) {
            StringBuilder sb = new StringBuilder("DIAG unified");
            t = System.nanoTime();
            long start = t;
            page.navigate(ProfileView.class);
            sb.append(" nav").append(lap());
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");
            sb.append(" name").append(lap());
            page.find(UnifiedTextField.class).withLabel("Email").setValue("alice@example.com");
            sb.append(" email").append(lap());
            page.find(UnifiedButton.class).withText("Save").click();
            sb.append(" save").append(lap());
            assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alice");
            sb.append(" check").append(lap());
            sb.append(" total ").append((System.nanoTime() - start) / 1_000_000);
            // probes, after the timed body
            Object textNow = page.find(UnifiedNotification.class).getText();
            sb.append(" | again").append(lap());
            NotificationElement n = page.find(UnifiedNotification.class).unwrap(NotificationElement.class);
            sb.append(" unwrap").append(lap());
            n.getText();
            sb.append(" tbGetText").append(lap());
            WebDriver d = page.unwrap(WebDriver.class);
            ((JavascriptExecutor) d).executeScript("return 1");
            sb.append(" js").append(lap());
            System.out.println(sb);
        }
    }

    @Test
    void cardRightAfterClick(UnifiedPage page) {
        for (int round = 0; round < 8; round++) {
            if (round == 4) {
                Object wrapped = ((com.vaadin.testbench.TestBenchDriverProxy) page.unwrap(WebDriver.class)).getWrappedDriver();
                ((org.openqa.selenium.chromium.HasCdp) wrapped).executeCdpCommand("Emulation.setFocusEmulationEnabled",
                        java.util.Map.of("enabled", true));
                System.out.println("DIAG focus emulation on");
            }
            page.navigate(ProfileView.class);
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");
            page.find(UnifiedTextField.class).withLabel("Email").setValue("alice@example.com");
            page.find(UnifiedButton.class).withText("Save").click();
            WebDriver d = page.unwrap(WebDriver.class);
            System.out.println("DIAG samples-unified" + samples(d));
            Object state = ((JavascriptExecutor) d).executeScript(
                    "const n = document.querySelector('vaadin-notification'); const c = n && n._card;"
                            + "return String(!!n) + ' opened=' + (n && n.opened) + ' card=' + !!c"
                            + " + ' opacity=' + (c && getComputedStyle(c).opacity)"
                            + " + ' anim=' + (c && c.getAnimations({subtree: true}).map(a => a.animationName + ':' + a.currentTime).join(','))"
                            + " + ' text=' + (c && c.innerText.trim());");
            System.out.println("DIAG cardAfterClick " + state);
        }
    }

    static final String SAMPLE = "const n = document.querySelector('vaadin-notification'); const c = n && n._card;"
            + "return document.visibilityState + ' focus=' + document.hasFocus()"
            + " + ' op=' + (c && getComputedStyle(c).opacity)"
            + " + ' anim=' + (c && c.getAnimations({subtree: true}).map(a => a.playState + ':' + Math.round(a.currentTime)).join(','))"
            + " + ' t=' + Math.round(performance.now());";

    static String samples(org.openqa.selenium.WebDriver d) {
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) d;
        org.openqa.selenium.WebElement card = (org.openqa.selenium.WebElement) js.executeScript(
                "return document.querySelector('vaadin-notification')._card");
        StringBuilder sb = new StringBuilder();
        long t0 = System.nanoTime();
        for (int i = 0; i < 30; i++) {
            String text = card.getText();
            sb.append(String.format("%n  %4dms text='%s' %s", (System.nanoTime() - t0) / 1_000_000, text,
                    js.executeScript(SAMPLE)));
            if (!text.isEmpty()) {
                break;
            }
        }
        return sb.toString();
    }
}
