---
section: Migration
---
- **App layout** ([#669](https://github.com/parttio/unified-testing-tool/issues/669)): the Migration Recipes convert
  TestBench's `AppLayoutElement` and `DrawerToggleElement` and Dramafinder's `AppLayoutElement` to
  `page.find(UnifiedAppLayout.class)`: `isDrawerOpened()`, `openDrawer()`, `closeDrawer()`, `toggleDrawer()`,
  `assertThat(layout).isDrawerOpened()` / `isDrawerClosed()`, and `isOverlay()` / `assertThat(layout).isOverlay()`,
  which add `@RequiresBrowser` to the test. TestBench's `setDrawerOpened(true)` becomes `openDrawer()` with a
  Migration Marker, as TestBench set the property and the Unified API clicks the DrawerToggle as a user does;
  `getDrawerToggle().click()` becomes `toggleDrawer()`. See
  [App layout the Migration Recipes convert](../spec/appendix-mapping.md#app-layout-the-migration-recipes-convert).
