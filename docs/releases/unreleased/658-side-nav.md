---
section: New
---
- **Side navigation** ([#658](https://github.com/parttio/unified-testing-tool/issues/658)):
  `page.find(UnifiedSideNavItem.class).withLabel("Orders").click()` clicks an item of a `SideNav` as a user does,
  following its path as a link and waiting for the new page; a nested item is found inside its parent item or its
  `UnifiedSideNav`, and its collapsed parents are expanded first. `expand()`, `collapse()`, `getPath()`, `getItems()`
  and `assertThat(item).isCurrent()`, `isExpanded()`, `hasPath(..)`, `hasItems(..)`. The Findable View lists side
  navs and their items, with `(current)` and `(collapsed)`. On Browserless, an item is current and expanded as the web
  component makes it in a browser. See [Side navigation](../guide/components.md#side-navigation).
