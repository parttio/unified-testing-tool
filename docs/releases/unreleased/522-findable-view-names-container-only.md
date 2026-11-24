---
section: Fixed
---
- **The Findable View names a container only when it narrows the alike down**
  ([#522](https://github.com/parttio/unified-testing-tool/issues/522)): `not unique: 7 alike` instead of
  `…; this one in container (test id “meal-row”)` when every row shares that test id; and
  `not unique: 3 alike; 2 in container (test id “billing”)` when the one container with that test id holds several.
