---
section: Changed behaviour
---
- **Dramafinder 2.2.3** ([#448](https://github.com/parttio/unified-testing-tool/issues/448)): `unified-test-bom`
  manages Dramafinder 2.2.3 (was 2.2.2).
  - **Waiting after an action is Dramafinder's own wait on Playwright.** Since 2.2.3 that wait also covers a page
    load the action started (`Page.setLocation(..)`, a link to another document, a form post), and Flow starting the
    new page's UI ([parttio/dramafinder#320](https://github.com/parttio/dramafinder/issues/320)). The adapter's own
    navigation, page-load and idle scripts are gone. Its 5-second idle window before Dramafinder's 30-second wait is
    gone with them; the wait before an action ([ADR 0014](../adr/0014-playwright-actions-wait-for-flow-idle-before-acting.md))
    keeps its 5-second cap.
  - **Selecting an already selected item of a `MultiSelectComboBox` keeps it selected on Playwright.** It used to
    toggle the item off, and the select then failed, unlike on the other Testing Tools
    ([parttio/dramafinder#321](https://github.com/parttio/dramafinder/issues/321)).
