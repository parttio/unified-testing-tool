---
section: Fixed
---
- **The Findable View lists the same components on every Testing Tool**
  ([#517](https://github.com/parttio/unified-testing-tool/issues/517)): in a browser it no longer lists a
  component's internal parts, which the component renders itself and the application did not create, such as the
  text area inside a message input. Adapters report this as `ElementFacts.internalPart()`.
