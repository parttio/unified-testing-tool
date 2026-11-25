---
section: Fixed
---
- **Findable View** ([#658](https://github.com/parttio/unified-testing-tool/issues/658)): a component that isn't
  unique is printed with the lookup through a dialog or another container only when the container holds that very
  component. Before, the profile's own Email field was printed as "one in dialog “Change email”" while a dialog held
  another Email field. A component named by a Wrapper's own filter, such as a side nav item's `withLabel(..)`, is now
  told apart by its container too.
