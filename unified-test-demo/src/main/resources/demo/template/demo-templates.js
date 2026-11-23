// The client side of the demo's Templates (demo.template), inlined into the page by demo.Application. Plain
// custom elements with an open shadow root, so the demo needs no frontend build: Flow binds the @Id fields of a
// LitTemplate to the elements with those ids in its shadow root, whatever defines the element.

// What Flow's client checks before it binds @Id fields: the shape of a Lit element, rendered once at creation
class DemoTemplate extends HTMLElement {
  update() {}
  shouldUpdate() { return true; }
  firstUpdated() {}
  get updateComplete() { return Promise.resolve(true); }
}

class DemoSearchBar extends DemoTemplate {
  constructor() {
    super();
    // "New order" is written here, in the template: the server's Button has no text (the Browserless gap)
    this.attachShadow({ mode: 'open' }).innerHTML = `
      <vaadin-text-field id="filter" label="Filter"></vaadin-text-field>
      <vaadin-button id="new">New order</vaadin-button>
      <vaadin-button>Not bound</vaadin-button>`;
  }
}
if (!customElements.get('demo-search-bar')) customElements.define('demo-search-bar', DemoSearchBar);

class DemoOrdersPanel extends DemoTemplate {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' }).innerHTML = `
      <demo-search-bar id="search"></demo-search-bar>
      <vaadin-button id="new"></vaadin-button>
      <span id="status"></span>
      <slot></slot>`;
  }
}
if (!customElements.get('demo-orders-panel')) customElements.define('demo-orders-panel', DemoOrdersPanel);
