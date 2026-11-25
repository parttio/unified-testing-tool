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

  const matches = targets.map((t) => Array.from(document.querySelectorAll(t.css)).map(measure));
  return JSON.stringify({ findings, matches, appLayouts: [], menuBars: [] });
}
