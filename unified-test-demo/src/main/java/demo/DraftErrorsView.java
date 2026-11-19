package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A view that goes wrong without showing it: one button makes the server log an error, the other makes the
 * browser's console get one. The user sees nothing either time.
 */
@AnonymousAllowed
@Route("draft-errors")
public class DraftErrorsView extends VerticalLayout {

    private static final Logger LOG = LoggerFactory.getLogger(DraftErrorsView.class);

    public DraftErrorsView() {
        add(new H1("Draft"),
                new Button("Save draft", e -> LOG.error("Could not save the draft", new IllegalStateException("no id"))),
                new Button("Load chart", e -> getElement().executeJs("console.error($0)", "Could not load the chart")));
    }
}
