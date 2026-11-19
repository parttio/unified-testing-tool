# Wrappers for your own components

`page.findComponent(MyComponent.class)` already works for any component with a `@Tag` ([Components without a Wrapper](finding-components.md#components-without-a-wrapper)). Write a Wrapper when tests use a component often: it gives the component its own `find(..)`, its own filters, actions named after what a user does, and its own words in the reports. Spec: [6. Wrappers](../spec/06-wrappers.md#custom-and-add-on-components).

## A Wrapper is an interface

No implementation class: the Unified API implements the interface at run time, on every Testing Tool. Methods are default methods written on `element()`, the [Unified Element](../spec/06-wrappers.md#unified-element).

```java
--8<-- "unified-test-demo/src/main/java/demo/addon/testing/UnifiedRating.java:wrapper"
```

| Part | Role |
|---|---|
| `@UnifiedComponent(Rating.class)` | the Flow class for Browserless; the tag for browsers comes from its `@Tag` (`tag = "…"` for a `Composite`) |
| `extends UnifiedWrapper` | makes it a Wrapper: `element()`, `withTimeout(..)`, the Escape Hatch |
| `CommonFilters<UnifiedRating>` | makes it findable with `page.find(UnifiedRating.class)`, with the filters every Wrapper has: `withAccessibleName`, `withTestId`, `withId`, `visible()`, `where(..)`, `all()`, … |
| `LabelFilter<UnifiedRating>` | the filter mixins it accepts besides those: `LabelFilter`, `TextFilter`, `PlaceholderFilter` |
| default methods | your own filters, actions and getters, written on `element()` |

Each filter mixin takes the Wrapper itself as its type parameter, so `withLabel("Food")` returns a `UnifiedRating`. You list the mixins and write no filter code: the Unified API implements them.

Use it like a built-in Wrapper:

```java
--8<-- "unified-test-demo/src/test/java/demo/AddonTest.java:wrapperWithoutOverride"
```

Wrapper interfaces must be public, and so must any class they are nested in. An add-on can ship its Wrappers in its own jar, with the Unified API as an `optional` dependency ([ADR 0003](../adr/0003-wrappers-are-interfaces-shipped-in-the-addon-jar.md)).

## Custom filters

A filter of your own is a default method that returns the Wrapper, built from `where(Predicate<UnifiedElement>)`, `withProperty(..)` or `withAttribute(..)`. `withValue(int)` above is one:

```java
--8<-- "unified-test-demo/src/test/java/demo/AddonTest.java:customFilters"
```

A filter declared on the Wrapper reads as itself in the reports and in failures (`rating (value “4”)`), not as `where(..)`. A `where(..)` written inline in a test can't be read, so the reports show a gap line for it: give the condition a name on the Wrapper instead.

## Its words in the reports

Every action of a Wrapper is a User Step in the [reports](reports-and-failures.md). By default the component kind is the Wrapper's name in words, without `Unified`, and the verb comes from the core verb table (`click`, `setValue` → *fill*, …) or the method name in words. `UnifiedRating.rate(3)` reads:

```
   2  rate rating “Food” with “3”
```

When that doesn't read like what a user does, name the kind with `@UnifiedComponent(kind = "…")` and the verb with `@UserStepVerb("…")`. `{value}` places the arguments:

```java
--8<-- "unified-test-demo/src/test/java/demo/UnifiedStarRating.java:wrapper"
```

```java
--8<-- "unified-test-demo/src/test/java/demo/AddonTest.java:wrapperWithItsOwnWords"
```

```
✔ AddonTest › The user gives the food three stars   browserless · 1.3 s
   1  open Addon
   2  give “3” stars to star rating “Food”
   3  see star rating “Food” (stars “3”)
```

## A component built from a common one

`MetricCard extends VerticalLayout` has the tag of a `VerticalLayout`: a browser can't tell it apart by class. Add a `selector` with what does:

```java
--8<-- "unified-test-demo/src/test/java/demo/UnifiedMetricCard.java:wrapper"
```

```java
--8<-- "unified-test-demo/src/test/java/demo/DashboardTest.java:findsCardsByTheirWrapper"
```

`selector` is a compound CSS selector of `.class`, `#id`, `[attribute="value"]` and `[theme~="value"]`, without spaces.

## When one Testing Tool needs other code

Browserless runs no client-side JavaScript, so content the browser draws isn't there. Override the methods that need it for that tool, in an interface extending the Wrapper:

```java
--8<-- "unified-test-demo/src/main/java/demo/addon/testing/UnifiedBadge.java:badge"

--8<-- "unified-test-demo/src/main/java/demo/addon/testing/UnifiedBadgeBrowserless.java:badgeBrowserless"
```

Tests use `UnifiedBadge` and stay the same on every tool. The override runs only on the Testing Tool it is for, so the tests need no `@RequiresTestingTool`.
