/*
 * The layout script of the Unified API (ADR 0011): one round trip that measures what a user can reach on the page at
 * its current Viewport. It only reads: it never scrolls, styles or changes the page.
 *
 * In:  [argument, subject]: argument is {"targets": [{"css": "vaadin-button", "interactive": true}, ...]} as a JSON
 *       string; subject is an element to report on as well, or null
 * Out: {"findings": [...], "matches": [[{"box": [x, y, width, height], "reach": "visible"}, ...], ...],
 *       "appLayouts": [...], "menuBars": [...], "subject": {"box": ..., "reach": ...}}, as a JSON string: matches per target, in document order, outside
 *       shadow roots, as find() searches.
 */
async (input) => {
  const [argument, subject] = Array.isArray(input) ? input : [input, null];
  const { targets } = JSON.parse(argument);

  // never measure while AppLayout's drawer slides: wait for its animations, at most a second (#491). Playwright's
  // evaluate and WebDriver's Execute Script both await the promise this function returns.
  const DRAWER_PARTS = '[part~="drawer"], [part~="backdrop"]';
  const drawerAnimations = () => Array.from(document.querySelectorAll('vaadin-app-layout'))
      .flatMap((layout) => Array.from(layout.shadowRoot ? layout.shadowRoot.querySelectorAll(DRAWER_PARTS) : []))
      .flatMap((part) => part.getAnimations());
  const running = drawerAnimations();
  if (running.length) {
    await Promise.race([Promise.all(running.map((a) => a.finished.catch(() => null))),
        new Promise((done) => setTimeout(done, 1000))]);
  }
  const animating = drawerAnimations().some((a) => a.playState === 'running');
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

  // how a user reaches the point (x, y) of an element through its containing blocks: {reach: 'clipped', by} when a
  // box with hidden overflow cuts it off, {reach: 'after scroll'} when a scrolling box shows it once scrolled
  const reachThroughBoxes = (element, x, y) => {
    let afterScroll = false;
    for (const box of containingBlocks(element)) {
      const style = getComputedStyle(box);
      const r = box.getBoundingClientRect();
      const left = r.left + box.clientLeft;
      const top = r.top + box.clientTop;
      const outsideX = x < left || x > left + box.clientWidth;
      const outsideY = y < top || y > top + box.clientHeight;
      const hides = (overflow) => overflow === 'hidden' || overflow === 'clip';
      if ((hides(style.overflowX) && outsideX) || (hides(style.overflowY) && outsideY)) {
        return { reach: 'clipped', by: describe(box) };
      }
      const scrolls = (overflow) => overflow === 'auto' || overflow === 'scroll';
      if ((scrolls(style.overflowX) && outsideX) || (scrolls(style.overflowY) && outsideY)) {
        // a scrolling box: nothing scrolls to before its origin (in its content, after its scroll offset)
        if (x - left + box.scrollLeft < 0 || y - top + box.scrollTop < 0) {
          return { reach: 'clipped', by: describe(box) };
        }
        afterScroll = true;
        // once scrolled into view, the point is inside the box: the boxes around it and the page judge that place
        x = Math.min(Math.max(x, left), left + box.clientWidth);
        y = Math.min(Math.max(y, top), top + box.clientHeight);
      }
    }
    return { reach: afterScroll ? 'after scroll' : 'visible', x, y };
  };

  // how a user reaches the point (x, y) on the page itself, or null when it is in the Viewport: nothing scrolls to
  // the left of or above the page's origin, nor past an edge whose overflow the page hides, nor to a fixed box
  const pageOverflowY = (() => {
    const html = getComputedStyle(document.documentElement).overflowY;
    return html !== 'visible' || !document.body ? html : getComputedStyle(document.body).overflowY;
  })();
  const reachOnThePage = (element, x, y) => {
    if (x + window.scrollX < 0 || y + window.scrollY < 0) {
      return { reach: 'clipped', by: 'the page' };
    }
    const outsideX = x > root.clientWidth;
    const outsideY = y > root.clientHeight;
    if (!outsideX && !outsideY) {
      return null;
    }
    const hidesY = pageOverflowY === 'hidden' || pageOverflowY === 'clip';
    const fixed = getComputedStyle(element).position === 'fixed';
    if (fixed || (outsideX && !pageScrolls) || (outsideY && hidesY)) {
      return { reach: 'clipped', by: 'the page' };
    }
    return { reach: 'after scroll' };
  };

  // what a click at (x, y) reaches: elementFromPoint, descending into each open shadow root, as Playwright's
  // expectHitTarget does (Apache-2.0)
  const hitTarget = (x, y) => {
    let hit = document.elementFromPoint(x, y);
    while (hit && hit.shadowRoot) {
      const inner = hit.shadowRoot.elementFromPoint(x, y);
      if (!inner || inner === hit) {
        break;
      }
      hit = inner;
    }
    return hit;
  };

  // whether a click on hit lands on element: hit is element or inside it, across shadow roots and slots
  const reaches = (hit, element) => {
    for (let n = hit; n; n = flatParent(n)) {
      if (n === element) {
        return true;
      }
    }
    return false;
  };

  // the matched elements, so that a cover is named as the component the test knows: element -> {target, index}
  const matchedAt = new Map();

  // the nearest matched element at or above n, across shadow roots, or null
  const matchedAround = (n, interactiveOnly) => {
    for (; n; n = flatParent(n)) {
      const at = matchedAt.get(n);
      if (at && (!interactiveOnly || targets[at.target].interactive)) {
        return n;
      }
    }
    return null;
  };

  // what covers element at (x, y): {by, byMatch: [target, index] when it is a match}, or null when a click there
  // reaches it
  const coveredBy = (element, x, y) => {
    const hit = hitTarget(x, y);
    if (!hit || reaches(hit, element)) {
      return null;
    }
    if (inAppLayoutDrawer(hit)) {
      return { by: APP_LAYOUT_DRAWER };
    }
    const match = matchedAround(hit, false);
    if (match) {
      const at = matchedAt.get(match);
      return { by: describe(match), byMatch: [at.target, at.index] };
    }
    return { by: describe(hit) };
  };

  // Vaadin's own disclosures, named the same way everywhere (#491): AppLayout's drawer and MenuBar's overflow menu
  const APP_LAYOUT_DRAWER = 'the AppLayout drawer';
  const MENU_BAR_OVERFLOW = 'the MenuBar overflow';

  // whether n is in an AppLayout's drawer: its drawer or backdrop part, or content slotted into the drawer
  const inAppLayoutDrawer = (n) => {
    for (; n; n = flatParent(n)) {
      if (n.getAttribute && n.getAttribute('slot') === 'drawer' && n.parentElement
          && n.parentElement.tagName.toLowerCase() === 'vaadin-app-layout') {
        return true;
      }
      const root = n.getRootNode && n.getRootNode();
      if (root instanceof ShadowRoot && root.host.tagName.toLowerCase() === 'vaadin-app-layout' && n.matches
          && n.matches(DRAWER_PARTS)) {
        return true;
      }
    }
    return false;
  };

  // why a hidden element is hidden, when Vaadin hides it on purpose: in a closed overlay drawer, or collapsed
  // into MenuBar's overflow button (inline visibility: hidden on the button, as Vaadin's MenuBar does)
  const hiddenIn = (element) => {
    if (inAppLayoutDrawer(element)) {
      return APP_LAYOUT_DRAWER;
    }
    for (let n = element; n; n = flatParent(n)) {
      if (n.tagName && n.tagName.toLowerCase() === 'vaadin-menu-bar-button' && n.style.visibility === 'hidden') {
        return MENU_BAR_OVERFLOW;
      }
    }
    return null;
  };

  const measure = (element) => {
    const r = element.getBoundingClientRect();
    const match = { box: [round(r.left), round(r.top), round(r.width), round(r.height)], reach: 'visible' };
    if (!element.checkVisibility({ visibilityProperty: true })) {
      match.reach = 'hidden';
      const why = hiddenIn(element);
      if (why) {
        match.by = why;
      }
      return match;
    }
    const x = r.left + r.width / 2;
    const y = r.top + r.height / 2;
    let reach = reachThroughBoxes(element, x, y);
    if (reach.reach !== 'clipped') {
      reach = reachOnThePage(element, reach.x, reach.y) || reach;
    }
    if (reach.reach === 'visible') {
      const cover = coveredBy(element, x, y);
      if (cover) {
        reach = { reach: 'behind', ...cover };
      }
    }
    match.reach = reach.reach;
    if (reach.by) {
      match.by = reach.by;
    }
    if (reach.byMatch) {
      match.byMatch = reach.byMatch;
    }
    return match;
  };

  const elements = targets.map((t) => Array.from(document.querySelectorAll(t.css)));
  elements.forEach((list, target) => list.forEach((e, index) => matchedAt.set(e, { target, index })));
  const matches = elements.map((list) => list.map(measure));

  // an element in an open overlay (a modal dialog, a popover, a Vaadin overlay): what it covers is meant to be covered
  const OVERLAY = ':popover-open, dialog[open], [aria-modal="true"], [opened][role="dialog"]';
  const inOverlay = (element) => {
    for (let n = element; n; n = flatParent(n)) {
      if (n.matches && n.matches(OVERLAY)) {
        return true;
      }
    }
    return false;
  };

  // overlap: an interactive match on which two or more of five points (its centre and its corners, inset) land on
  // another interactive match, so that a 1 px touch is not one (#491)
  const INSET = 2;
  for (const [element, at] of matchedAt) {
    if (!targets[at.target].interactive || matchedAround(flatParent(element), true)) {
      continue;
    }
    const r = element.getBoundingClientRect();
    if (r.width <= 2 * INSET || r.height <= 2 * INSET || ['clipped', 'hidden'].includes(matches[at.target][at.index].reach)) {
      continue;
    }
    const points = [[r.left + r.width / 2, r.top + r.height / 2], [r.left + INSET, r.top + INSET],
        [r.right - INSET, r.top + INSET], [r.left + INSET, r.bottom - INSET], [r.right - INSET, r.bottom - INSET]];
    const covers = new Map();
    for (const [x, y] of points) {
      const hit = hitTarget(x, y);
      const other = hit && !reaches(hit, element) ? matchedAround(hit, true) : null;
      if (other && !reaches(element, other) && !inOverlay(other)) {
        covers.set(other, (covers.get(other) || 0) + 1);
      }
    }
    for (const [other, count] of covers) {
      if (count >= 2) {
        const by = matchedAt.get(other);
        findings.push({ kind: 'overlap', target: at.target, index: at.index, byTarget: by.target, byIndex: by.index });
        break;
      }
    }
  }
  matches.forEach((list, target) => list.forEach((m, index) => {
    if (m.reach === 'clipped') {
      findings.push({ kind: 'clipped', target, index, by: m.by });
    }
  }));
  const result = { findings, matches, appLayouts: [], menuBars: [] };
  if (animating) {
    result.animating = true;
  }
  if (subject) {
    result.subject = measure(subject);
  }
  return JSON.stringify(result);
}
