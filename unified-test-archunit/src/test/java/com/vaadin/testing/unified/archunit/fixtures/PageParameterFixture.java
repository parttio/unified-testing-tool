package com.vaadin.testing.unified.archunit.fixtures;

import com.vaadin.testing.unified.UnifiedPage;
import org.junit.jupiter.api.Test;

/** No @UnifiedUITest here (the extension is registered elsewhere), but its UnifiedPage parameter makes it one. */
public class PageParameterFixture {

    @Test
    void opensTheProfile(UnifiedPage page) throws InterruptedException {
        page.navigate("profile");
        Thread.sleep(100);
    }
}
