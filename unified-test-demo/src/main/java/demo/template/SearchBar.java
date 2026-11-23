package demo.template;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.littemplate.LitTemplate;
import com.vaadin.flow.component.template.Id;
import com.vaadin.flow.component.textfield.TextField;

/** A Template nested in {@link OrdersPanel}: a filter and a "New order" button whose text the template writes. */
@Tag("demo-search-bar")
public class SearchBar extends LitTemplate {

    @Id("filter")
    TextField filter;

    @Id("new")
    Button newOrder;
}
