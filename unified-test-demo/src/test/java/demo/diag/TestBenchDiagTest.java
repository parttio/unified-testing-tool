package demo.diag;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testbench.BrowserTest;
import com.vaadin.testbench.BrowserTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.JavascriptExecutor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Temporary diagnostic for #447: times each line of TestBench's smallForm. Not to be merged. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "vaadin.testing.tool", matches = "selenium")
class TestBenchDiagTest extends BrowserTestBase {

    @LocalServerPort
    int port;

    @BrowserTest
    void smallFormLines() {
        for (int round = 0; round < 12; round++) {
            StringBuilder sb = new StringBuilder("DIAG testbench");
            UnifiedDiagTest.t = System.nanoTime();
            long start = UnifiedDiagTest.t;
            getDriver().get("http://localhost:" + port + "/profile");
            sb.append(" nav").append(UnifiedDiagTest.lap());
            $(TextFieldElement.class).withLabel("Name").single().setValue("Alice");
            sb.append(" name").append(UnifiedDiagTest.lap());
            $(TextFieldElement.class).withLabel("Email").single().setValue("alice@example.com");
            sb.append(" email").append(UnifiedDiagTest.lap());
            $(ButtonElement.class).withText("Save").single().click();
            sb.append(" save").append(UnifiedDiagTest.lap());
            Assertions.assertEquals("Saved Alice", $(NotificationElement.class).waitForFirst().getText());
            sb.append(" check").append(UnifiedDiagTest.lap());
            sb.append(" total ").append((System.nanoTime() - start) / 1_000_000);
            System.out.println(sb);
        }
    }

    @BrowserTest
    void cardRightAfterClick() {
        System.out.println("DIAG tb window " + getDriver().manage().window().getSize());
        for (int round = 0; round < 8; round++) {
            if (round == 4) {
                getDriver().manage().window().setSize(new org.openqa.selenium.Dimension(1280, 1024));
                System.out.println("DIAG tb window now " + getDriver().manage().window().getSize());
            }
            getDriver().get("http://localhost:" + port + "/profile");
            $(TextFieldElement.class).withLabel("Name").single().setValue("Alice");
            $(TextFieldElement.class).withLabel("Email").single().setValue("alice@example.com");
            $(ButtonElement.class).withText("Save").single().click();
            System.out.println("DIAG samples-tb" + UnifiedDiagTest.samples(getDriver()));
            Object state = ((JavascriptExecutor) getDriver()).executeScript(
                    "const n = document.querySelector('vaadin-notification'); const c = n && n._card;"
                            + "return String(!!n) + ' opened=' + (n && n.opened) + ' card=' + !!c"
                            + " + ' opacity=' + (c && getComputedStyle(c).opacity)"
                            + " + ' anim=' + (c && c.getAnimations({subtree: true}).map(a => a.animationName + ':' + a.currentTime).join(','))"
                            + " + ' text=' + (c && c.innerText.trim());");
            System.out.println("DIAG cardAfterClick-tb " + state);
        }
    }
}
