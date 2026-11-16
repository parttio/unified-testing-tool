package com.vaadin.testing.unified.playwright;

import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/** Spring-only code: base URL from the random port of {@code @UnifiedSpringBootTest}. */
final class SpringSupport {
    private SpringSupport() {}

    /**
     * The app's URL when the test's Spring context runs a web server on a port, such as
     * {@code @UnifiedSpringBootTest}'s random port.
     *
     * @param springContext the test's {@code ApplicationContext}, from {@code AdapterContext.springContext()}
     */
    static Optional<String> baseUrl(Object springContext) {
        Environment env = ((ApplicationContext) springContext).getEnvironment();
        String port = env.getProperty("local.server.port");
        if (port == null) {
            return Optional.empty();
        }
        String contextPath = env.getProperty("server.servlet.context-path", "");
        return Optional.of("http://localhost:" + port + contextPath);
    }
}
