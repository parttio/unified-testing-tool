---
section: Changed behaviour
---
- **`UnifiedDatePicker.setValue(..)` sets a picker whose calendar is open, though its field is hidden**
  ([#516](https://github.com/parttio/unified-testing-tool/issues/516)): such as a week picker an application opens
  from a badge of its own. Auto-Wait waits for the field to be visible and enabled, or for the calendar to be open.
  With the field hidden, the date is set as a pick in the calendar sets it and the calendar closes; a visible field
  is set as before, in as many round trips. A hidden picker whose calendar is closed still fails as hidden.
