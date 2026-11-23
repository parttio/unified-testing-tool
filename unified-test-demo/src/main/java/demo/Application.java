package demo;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.page.Inline;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * The client side of the demo's Templates ({@code demo.template}), inline in the page so that their elements are
     * defined before Flow binds their {@code @Id} fields, without a frontend build.
     */
    @Override
    public void configurePage(AppShellSettings settings) {
        try (InputStream in = Application.class.getResourceAsStream("template/demo-templates.js")) {
            settings.addInlineWithContents(Inline.Position.APPEND, new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    Inline.Wrapping.JAVASCRIPT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
