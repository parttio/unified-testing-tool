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

  // the element's parent in the flat tree: its slot, its parent, or its shadow root's host
  const flatParent = (e) => e.assignedSlot || e.parentElement
      || (e.getRootNode() instanceof ShadowRoot ? e.getRootNode().host : null);

  // the boxes that can clip an element: its containing-block chain, not its DOM parents (CSS 2 §10.1; Siteimprove
  // Alfa's and Cypress's visibility rules, MIT): an absolute box escapes static ancestors, a fixed box every ancestor
  // without a transform, filter or containment
  const containingBlocks = (element) => {
    const chain = [];
    let position = getComputedStyle(element).position;
    for (let n = flatParent(element); n && n !== document.documentElement; n = flatParent(n)) {
      const style = getComputedStyle(n);
      if (style.display === 'contents') {
        continue;
      }
      const createsBlockForFixed = style.transform !== 'none' || style.filter !== 'none'
          || /paint|layout|strict|content/.test(style.contain);
      if (position === 'fixed' && !createsBlockForFixed) {
        continue;
      }
      if (position === 'absolute' && style.position === 'static' && !createsBlockForFixed) {
        continue;
      }
      chain.push(n);
      position = style.position;
    }
    return chain;
  };

  // what clips the point (x, y) of an element, or null: a box with hidden overflow it lies outside of
  const clippedBy = (element, x, y) => {
    for (const box of containingBlocks(element)) {
      const style = getComputedStyle(box);
      const r = box.getBoundingClientRect();
      const left = r.left + box.clientLeft;
      const top = r.top + box.clientTop;
      const outsideX = x < left || x > left + box.clientWidth;
      const outsideY = y < top || y > top + box.clientHeight;
      const hides = (overflow) => overflow === 'hidden' || overflow === 'clip';
      if ((hides(style.overflowX) && outsideX) || (hides(style.overflowY) && outsideY)) {
        return describe(box);
      }
    }
    return null;
  };

  const measure = (element) => {
    const r = element.getBoundingClientRect();
    const match = { box: [round(r.left), round(r.top), round(r.width), round(r.height)], reach: 'visible' };
    const by = clippedBy(element, r.left + r.width / 2, r.top + r.height / 2);
    if (by) {
      match.reach = 'clipped';
      match.by = by;
    }
    return match;
  };

  const matches = targets.map((t) => Array.from(document.querySelectorAll(t.css)).map(measure));
  matches.forEach((list, target) => list.forEach((m, index) => {
    if (m.reach === 'clipped') {
      findings.push({ kind: 'clipped', target, index, by: m.by });
    }
  }));
  return JSON.stringify({ findings, matches, appLayouts: [], menuBars: [] });
}
