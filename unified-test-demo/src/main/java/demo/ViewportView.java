package demo;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * What size the browser and the server see (ADR 0011): "Measure" shows the browser's Viewport now; the second line
 * is the last window resize the server heard of, which Flow sends 300 ms after the browser's last resize event.
 */
@AnonymousAllowed
@Route("viewport")
public class ViewportView extends VerticalLayout {

    private final Span heard = new Span("no resize heard");

    public ViewportView() {
        Span measured = new Span("not measured");
        measured.setId("measured");
        heard.setId("heard");
        Button measure = new Button("Measure", e -> getElement()
                .executeJs("return window.innerWidth + ' × ' + window.innerHeight")
                .then(String.class, measured::setText));
        add(measure, measured, heard);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        attachEvent.getUI().getPage().addBrowserWindowResizeListener(
                e -> heard.setText(e.getWidth() + " × " + e.getHeight()));
    }
}
