package com.vaadin.testing.unified.selenium;

import com.vaadin.testing.unified.spi.AppLayoutAccess;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** AppLayout on Selenium (#493), as on Playwright: the web component's own state. */
final class SeleniumAppLayout {
    private SeleniumAppLayout() {}

    record Access(SeleniumSession session, WebElement element) implements AppLayoutAccess {
        @Override
        public boolean drawerOpened() {
            return Boolean.TRUE.equals(session.js("return arguments[0].drawerOpened;", element));
        }

        @Override
        public void toggleDrawer() {
            List<WebElement> toggles = element.findElements(By.cssSelector("vaadin-drawer-toggle"));
            if (toggles.isEmpty()) {
                throw new IllegalStateException("The app layout has no DrawerToggle to open or close its drawer with");
            }
            if (Boolean.TRUE.equals(session.js("return arguments[0].overlay && arguments[0].drawerOpened;", element))) {
                session.pressKey("Escape"); // the opened overlay drawer covers the toggle
            } else {
                toggles.get(0).click();
            }
            session.waitForVaadin();
        }
    }
}
