---
status: accepted
---

# Lookups don't enter shadow roots; a test enters a Template explicitly

A LitTemplate's `@Id` components live in its shadow root. Since #251, no lookup searches there on any Testing Tool, so an app built from templates can't be tested through the Unified API (#347). #347 proposed searching the shadow roots of every app element (all but `vaadin-*` and `copilot-*` hosts) by default. We decide instead that lookups never enter a shadow root by themselves. A test enters one Template at a time by finding it, either through a Wrapper that extends the `TemplateScope` mixin or through `findTemplate(SearchBar.class)`, and searches inside it:

```java
page.findTemplate(StorefrontView.class)
    .findTemplate(SearchBar.class)
    .find(UnifiedButton.class).withId("new").click();
```

## Decisions

- **A Template's Scope covers its shadow root and its light DOM**, including slotted and server-added children, and stops at nested Templates. Slotted content is found once, at its light-DOM position. Ids are compared within the Template entered, so an id repeated across Templates is not ambiguous.
- **Only what the server knows is findable inside a LitTemplate**, on every Testing Tool: `@Id`-bound components and what is under them. Browsers approximate this as elements with an `id` and their descendants. Text, labels and attributes written in the template's HTML are empty on the server, so inside a Template `withId(..)` is the portable lookup, and the rest is listed in `appendix-gaps.md`.
  _Amended 2026-11-25 by "A findDescendants that enters a Template's open shadow root in a browser" (#617): declined. There is no browser-only Unified call that pierces a Template's shadow root, whether a `findDescendants` variant or a tag lookup that also finds id-less template HTML. Browserless can't match it, and unlike a grid column's renderer ([ADR 0015](0015-renderer-markup-is-found-by-tag-wrappers-row-click-avoids-it.md)) it has no signal to fail on, so an empty result would let `doesNotExist()` pass. Template HTML without an `@Id` is reached through the Escape Hatch; see `.out-of-scope/shadow-piercing-lookups.md`._
- **Client-rendered web components** (add-ons) use the same mechanism, but Browserless can't see inside them, so such a test requires the real-browser Capability.
- **Findability is announced, not automatic.** The Findable View lists a Template as one line with the lookup that enters it. A failed lookup searches inside Templates and reports a Near Miss with the chained lookup that would find the component. `getText()` and `withText(..)` on a Template stay light-DOM only.
- **No global setting.** Finding the Template is the opt-in.

## Considered Options

- **Search app shadow roots by default** (#347's proposal). Rejected because ids repeat across Templates and a page-wide search makes them ambiguous, and because a default that Browserless can't match breaks parity for every lookup, not only for those that enter a Template. Every Dramafinder query would also cost one more round trip.
- **A global switch for shadow search.** Rejected because it brings back behaviour that differs between setups.
- **Component References to `@Id` fields**, which is how Karibu-Testing works around templates. Rejected because Browserless is the only Testing Tool that has them.

## Consequences

- This matches TestBench, where a query on an element searches that element's shadow root one level deep and a page query searches none, so TestBench tests migrate naturally: the TestBench and Browserless Migration Recipes turn lookups into a `LitTemplate` into Template lookups (#543).
- Bakery's obvious `page.find(UnifiedButton.class).withText("New order")` fails. Its failure names the Template to enter.
