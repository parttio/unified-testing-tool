/*
 * The layout script of the Unified API (ADR 0011): one round trip that measures what a user can reach on the page at
 * its current Viewport. It only reads: it never scrolls, styles or changes the page.
 *
 * In:  {"targets": [{"css": "vaadin-button", "interactive": true}, ...]}, as a JSON string
 * Out: {"findings": [...], "matches": [[{"box": [x, y, width, height], "reach": "visible"}, ...], ...],
 *       "appLayouts": [...], "menuBars": [...]}, as a JSON string: matches per target, in document order, outside
 *       shadow roots, as find() searches.
 */
(argument) => {
  const { targets } = JSON.parse(argument);
  const round = (n) => Math.round(n * 10) / 10;

  const measure = (element) => {
    const r = element.getBoundingClientRect();
    return { box: [round(r.left), round(r.top), round(r.width), round(r.height)], reach: 'visible' };
  };

  // a box that sticks out by less than this is rounding, not something a user scrolls to
  const TOLERANCE = 1;
  const findings = [];

  // sideways scroll of the page itself
  const root = document.scrollingElement || document.documentElement;
  // the viewport takes html's overflow, or body's when html's is visible (CSS Overflow 3, propagation)
  const htmlOverflowX = getComputedStyle(document.documentElement).overflowX;
  const pageOverflowX = htmlOverflowX !== 'visible' || !document.body ? htmlOverflowX
      : getComputedStyle(document.body).overflowX;
  const pageScrolls = pageOverflowX !== 'hidden' && pageOverflowX !== 'clip';
  if (pageScrolls && root.scrollWidth > root.clientWidth + TOLERANCE) {
    findings.push({ kind: 'scroll', container: 'the page', by: root.scrollWidth - root.clientWidth });
  }

  // every element of the document and of each open shadow root, one TreeWalker each (as Lighthouse's
  // getElementsInDocument does)
  const allElements = () => {
    const all = [];
    const walk = (root) => {
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
      for (let e = walker.nextNode(); e; e = walker.nextNode()) {
        all.push(e);
        if (e.shadowRoot) {
          walk(e.shadowRoot);
        }
      }
    };
    walk(document);
    return all;
  };

  // how a finding names an element: its tag and id; in a shadow root, its host and its part
  const describe = (e) => {
    const root = e.getRootNode();
    if (root instanceof ShadowRoot) {
      const part = e.getAttribute('part');
      const named = Array.from(e.attributes).find((a) => a.name !== 'style' && a.name !== 'class');
      const attribute = part ? '[part=' + part + ']' : named ? '[' + named.name + ']' : e.tagName.toLowerCase();
      return root.host.tagName.toLowerCase() + ' ' + attribute;
    }
    return e.tagName.toLowerCase() + (e.id ? '#' + e.id : '');
  };

  // components that scroll sideways by design, with their own affordance: not a finding (#491)
  const SCROLL_BY_DESIGN = new Set(['vaadin-grid', 'vaadin-tabs', 'vaadin-tabsheet', 'vaadin-virtual-list']);
  const scrollsByDesign = (e) => {
    for (let n = e; n; n = n.parentElement || (n.getRootNode() instanceof ShadowRoot ? n.getRootNode().host : null)) {
      if (SCROLL_BY_DESIGN.has(n.tagName.toLowerCase())) {
        return true;
      }
    }
    return false;
  };

  // sideways scroll of a container: something a user must scroll sideways in
  for (const e of allElements()) {
    const overflowX = getComputedStyle(e).overflowX;
    if ((overflowX === 'auto' || overflowX === 'scroll') && e.scrollWidth > e.clientWidth + TOLERANCE
        && e.checkVisibility() && !scrollsByDesign(e)) {
      findings.push({ kind: 'scroll', container: describe(e), by: e.scrollWidth - e.clientWidth });
    }
  }

  const matches = targets.map((t) => Array.from(document.querySelectorAll(t.css)).map(measure));
  return JSON.stringify({ findings, matches, appLayouts: [], menuBars: [] });
}
